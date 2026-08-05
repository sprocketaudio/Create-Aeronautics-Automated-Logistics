# Create Aeronautics: Automated Logistics

Build automated logistics routes that keep running when nobody is nearby.

Create Aeronautics: Automated Logistics adds recorded route automation for Create Aeronautics airships, with experimental Simurail train compatibility. Fly each route yourself, install a schedule on the vehicle's **Transponder**, then let it repeat the journey between compatible stations.

Vehicles can travel while unloaded, return for physical docking and cargo transfer, wait for conditions, and queue safely when another vehicle is using the same dock.

Current builds also include experimental transport-gated **Simurail Station** compatibility for recorded train routes, using the same schedule and route-recording model without mixing airship and train station networks. That transport split is also part of the longer-term groundwork for future non-airship vehicle support.

## Main Features

### Recorded Airship Routes

- Record real station-to-station flight paths by flying them manually.
- Build a separate route plan for each ship directly through its Transponder.
- Preview individual routes, every route connected to a station, or routes filtered by ship.
- Use distinct per-ship route colours to make larger networks easier to read.
- Airship stations only see and control airship routes and airship-capable transponders.
- The same transport-aware route/station split is intended to scale later to other supported vehicle types instead of staying airship-only forever.

### Experimental Simurail Compatibility

- Record and run train-only routes through **Simurail Stations**.
- Train stations only see and control train routes and train-capable transponders.
- Airship stations and Simurail stations stay separated by transport mode and cannot be linked interchangeably.
- This is a compatibility layer built on recorded route playback; it is not rail-aware pathfinding, native dispatch logic, or a replacement for future Simurail-native automation.

### Unloaded Ship Travel

- Active vehicles continue progressing along recorded routes while players are elsewhere.
- Route progress and schedules survive chunk unloads, game reloads, and server restarts.
- Ships return to the world near stops so docking, cargo movement, and other physical interactions still happen normally.
- Runtime failures remain visible and recoverable instead of silently deleting the vehicle or its route data.

### Docking And Queues

- Link a Create Simulated dock to each station and vehicle.
- Transfer items and fluids through the physical vehicle and dock setup.
- Shared docks use arrival-order reservations so approaching vehicles do not all converge on the same dock.
- Waiting vehicles hold at a configurable clearance distance until the previous vehicle has departed safely.

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

### Map Integration

- Named automated vehicles can appear on supported live maps.
- FTB Chunks and JourneyMap integrations refresh markers on minimaps and fullscreen map views.
- Marker state is synchronized from the server so long-running routes remain visible while automation is active.
- Airships use a blimp icon and Simurail trains use a train icon across supported map integrations.
- A placed **Logistics Terminal** provides an in-mod network view with stations, active vehicles, and recorded routes on a dark abstract logistics map.
- The Logistics Terminal supports zoom, pan, route hover previews, and right-click tracking without depending on external map mods.

### Multiplayer And Diagnostics

- Server-known vehicles and stations remain available to selection menus even when their chunks are not visible to the player opening the UI.
- Vehicle and station ownership checks protect important controls.
- Optional FTB Teams integration can allow same-team or allied players to control owned stations and transponders.
- Station permissions now distinguish between **station use** and **station control**.
- Use permission allows landing, queueing, docking, route browsing, and starting or stopping allowed vehicles.
- Control permission additionally allows station-admin actions such as dock-link and cargo-link changes.
- Active vehicle limits can be bucketed per owner or per FTB team, depending on configuration.
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

If you are using the current Simurail compatibility layer, use Simurail Stations instead of Airship Stations and keep the recorded network entirely within train-mode stations.

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

- Functional Storage is not currently recommended on moving vehicles.
- Storage Drawers work on moving vehicles, but some interaction and display behavior remains limited by underlying Sable compatibility.

## Important Behavior And Limits

- Routes use the path you recorded. There is no pathfinding, obstacle avoidance, or automatic rerouting.
- Loaded vehicles move physically and can collide with terrain or other vehicles if a route is unsafe.
- Unloaded travel follows authoritative recorded-route progress; it is not continuous off-screen collision simulation.
- Recovery may reposition a restored vehicle onto its authoritative route pose when necessary. Normal loaded travel still follows the recorded path physically.
- Routes belong to their recorded vehicle and are not generic paths shared across unrelated vehicles.
- Simurail train support is a compatibility layer built on recorded route playback, not a replacement for future rail-aware routing.
- Full live-control autopilot and broader ground-vehicle support are outside the current scope.

## Requirements

- Minecraft 1.21.1
- NeoForge
- Create
- Create Aeronautics

## Optional Integrations

- **FTB Teams:** team and ally use/control rules, plus team-scoped active vehicle limits
- **FTB Chunks:** live automated vehicle markers on minimap and fullscreen map
- **JourneyMap:** live automated vehicle markers on minimap and fullscreen map
- **Simurail:** experimental train-only station compatibility using recorded-route playback

## Updating

Existing worlds from version 0.4.5 are supported. Back up important worlds before updating, particularly when vehicles have active schedules.

## About

This is an unofficial Create Aeronautics addon and is not affiliated with or endorsed by the Create or Create Aeronautics teams.

Feedback and bug reports are welcome through the mod page or GitHub issues.
