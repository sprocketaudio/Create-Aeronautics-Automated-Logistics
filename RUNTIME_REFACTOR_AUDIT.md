# Runtime Refactor Audit And Plan

This document audits the current automated ship runtime and defines a phased
refactor plan before any more runtime code is changed.

The current bug pattern is not a single defect. Route persistence, active
runtime state, Sable ship materialization, station chunk loading, cleanup, and
UI projection are coupled closely enough that fixing one lifecycle path can
change another. This plan treats the current working behavior as the
compatibility target, then separates ownership so future changes can be made
without rebreaking route saves, reloads, multi-ship schedules, or Sable restore.

## Goals

- Persistent route and schedule data must survive runtime failure,
  materialization failure, chunk unload, server restart, and missing live Sable
  bodies.
- Runtime state must be recoverable, inspectable, and stoppable without
  mutating route truth.
- Sable materialization must report body availability and restore results
  without deciding route validity.
- Menus, commands, toasts, and previews must display snapshots from persistent
  route state plus runtime state. They must not own route truth.
- The refactor must support the current recorded playback system and the future
  Advanced Transponder / Sable Drive Mode track.

## Non-Goals For The First Refactor Pass

- Do not redesign the schedule UI.
- Do not add Sable Drive Mode behavior during this refactor.
- Do not change recorded-route playback semantics unless the behavior matrix
  explicitly marks a behavior-changing phase.
- Do not replace Create-style UI presentation.
- Do not delete any cleanup behavior without replacing it with a safer,
  identity-aware equivalent.

## Hard Invariants

These are the rules the refactor must enforce.

- Runtime or materialization failure must never delete persistent route legs,
  schedules, dock links, or cargo links.
- `RouteSegmentRegistry` must become an index/cache only. It must not be the
  source of route truth.
- UI projection must never mutate saved route truth.
- Sable materialization must never decide schedule validity.
- Startup ordering alone must never determine whether saved routes are valid.
- Cleanup must only run for explicit lifecycle events or proven identity
  deletion, not for temporary missing runtime/materialization state.
- Schedule validity must be computed from persistent route data, not from
  whether transient registries are currently populated.
- Every active runtime must be anchored by stable identities:
  transponder id, schedule id or schedule revision, route segment id, route id,
  station ids, and linked Sable vehicle id where available.
- Every status shown to a player should come from a projection snapshot with
  its source clearly defined.

## A. Current Runtime Audit

### A.1 High-Level Ownership Map

| Area | Current owner | Current problem | Target owner |
| --- | --- | --- | --- |
| Recorded route legs | `AirshipStationBlockEntity.routeSegments` plus `RouteSegmentRegistry` | Persistent data and transient index are treated interchangeably in some paths | Persistent Route Model |
| Ship stop schedule | `ShipTransponderBlockEntity.ownedSchedule` | Schedule is saved correctly, but cleanup and validation can depend on route registry timing | Persistent Route Model |
| Active schedule progress | `AirshipScheduleExecutionService.activeSchedules` | Mixed with start validation, status projection, runtime reload, and playback coordination | Runtime State Machine |
| Active route playback | `VehicleRoutePlaybackService.activePlaybacks` | Mixed with physical motion, Sable restore, unloaded transit, wait logic, dock/cargo waits, and visuals | Runtime State Machine plus Motion Runner |
| Sable stored/live body | `ShipRecoveryService`, `SableSubLevelVehicleController`, `VehicleRoutePlaybackService` | Storage scanning, recovery commands, relocation, duplicate pruning, and runtime restore are mixed | Ship Materialization Layer |
| Station chunk loading | `StationChunkLoadingService` | Station chunk forcing is mixed with Sable startup cleanup hooks | Materialization support service |
| UI state | `ShipTransponderMenu`, `AirshipStationMenu`, `AirshipScheduleMenu`, screens, sync payloads | Much improved by recent menu DTO work, but still needs to be downstream of runtime snapshots | UI Projection Layer |
| Commands | `AutomatedLogisticsCommands` | Runtime controls depend on playback summaries and route id stability | Runtime command facade |
| Cleanup | `ScheduleRouteCleanup`, BE `setRemoved`, station ticks/opening | Cleanup can be called from display/tick paths and historically pruned routes too aggressively | Explicit lifecycle cleanup service |

### A.2 Persistent Route Model Audit

#### `AirshipStationBlockEntity`

Current responsibilities:

- Owns station identity, name, owner, selected transponder, dock link, cargo
  links, recorded route data, route segments, recording state, failure/status,
  and redstone output.
- Persists route segments under `ROUTE_SEGMENTS`.
- Registers station snapshots and route segments into transient registries.
- Calls cleanup and validation during server tick and menu creation.
- Handles route recording state and station playback status updates.

Important methods and paths:

| Method/path | Today | Risk | Target |
| --- | --- | --- | --- |
| `serverTick` | Refreshes dock link and calls `ScheduleRouteCleanup.pruneInvalidRouteSegments` every refresh interval | Tick-time cleanup can alter persistent route availability due to transient conditions | Tick may refresh live dock status only; route cleanup moves to explicit lifecycle service |
| `createMenu` | Registers station snapshot, prunes route segments, refreshes dock link, reconciles selected ship runtime | UI open has side effects on persistent data and runtime | Build menu projection only; no route pruning |
| `addRouteSegment` | Adds segment, prunes segment history, updates `RouteSegmentRegistry`, syncs UI | Persistent write and transient index update are mixed but acceptable if index is best-effort | Write through route repository, then update route index |
| `removeRouteSegment` | Removes route segment and updates registry | Explicit user delete path, should remain valid | Move to route repository/delete transaction |
| `registerLoadedSnapshot` | Updates identity directory, station registry, chunk loading, and route registry | BE load performs several cross-layer side effects | Replace with startup reconciliation service and route index rebuild |
| `setRemoved` | Clears registries and previews | Correct intent, but must not run for normal unload as if deleting station | Split unload vs block deletion handling clearly |
| `writeStationData` / `loadAdditional` | Saves/loads station data and UI-ish status snapshots | Durable status and live runtime status are mixed | Save durable route/dock/cargo identity only; project runtime separately |

Target after refactor:

- Station BE owns durable station data only: identity, name, owner, selected
  ship preference, route segments, dock link, cargo link, and recording buffer.
- It should not decide active runtime status.
- It should not prune schedules.
- It should not use client/UI sync to keep route truth valid.

#### `ShipTransponderBlockEntity`

Current responsibilities:

- Owns transponder identity, ship name, owner, runtime ship id, last known
  position, dock link, dock output, append mode, owned schedule, recording
  target, runtime status, schedule item slot, cargo links, cargo summary, and
  cargo failure context.
- Refreshes Sable runtime ship and dock link every tick interval.
- Creates transponder menu and reconciles runtime status on menu open.
- Stores `runtimeStatus` as block entity state.

Important methods and paths:

| Method/path | Today | Risk | Target |
| --- | --- | --- | --- |
| `serverTick` | Refreshes runtime ship and ship dock link | OK for live discovery, but status should come from runtime projection | Keep discovery, output snapshot to projection service |
| `createMenu` | Refreshes runtime ship, dock link, migrates schedule, reconciles runtime status, builds menu | Menu open has runtime side effects | Build projection from persistent route model plus runtime snapshot |
| `setOwnedSchedule` | Saves schedule and syncs UI | Correct persistent write, but downstream cleanup must be safe | Route model write transaction |
| `setRuntimeStatus` | Mutates BE status | Durable BE state can drift from runtime truth | Replace with projected runtime status snapshot |
| `pruneInvalidOwnedSchedule` | Delegates to `ScheduleRouteCleanup` | Historically dangerous if registry incomplete | Only explicit identity deletion may prune |
| `setRemoved` | Transponder teardown and cleanup | Correct place for explicit delete cleanup, but must clear runtime holds and previews without orphaning other ships | Explicit transponder deletion transaction |

Target after refactor:

- Transponder BE owns durable ship/transponder identity, route schedule, dock
  link, cargo link, and recording preferences.
- Runtime state is not stored as authoritative BE state.
- Runtime state is projected from `RuntimeStateMachine.snapshot(transponderId)`.

#### `RouteSegmentRegistry`

Current responsibilities:

- Static map of `RouteSegmentId` to `RouteSegment`.
- Supports lookup by id, start station, end station, transponder, and pair.
- Updated from station load, station route add/remove, and cleanup paths.

Risk:

- It is transient and order-dependent.
- If used as truth during startup, route segments can appear missing until all
  stations have loaded and registered.
- If cleanup treats missing registry entries as invalid data, saved routes can
  be lost.

Target:

- Rename conceptually to `RouteSegmentIndex`.
- Rebuild from persistent station route data.
- Missing index entries mean "index incomplete", not "route deleted".
- Resolver must be able to query persistent route repository when index misses.

#### `RouteSegmentResolver`

Current responsibilities:

- Finds newest valid segments from `RouteSegmentRegistry`.

Risk:

- It inherits registry incompleteness.

Target:

- `RouteSegmentResolver` becomes a query facade backed by persistent route
  repository plus optional index acceleration.
- It should return structured results:
  - found exact pinned segment
  - found compatible fallback
  - missing start station
  - missing target station
  - route leg absent
  - route index not ready

#### `ScheduleRouteCleanup`

Current responsibilities:

- Prunes transponder schedules.
- Removes routes for deleted stations and transponders.
- Removes route segments from loaded stations.
- Prunes invalid route segments from stations.
- Prunes loaded transponder schedules after route changes.

Risk:

- It mixes explicit deletion cleanup with inferred invalidity cleanup.
- Some methods are safe only for explicit delete, but are callable from tick/menu
  paths.
- It uses `RouteSegmentRegistry` heavily, which makes cleanup sensitive to
  transient registry state.

Target:

- Split into:
  - `RouteDeletionCleanup` for explicit station/transponder deletion.
  - `RouteScheduleRepair` for user-initiated repair.
  - `RouteIndexReconciler` for index rebuild and diagnostics.
- No automatic cleanup should delete persistent route/schedule data due to
  temporary missing runtime or registry state.

### A.3 Runtime State Machine Audit

#### `AirshipScheduleExecutionService`

Current responsibilities:

- Owns `activeSchedules`, `lastStartFailures`, and cargo failure context.
- Starts schedules from station/transponder.
- Validates schedule chain, docking requirements, cargo requirements, and start
  station.
- Starts route playback through `VehicleRoutePlaybackService`.
- Handles schedule completion and next-leg advancement.
- Saves and reloads runtime state.
- Reconciles transponder runtime status.

Current coupling:

- Reads persistent transponder schedule and route registry.
- Writes station status and transponder runtime status.
- Calls playback service and depends on route id.
- Stores failure summaries used by UI.
- Computes UI-visible status text indirectly through block entities and menus.

Important methods/paths:

| Method/path | Today | Risk | Target |
| --- | --- | --- | --- |
| `start` / `startFromTransponder` | Validates, creates active schedule, starts playback | Validation, runtime mutation, and UI status mutation happen together | Transition command on runtime state machine |
| `startEntry` | Resolves route segment and starts playback | Route resolving can depend on transient registry | Use route repository query result |
| `completeRoute` / advance paths | Moves to next schedule entry | Multiple active ships and route ids must stay stable | Explicit state transition with persisted transition record |
| `loadRuntime` / `saveRuntime` | Serializes active schedules and failures | Runtime can restore before persistent route index is ready | Runtime loader enters `PendingRestore` until dependencies report ready |
| `reconcileRuntimeStatus` | Updates transponder runtime status | UI/status mutation can hide runtime mismatch | Projection service should derive status without mutating BE |
| `pauseRuntimePlayback` / command paths | Delegates to playback and marks state | Need stable command target independent of current route id | Runtime id should be transponder/schedule anchored |

Target:

- `AirshipScheduleExecutionService` becomes or delegates to an explicit
  `ScheduleRuntimeStateMachine`.
- It owns schedule runtime records only:
  - transponder id
  - schedule revision/hash
  - current entry index
  - current station id
  - active route id if any
  - runtime mode/state
  - last failure
  - pause reason
  - wait context
- It never mutates route definitions.
- It emits runtime snapshots for UI and commands.

#### `VehicleRoutePlaybackService`

Current responsibilities:

- Owns active route playbacks.
- Applies movement, hold, pause, fault, wait, dock, cargo, restore catch,
  unloaded transit, endpoint settle, stalled detection, visuals, and runtime
  persistence.
- Stores pending runtime playbacks when Sable body is not materialized.
- Calls `ShipRecoveryService` for stored Sable lookup/move/materialize/prune.
- Mutates station state and dock outputs.

Current coupling:

- Physical playback, wait state, Sable restore, unloaded simulation, and UI
  visuals are all in one large service.
- Materialization failures and route runtime failures share logic.
- Stored Sable cleanup can run while restoring route runtime.

Important methods/paths:

| Method/path | Today | Risk | Target |
| --- | --- | --- | --- |
| `startPlayback` | Validates station/controller, creates active playback, primes motion, starts station status | Playback start is physical and UI-facing | Runtime transition plus motion runner start |
| `stopPlayback` | Stops controller, clears dock outputs, updates station state | Stop can be called for runtime, teardown, command, failure | State machine event with reason |
| `loadRuntime` / `restorePendingRuntime` | Restores pending playbacks and possibly prunes stored ship entries | Startup order and Sable availability drive runtime behavior | Runtime restore waits for materialization service readiness |
| `saveRuntime` | Saves active and pending playback tags | Correct but currently saves implementation details | Persist stable runtime snapshot, not controller internals where avoidable |
| `tick` / movement loop | Applies motion and waits | Too many states hidden in one tick path | Delegate to `RouteMotionRunner` and `WaitRuntime` |
| `ActivePlayback` | Holds route, controller ref, pause state, wait state, dock state, restore state, movement state | Large mutable state object spans every layer | Split into runtime record plus motion/materialization state |
| `pendingRuntimePlaybacks` | Stores route tags waiting for restore | Pending route can become disconnected from schedule runtime | Pending runtime anchored by transponder/schedule, not only route id |

Target:

- Split into:
  - `RoutePlaybackRuntime` for route-level state.
  - `RouteMotionRunner` for loaded physical motion.
  - `WaitRuntime` for wait condition state.
  - `DockRuntimeCoordinator` for docking waits and redstone output.
  - `UnloadedTransitRuntime` for estimated progression.
  - `MaterializationClient` interface for Sable body availability.
- Keep public behavior stable by introducing wrappers first.

#### `AutomationRuntimeSavedData`

Current responsibilities:

- Captures `PLAYBACK.saveRuntime()` and `SCHEDULES.saveRuntime()`.
- Applies saved schedule runtime then playback runtime.
- Logs snapshot counts.

Risk:

- Apply ordering matters.
- Playback restore can happen before persistent route/model/index readiness.
- Runtime saved data is split across schedule/playback services but has no
  unified lifecycle version or recovery state.

Target:

- Save a single `RuntimeSnapshot` with explicit sections:
  - schema version
  - active schedules
  - active route playbacks
  - waits
  - pending materialization requests
  - failures
  - command pause/kill markers
- Apply snapshot into a `RuntimeRestoreCoordinator`.
- Restore should be staged:
  1. load raw runtime snapshot
  2. load persistent route model
  3. rebuild indexes
  4. bind runtime schedules to route model
  5. request materialization
  6. resume or pause with explicit failure

### A.4 Ship Materialization Audit

#### `SableSubLevelVehicleController`

Current responsibilities:

- Resolves Sable sublevel controllers by id, local position, world position, or
  Sable helper.
- Applies route movement, hold, stop, and position/rotation reads.
- Handles low-level Sable motion and stabilization.

Risk:

- Controller resolution is called by runtime and can fail for reasons unrelated
  to route validity.
- It knows enough about Sable storage/world position that it should be below the
  runtime layer.

Target:

- Keep low-level Sable API access here.
- Surface typed results:
  - `LoadedBodyAvailable`
  - `StoredBodyFound`
  - `StoredBodyMissing`
  - `SublevelLoadFailed`
  - `UnsafeToMove`
  - `ControllerBlockMissing`
- Avoid returning plain `Optional.empty()` for materially different failures.

#### `ShipRecoveryService`

Current responsibilities:

- Command recovery and player teleport helpers.
- Stored Sable ship lookup.
- Stored ship relocation/materialization.
- Stored pointer cleanup and duplicate/stale pruning.
- Ship identity lookup and known name/id suggestions.

Risk:

- This service is doing several jobs:
  - operator recovery commands
  - materialization mechanics
  - Sable stored data garbage collection
  - identity lookup
- Runtime restore calls cleanup paths that can affect Sable stored state.
- Recovery commands and runtime materialization share low-level mutation paths
  without a clear safety boundary.

Target:

- Split into:
  - `ShipMaterializationService`
  - `SableStoredShipRepository`
  - `SableStoredShipGarbageCollector`
  - `ShipRecoveryCommandService`
- Runtime may request materialization but cannot directly prune or relocate
  Sable storage except through audited materialization operations.
- Cleanup must log identity, reason, and proof before deleting stored Sable data.

#### `StationChunkLoadingService`

Current responsibilities:

- Force-loads station chunks when configured.
- Tracks station chunks through saved data.
- Handles startup grace and calls Sable stored ship cleanup during pre tick.

Risk:

- Chunk loading is a support concern, but startup Sable cleanup is a
  materialization concern.
- If cleanup timing changes here, runtime restore behavior changes.

Target:

- Keep force-loading here.
- Move Sable cleanup startup grace to `ShipMaterializationStartupService`.
- Expose station chunk readiness as a dependency for runtime restore.

### A.5 UI Projection Audit

The recent `MENU_SYSTEM_AUDIT.md` already maps the menu refactor. This runtime
plan treats that document as the UI sub-plan and adds the runtime boundary.

Current UI projection points:

- `ShipTransponderMenu.StatusSnapshot`
- `AirshipStationMenu.ClientState`
- `SyncTransponderMenuStatePayload`
- `SyncStationMenuStatePayload`
- `SyncTransponderOwnedSchedulePayload`
- `SyncStationRouteChoicesPayload`
- `SetFlightPathPreviewPayload`
- `ShowShipTransponderHighlightPayload`
- Screens that render transponder/station/schedule buttons, tooltips, colors,
  route previews, dock/cargo previews, and action messages.

Current risks:

- UI is much better than before, but still depends on runtime services and BE
  state being reconciled correctly.
- Route preview state is client-global and can outlive the block that produced
  it unless explicit clear events fire.
- Broad block entity sync was previously used for UI freshness and caused NBT
  risks; targeted menu packets are the correct direction.

Target:

- Introduce a `RuntimeProjectionService`.
- Projection snapshots are generated on the server from:
  - persistent route repository
  - runtime state machine snapshot
  - materialization status snapshot
  - dock/cargo link summaries
- Menus display snapshots only.
- Commands use the same projection where possible.

### A.6 Command Audit

#### `AutomatedLogisticsCommands`

Current responsibilities:

- Registers `/aal` command tree.
- Provides ship recovery, teleport-to-ship, runtime list, pause, show, and kill.
- Filters runtime list by owner for normal players and all active ships for ops.
- Builds clickable command output.

Risks:

- Runtime command targets are route-id based in places, but user intent is
  transponder/schedule based.
- If a route advances between list and click, stale route ids can fail.
- Kill/pause/show should operate on active transponder runtime, not current leg
  route id only.

Target:

- Commands call a `RuntimeCommandFacade`.
- Runtime entries have stable command ids, preferably transponder id plus active
  runtime generation/revision.
- Command output is a projection of active runtime records, not active route
  playback internals.

### A.7 Dock And Cargo Runtime Audit

Current areas:

- `DockingRuntime`
- `DockingConnectorDiscovery`
- `CargoLinkDiscovery`
- `StationCargoSavedData`
- `TransponderCargoSavedData`
- `CustomCargoEndpointSupport`
- Dock/cargo wait code inside `VehicleRoutePlaybackService`.

Risks:

- Dock and cargo links are persistent setup data, but dock/cargo wait progress
  is runtime state.
- Dock redstone output is physical runtime state, but link existence belongs to
  persistent route setup.
- Cargo/dock validation messages should be projection-level results, not direct
  BE state mutations.

Target:

- Link definitions live in persistent route model.
- Wait progress lives in runtime state machine.
- Physical dock/cargo operations live in loaded materialization/wait adapters.
- UI gets compact dock/cargo summaries from projection snapshots.

### A.8 Debug Logging Audit

Current debug channels:

- master `debugLogging`
- `playback`
- `vehicle`
- `docking`
- `cargo`
- `uiSync`

Current issue:

- Channels exist, but runtime/materialization/persistence concerns are still
  mixed inside playback/vehicle logs.
- Some useful events need strict once-per-transition logging rather than
  repeated tick spam.

Target additions:

- Keep channel style instead of levels.
- Add or split channels only if needed:
  - `runtime` for state-machine transitions
  - `materialization` for Sable body availability/load/restore
  - `persistence` for route/schedule/save/index rebuild
- Every state transition should log once with:
  - transponder id
  - ship name when known
  - route id/current leg
  - from state
  - to state
  - reason
  - loaded/materialized status

## B. Failure Analysis

### B.1 Confirmed Bugs And Concrete Risks

| Issue | Evidence / observed behavior | Boundary violation | Why it matters |
| --- | --- | --- | --- |
| Registry availability affected route/schedule status | Recent route-loss and partial-route regressions after reload; cleanup touched route data based on registry state | Persistent route model depended on transient index | Saved routes could disappear or appear invalid after startup order changes |
| Schedule status could show `Partial Route` while route would run | Route validation and status projection were not using the same resolved model | UI projection and runtime validation diverged | Players cannot trust readiness/status |
| Multi-ship runtime exposed stale or missing active wrappers | Runtime command and reload testing showed one ship could continue while another lost active state | Runtime state split across schedule/playback maps | Active schedule and active playback can fall out of sync |
| Station route lists showed orphaned/old routes | Station routes showed deleted station names and transponder-deleted routes | Cleanup/repository/index ownership unclear | Route display becomes polluted and dangerous to clean manually |
| Route preview state survived deletion/unload cases | Preview remained after transponder/station deletion | UI projection state not tied to producer lifecycle | Debugging and player feedback become misleading |
| Sable restore/toast failures recur around station entry/reload | Failed-to-load-sublevel toast seen repeatedly during unloaded/station restore testing | Materialization errors leak into runtime behavior | Route simulation can look correct but lose or fail physical body restore |
| Stored Sable cleanup can affect runtime restore | Cleanup functions are called from startup/chunk/runtime service paths | Materialization lifecycle and garbage collection are mixed | A cleanup fix can create ship-loss regressions |
| Commands initially targeted stale route legs | Runtime list click could fail after ship advanced | Command facade depended on current playback route id | Recovery command reliability depends on timing |
| Manual pause could resume when menu opened | Menu/runtime reconciliation changed active state | UI open mutated runtime state | Merely inspecting state can change execution |
| Broad BE sync caused earlier ghost block/NBT risk | 0.4.4 moved toward targeted menu packets | UI refresh and world BE sync were coupled | UI fixes can corrupt or interfere with Sable client updates |

### B.2 Suspected Risks

- `AirshipStationBlockEntity.serverTick` still running route cleanup is risky
  even after the latest narrow fixes.
- `createMenu` on station/transponder still performs refresh and reconcile work
  that could change state when the player only wants to inspect it.
- `VehicleRoutePlaybackService.ActivePlayback` is too large and mutable. It is
  difficult to reason about every pause/wait/restore/motion transition.
- Runtime saved data has no global transaction boundary between schedule state
  and playback state.
- Route preview and dock/cargo preview use client-global visual state; that
  needs producer identity and lifecycle ownership.
- Future Advanced Transponder work will multiply complexity if current
  recorded-playback runtime remains the only runtime shape.

### B.3 Design Smells

- Transient registry used as truth.
- Cleanup called from tick/open paths.
- Menu open reconciles runtime.
- Block entities store runtime-looking status.
- One service controls movement, waits, restore, Sable storage, visuals, and
  station status.
- `Optional.empty()` hides why Sable controller resolution failed.
- Same data represented in several places:
  - station status
  - transponder runtime status
  - active schedule status
  - active playback pause state
  - menu status snapshot

### B.4 Behavior That Must Be Preserved

- 0.4.4 style route/schedule persistence stability.
- Recording station-to-station route legs.
- Appending stops to transponder-owned schedules.
- Looping schedules defaulting to enabled.
- `No Route`, `Partial Route`, `Ready`, `Running`, `Waiting`, and paused fault
  status semantics.
- Starting from any valid station in a looped schedule.
- Multi-ship playback with shared stations.
- Pause/kill/show runtime commands, including clickable list actions.
- Transponder deletion releasing physics holds and deleting that ship's routes.
- Station deletion deleting route legs tied to that station and clearing previews.
- Dock and cargo waits when physically loaded.
- Current Create-style menu visuals, button layout, tooltip behavior, and
  read-only schedule runtime view.
- Station chunk loading option for route starts and station waits.

### B.5 Behavior That Should Intentionally Change

- Menu open should not prune routes or mutate runtime.
- Tick refresh should not prune persistent route data.
- Runtime restore should not directly prune Sable stored pointers unless an
  explicit materialization cleanup service returns proof and reason.
- Route/schedule readiness should be computed through one route model query,
  shared by UI and runtime start validation.
- Commands should target transponder runtime records, not only current route id.
- Sable materialization failures should produce a runtime materialization state,
  not route deletion or schedule pruning.

## C. Proposed Architecture

### C.1 Data Flow

```text
Persistent Route Model
    -> Route Query / Validation
    -> Runtime State Machine
    -> Route Motion Runner
    -> Ship Materialization Layer

Persistent Route Model
    -> UI Projection Layer

Runtime State Machine
    -> UI Projection Layer
    -> Runtime Commands

Ship Materialization Layer
    -> Runtime State Machine
    -> UI Projection Layer
```

Forbidden flow:

```text
UI -> mutate route truth during render/open
Materialization -> prune schedule/route validity
Runtime failure -> delete route/schedule
Transient route index miss -> persistent cleanup
```

### C.2 Persistent Route Model

Responsibilities:

- Own station identities and saved station refs.
- Own transponder identities and saved transponder refs.
- Own recorded route segments.
- Own transponder-owned schedules.
- Own persistent dock and cargo link definitions.
- Own explicit deletion transactions.
- Rebuild route indexes.
- Answer route validity queries from persistent state.

Non-responsibilities:

- Active playback position.
- Whether the Sable body is currently loaded.
- Wait progress.
- UI status color.
- Runtime pause/kill/retry.

Proposed API:

```java
interface RouteRepository {
    RouteModelSnapshot snapshot();
    Optional<RouteSegment> segmentById(RouteSegmentId id);
    List<RouteSegment> segmentsForTransponder(UUID transponderId);
    List<RouteSegment> segmentsConnectedToStation(UUID stationId);
    RouteQueryResult resolveLeg(UUID transponderId, UUID startStationId, UUID targetStationId,
                                Optional<RouteSegmentId> pinnedSegment);
    ScheduleValidationResult validateSchedule(UUID transponderId, AirshipSchedule schedule,
                                              UUID startStationId);
    DeleteResult deleteStation(UUID stationId);
    DeleteResult deleteTransponder(UUID transponderId);
    void rebuildIndex();
}
```

Persistence rules:

- Route segments are saved in station data or moved to dedicated saved data in a
  later phase.
- If left on station BEs initially, repository must still treat loaded station
  BEs as durable route sources and rebuild index from identity directory and
  loaded forced station chunks.
- A route segment is invalid only if its owning station or owning transponder is
  explicitly deleted or if the segment's saved NBT is structurally corrupt.
- Missing Sable body, missing runtime playback, missing registry entry, or
  unloaded client chunk is not invalid route data.

Logging:

- `persistence` channel logs:
  - route segment added/deleted
  - schedule saved
  - index rebuilt
  - explicit station/transponder delete cleanup
  - refused cleanup with reason

### C.3 Runtime State Machine

Responsibilities:

- Own active schedule state.
- Own current leg/route playback reference.
- Own wait state, pause state, kill state, retry state, and failure state.
- Bind runtime records to persistent route model.
- Save/load runtime snapshots.
- Expose runtime snapshots for UI/commands.
- Drive motion runner and materialization requests through interfaces.

Non-responsibilities:

- Persisting route definitions.
- Pruning saved schedules.
- Direct Sable storage mutation.
- Rendering UI.

Proposed states:

| State | Meaning |
| --- | --- |
| `IDLE` | No active runtime |
| `STARTING` | Runtime accepted start request and is resolving current leg |
| `RUNNING_LOADED` | Physical ship is loaded and route motion is active |
| `RUNNING_UNLOADED` | Runtime is advancing estimated route progress without loaded body |
| `MATERIALIZING` | Runtime needs live Sable body at a route/station pose |
| `WAITING` | Stop wait is active with loaded context where needed |
| `PAUSED_MANUAL` | Command/user pause hold |
| `PAUSED_FAULT` | Fault hold requiring user action |
| `RECOVERING` | Runtime is retrying after load/materialization failure |
| `KILLED` | Runtime has been stopped and holds released |
| `COMPLETED` | Non-looping runtime finished |

Proposed API:

```java
interface ScheduleRuntimeStateMachine {
    RuntimeStartResult start(RuntimeStartRequest request);
    RuntimeStopResult stop(UUID transponderId, StopReason reason);
    RuntimePauseResult pause(UUID transponderId, PauseReason reason);
    RuntimeResumeResult resume(UUID transponderId);
    RuntimeSkipResult skipCurrentStop(UUID transponderId);
    void tick(ServerLevel level);
    void onRoutePlaybackComplete(RouteId routeId, CompletionReason reason);
    RuntimeSnapshot snapshot(UUID transponderId);
    List<RuntimeSnapshot> activeSnapshots(Optional<UUID> ownerFilter);
    CompoundTag saveRuntime();
    void loadRuntime(MinecraftServer server, CompoundTag tag);
}
```

Reload/recovery:

- Runtime load never immediately deletes anything.
- Runtime load enters `STARTING` or `MATERIALIZING` if dependencies are missing.
- Runtime waits for route repository readiness before deciding validity.
- If persistent route is missing, runtime pauses fault with
  `PERSISTENT_ROUTE_MISSING` and does not fabricate/delete route data.
- If Sable body is missing, runtime pauses or materializes based on the
  materialization policy and logs exact reason.

### C.4 Route Motion Runner

Responsibilities:

- Apply loaded route playback motion.
- Interpolate position and all attitude axes toward the target route endpoint.
- Detect arrival, stuck, collision/obstruction, and segment timeout.
- Report completion/failure to the runtime state machine.

Non-responsibilities:

- Choosing the next schedule leg.
- Saving route definitions.
- Sable stored-body relocation.
- UI status.

Proposed API:

```java
interface RouteMotionRunner {
    MotionStartResult start(RoutePlaybackSnapshot playback, LoadedShipBody body);
    MotionTickResult tick(RouteId routeId, LoadedShipBody body);
    void hold(RouteId routeId, HoldPose pose);
    void release(RouteId routeId);
    CompoundTag save(RouteId routeId);
    RoutePlaybackSnapshot restore(CompoundTag tag);
}
```

### C.5 Ship Materialization Layer

Responsibilities:

- Resolve live Sable body for a transponder/controller ref.
- Track loaded vs stored body state.
- Request safe materialization at recorded route/station pose.
- Handle Sable sublevel load failures and cooldowns.
- Own Sable stored-data cleanup with proof.
- Report materialization state to runtime.

Non-responsibilities:

- Route/schedule validity.
- Pruning route/schedule data.
- UI display decisions.

Proposed API:

```java
interface ShipMaterializationService {
    MaterializationSnapshot snapshot(UUID transponderId, VehicleControllerRef controllerRef);
    MaterializationResult requireLoadedBody(MaterializationRequest request);
    MaterializationResult materializeAt(MaterializationRequest request);
    StoredBodyLookupResult lookupStoredBody(UUID shipId, ResourceKey<Level> dimension);
    CleanupResult pruneStoredBodyIfProvenStale(UUID shipId, CleanupProof proof);
}
```

Result types should be explicit:

- `LOADED`
- `STORED_AVAILABLE`
- `STORED_MISSING`
- `LOAD_FAILED_SUBLEVEL`
- `LOAD_FAILED_CHUNK`
- `CONTROLLER_MISSING`
- `UNSAFE_TO_RELOCATE`
- `SABLE_API_UNAVAILABLE`
- `STARTUP_GRACE_WAIT`

### C.6 UI Projection Layer

Responsibilities:

- Build transponder menu state snapshots.
- Build station menu state snapshots.
- Build schedule menu runtime/read-only snapshots.
- Build command-visible runtime summaries.
- Build toast/action-bar status payloads.
- Manage route/dock/cargo preview lifecycle.

Non-responsibilities:

- Mutating persistent route data.
- Rebuilding runtime.
- Resolving Sable body.

Proposed API:

```java
interface RuntimeProjectionService {
    TransponderProjection transponder(UUID transponderId, ProjectionContext context);
    StationProjection station(UUID stationId, Optional<UUID> selectedTransponderId,
                              ProjectionContext context);
    ScheduleProjection schedule(UUID transponderId, ProjectionContext context);
    RuntimeCommandProjection commandSummary(UUID transponderId);
}
```

Projection sources:

- Persistent route model snapshot.
- Runtime state machine snapshot.
- Materialization snapshot.
- Dock/cargo link summaries.
- Permission snapshot.

### C.7 Cleanup And Deletion Model

Explicit deletion events:

- Station block destroyed.
- Transponder block destroyed.
- User deletes a stop.
- User deletes route/schedule entries.
- Admin command kills runtime.

Allowed cleanup:

- Delete routes owned by a deleted transponder.
- Delete route legs whose start or target station was explicitly deleted.
- Remove schedules owned by deleted transponder.
- Release runtime holds for deleted transponder.
- Clear previews created by deleted station/transponder.
- Prune stored Sable pointers only with proof that they point to a stale or
  colliding stored body, not just because body is not loaded.

Forbidden cleanup:

- Delete route legs because `RouteSegmentRegistry.byId` missed.
- Delete schedules because Sable body is missing.
- Delete route legs because runtime is inactive.
- Delete routes from UI open/reconcile.

## D. Phased Refactor Plan

### Phase 0 - Baseline Freeze And Compatibility Notes

Goal:

- Freeze the current expected behavior before changing code.

Files/classes affected:

- Documentation only.
- `RUNTIME_REFACTOR_AUDIT.md`
- `SPEC.md` if this is later promoted into spec.

Concrete changes:

- Keep this document updated with observed behavior and known broken states.
- Mark every later phase as behavior-compatible or behavior-changing.

Behavior unchanged:

- All runtime code.

Risks:

- None.

Validation:

- Document exists and names actual code seams.

Rollback:

- Delete the document.

Compatibility:

- Behavior-compatible.

### Phase 1 - Stabilize The Boundary Before Structural Changes

This phase is intentionally split into ordered subphases:

1. Phase 1A - add minimal diagnostics around cleanup/refuse/delete and route validation.
2. Phase 1B - introduce the persistent `RouteRepository` facade.
3. Phase 1C - stop automatic cleanup from tick/menu-open paths.
4. Phase 1D - expand transition diagnostics once the boundary is stable.

Goal:

- Make current lifecycle observable without adding tick spam, then harden
  the persistent route boundary before changing runtime structure.

Files/classes likely affected:

- `CreateAeronauticsAutomatedLogistics`
- `AutomatedLogisticsConfig`
- `AirshipScheduleExecutionService`
- `VehicleRoutePlaybackService`
- `AutomationRuntimeSavedData`
- `ShipRecoveryService`
- `StationChunkLoadingService`
- `ScheduleRouteCleanup`

#### Phase 1A - Minimal diagnostics

Status:

- Complete on 2026-06-18.
- Added targeted runtime/load/restore diagnostics in schedule execution and
  playback restore paths to explain unreadable runtime entries, invalid pinned
  routes, and pending playback restore failures without per-tick spam.

- Add minimal debug channels if needed: `runtime`, `materialization`,
  `persistence`.
- Log only the critical lifecycle points needed to explain route loss:
  - cleanup delete/refuse
  - route validation accept/reject
  - runtime save/load apply
- Include:
  - transponder id
  - route id
  - current entry index
  - station ids
  - Sable ship id
  - loaded/stored/materialized state

#### Phase 1B - Route repository boundary

Status:

- Complete on 2026-06-18.
- Added `RouteRepository` as a persistent route facade backed by station-owned
  route segments, registered in `AutomatedLogisticsServices`, and routed
  schedule/menu validation lookups through it.

- Introduce the persistent `RouteRepository` facade.
- Route all schedule validation/status queries through it.
- Keep the facade backed by the existing station route segments at first.

#### Phase 1C - Stop automatic cleanup

Status:

- Complete on 2026-06-18.
- Removed automatic route pruning from station tick and menu-open paths while
  keeping explicit delete cleanup behavior.

- Stop automatic cleanup from tick/menu-open paths.
- Keep cleanup explicit and intentional so a UI refresh or tick path cannot
  silently erase persistent route truth.

#### Phase 1D - Expand transition diagnostics

Status:

- Complete on 2026-06-18.
- Added explicit transition diagnostics for schedule start, leg start, leg
  complete, schedule completion/loop restart, wait start/end, unloaded transit,
  materialization request/result, runtime restore, and route index rebuild.
- Existing validation/load/restore diagnostics remain in place to explain
  unreadable runtime entries and route-resolution failures.

- Once the boundary is stable, add transition logging for:
  - schedule start
  - leg start
  - leg complete
  - wait start/end
  - unload simulation start/end
  - materialization request/result
  - route index rebuild
- Add intended pose and actual pose where available.

Behavior unchanged:

- No gameplay or persistence changes.

Risks:

- Log spam if transition guards are wrong.

Validation:

- Run one ship loop and two ship loop with debug on.
- Confirm no repeated per-tick logs except explicit progress logs.
- Confirm logs can answer: where should the ship be, where is it, why is it
  waiting, and what materialization did.

Rollback:

- Disable new logs or remove channel.

Compatibility:

- Behavior-compatible.

### Phase 2 - Freeze Persistent Route Model Ownership

Status:

- Complete on 2026-06-18.
- Route validation and server-side route/status reads now go through
  `RouteRepository`/`RouteSegmentResolver` instead of treating
  `RouteSegmentRegistry` as route truth.
- Server-side route index mutations now go through the repository/cache facade.
- Added a persistent route segment directory plus pending-deletion queue so
  explicit station/transponder/stop delete cleanup can discover affected route
  segment identities even when holder station block entities are unloaded.
- Explicit delete cleanup now backfills missing or incomplete route-directory
  records from persisted station identities before refusing cleanup, so older
  worlds do not depend on prior station registration just to prove route
  ownership during station/transponder/stop deletion.
- Deferred route deletions are applied on later station load/register, then the
  stored route list and cache/index are updated.
- `RouteSegmentRegistry` is explicitly documented and used as an index/cache,
  while explicit station/transponder delete cleanup remains intact without
  making registry misses invalidate route truth.

Goal:

- Stop transient registry or runtime availability from deciding route truth.

Files/classes likely affected:

- `RouteSegmentRegistry`
- `RouteSegmentResolver`
- `ScheduleRouteCleanup`
- `AirshipStationBlockEntity`
- `ShipTransponderBlockEntity`
- `AirshipScheduleExecutionService`
- `AirshipScheduleMenu`

Concrete changes:

- Introduce `RouteRepository` facade backed initially by existing station route
  segments.
- Make `RouteSegmentRegistry` private/internal to the repository or clearly
  treat it as cache.
- Replace direct route validation calls with repository queries.
- Remove route pruning from station tick/menu open.
- Keep explicit station/transponder delete cleanup.
- Ensure stop deletion only deletes route legs intentionally associated with the
  deleted stop.

Behavior unchanged:

- Recording, showing, deleting, and playing routes should behave as before.
- Old route cleanup still happens on explicit deletion.

Risks:

- Station routes may temporarily show stale entries if delete cleanup is missed.

Validation:

- Record 1-2, 2-1 loop, reload, route still exists.
- Record 1-3, 3-2, 2-1 loop, status ready.
- Delete station, related routes removed.
- Delete transponder, that ship's routes removed.
- Run two ships with shared stations and verify each keeps its own routes.

Rollback:

- Restore direct registry calls if repository facade breaks.

Compatibility:

- Behavior-compatible if done as a facade first.

### Phase 3 - Separate Explicit Cleanup From Repair

Status:

- Complete on 2026-06-18.
- Cleanup entry points are now explicit and named:
  `deleteStationRoutes(...)`, `deleteTransponderRoutes(...)`,
  and `deleteStopAssociatedSegments(...)`.
- The vague `pruneInvalid...` cleanup entry points were removed from the active
  API surface.
- Explicit delete paths keep their auditable logs for request, refusal,
  immediate delete, deferred delete, and later deferred application.
- Explicit delete cleanup no longer calls broad loaded-transponder schedule
  repair. Station deletion may only remove loaded schedule entries that directly
  reference the deleted station id or a persistent route-directory segment
  connected to that station. Transponder and stop deletion do not repair
  unrelated loaded schedules.

Goal:

- Make cleanup safe, named, and auditable.

Files/classes likely affected:

- `ScheduleRouteCleanup`
- `AirshipStationBlockEntity`
- `ShipTransponderBlockEntity`
- `AirshipScheduleMenu`
- `RouteRepository`

Concrete changes:

- Split cleanup entry points:
  - `deleteStationRoutes(stationId)`
  - `deleteTransponderRoutes(transponderId)`
  - `deleteStopAssociatedSegments(transponderId, scheduleBeforeDelete, index)`
- Remove vague `pruneInvalid...` calls from automatic paths.
- Log every deletion with reason and source event.
- Add refused-cleanup logs when route data is missing due to index not ready.
- Remove mutating schedule repair that deleted entries because route resolution
  or start-station derivation failed under partial repository visibility.

Behavior unchanged:

- Explicit delete still cleans stale routes.

Risks:

- Existing hidden cleanup may have been masking old data corruption.

Validation:

- Station route list after stop delete.
- Station route list after transponder delete.
- Station route list after station delete.
- Restart after deletes.

Rollback:

- Re-enable old cleanup only for explicit delete paths.

Compatibility:

- Mostly behavior-compatible. It intentionally stops automatic pruning from
  transient state.

### Phase 4 - Extract Runtime State Machine

Status:

- Complete on 2026-06-18.
- Schedule runtime now uses explicit `RuntimeState`,
  `ActiveScheduleRuntime`, and `RuntimeSnapshot` types.
- Active schedule runtime identity is anchored by transponder id; route id is
  now a referenced current leg, not the runtime identity.
- Runtime commands now discover and target schedule runtime snapshots by
  transponder/runtime id instead of playback-owned route summaries.
- Runtime persistence saves and restores the explicit runtime state alongside
  the current referenced route id.
- Runtime status/menu projection is read-only; menu open no longer reconciles
  or prunes active runtime state.
- Runtime failures now transition to inspectable fault states instead of
  removing `activeRuntimes` outside explicit kill or normal completion.
- Invalid/corrupt saved runtime entry indices restore as `INVALID_RUNTIME` and
  remain listable/showable/killable.
- Command pause/kill now routes through the schedule runtime facade by stable
  transponder/runtime id.

Goal:

- Make schedule runtime an explicit state machine independent of playback and
  UI.

Files/classes likely affected:

- `AirshipScheduleExecutionService`
- `VehicleRoutePlaybackService`
- `AutomationRuntimeSavedData`
- `AutomatedLogisticsCommands`
- `ShipTransponderMenu`
- `AirshipStationMenu`

Concrete changes:

- Add `RuntimeState` enum and `ActiveScheduleRuntime` record.
- Replace ad hoc active schedule map operations with transition methods.
- Log runtime transitions with stable ids, old/new state, reason, entry index,
  current route, start/target station, playback existence, and controller
  existence.
- Make runtime id stable by transponder id, not route id.
- Commands target runtime id/transponder id.
- Schedule runtime records reference current route id but do not depend on it as
  their identity.
- Runtime emits `RuntimeSnapshot`.
- Skip-stop progression now advances the schedule runtime to the next leg
  immediately instead of leaving an implicit no-route active state.

Behavior unchanged:

- Same start/stop/pause/kill/show behavior.

Risks:

- Schedule advancement, especially loops and rotated starts, is core behavior.

Validation:

- Two ships running, one 2-stop loop, one 3-stop loop.
- Reload while both running.
- Pause/kill/show after route advances.
- Skip stop during active wait.
- Start from each valid station in a loop.

Rollback:

- Keep old service methods and route calls behind the new facade until stable.

Compatibility:

- Behavior-compatible if facade is preserved.

### Phase 5 - Extract Route Motion Runner

Status:

- Complete on 2026-06-18.
- Loaded physical route motion, route-leg validation, hold-at-target behavior,
  and motion priming are now routed through a dedicated `RouteMotionRunner`
  seam inside `VehicleRoutePlaybackService`.
- Schedule/runtime ownership, route persistence, wait semantics, UI
  projection, runtime save/load, and Sable materialization policy were left
  unchanged for this phase.
- Existing playback entry points now call the motion runner for loaded movement
  instead of embedding that logic directly in the service tick body.

Goal:

- Separate loaded physical movement from schedule runtime.

Files/classes likely affected:

- `VehicleRoutePlaybackService`
- `SableSubLevelVehicleController`
- `VehicleMotionResult`
- `RouteRotation`
- `RoutePoint`

Concrete changes:

- Move movement-only logic from `ActivePlayback` into `RouteMotionRunner`.
- Keep route arrival rules:
  - position reaches target endpoint
  - rotation reaches target endpoint attitude
  - endpoint settle logic remains
- Make motion runner report typed results:
  - `IN_PROGRESS`
  - `ARRIVED`
  - `STUCK`
  - `MISSING_BODY`
  - `COLLISION`
  - `TIMEOUT`

Behavior unchanged:

- Ship should follow the same path and finish with same endpoint pose.

Risks:

- Movement is user-visible and easy to regress.

Validation:

- Existing 2-stop and 3-stop routes.
- Ship recorded with different endpoint attitude.
- Stuck/collision pause fault.
- Route preview and actual movement still match enough for current behavior.

Rollback:

- Keep old movement code until runner returns equivalent results.

Compatibility:

- Behavior-compatible.

### Phase 6 - Extract Wait Runtime

Status:

- Complete on 2026-06-18.
- Stop-wait progression, grouped wait-condition evaluation, wait start/end, and
  unloaded non-physical wait completion now run through a dedicated
  `WaitRuntime` seam inside `VehicleRoutePlaybackService`.
- Dock, cargo, redstone, time-of-day, timed, and grouped AND/OR wait behavior
  remains on the existing logic path, now called through the new adapter.
- Route persistence, schedule cleanup, runtime save/load semantics, UI
  projection, and Sable materialization policy were left unchanged for this
  phase.

Goal:

- Separate wait progression from motion and materialization.

Files/classes likely affected:

- `VehicleRoutePlaybackService`
- `DockingRuntime`
- cargo wait code
- `AirshipScheduleCondition`
- `WaitCondition`

Concrete changes:

- Add `WaitRuntimeState` per active stop.
- Route runtime asks wait runtime to tick when stop is active.
- Wait runtime uses adapters:
  - time wait adapter
  - redstone wait adapter
  - dock wait adapter
  - cargo wait adapter
- Mark which waits require loaded physical context.
- Mark which waits can advance unloaded, if any.

Behavior unchanged:

- Current stop waits and AND/OR condition behavior.

Risks:

- Dock/cargo waits are sensitive to real connectors and inventories.

Validation:

- Time wait.
- Redstone wait.
- Item empty/full.
- Fluid empty/full.
- Dock wait with transfer.
- Mixed item/fluid AND waits.
- Unloaded station wait behavior.

Rollback:

- Keep wait logic called through old service until parity is proven.

Compatibility:

- Behavior-compatible.

### Phase 7 - Extract Ship Materialization Layer

Status:

- Complete on 2026-06-18.
- Added `ShipMaterializationService` as the runtime/playback-facing boundary
  for live Sable body lookup, stored body lookup, stored-body materialization,
  materialization diagnostics, and loaded-body stored-entry pruning.
- Added typed materialization result categories for loaded body, stored body,
  missing stored body, sublevel load failure, chunk/load readiness, controller
  reference problems, unsafe relocation/materialization, Sable API
  unavailability, startup grace, unknown failure, and successful materialize.
- Added `SableStoredShipGarbageCollector` so startup/stale stored-body cleanup
  is called through explicit reason-coded cleanup/prune requests.
- `VehicleRoutePlaybackService` now asks materialization for body availability
  and materialization results instead of directly calling broad
  `ShipRecoveryService` restore/prune methods.
- `VehicleRoutePlaybackService` now routes live controller/body refresh and
  runtime restore lookup through `ShipMaterializationService` typed live lookup
  results instead of directly resolving `VehicleControllerResolver` failures.
- Route persistence, schedule cleanup, UI projection, route validity, and
  runtime save/load record shape were left unchanged for this phase.

Goal:

- Make Sable body loading/restoring an explicit dependency of runtime, not a
  side effect inside playback.

Files/classes likely affected:

- `ShipRecoveryService`
- `SableSubLevelVehicleController`
- `VehicleRoutePlaybackService`
- `StationChunkLoadingService`
- `StationChunkLoadingSavedData`

Concrete changes:

- Add `ShipMaterializationService`.
- Move stored Sable lookup/materialize/move operations behind it.
- Move startup stale/colliding pointer cleanup behind
  `SableStoredShipGarbageCollector`.
- Runtime requests materialization at:
  - active loaded route point
  - station arrival pose
  - dock wait station pose
- Materialization returns typed result, never mutates route data.
- Station chunk service reports station chunk readiness only.

Behavior unchanged:

- Current loaded playback.
- Current unloaded movement target behavior if already working.

Risks:

- This is the highest-risk 0.5 area.
- Sable API behavior is external and partly timing-sensitive.

Validation:

- Reload mid-air.
- Reload at dock wait.
- Leave and return mid-leg.
- Leave and return as ship enters station chunk.
- Two ships unloaded at once.
- Sable failed-to-load-sublevel toast no longer occurs or is converted into
  clear runtime materialization failure.

Rollback:

- Keep old `ShipRecoveryService` operations callable through adapter until the
  new service is proven.

Compatibility:

- Behavior-compatible if it preserves current tested 0.5 behavior.

### Phase 8 - Replace Direct UI Coupling With Projection Snapshots

Status:

- Complete on 2026-06-18.
- Added `RuntimeProjectionService` so server menu-open and sync paths build
  `ShipTransponderMenu.StatusSnapshot` and `AirshipStationMenu.ClientState`
  through one projection boundary.
- `createMenu`, block open handlers, and transponder reopen now use projection
  snapshots without refreshing runtime ship discovery, refreshing dock links,
  migrating schedules, or registering station snapshots during menu open.
- Preview payloads now carry producer identity (`routeId`, `stationId`,
  `transponderId`, `transponderPos`) so client preview state clears by explicit
  producer identity instead of depending on route-cache scans.
- Existing menu DTOs, colors, text, controls, and payload shapes for station
  and transponder state were preserved apart from the preview identity
  extension.

Goal:

- Finish the menu refactor from `MENU_SYSTEM_AUDIT.md` using runtime snapshots.

Files/classes likely affected:

- `ShipTransponderMenu`
- `AirshipStationMenu`
- `AirshipScheduleMenu`
- `ShipTransponderScreen`
- `AirshipStationScreen`
- `AirshipScheduleScreen`
- sync payloads
- `SetFlightPathPreviewPayload`

Concrete changes:

- Add `RuntimeProjectionService`.
- Build `StatusSnapshot` and `ClientState` from projection service.
- Menu open must not reconcile runtime or prune routes.
- Preview state includes producer identity and clears on:
  - explicit station delete
  - explicit transponder delete
  - user toggles off
  - different producer toggles on
- Survival, creative, and spectator use same DTOs and action permissions.

Behavior unchanged:

- Same UI colors, text, positions, tooltips, and buttons.

Risks:

- UI regressions have been common and should be tested exhaustively.

Validation:

- Use `MENU_SYSTEM_AUDIT.md` preservation checklist.
- Test spectator, creative, survival.
- Test station and transponder previews.

Rollback:

- Keep old menu DTOs and replace only their builders first.

Compatibility:

- Behavior-compatible.

### Phase 9 - Runtime Restore Coordinator

Status:

- Complete on 2026-06-18.
- Added `RuntimeRestoreCoordinator` as the saved runtime apply path.
- `AutomationRuntimeSavedData` now routes restore through the coordinator, with
  the previous direct schedule/playback loader kept behind a migration flag for
  this phase.
- Restore now loads raw schedule and playback snapshots first, rebuilds the
  loaded route segment cache from persistent station data, rebinds schedule
  runtimes to their playback records, logs orphan playback records, submits
  pending playback materialization/restore, then rechecks schedule runtime
  state.
- `VehicleRoutePlaybackService` can load pending playback runtime without
  immediately materializing it, and exposes restore diagnostics for active and
  pending route playback records.
- `AirshipScheduleExecutionService` logs each restored transponder runtime and
  transitions corrupt entry indices or missing playback records into
  inspectable fault states instead of deleting route or schedule data.
- Route cache rebuild is explicit and read-only; missing loaded station BEs
  remain non-fatal and do not invalidate persistent routes.

Goal:

- Make save/load deterministic and multi-ship safe.

Files/classes likely affected:

- `AutomationRuntimeSavedData`
- `AirshipScheduleExecutionService`
- `VehicleRoutePlaybackService`
- `RouteRepository`
- `ShipMaterializationService`

Concrete changes:

- Introduce restore phases:
  1. raw runtime snapshot loaded
  2. persistent route model loaded
  3. route indexes rebuilt
  4. runtime schedules rebound to persistent schedules
  5. playback records rebound to runtime schedules
  6. materialization requests submitted
  7. resume or pause fault
- Runtime restore logs per ship.
- Restore must tolerate:
  - active schedule missing playback
  - playback missing active schedule
  - route missing due to explicit deletion
  - Sable body missing
  - station chunk not ready yet

Behavior unchanged:

- Running ships should continue after reload when valid.
- Invalid/corrupt runtime should pause/fault, not delete route data.

Risks:

- Existing worlds may have damaged runtime state from test builds.

Validation:

- Reload with one ship running.
- Reload with two ships running.
- Reload while one ship waiting and one moving.
- Reload after runtime command pause.
- Reload after runtime command kill.

Rollback:

- Keep legacy runtime loader behind migration flag for one phase.

Compatibility:

- Behavior-compatible with improved recovery.

### Phase 10 - Tests, Validation Commands, And Dead-Code Removal

Goal:

- Lock behavior and remove old pathways only after validation.

Files/classes likely affected:

- New test helpers where possible.
- `AutomatedLogisticsCommands`
- old cleanup paths
- old menu fallback paths

Concrete changes:

- Add commands or debug output for:
  - route repository dump for transponder
  - runtime snapshot dump
  - materialization snapshot dump
  - route index rebuild check
- Add unit-style tests for pure route/schedule validation if test framework
  supports it.
- Remove old direct registry cleanup once replaced.

Behavior unchanged:

- No user-visible behavior changes except extra debug/admin tools if retained.

Risks:

- Removing old code too early could hide missing path coverage.

Validation:

- Full behavior matrix.
- Build.
- Manual clean-world test.
- Manual old-world migration test.

Rollback:

- Revert dead-code removal, not the new architecture.

Compatibility:

- Behavior-compatible.

## E. Testing And Validation Plan

### E.1 Required Behavior Matrix

Status values:

- Confirmed: current behavior is known and should be preserved.
- Broken: current behavior is known broken and should be fixed.
- Uncertain: needs reproduction/logging before code change.
- New: behavior being introduced by 0.5 and not yet shipped.

| Scenario | Starting state | Action/event | Expected persistent route state | Expected runtime state | Expected materialization state | Expected UI/status/toast | Recovery behavior | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Record leg | Ship at station A, recording to B | Finish/save recording | New segment A->B saved under persistent route model and indexed | No active runtime unless already running | Loaded ship remains normal | Transponder status updates to `Partial Route` or `Ready` based on schedule validity | None | Confirmed but refresh fragile |
| Complete 2-stop loop | A->B and B->A saved | Open transponder | Both route legs persist after reload | Idle | No materialization request | `Ready` by default loop config | Play can start from A or B | Confirmed |
| Complete 3-stop loop | A->B, B->C, C->A saved | Open transponder | All legs persist | Idle | No materialization request | `Ready` | Play can start from any valid stop in loop | Recently fragile |
| Partial route | A->B only, loop required | Open transponder | Segment persists | Idle | None | `Partial Route`, play disabled/fails clearly | Record missing leg or enable one-way config | Confirmed |
| One-way route allowed | Config enabled, A->B saved | Open transponder | Segment persists | Idle | None | `Ready` for one-way if config permits | Play completes or stops as configured | Confirmed |
| Reload while idle | Valid schedule, no active runtime | Restart world | Routes and schedule persist | Idle | None | Same status as before restart | None | Confirmed in 0.4.4 |
| Reload while running | Active ship mid-route | Restart world | Routes and schedule persist unchanged | Runtime restores to running or pending materialization | Loaded or materialized based on Sable state | Shows running/waiting/fault accurately | If materialization fails, pause fault without deleting route | Broken/fragile in 0.5 |
| Reload while waiting | Active stop wait | Restart world | Routes and schedule persist unchanged | Runtime restores wait state | Loaded body required for dock/cargo waits | Waiting or paused materialization fault | Retry materialization; no route deletion | Needs investigation |
| Two ships running | Two transponders active, possible shared stations | Tick, route advance, restart | Each ship keeps its own routes/schedule | Two independent active runtimes | Each body tracked separately | Commands list both; UI per ship correct | Pause/kill one does not affect other | Recently broken |
| Unload/reload mid-leg | Player leaves route chunks | Ship chunk unloads | Routes unchanged | Runtime advances unloaded or holds per policy | Body stored/unloaded | Debug logs unloaded progress; UI correct on return | Materialize at expected pose on return/station | New/fragile |
| Unload/reload near station | Player away while ship approaches station | Ship reaches station chunk | Routes unchanged | Runtime should enter station wait/next leg | Station chunk loaded; body materializes for station actions | No failed sublevel toast; dock/cargo acts | If body cannot load, pause materialization fault | New/fragile |
| Dock wait while away | Dock wait active, station chunks loaded | Player away/tick sprint | Routes unchanged | Runtime waits until physical dock/cargo succeeds | Body must be materialized at station | Dock status accurate on return | Fault if dock cannot lock | New, promising but fragile |
| Transponder delete | Ship transponder block broken | Explicit delete | Routes owned by that transponder removed | Runtime killed and holds released | Sable body released/stopped | Related previews clear; station route list updates | No orphan runtime | Confirmed after recent fixes, needs matrix retest |
| Station delete | Station block broken | Explicit delete | Routes connected to station removed | Active runtimes depending on station fault/stop | Station chunk untracked | Landing/route previews clear | Affected ships pause/fault clearly | Needs retest |
| Pause command | `/aal runtime list`, click pause | Active runtime | Routes unchanged | Runtime enters manual pause | Body held if loaded; pending if unloaded | UI shows paused/fault/held consistently | Play resumes from same runtime | Confirmed after fixes |
| Kill command | Runtime kill | Active runtime | Routes unchanged | Runtime removed/killed | Holds released, dock outputs cleared | UI returns to route readiness | Can play again from valid station | Confirmed after fixes |
| Show command | Runtime show | Active runtime | Routes unchanged | Runtime unchanged | No materialization mutation | Transponder highlighted briefly | None | Confirmed |
| Sable restore success | Stored body available | Runtime asks materialization | Routes unchanged | Runtime resumes at intended pose | Body loaded safely | No toast/error | Continue route | New, needs sustained testing |
| Sable restore failure | Stored body missing/load fails | Runtime asks materialization | Routes unchanged | Runtime enters materialization fault/pending retry | Missing/failed typed result | Toast/action message should be clear and not spam | Retry or command kill/pause | Broken/fragile |
| Route exists but ship not materialized | Valid route, Sable body absent | Open UI/start/reload | Routes unchanged | Idle or materialization fault if runtime active | Missing body snapshot | `Ready` only means route valid, materialization warning if active/start fails | Restore or rebuild ship | Needs defined messaging |
| Schedule exists but runtime inactive | Valid schedule, no active runtime | Open UI | Routes unchanged | Idle | None | `Ready` or `Partial Route` based on route model | Play starts new runtime | Confirmed |
| Runtime exists but persistent route missing | Corrupt save or explicit deletion during runtime | Reload/tick | Missing route remains missing; no fabricated data | Runtime pauses fault or kills with clear reason | Release/hold per safety | Clear fault text | Command kill releases | Needs explicit handling |
| Corrupted/partial saved state | Damaged runtime NBT | Load world | Persistent routes preserved | Runtime loads best effort or pauses fault | Materialization best effort | One clear warning/log | Admin commands can clean | Needs tests |
| World/server restart | Save and reload | Runtime active/idle mixed | Routes persist | Runtime restore deterministic | Materialization staged | No route loss, no toast spam | Commands list active/pending | Broken/fragile |
| Chunk unload/reload | Player moves away/back | Station chunks may stay forced | Routes persist | Runtime follows policy | Body loaded/stored per policy | Accurate route/status on return | No deletion | New/fragile |
| Player disconnect/reconnect | Singleplayer or server player leaves | Runtime active | Routes persist | Runtime continues or holds per server policy | Body unaffected unless chunks unload | On reconnect UI accurate | None | Needs testing |
| Tick lag/delayed tick | Tick sprint or server lag | Active runtime | Routes persist | Segment timers handle lag without false stuck where possible | Body remains stable | No spam | Fault only after meaningful timeout | Needs testing |

### E.2 Manual Test Matrix

Minimum clean-world pass:

1. Record A->B.
2. Confirm `Partial Route`.
3. Record B->A.
4. Confirm `Ready`.
5. Start route from A.
6. Stop route.
7. Start route from B.
8. Reload while idle.
9. Reload while running.
10. Delete a stop and confirm station routes update.
11. Delete transponder and confirm holds/previews/routes clear.

Multi-ship pass:

1. Create two ships with separate transponders.
2. Give ship 1 a 2-stop loop.
3. Give ship 2 a 3-stop loop.
4. Use at least one shared station.
5. Start both.
6. Confirm both advance through at least three legs.
7. Reload while both running.
8. Confirm both keep schedules/routes and continue or enter clear runtime state.
9. Pause ship 1 by command and ensure ship 2 continues.
10. Kill ship 1 and ensure ship 2 continues.

Unloaded transit pass:

1. Enable station chunk loading.
2. Start one route with long legs.
3. Leave mid-leg.
4. Return mid-leg.
5. Leave near station approach.
6. Return after station wait should have begun.
7. Repeat with dock wait and cargo transfer.
8. Repeat with two active ships.

Persistence pass:

1. Save while idle.
2. Save while running.
3. Save while waiting.
4. Save while paused manually.
5. Save while paused fault.
6. Save after command kill.
7. Verify route/schedule persisted independently of runtime state.

Deletion pass:

1. Show route preview from transponder, delete transponder, preview clears.
2. Show station route preview, delete station, preview clears.
3. Delete station used by active runtime, runtime faults/stops clearly.
4. Delete transponder used by active runtime, runtime killed and physics released.
5. Delete stop from schedule, only intended route segments removed.

### E.3 Automated Or Semi-Automated Tests

Pure Java tests are most realistic for:

- schedule chain validation
- loop completeness validation
- one-way config behavior
- route repository query results
- cleanup transaction results
- runtime state transitions independent of Sable
- runtime save/load round trips
- projection snapshot generation from mocked persistent/runtime/materialization
  state

Minecraft integration/manual tests are still required for:

- Sable materialization
- actual ship movement
- dock connectors
- cargo transfer
- client previews
- menu interaction and spectator behavior

### E.4 Performance Checks

- No per-tick full scan of all route segments in normal runtime.
- No per-tick full scan of all Sable stored ships.
- Station chunk forcing reconciles on startup and explicit changes, not on every
  station tick.
- Menu payloads are scoped to open viewers only.
- Block entity update tags must not carry full route lists or schedule
  projections.
- Debug logs must be transition-based unless explicit progress logging is
  enabled.

## F. Rules For The AI Performing The Refactor

- Do not start code changes until this document is accepted or updated.
- Do not patch individual symptoms unless they are emergency escape hatches.
- Treat current working behavior as the compatibility spec.
- Keep behavior-compatible refactors separate from behavior-changing fixes.
- Never let runtime/materialization failure delete persistent route or schedule
  data.
- Never let UI projection mutate saved route truth.
- Never let Sable materialization decide schedule validity.
- Never let startup ordering alone determine whether saved routes are valid.
- Prefer explicit state transitions over implicit side effects.
- Prefer immutable or snapshot-style reads across layer boundaries.
- Add logging before changing behavior where lifecycle ordering is unclear.
- When uncertain, document the uncertainty and choose the safest implementation
  path.
- Make changes in small, reviewable phases.
- After each phase, validate against the behavior matrix.
- If current code contradicts this architecture, document the contradiction
  before changing it.
- Use feature flags or adapter facades for high-risk swaps.
- Keep 0.4.x stable behavior as the fallback reference when 0.5 runtime work
  regresses.
- Do not use Sable body absence as proof that a route/schedule should be
  removed.
- Do not use route index absence as proof that persistent data should be
  removed.
- Implement only the next approved phase. Do not proceed to later phases
  unless explicitly instructed. After each phase, report changed files,
  behavior preserved, validation performed, and any deviations from this
  document.

## G. Immediate Recommended Next Step

Do not begin the full refactor by rewriting `VehicleRoutePlaybackService`.

The safest first implementation step is:

1. Add Phase 1A minimal diagnostics.
2. Introduce the persistent route repository facade.
3. Stop automatic cleanup from tick/menu-open paths.
4. Expand transition diagnostics.
5. Only then extract the runtime state machine.

Reason:

- Most destructive regressions came from route truth, cleanup timing, and
  registry availability.
- Fixing the persistent route boundary first reduces the chance that later
  runtime/materialization work deletes routes while testing unloaded movement.

## H. Future Sable Drive Mode Considerations

The refactor should avoid assuming there is only one movement implementation.

Current recorded-route playback and future Advanced Transponder drive mode
should share:

- persistent route/schedule model
- runtime state machine concepts
- materialization layer
- command facade
- UI projection layer

They should not share:

- low-level movement runner internals
- drive-output mapping
- capability validation
- route-following control algorithm

Future structure:

```text
ScheduleRuntimeStateMachine
    -> RecordedRoutePlaybackRunner
    -> SableDriveModeRunner
    -> ShipMaterializationService
```

This lets 0.5 experimental drive mode and 1.0 Advanced Transponder work reuse
the same route/runtime/materialization foundation without forcing recorded
playback bugs into drive mode.
