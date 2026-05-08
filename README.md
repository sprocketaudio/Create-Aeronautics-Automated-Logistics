# Create Aeronautics: Logistics

This mod is in a very early development state.

Logistics is an experimental Create Aeronautics add-on that adds early autonomous route recording and playback for airships.

At this stage, the mod **mimics ship flight along recorded routes** rather than fully piloting the ship through real player-style controls. The long-term goal is deeper logistics automation, but this first version is focused on proving the core idea: record a journey once, then let the ship repeat it.

Expect rough edges, changing mechanics, and possible breaking changes while the mod develops.

## What it can currently do

- Add Airship Stations as named destinations
- Add Ship Transponders to identify and name ships
- Record station-to-station airship routes
- Save routes as directional segments, such as `Base Dock -> Mining Dock`
- Play back recorded routes automatically
- Run installed Airship Schedules from a ship transponder
- Support multiple schedule entries
- Support timed waits at stations
- Support docking-aware waits
- Detect linked docking connectors near stations and ships
- Output redstone signals from stations and transponders for dock automation
- Wait until docking connectors lock
- Wait for cargo inactivity through connected docking connectors
- Preview landing areas and flight paths
- Stop safely on failure instead of teleporting, rerouting, or phasing through blocks

## Important limitations

- No pathfinding
- No obstacle avoidance
- No automatic rerouting
- No recovery if the ship crashes, drifts, or is stopped in a bad place
- Routes must be recorded manually first
- Each route is currently tied to the ship/transponder it was recorded with
- Docking requires player-built redstone wiring
- Cargo handling depends on existing Create / Create Simulated docking systems
- This is not yet a full live autopilot system

## How to Use

### 1. Build and Assemble an Airship

Use a Physics Assembler to assemble a stable ship with working lift and propulsion.

### 2. Place and Name Airship Stations

Place Airship Stations at each destination and give each one a clear, unique name.

### 3. Install a Ship Transponder

Place a Ship Transponder on the ship, set a ship name, and confirm stations can see and select it.

### 4. Record Route Segments by Flying Manually

At the start station, select the ship and start recording. Fly to the next station and save the segment there. Repeat for each leg you want to automate.

### 5. Build an Airship Schedule

Create an Airship Schedule and add `Travel to Station` entries in the order you want. For each stop, choose a matching recorded route segment.

### 6. Install and Start Automation

Insert the schedule into the transponder, open a station with the ship in range, and press Start to begin automated playback.

### 7. Optional Docking - Redstone-Driven

Docking Connectors require redstone to activate. For docking-enabled stops:

- Place one Docking Connector on the ship.
- Place one Docking Connector near the station.
- Use redstone output from the Ship Transponder and/or Airship Station to power those connectors at the right schedule phase.

This lets automation trigger docking lock/unlock timing without manual switching.

### 8. Monitor and Iterate

Watch status and failure feedback, stop if needed, and re-record any segments when station layout or route geometry changes.

## Design Goal

This mod aims to feel like a rail-free logistics layer for Create Aeronautics.

Instead of placing sky rails or path markers, the player proves the route by flying it once. The automation then follows that recorded route and handles station waits, docking signals, and basic schedule flow.

Bad routes are still bad routes. If the ship hits something, loses its route context, or cannot complete the journey, automation stops rather than cheating around the problem.

## Disclaimer

This is an unofficial Create Aeronautics addon and is not affiliated with or endorsed by the Create or Create Aeronautics teams.

Some UI elements are styled to match Create's schedule interface. Create and Create Aeronautics belong to their respective authors.

Requires Create Aeronautics and Create.
