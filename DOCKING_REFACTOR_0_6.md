# Docking Refactor Plan for 0.6

## Status

This document defines the intended 0.6 refactor of docking, dock queues, station
waits, cargo transfer, unload handling, and restore handling.

- D0 baseline and characterization: complete.
- D1 typed model and read-only projection: complete.
- D2 endpoint and reservation ownership: complete.
- D3 physical handshake ownership: complete.
- D4 coordinator cutover for loaded playback: complete.
- D5 wait and cargo integration: complete.
- D6 unloaded and restore cutover: complete.

The refactor is foundational work for 0.6. It should be completed before the
Advanced Transponder starts depending on docking behavior.

The first objective is behavior preservation. New docking or drive-mode
features should not be added until the existing classic logistics behavior
passes the acceptance tests in this document.

## Why This Refactor Is Needed

Docking works in 0.5.1, but its coordination is still spread across
`VehicleRoutePlaybackService`, `DockingRuntime`, playback save/load, station
block entities, transponder block entities, station chunk loading, Sable force
loading, materialization, and schedule wait evaluation.

The main structural risks are:

- Docking is represented by several booleans, optional values, timers, and
  inferred conditions instead of one explicit phase.
- Loaded playback, unloaded playback, queue handling, and restore each contain
  their own docking decisions.
- Queue ownership currently uses route IDs, even though route identity can
  change during schedule leg handoff.
- `DockingRuntime` owns reservation state, connector discovery, redstone output,
  lock checks, transfer snapshots, and some failure conversion.
- Some APIs cannot distinguish success from a non-error wait. For example,
  starting a docking wait and being queued can both produce no failure.
- Correctness depends on call order: queue grant, approach, alignment, output
  activation, connector lock, wait timers, output clearing, lease release, and
  reservation release must happen in the right sequence.
- Restore reconstructs docking from a mixture of saved booleans, stop identity,
  reservation state, live controller state, and materialization state.

These are architecture risks, not a reason to replace working behavior all at
once.

## Non-Negotiable Invariants

The refactor must preserve all of the following:

### Persistent Data Safety

- Docking never decides whether a route or schedule is valid.
- Docking failure never deletes routes, schedules, dock links, cargo links, or
  Sable stored-body data.
- Missing station, dock, transponder, controller, body, sublevel, chunk, or
  runtime visibility produces a typed blocked/recovering/fault result.
- Persistent route and schedule cleanup remains limited to the established
  explicit deletion paths.

### Dock Identity

- An explicit stop station or dock identity is authoritative.
- If an explicit destination station is unavailable, docking waits or faults.
  It never falls back to a different station.
- Legacy fallback is allowed only for old saved stops that contain no explicit
  destination identity.
- A reservation is attached to one exact dock target in one dimension.

### Queue Safety

- One dock has at most one granted docking session.
- Queue order is deterministic and survives save/load.
- A queued ship cannot activate dock outputs or begin a dock-lock timeout.
- Queue release transitions a ship to approach, not directly to docked or
  waiting.
- A ship retains the reservation until it has departed the configured clearance
  distance.
- The next ship cannot approach until the previous holder has cleared that
  distance.

### Physical Docking Safety

- Dock outputs are enabled only after reservation grant and confirmed arrival
  at the recorded endpoint pose.
- Position and rotation are both checked before the connector handshake starts.
- Dock wait and cargo condition timers start only after the expected connector
  pair is physically locked.
- A lock to an unexpected connector is a typed fault.
- A physically docked ship remains held at the confirmed dock pose.
- Failure and completion clear route-owned outputs before releasing physical
  loading leases or reservation ownership.
- Output clearing is owner-checked so one ship cannot clear another ship's
  output.

### Loaded and Unloaded Equivalence

- A schedule has one logical docking phase regardless of whether the player is
  nearby.
- Unloaded transit may simulate travel and queue waiting.
- Physical docking and physical cargo transfer require a materialized body and
  loaded interaction area.
- Cargo is never simulated or moved magically while the physical ship
  inventories, pipes, belts, or connectors are unavailable.
- A queued stored ship remains stored until it is safe and necessary to
  materialize it.
- Materialization is requested only through `ShipMaterializationService`.

### Runtime and UI Safety

- The active runtime remains inspectable in every blocked, recovering, and
  faulted docking phase.
- Menu and status projection are read-only.
- UI status is derived from a docking snapshot; opening a menu never advances,
  repairs, or reconciles docking.
- Commands can inspect and stop a docking session by stable transponder
  identity.

## Target Ownership Model

`VehicleRoutePlaybackService` remains the owner of route progress. It asks the
docking layer what must happen at the current stop and acts on a typed result.
It must not coordinate individual docking steps itself.

The target components are:

### `DockingCoordinator`

The single owner of a ship's docking session.

Responsibilities:

- Advance the docking state machine.
- Coordinate reservation, physical approach, connector handshake, wait
  evaluation, release, and recovery.
- Persist and restore the docking session snapshot.
- Produce read-only status and diagnostic snapshots.
- Return typed commands to playback, motion, materialization, and station
  loading boundaries.

It does not own:

- Route validity or cleanup.
- Schedule validity or cleanup.
- General route motion.
- Sable body lifecycle.
- UI mutation.

### `DockReservationService`

The single owner of queue order and dock occupancy.

Responsibilities:

- Key reservations by exact dock target.
- Queue by stable docking-request identity.
- Grant, transfer, release, and restore reservations.
- Track clearance after departure.
- Refuse ambiguous or unproven release.

Suggested identities:

```text
DockTargetId:
  dimension
  stationId
  stationPos
  stationDockPos

DockRequestId:
  transponderId
  scheduleExecutionId
  stopId
```

The route ID may be retained as diagnostic metadata, but it should not be the
primary queue identity. This removes route-leg handoff from reservation
correctness.

### `DockEndpointResolver`

A read-only, typed resolver for the exact station, station connector, ship
transponder, and ship connector.

Example results:

- `READY`
- `STATION_NOT_LOADED`
- `STATION_ID_MISMATCH`
- `STATION_DOCK_MISSING`
- `STATION_DOCK_AMBIGUOUS`
- `SHIP_BODY_NOT_LOADED`
- `SHIP_TRANSPONDER_MISSING`
- `SHIP_DOCK_MISSING`
- `SHIP_DOCK_AMBIGUOUS`
- `WRONG_CONNECTOR_LOCKED`
- `CHUNK_NOT_READY`

Temporary absence is not structural invalidity.

### `DockHandshakeService`

The only owner of dock output and connector handshake operations.

Responsibilities:

- Claim route/session-owned station and ship outputs.
- Confirm the expected connector pair.
- Report lock progress and timeout diagnostics.
- Clear owned outputs in a defined order.
- Reset or release a connector pair only when explicitly requested with proof.
- Capture physical transfer snapshots after lock.

This service must not reserve docks, move ships, tick schedule waits, or decide
route validity.

### `StationWaitRuntime`

The owner of stop-condition evaluation after arrival.

Responsibilities:

- Evaluate grouped time, redstone, dock, item, fluid, and cargo conditions.
- Start dock-dependent timers only after physical lock.
- Preserve timer state across save/load.
- Report `PENDING`, `SATISFIED`, or a typed failure.

It does not acquire reservations, move the ship, activate outputs, or resolve
Sable bodies.

### Existing Boundaries

The refactor should keep and call the existing boundaries:

- `RouteMotionRunner` or the current movement adapter for approach and departure.
- `ShipMaterializationService` for body availability and restore.
- `StationChunkLoadingService` for temporary station interaction loading.
- Sable force-load leasing through one dedicated adapter.
- Runtime projection services for UI and command display.

## Docking Session State Machine

Use one versioned `DockingSession` instead of inferring phase from unrelated
booleans.

| State | Meaning | Allowed next states |
| --- | --- | --- |
| `NONE` | Current route progress is not interacting with a dock | `QUEUE_REQUESTED` |
| `QUEUE_REQUESTED` | Exact target is resolved and a reservation is requested | `QUEUED`, `APPROACHING`, `BLOCKED`, `FAULTED` |
| `QUEUED` | Request is ordered but not granted | `APPROACHING`, `BLOCKED`, `FAULTED`, `CANCELLED` |
| `APPROACHING` | Reservation is granted; ship moves toward the endpoint | `ALIGNING`, `BLOCKED`, `RECOVERING`, `FAULTED` |
| `ALIGNING` | Position is close enough and final rotation/pose is being acquired | `LOCKING`, `RECOVERING`, `FAULTED` |
| `LOCKING` | Outputs are owned and the expected connectors are handshaking | `DOCKED`, `RECOVERING`, `FAULTED` |
| `DOCKED` | Expected connector pair is physically locked | `WAITING`, `RELEASING`, `RECOVERING`, `FAULTED` |
| `WAITING` | Schedule conditions and physical cargo transfer are active | `RELEASING`, `RECOVERING`, `FAULTED` |
| `RELEASING` | Conditions completed; outputs and connector lock are released | `DEPARTING_CLEARANCE`, `RECOVERING`, `FAULTED` |
| `DEPARTING_CLEARANCE` | Ship has left but still owns the reservation | `COMPLETE`, `RECOVERING`, `FAULTED` |
| `COMPLETE` | Docking session is terminal and route progress may continue | `NONE` |
| `BLOCKED` | Required temporary context is unavailable; no destructive action | Previous safe phase, `RECOVERING`, `FAULTED`, `CANCELLED` |
| `RECOVERING` | Restore or physical state must be re-confirmed | A proven safe phase, `BLOCKED`, `FAULTED` |
| `FAULTED` | Inspectable fault requiring retry, stop, or admin action | `RECOVERING`, `CANCELLED` |
| `CANCELLED` | Explicit runtime stop/kill has safely released owned resources | `NONE` |

`BLOCKED` must retain the intended resume phase and reason. It must not guess a
new phase from current block-entity visibility.

## Transition Rules

The following ordering is mandatory:

```text
resolve exact target
  -> request reservation
  -> queue or receive grant
  -> ensure interaction loading
  -> request materialization if needed
  -> approach recorded endpoint
  -> align position and rotation
  -> claim dock outputs
  -> confirm expected connector pair
  -> start schedule wait/cargo timers
  -> satisfy conditions
  -> clear owned outputs
  -> release physical dock/interaction leases
  -> depart
  -> clear configured distance
  -> release reservation
  -> continue schedule
```

Prohibited transitions:

- `QUEUED -> LOCKING`
- `QUEUED -> WAITING`
- `APPROACHING -> WAITING`
- `LOCKING -> WAITING` without confirmed expected-pair lock
- `WAITING -> COMPLETE` without output release
- Restore directly to `DOCKED` based only on a saved boolean
- Any failure transition that deletes persistent route or schedule data

## Typed Tick Contract

Playback should call one method, conceptually:

```java
DockingTickResult tick(DockingTickContext context, DockingSession session);
```

The result should separate state from requested side effects:

```text
DockingTickResult:
  updated session
  outcome:
    IN_PROGRESS
    ROUTE_MAY_CONTINUE
    BLOCKED
    FAULTED
  motion request:
    NONE
    HOLD_CURRENT_POSE
    MOVE_TO_QUEUE_HOLD
    MOVE_TO_DOCK_POSE
    DEPART_DOCK
  materialization request, if required
  interaction-loading request, if required
  failure/reason code, if present
```

There must be no `Optional.empty()` result that can mean both "started" and
"not granted".

Side effects should be idempotent. Repeating a tick after a save, lag spike, or
partial restore must not enqueue twice, activate two loads, reset a wait timer,
or claim another ship's output.

## Save and Restore Model

`DockingSession` and reservations should have explicit schema versions.

Minimum saved session fields:

- Schema version.
- Stable transponder ID.
- Schedule execution ID.
- Stop ID.
- Current route/segment ID as diagnostic context.
- Exact dock target identity.
- Current phase.
- Resume phase when blocked/recovering.
- Queue request identity and queue position snapshot.
- Recorded queue hold pose, if one exists.
- Recorded dock endpoint pose.
- Whether outputs are owned.
- Whether expected connector lock was last confirmed.
- Condition runtime/timer state.
- Reservation clearance origin and required distance.
- Last transition reason and game time.

Restore order:

1. Load persistent route and schedule data.
2. Load docking reservations without granting new requests.
3. Rebind schedule runtime and playback by stable transponder identity.
4. Rebind each `DockingSession`.
5. Resolve the exact station and endpoint.
6. Query materialization status through `ShipMaterializationService`.
7. Re-confirm physical connector lock; never trust the saved lock flag alone.
8. Resume from the safest proven phase.
9. Enable outputs only if the restored phase and physical proof require them.

Examples:

- Saved `QUEUED`: restore queue order and hold. Do not materialize at the dock.
- Saved `APPROACHING`: re-confirm reservation, then restore to route/approach
  pose.
- Saved `LOCKING`: clear stale owned outputs first, re-align, then start a new
  handshake.
- Saved `DOCKED` or `WAITING`: if the expected pair is still locked, resume
  waiting without resetting completed condition progress. Otherwise enter
  `RECOVERING` and reacquire pose/lock before timers continue.
- Saved `RELEASING`: finish idempotent output and lease release, then depart.
- Missing station/body/controller/chunk: enter `BLOCKED`; retain the session and
  saved route/schedule data.

## Loaded and Unloaded Behavior

The state machine is shared; only available capabilities differ.

### While Unloaded

- Route travel may advance through the existing unloaded simulation.
- Queue request and queue order may advance logically.
- A queued ship can remain stored.
- Time-of-day or scheduled-delay waits may advance only if their established
  semantics allow unloaded evaluation.
- Dock lock, item transfer, fluid transfer, dock inactivity, and cargo
  conditions do not advance without the required physical context.

### Before Physical Interaction

- Request station interaction chunk loading.
- Request ship body materialization through `ShipMaterializationService`.
- Keep a queue holder away from the dock until its reservation is granted.
- Materialize at a safe route/hold/approach pose, not blindly at the dock
  endpoint.
- Run approach and final alignment before enabling outputs.

### If the Player Arrives Mid-Operation

- Continue the same saved docking phase.
- Do not restart the queue request, wait timer, or cargo baseline.
- Do not teleport a queued ship onto an occupied dock.
- Do not briefly release gravity/control before hold or restore pose is
  established.

## Failure and Cleanup Order

For stop, kill, completion, fault, or explicit cancellation:

1. Freeze or stop new docking transitions.
2. Clear session-owned station and ship outputs.
3. Release/reset the expected connector pair only when needed and proven.
4. Release the Sable dock-stop force-load lease.
5. Release temporary station interaction loading.
6. Release or cancel the exact reservation request.
7. Retain an inspectable terminal/fault snapshot where runtime policy requires
   it.

Failure cleanup must be idempotent and safe when any block entity is unloaded.
If an owned output cannot be reached, record a targeted deferred output clear
by exact station/transponder identity. Do not clear unrelated outputs through a
fallback station.

## Logging and Debugging

Every docking state transition should produce one structured transition log:

```text
docking transition
transponderId
scheduleExecutionId
routeId
stopId
dockTarget
fromState
toState
reasonCode
queuePosition
bodyStatus
stationLoaded
shipLoaded
outputsOwned
expectedPairLocked
positionError
rotationError
```

High-frequency pending logs should be rate-limited and emitted only when the
reason or relevant measurement changes.

Debug/admin inspection should show:

- Dock reservation holder and ordered queue.
- Stable request identities and associated ship names.
- Docking phase for each active transponder.
- Intended station and connector positions.
- Current body/materialization status.
- Output ownership.
- Expected-pair lock diagnostic.
- Wait-condition timer summary.
- Interaction and Sable lease ownership.
- Last transition and reason.

Any repair/reset command must be explicitly named, permission-gated, targeted,
and logged. Inspection commands remain read-only.

## Phased Implementation

Each phase must compile and pass its focused tests before the next phase starts.
Do not leave temporary dual writers.

### D0 - Baseline and Characterization

- Record the current 0.5.1 behavior matrix and representative transition logs.
- Add focused tests around current queue ordering, output ownership, wait timer
  start, restore, and reservation clearance.
- Capture save fixtures for 0.4.5 and 0.5.1 runtime data.
- Make no gameplay behavior changes.

Exit gate:

- Existing behavior is reproducible and failures can be attributed to a phase.

### D1 - Typed Model and Read-Only Projection

- Add `DockingPhase`, `DockingSession`, `DockTargetId`, `DockRequestId`, and
  typed reason/result values.
- Derive a shadow `DockingSession` snapshot from existing playback fields.
- Add transition validation and diagnostics without changing control flow.
- Add schema-versioned serialization with legacy read compatibility.

Exit gate:

- The typed model represents every current loaded, unloaded, queued, waiting,
  paused, and restored state without ambiguity.

### D2 - Endpoint and Reservation Ownership

- Extract `DockEndpointResolver`.
- Extract `DockReservationService`.
- Change queue identity from route ID to stable docking-request identity.
- Preserve queue order and route-leg handoff compatibility through migration.
- Remove exact-target fallback to unrelated stations.
- Keep connector activation and wait logic on the old path for this phase.

Exit gate:

- Queue behavior and restore order match 0.5.1, with no route-ID handoff
  dependency.

### D3 - Physical Handshake Ownership

- Extract `DockHandshakeService`.
- Move output claim/clear, connector pair checks, lock timeout, transfer
  snapshots, and lock diagnostics behind it.
- Use typed handshake results.
- Enforce position and rotation proof before output activation.
- Enforce output-clear-before-lease-release ordering.

Exit gate:

- Loaded single-ship docking, failure, retry, stop, and reload pass without
  playback directly manipulating dock outputs or connectors.

Implemented:

- `DockHandshakeService` is the sole runtime owner of dock output claims,
  connector-pair verification, lock timeout, transfer snapshots, diagnostics,
  and explicit connector-pair reset.
- Playback supplies position and rotation proof and consumes typed handshake
  results; it no longer manipulates dock outputs or connectors directly.
- Output ownership conflicts are refused instead of overwritten, queued ships
  cannot claim outputs, and cleanup clears owned outputs before releasing the
  Sable dock lease.
- Focused tests cover pose-proof validation and the elapsed-time lock clock.

### D4 - Coordinator Cutover for Loaded Playback

- Introduce `DockingCoordinator`.
- Move loaded queue, approach, alignment, locking, waiting, release, and
  clearance transitions into the coordinator.
- Playback consumes typed outcomes and delegates movement through the existing
  motion boundary.
- Move docking-related playback booleans into `DockingSession`.
- Keep non-docking schedule waits behavior-compatible.

Exit gate:

- `VehicleRoutePlaybackService` no longer contains loaded docking sequencing.
- Two loaded ships sharing one dock pass ordering and clearance tests.

Implemented:

- `DockingCoordinator` now owns loaded reservation, queue, approach, alignment,
  locking, docked wait, release, departure-clearance, pause, fault, and resume
  phase transitions.
- Loaded playback consumes typed queue and handshake directives and applies
  movement or hold operations through the existing vehicle-controller boundary.
- `DockingSession` is authoritative for loaded docking phase, output evidence,
  connector-lock evidence, pause/fault state, and clearance progress.
- Stable schedule route handoff explicitly transfers the previous dock's
  departure-clearance phase into the next loaded playback.
- The old loaded `dockLocked` and dock-reacquire booleans were removed. Legacy
  NBT fields remain as compatibility readers/writers until D6/D7.
- Unloaded reservation handling and pending restore preflight remain on their
  existing paths for the deliberate D6 cutover.
- Focused tests cover queue grant/loss, approach recovery, explicit release and
  clearance phases, and inspectable fault recovery.

### D5 - Wait and Cargo Integration

- Extract `StationWaitRuntime` from playback.
- Start dock-dependent waits only after a confirmed expected-pair lock.
- Preserve grouped-condition semantics and saved timer progress.
- Keep physical cargo transfer dependent on loaded ship machinery.
- Confirm cargo baselines do not reset on ordinary reload/rebind.

Exit gate:

- Time, redstone, dock, inactivity, item, fluid, and cargo conditions match the
  0.5.1 behavior matrix.

Implemented:

- `StationWaitRuntime` now owns grouped station wait evaluation, including time,
  redstone, docked, dock-idle, and cargo wait condition decisions.
- `VehicleRoutePlaybackService` delegates station wait ticking through a narrow
  context adapter and keeps existing cargo/dock/redstone lookup behavior behind
  that adapter.
- Condition runtime state was moved out of playback so saved timer progress,
  idle baselines, cargo timeouts, and legacy condition-state migration keep the
  same NBT shape.
- Dock-dependent waits still do not tick until `DockingSession` reports the
  expected connector pair as physically locked.
- Legacy non-grouped dock idle/cargo wait helpers were removed from playback.
- Focused tests cover wait-type classification and runtime timer-state
  preservation/reset behavior.

### D6 - Unloaded and Restore Cutover

- Route unloaded queue gating through `DockingCoordinator`.
- Route materialization and interaction-loading requests through typed
  coordinator results.
- Replace ad hoc pending-dock restore preflight with session restore.
- Re-confirm exact physical state before resuming timers.
- Test player arrival during every phase.

Exit gate:

- Loaded, unloaded, and restored docking use the same state machine.
- No stored body is materialized onto an occupied dock.
- No route or schedule data is deleted by a restore failure.

Implemented:

- Unloaded dock queue gating now requests reservation/hold/approach through
  `DockingCoordinator` using `DockingRuntimeMode.UNLOADED`.
- Playback runtime mode changes update the active `DockingSession` explicitly
  when entering unloaded transit and after successful rehydration back to loaded
  playback.
- Station interaction loading now prefers the typed docking target before
  falling back to legacy stop/station fields.
- Pending dock restore preflight now restores or creates a typed
  `DockingSession`, routes queue decisions through the coordinator, and writes
  the resulting session back to pending runtime data.
- Restored dock timers are no longer restarted from saved lock booleans before
  the exact physical dock state is re-confirmed.
- Focused tests cover unloaded queue/approach mode preservation and restored
  session runtime-mode transitions.

### D7 - Remove Legacy Coordination

- Remove obsolete docking booleans, duplicate queue gates, handoff patches, and
  old restore inference only after all parity tests pass.
- Keep required 0.4.5 and 0.5.1 save migration readers.
- Remove migration writers once all new saves use the versioned session.
- Update architecture documentation and command diagnostics.

Exit gate:

- There is one writer for docking session state, one reservation owner, and one
  physical handshake owner.

## Acceptance Test Matrix

At minimum, test:

### Normal Operation

- One loaded ship docks, transfers items, waits, releases, and departs.
- One unloaded ship reaches a stop, materializes, docks, transfers, and departs.
- A large multi-chunk ship transfers through its real onboard machinery.
- A non-docking station stop never activates dock outputs.

### Queueing

- Two loaded ships approach one dock.
- Two unloaded ships approach one dock.
- One loaded and one unloaded ship approach one dock.
- Arrival order determines queue order.
- The next ship remains held until the previous ship clears the configured
  distance.
- Very short final route legs do not place the queue hold at the dock.
- Queue release starts approach and alignment, not lock or wait.

### Save and Restore

- Reload in every state in the state table.
- Reload while one ship is docked and another is queued.
- Reload twice while both ships are unloaded.
- Reload after outputs activate but before lock.
- Reload during cargo transfer.
- Reload during departure clearance.
- Old 0.4.5 and 0.5.1 saves migrate safely.

### Temporary Absence and Faults

- Destination station chunk temporarily unavailable.
- Ship body stored, loading, live, or controller-registration pending.
- Station connector missing or ambiguous.
- Ship connector missing or ambiguous.
- Connector locked to the wrong partner.
- Ship reaches the endpoint with incorrect rotation.
- Dock lock timeout.
- Cargo storage unavailable.
- Player stops or kills runtime in every phase.

### Safety Assertions

- No test deletes route, schedule, dock link, cargo link, or stored Sable data.
- No UI/menu action changes docking state.
- No queued ship activates dock outputs.
- No wait timer advances before physical lock when lock is required.
- No ship can own two dock reservations.
- No dock can grant two sessions.
- No ship is materialized onto an occupied dock.
- No ship clears another ship's outputs.
- Runtime remains listable, inspectable, and killable after every fault.

## 0.6 Completion Criteria

The docking refactor is complete when:

- `VehicleRoutePlaybackService` owns route progress but not docking sequencing.
- One `DockingCoordinator` owns each docking session.
- One `DockReservationService` owns deterministic queue state.
- One `DockHandshakeService` owns outputs and connector lock checks.
- Dock-dependent wait evaluation starts only after proven physical lock.
- Loaded, unloaded, and restored behavior use the same explicit phases.
- Stable transponder/session/stop identity replaces route-ID queue ownership.
- Save migration from 0.4.5 and 0.5.1 is covered.
- All acceptance tests pass with transition logs enabled.
- Existing 0.5.1 gameplay behavior is preserved unless a separately documented
  0.6 behavior change is intentionally approved.

## Relationship to Advanced Transponder Work

The Advanced Transponder should integrate through the same coordinator but
provide a different motion/control adapter.

It may influence approach, alignment, braking, and dock intent. It must not own
reservations, connector truth, wait timers, cargo completion, restore policy, or
route validity.

This boundary allows classic movement and Sable Drive Mode to share docking
rules without duplicating the fragile parts of docking.
