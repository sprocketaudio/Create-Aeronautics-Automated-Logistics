# Create Aeronautics: Automated Logistics

Build airship cargo routes that keep running when nobody is nearby.

Create Aeronautics: Automated Logistics adds recorded route automation for Create Aeronautics airships. Fly each route yourself, install a schedule on the ship's **Transponder**, then let it repeat the journey between **Airship Stations**.

Ships can travel while unloaded, return for physical docking and cargo transfer, wait for conditions, and queue safely when another ship is using the same dock.

## Main Features

### Recorded Airship Routes

- Record real station-to-station flight paths by flying them manually.
- Build a separate route plan for each ship directly through its Transponder.
- Preview individual routes, every route connected to a station, or routes filtered by ship.
- Use distinct per-ship route colours to make larger networks easier to read.

### Unloaded Ship Travel

- Active ships continue progressing along recorded routes while players are elsewhere.
- Route progress and schedules survive chunk unloads, game reloads, and server restarts.
- Ships return to the world near stops so docking, cargo movement, and other physical interactions still happen normally.
- Runtime failures remain visible and recoverable instead of silently deleting the ship or its route data.

### Docking And Queues

- Link a Create Simulated dock to each station and ship.
- Transfer items and fluids through the physical ship and dock setup.
- Shared docks use arrival-order reservations so approaching ships do not all converge on the same dock.
- Waiting ships hold at a configurable clearance distance until the previous ship has departed safely.

### Schedules And Conditions

Create multi-stop schedules with grouped conditions, including:

- Scheduled delays
- Docked time and dock inactivity
- Redstone links
- Time of day
- Item or fluid empty/full checks
- Filtered item and fluid cargo conditions
- Ship-side or station-side cargo targets

### Station Chunk Loading

Airship Stations can manage the world chunks needed for unattended logistics:

- Each station keeps its own chunk loaded while station chunk loading is enabled.
- During docking and cargo interaction, the station can temporarily load a configurable square area.
- The default interaction radius is `1`, producing a `3x3` chunk area.
- The station UI can preview the configured chunk-loading area in-world.
- Chunk loading can be disabled if the server already uses another chunk-loading mod.

Docking stops require the station, dock, and any station-side cargo blocks to be in loaded chunks. Increase the interaction radius or provide external chunk loading when a station build extends beyond that area.

### Multiplayer And Diagnostics

- Server-known ships and stations remain available to selection menus even when their chunks are not visible to the player opening the UI.
- Ship and station ownership checks protect important controls.
- Runtime, recovery, route, and materialization commands help administrators inspect and recover problem setups.
- Separate debug categories are available for playback, vehicles, docking, cargo, and UI synchronization.

## Basic Workflow

1. Build and assemble a Create Aeronautics airship.
2. Place and name Airship Stations at each destination.
3. Place and name a Ship Transponder on the airship.
4. Fly the ship between stations and record each route leg.
5. Build the ship's stop schedule through the Transponder.
6. Link any required docks and cargo storage.
7. Start the schedule from a valid station on its route.

## Supported Cargo Storage

Cargo linking supports item and fluid storage from:

- **Minecraft:** Chests, Trapped Chests, Barrels, and Shulker Boxes
- **Create:** Item Vaults and Fluid Tanks
- **Storage Drawers:** Drawers, Drawer Controllers, and Controller Slaves
- **Functional Storage:** Drawers, framed drawers, and Storage Controllers
- **Sophisticated Storage / Backpacks:** Storage blocks, controller-linked storage, and placed backpacks
- **Iron Chests**
- **Mekanism:** Bins, Personal Chests, Personal Barrels, Fluid Tanks, QIO Drive Arrays, and QIO Exporters
- **Tom's Simple Storage:** Connector, interface, proxy, and inventory endpoints
- **Refined Storage 2:** Controllers and Exporters
- **Applied Energistics 2:** Controllers, valid multiblock Controllers, ME Chests, Sky Stone Tanks, and cable-bus export parts

Compatibility notes:

- Functional Storage is not currently recommended on moving ships.
- Storage Drawers work on ships, but some interaction and display behavior remains limited by underlying Sable compatibility.

## Important Behavior And Limits

- Routes use the path you recorded. There is no pathfinding, obstacle avoidance, or automatic rerouting.
- Loaded ships move physically and can collide with terrain or other ships if a route is unsafe.
- Unloaded travel follows authoritative recorded-route progress; it is not continuous off-screen collision simulation.
- Recovery may reposition a restored ship onto its authoritative route pose when necessary. Normal loaded travel still follows the recorded path physically.
- Routes belong to their recorded ship and are not generic paths shared across unrelated ships.
- Full live-control autopilot and ground-vehicle support are outside the current scope.

## Requirements

- Minecraft 1.21.1
- NeoForge
- Create
- Create Aeronautics
- Sable
- Create Simulated for docking support

## Updating

Existing worlds from version 0.4.5 are supported. Back up important worlds before updating, particularly when ships have active schedules.

## About

This is an unofficial Create Aeronautics addon and is not affiliated with or endorsed by the Create or Create Aeronautics teams.

Feedback and bug reports are welcome through the mod page or GitHub issues.
