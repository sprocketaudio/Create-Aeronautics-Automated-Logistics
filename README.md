# Create Aeronautics: Logistics

**This mod is in a very early development state.**

Create Aeronautics: Logistics is a Create Aeronautics add-on that adds autonomous route recording and playback for airships.

The core idea is simple: fly a journey once, record it, then let the ship repeat that route through an Airship Schedule. Schedules can include multiple route entries, station waits, docking waits, and cargo inactivity waits through connected docking systems.

At this stage, the mod **mimics ship flight along recorded routes** rather than fully piloting the ship through real player-style controls. The long-term goal is deeper logistics automation, but this version is focused on making recorded route playback reliable, understandable, and useful.

**Expect rough edges, changing mechanics, and possible breaking changes while the mod develops. Feedback is very welcome in the mod page comments or on GitHub by opening an Issue.**

Primarily designed for Create Aeronautics airships. Other Sable-based vehicles may work experimentally, but ground vehicles are not officially supported yet and may behave unpredictably during playback.

## What it can currently do

- Add Airship Stations as named destinations
- Add Ship Transponders to identify and name ships
- Record station-to-station airship routes
- Save routes as directional segments, such as `Base Dock -> Mining Dock`
- Play back recorded routes automatically
- Run installed Airship Schedules from a ship transponder
- Bind schedules to the ship/transponder they belong to
- Resume active schedules after server restart, world reload, or singleplayer reload
- Support multiple schedule entries
- Support timed waits at stations
- Support docking-aware waits
- Support cargo inactivity waits through connected docking connectors
- Link station-side and ship-side docking connectors directly
- Output redstone signals from stations and transponders for dock automation
- Wait until docking connectors lock
- Preview landing areas and flight paths in-world
- Show the full schedule route chain from the transponder
- Stop safely on failure instead of teleporting, rerouting, or phasing through blocks

## Important limitations

- No pathfinding
- No obstacle avoidance
- No automatic rerouting
- No recovery if the ship crashes, drifts, or is stopped in a bad place
- Routes must be recorded manually first, so your ship must be able to complete the route
- Each route is tied to the ship/transponder it was recorded with
- Schedules currently need to start from the first station in the route chain
- Keep ship and route chunks loaded while schedules are running, as unloaded ships may stop and fall or drift
- Docking requires player-built redstone wiring
- Cargo handling depends on existing Create / Create Simulated docking systems
- This is not yet a full live autopilot system

## How to Use

1. Build and Assemble an Airship  
Use a Physics Assembler to assemble a stable ship with working lift and propulsion.

2. Place and Name Airship Stations  
Place Airship Stations at each destination and give each one a clear name.

3. Install a Ship Transponder  
Place a Ship Transponder on the ship, set a ship name, and confirm stations can see and select it.

4. Record Route Segments by Flying Manually  
At the start station, select the ship and start recording. Fly to the next station and save the segment there. Repeat for each leg you want to automate.

5. Build an Airship Schedule  
Create an Airship Schedule and add `Travel to Station` entries in the order you want. Choose matching recorded routes for the ship.

6. Install and Start Automation  
Insert the schedule into the transponder, move the ship to the first station in the schedule route, and press Start.

7. Optional Docking - Redstone-Driven  
Docking Connectors require redstone to activate. For docking-enabled stops:

- Place one Docking Connector on the ship
- Place one Docking Connector near the station
- Link the ship dock from the Ship Transponder
- Link the station dock from the Airship Station
- Wire the station/transponder redstone outputs into your dock activation setup

This lets automation trigger docking lock/unlock timing without manually switching the docks.

## Design Goal

This mod aims to feel like a rail-free logistics layer for Create Aeronautics.

Instead of placing sky rails or path markers, the player proves the route by flying it once. The automation then follows that recorded route and handles station waits, docking signals, and basic schedule flow.

Bad routes are still bad routes. If the ship hits something, loses its route context, unloads, or cannot complete the journey, automation stops.

***

This is an unofficial Create Aeronautics addon and is not affiliated with or endorsed by the Create or Create Aeronautics teams.

Some UI elements are styled to match Create’s schedule interface. Create and Create Aeronautics belong to their respective authors.

Requires Create Aeronautics and Create.
