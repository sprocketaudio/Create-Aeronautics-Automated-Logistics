# Docking D0 Baseline

This file records the 0.5.1 docking/runtime baseline before the 0.6 docking
refactor begins. It is intentionally descriptive, not aspirational.

## Scope

D0 covers:

- current queue and reservation behavior
- current docking output ownership behavior
- current wait-timer start behavior
- current restore and reservation rebind behavior
- save-shape assumptions that later phases must continue to migrate

D0 does not change gameplay behavior.

## Current Ownership Snapshot

Current live ownership is split across two places:

- `DockingRuntime`
  - reservation storage
  - reservation restore/save
  - output claim/clear helpers
  - connector lock checks
- `VehicleRoutePlaybackService`
  - queue gating
  - queue hold pose selection
  - queue release sequencing
  - restore preflight
  - docking wait start/tick timing
  - reservation release after clearance

That split is the main characterization target for the 0.6 refactor.

## Characterized Behaviors

### Reservation and Queue

- Reservation identity is currently `RouteId`.
- A route may hold at most one reservation at a time because requesting a new
  reservation releases the same route from other docks first.
- Queue ordering is first-come-first-served per dock.
- Releasing the holder promotes the next queued route immediately.
- Saving and loading reservations preserves holder plus queue order.
- Route-to-route reservation handoff currently exists through
  `DockingRuntime.transferReservation(...)`.

### Dock Outputs

- Dock outputs are claimed only by route identity.
- `DockingRuntime.ensureDockOutputsActive(...)` claims both station and ship
  outputs for the route.
- `DockingRuntime.clearDockOutputs(...)` clears route-owned outputs and refuses
  to clear stale owners.
- Playback currently decides when outputs are activated and cleared.

### Wait Timing

- Dock-dependent waits still begin from playback flow, not from a dedicated dock
  coordinator.
- A stop that requires dock lock relies on playback to:
  - recheck queue state
  - confirm approach/alignment
  - begin docking wait
  - tick docking wait until the expected pair locks
- Wait/cargo semantics therefore remain coupled to playback sequencing in 0.5.1.

### Restore and Rebind

- Runtime restore order is:
  1. schedule runtime
  2. playback runtime
  3. docking reservations embedded inside playback runtime
- Playback restore may re-enter dock queue hold before route relocation.
- Pending materialization may remain stored if reservation preflight does not
  grant approach yet.
- Reservation release after departure depends on the remembered dock position in
  playback state.

## Save-Shape Baseline

### Pre-Reservation Runtime Shape

The `0.4.4-fix` branch shows the same top-level runtime saved-data wrapper but
without a docking reservation snapshot reset/apply call. That means older saves
must still be treated as valid when playback runtime contains no dock reservation
section.

Relevant older shape:

```text
AutomationRuntimeSavedData
  Playback
    activePlaybacks: [...]
  Schedules
    activeSchedules: [...]
```

### 0.5.1 Runtime Shape

Current playback runtime adds a dock reservation snapshot under playback:

```text
AutomationRuntimeSavedData
  Playback
    activePlaybacks: [...]
    dockReservations
      reservations: [
        {
          dimension
          stationDock
          holder
          waiting: [routeId, ...]
        }
      ]
  Schedules
    activeSchedules: [...]
```

Important compatibility notes:

- missing `dockReservations` must remain valid
- malformed reservation entries are skipped, not fatal
- duplicate queued entries are normalized on load
- queued entries matching the holder are normalized on load

## Representative Log Anchors

These current log messages are useful parity markers for later phases:

- `Dock reservation granted`
- `Dock reservation queued`
- `Dock reservation released`
- `Dock reservation transferred`
- `Dock reservation runtime restored`
- `Dock lock pending`
- `Playback ... dock queue released for stop ...`
- `Playback ... holding for dock queue before stop ...`
- `Playback ... re-entered dock queue hold after restore ...`
- `Playback ... releasing dock reservation after clearing point ...`

Later phases may change wording, but must preserve equivalent observability.

## D0 Test Coverage Added

The D0 unit tests currently characterize pure reservation/runtime behavior only:

- reservation load/save preserves holder and queue order
- holder handoff preserves queue order
- targeted release promotes only the matching dock queue
- malformed reservation entries are ignored
- duplicate queue entries are normalized
- runtime reset clears tracked reservations

Manual gameplay parity still needs to be checked for:

- output ownership sequencing
- first dock wait timer tick
- loaded queue hold pose behavior
- restore into queued/docked states with real ships

## Exit Gate

D0 is complete when:

- a baseline document exists
- current reservation/runtime behavior is covered by focused tests
- older saves without reservation snapshots are explicitly treated as part of
  compatibility expectations
- no gameplay behavior changed
