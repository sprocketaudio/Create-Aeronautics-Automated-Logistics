# Menu System Audit

This file maps the current menu/screen/state flow for:

- `ShipTransponderMenu` + `ShipTransponderScreen`
- `AirshipStationMenu` + `AirshipStationScreen`
- `AirshipScheduleMenu` + `AirshipScheduleScreen`

The goal is to stop patching individual symptoms and replace the current mixed menu model with a single authoritative client-state model per screen that is:

- safe for packet/NBT size
- safe for spectator/creative/survival
- safe for missing live block entities
- explicit about what is saved vs what is only UI/runtime state
- testable against regressions

This is an audit and refactor map. It does not define the final implementation yet.

## 1. Current Problem Summary

The current UI stack mixes too many state sources:

- live block entity state
- open-menu buffer state
- client-side menu override state
- follow-up sync payload state
- fallback derived state
- screen-local UI state

That split is why the same screen can show:

- correct status but wrong name
- correct name but stale routes
- correct schedule but wrong selected ship
- different spectator vs survival behavior
- empty/default placeholder values until a later packet lands

The main architectural issue is not one broken field. It is that the menu contract is inconsistent.

## 2. State Layers In The Current System

### Durable world state

Saved in block entities / registries / saved data:

- transponder identity and ship name
- station identity and station name
- owned schedule
- selected ship
- route segments
- linked dock/cargo
- runtime status snapshots that are persisted as BE data
- saved runtime registries and route registries

### Live runtime state

Derived on server from services and live world:

- active playback / held / waiting / fault
- current failure context
- current cargo summary validity
- current dock link validity
- current selected ship runtime capability
- current route choices visible from station

### Open-buffer state

Written when the menu is opened, before follow-up payloads:

- transponder/station block pos
- initial mode flags
- initial schedule
- initial cargo summary
- initial linked cargo entries
- initial selected ship data
- initial station route choices
- initial status snapshot

### Client override state

Set later by payloads:

- transponder status snapshot
- transponder owned schedule override
- station client state DTO
- station route choices DTO

### Screen-local state

Not authoritative, only view/controller behavior:

- dropdown open/closed
- route popup open/closed
- typeahead buffer
- scroll positions
- local tooltip rectangles
- button green/active state calculation
- route-selection popup
- edit-box focus

## 3. Core Invariant We Need

For each open screen, all displayed business state should come from exactly one client DTO owned by the menu.

That DTO must be enough to render the screen correctly even when:

- the live block entity is absent client-side
- the player is a spectator
- the player is in another chunk
- the screen was reopened through a payload
- the initial open packet arrived before the live BE was available

Live block entities should only be used for:

- distance validity / stillValid
- world preview anchors when needed
- optional enhancement data that does not change core displayed truth

If a field is important enough to be shown, it must exist in the authoritative menu DTO.

## 4. Ship Transponder Flow

### 4.1 Open paths

Server open entry points:

- `ShipTransponderBlock.useWithoutItem(...)`
- `ReopenShipTransponderPayload.handle(...)`

Server BE menu factory:

- `ShipTransponderBlockEntity.createMenu(...)`

Client menu constructors:

- `ShipTransponderMenu(FriendlyByteBuf)`
- `ShipTransponderMenu(OpenData)`
- fallback constructors that default to `BlockPos.ZERO` and idle values

### 4.2 Open-buffer data currently written

From block open and reopen:

- transponder pos
- recording mode
- recording session active
- append-to-schedule
- recording destination station id
- runtime status enum
- dock output active
- has owned stops
- owned schedule NBT
- cargo revision
- cargo summary
- linked cargo entries
- cargo failure context
- playback failure
- status snapshot

### 4.3 Client-side display sources

Current transponder screen uses multiple sources:

- header name: `menu.shipName(player)`  
  Source order:
  - live transponder BE ship name
  - fallback `initialOwnedSchedule.title()`

- status footer: `menu.runtimeStateText(player)` / `runtimeStateColor(player)`
  Source order:
  - live BE/runtime services
  - if client + no live BE + `BlockPos.ZERO`: derived fallback logic
  - else initial or synced `StatusSnapshot`

- schedule existence / route readiness:
  - live BE owned schedule
  - else `clientOwnedScheduleOverride`
  - else `initialOwnedSchedule`

- dock / cargo compact text:
  - live BE if available
  - else initial summary/entries

### 4.4 Follow-up syncs

- `SyncTransponderMenuStatePayload`
  - updates `clientStatusSnapshotOverride`
- `SyncTransponderOwnedSchedulePayload`
  - updates `clientOwnedScheduleOverride`

Both currently special-case `BlockPos.ZERO` placeholder menus/screens.

### 4.5 Current transponder issues caused by split state

- Status can be correct while header name is wrong.
- Name is not part of the same authoritative DTO as status.
- `shipName()` still depends on live BE or initial schedule title.
- Placeholder menu handling exists for status and schedule, but not as a unified menu-state contract.
- Spectator/placeholder handling is now partially fixed by fallback status derivation, but still not systemically solved.

### 4.6 Transponder save/update triggers

Important mutators on `ShipTransponderBlockEntity` call `syncClientState()` which currently does:

- `serverLevel.sendBlockUpdated(...)`

Examples:

- `setShipName(...)`
- `setRuntimeStatus(...)`
- `setDockOutputActive(...)`
- cargo-link changes
- `setOwnedSchedule(...)`

Problem:

- broad BE updates are doing both persistence sync and UI refresh work
- menu updates and world-sync are not separated cleanly

### 4.7 Transponder render/controller notes

Screen-local concerns in `ShipTransponderScreen`:

- button active/visible state is recalculated every render
- tooltip regions are manual rectangles
- edit box is re-centered live
- open schedule editor path uses payload
- recording popup has its own local open state

This is acceptable, but only if all business state reads come from a single DTO.

## 5. Airship Station Flow

### 5.1 Open paths

Server open entry point:

- `AirshipStationBlock.useWithoutItem(...)`

Server BE menu factory:

- `AirshipStationBlockEntity.createMenu(...)`

Client menu constructors:

- `AirshipStationMenu(FriendlyByteBuf)`
- `AirshipStationMenu(OpenData)`
- default constructor fallback when buffer is missing/short

### 5.2 Open-buffer data currently written

- station pos
- selected transponder id
- selected ship name
- cargo revision
- cargo summary
- linked cargo entries
- cargo failure context
- route choice summaries
- full `ClientState`

### 5.3 Station client DTO

`AirshipStationMenu.ClientState` currently contains:

- selected transponder id
- selected ship name
- ship choice list
- route choice list
- selected ship state

This is already closer to the correct architecture than the transponder.

### 5.4 Station display sources

Good:

- many station screen values already read from `ClientState` when not on a server view

Still mixed:

- `stationName(player)` still uses live BE only
- dock/cargo compact text still prefer live BE
- some status/failure text paths are split between:
  - `statusText`
  - `panelStatusText`
  - `failureText`
  - `statusTooltipLines`

### 5.5 Follow-up syncs

- `SyncStationMenuStatePayload`
  - updates full station `ClientState`
- `SyncStationRouteChoicesPayload`
  - updates route choices only

Again, both now special-case `BlockPos.ZERO`.

### 5.6 Station save/update triggers

Important BE mutators call `syncClientState()` -> `sendBlockUpdated(...)`

Examples:

- `setStationName(...)`
- `setFailure(...)`
- `selectShip(...)`
- dock link changes
- cargo link changes
- route segment add/remove/cleanup
- recording state changes

Problem is the same as transponder:

- BE block updates are being used as a general-purpose UI refresh mechanism

### 5.7 Station screen-local concerns

`AirshipStationScreen` manages:

- ship dropdown open/close
- scroll position
- typeahead
- routes popup
- route preview toggle
- hover rectangles for selected ship / route list / status / dock / cargo

Known UI presentation areas that should stay in the audit:

- row spacing
- top/bottom fade behavior
- scrollbar styling
- selected row indicator
- hover highlight behavior
- z-order of dropdown / route popup / tooltips

Those are screen concerns and should remain screen concerns.

## 6. Schedule Editor Flow

### 6.1 Open paths

Opened from transponder via:

- `ShipTransponderScreen.openInstalledScheduleEditor()`
- `OpenInstalledScheduleEditorPayload.handle(...)`

Direct menu construction:

- `AirshipScheduleMenu(containerId, inventory, originTransponderPos, returnToRecordingMode, initialSchedule)`

### 6.2 Current schedule authority

Schedule authority is mixed:

- client render path from `initialSchedule` when opened from transponder on client
- server write path through `editableTransponder(player)` or held schedule item
- after writes, server pushes `SyncTransponderOwnedSchedulePayload`

### 6.3 Schedule update/save behavior

User actions in `AirshipScheduleMenu.clickMenuButton(...)` call `applyAction(...)`.

If the schedule changed:

- `writeSchedule(player, updated)`

Transponder path:

- `transponder.setOwnedSchedule(schedule)`
- send `SyncTransponderOwnedSchedulePayload`

Item path:

- mutate held `AirshipScheduleItem`

### 6.4 Schedule-specific behavior inventory

The schedule refactor must preserve:

- add travel
- remove stop
- duplicate stop
- move up/down
- wait +/- adjust
- toggle loop
- change selected entry
- cycle target station
- toggle wait
- cycle wait unit
- add condition
- add alternative condition group
- pin newest segment
- skip current stop
- read-only runtime mode
- current stop arrow
- return-to-recording reopen behavior
- title editing
- route-segment cleanup on stop delete

### 6.5 Schedule known fragility

Current schedule editor still depends on:

- server transponder state
- initial schedule copy
- sync payloads
- reopen payload path back to transponder

This is manageable, but it needs to be folded into the same authoritative DTO contract as the transponder.

## 7. Known Divergence Bugs From The Audit

These are structural classes of bugs, not one-off incidents.

### 7.1 Placeholder `BlockPos.ZERO` menus

Symptom:

- menu opens client-side before real-pos state is fully hydrated
- UI falls back to defaults
- later packets may or may not apply depending on position checks

Current mitigation:

- payload handlers now accept `BlockPos.ZERO`

Why it is still not enough:

- it is a patch around the fact that the client menu can exist without a valid authoritative DTO

### 7.2 Name/status split

Symptom from latest screenshot:

- status is right
- name is wrong

Cause:

- transponder header name uses different data path than status footer

### 7.3 Broad BE updates as UI refresh

Symptom:

- save/reopen/state refresh bugs keep regressing
- save-time and NBT-size risks get tied to UI correctness

Cause:

- `sendBlockUpdated(...)` is doing too much work

### 7.4 Spectator/survival/creative mismatch

Symptom:

- same open screen can display different state or color depending on game mode

Root cause:

- not game-mode logic itself
- different hydration availability on the client path
- some code branches on `ServerPlayer` vs non-server instead of on “authoritative DTO available”

## 8. Performance And NBT Constraints

### What should stay out of BE live update tags

Avoid putting large menu-only payloads into broad BE update tags:

- full route choice lists
- large tooltip arrays
- duplicate schedule data when not needed for world sync
- large linked-cargo preview expansions

### What is safe in menu payloads

These are menu-scoped and only needed by open viewers:

- selected-ship choice list
- route choice list
- status tooltip lines
- schedule DTO
- compact cargo summary
- dock summary
- header identity text

### Rule

Durable save data and menu view data must be separated.

- save data belongs in NBT / registries / saved data
- live menu DTO belongs in open buffer + menu sync payloads

## 9. Proposed Refactor Direction

## 9.1 One DTO per menu

Create one authoritative client-state record per menu:

### TransponderMenuState

Should include at minimum:

- transponder pos
- ship display name
- ship short id
- runtime status text
- runtime status color
- runtime status tooltip lines
- recording mode/session flags
- dock compact text/color/tooltip DTO
- cargo compact text/color/tooltip DTO
- route readiness flags
- owned schedule summary for main screen
- full owned schedule for schedule reopen path if needed

### StationMenuState

Can evolve from the existing `ClientState`, but should also include:

- station display name
- station status text/color/tooltip
- dock compact text/color/tooltip
- cargo compact text/color/tooltip

Do not leave those on live-BE-only paths.

### ScheduleMenuState

Should include:

- schedule title
- entries
- loop
- selected index
- read-only flag
- active entry index / active visible leg index
- return target info

The screen should not infer business state from multiple places.

## 9.2 Open-buffer contract

On open:

- write full initial DTO
- menu reads full DTO
- screen renders from DTO immediately

No placeholder default values should ever be visually acceptable unless explicitly marked as loading state.

## 9.3 Follow-up sync contract

Follow-up payloads should update the same DTO, not side caches.

That means replacing:

- `clientStatusSnapshotOverride`
- `clientOwnedScheduleOverride`
- separate station route-choice override list

with:

- one client DTO field per menu

## 9.4 Live BE usage after refactor

Allowed:

- stillValid
- preview anchor lookup
- optional local enhancement if DTO missing

Not allowed for core UI truth:

- header name
- status footer text/color
- canRun/canStop button logic
- selected ship runtime capability
- route availability display

## 9.5 Screen rules after refactor

Screens should only:

- render from menu DTO
- manage local interaction state
- send actions to server
- never reconstruct business truth ad hoc

## 10. Preservation Checklist

This is the list that must survive the refactor.

### Shared

- same functional behavior in survival / creative / spectator
- owner/permission checks unchanged
- same action-bar messaging
- same link-mode entry/clear behavior
- same runtime hold/fault behavior

### Transponder

- rename save
- run
- stop
- preview route
- show dock preview
- show cargo preview
- open schedule editor
- start/cancel/save recording
- route-selection popup
- dock link add/clear
- cargo link add/clear
- correct status text/color/tooltip
- correct header name
- correct cargo/dock compact text

### Station

- rename save
- select ship
- wheel cycle selected ship
- typeahead ship jump
- dropdown scroll
- dropdown selected row
- dropdown hover
- routes popup scroll
- route preview from popup
- run selected ship
- stop selected ship
- dock link add/clear
- cargo link add/clear
- landing area preview
- correct selected ship status text/color/tooltip
- correct station header name

### Schedule

- stop add/delete/duplicate/reorder
- wait edits
- condition edits
- loop toggle
- segment pinning
- stop skip
- current-leg arrow
- read-only runtime view
- reopen back to transponder

## 11. Recommended Refactor Phases

### Phase A: Freeze current behavior in docs

Done by this audit file.

Next additions still needed:

- packet field-size inventory
- exact tooltip key inventory
- exact button/action id inventory
- exact popup z-order inventory

### Phase B: Introduce DTOs without changing visuals

- add `TransponderMenuState`
- expand/replace station `ClientState`
- add `ScheduleMenuState`
- populate DTOs from current server logic
- keep screen drawing identical

### Phase C: Replace split reads

Remove screen/menu business reads from:

- direct BE name lookups for header text
- initial schedule title fallback for transponder header
- mixed status fallback branches except one explicit DTO bootstrap path

### Phase D: Replace broad UI refresh usage

- keep block updates for real world state only
- use targeted menu payloads for open viewers
- keep saved data persistence unchanged

### Phase E: Regression pass

Run the preservation checklist above against:

- survival
- creative
- spectator
- loaded BE
- missing client BE
- reopen path
- schedule editor path

## 12. Why The Latest “Status Right, Name Wrong” Bug Happened

Current transponder screen:

- header name comes from `menu.shipName(player)`
- footer status comes from `menu.runtimeStateText(player)`

Those are separate pipelines.

The status pipeline now has:

- snapshot sync
- placeholder fallback handling

The name pipeline does not.

So the fix is not “patch shipName again”.

The fix is:

- header name must be in the same transponder client DTO as status

## 13. Immediate Refactor Rules

When code work starts, follow these rules:

1. Do not delete existing behavior until the replacement DTO path renders the same UI.
2. Do not use BE update tags as the primary menu refresh mechanism.
3. Do not let screens read business truth from live BEs unless the DTO explicitly says it is allowed.
4. Do not special-case spectator unless the behavior is intentionally different.
5. If a field is shown on screen, put it in the menu DTO.
6. If a payload updates menu state, it must update the same DTO the screen already renders from.
7. Keep packet payloads menu-scoped and compact.

## 14. Remaining Audit Work

This file covers the architecture map and the critical split points.

Still worth adding later if needed:

- exact translatable key inventory per screen
- exact tooltip trigger rectangle inventory
- exact button positions / z-layer expectations
- packet size notes for large schedules and large route lists
- test script / manual test sheet for every action path

## 15. Implemented Refactor Notes

### 15.1 Transponder menu state

`ShipTransponderMenu.StatusSnapshot` is now the authoritative client state for the main transponder screen.

It includes:

- ship display name
- runtime status text, color, and tooltip lines
- schedule active / held flags
- owned-stop and ready-route flags
- route-preview availability
- server-derived control permission
- dock compact text, color, active flag, and tooltip lines
- cargo compact text, color, active flag, and tooltip lines

The client screen should not mutate the client block entity to prime UI state. The old screen-side runtime priming path has been removed.

### 15.2 Station menu state

`AirshipStationMenu.ClientState` is now the authoritative client state for the station screen.

It includes:

- station id and display name
- server-derived station control permission
- selected transponder id and ship name
- full known-ship dropdown snapshots
- route choice summaries
- selected ship runtime state
- dock compact text, color, active flag, and tooltip lines
- cargo compact text, color, active flag, and tooltip lines

Client-side route counts, ship selection, selected-ship status, dock display, cargo display, and control availability should use this DTO rather than reading the client block entity.

### 15.3 Packet/NBT boundary

Menu DTOs are sent through menu open buffers and targeted menu sync payloads only.

Do not put full menu DTO state into block-entity update tags. Broad BE updates should remain for world/block visual state only, not for live menu refresh.

### 15.4 Game-mode rule

Survival, creative, and spectator should render the same menu state because all visible business state comes from the same server-built DTO.

Do not special-case spectator for display or button state unless a future permission rule explicitly requires it.
