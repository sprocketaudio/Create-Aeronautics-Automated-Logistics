## Create Aeronautics: Automated Logistics

**Early development mod. Expect rough edges and possible breaking changes.**

Automated Logistics is an unofficial Create Aeronautics add-on for recording and replaying ship routes.

Fly a route once, save it, then let the ship repeat it from its **Ship Transponder**. The transponder now owns the full route workflow: recording routes, storing stops, and running the ship’s route plan.

This is **not a full autopilot**. Ships currently follow recorded route data rather than dynamically piloting around obstacles.

Primarily designed for Create Aeronautics airships. Other Sable-based vehicles may work experimentally. Ground vehicles are not officially supported and may behave unpredictably.

Feedback is welcome in the mod page comments or by opening an Issue on GitHub.

 

## Current Features

• Named Airship Stations  
• Ship Transponders for naming, identifying, and controlling ships  
• Manual station-to-station route recording  
• Directional per-ship route segments, such as `Base Dock -> Mining Dock`  
• Automatic playback of recorded routes  
• Transponder-owned stop list and route plan  
• Stop management from the transponder, including: stop order changes, stop removal, and wait/dock logic editing.  
• Timed waits  
• Docking-aware waits  
• Cargo inactivity waits through connected docking systems  
• Direct linking for station-side and ship-side docking connectors  
• Redstone outputs from stations and transponders for dock automation  
• In-world landing area and flight path previews  
• Ownership and permission support for multiplayer  
• Ponder/in-game learning support

 

## Important Limitations

• No pathfinding  
• No obstacle avoidance  
• No automatic rerouting  
• No crash recovery  
• Routes must still be flown and recorded manually first  
• The ship must be able to complete the route normally under Sable physics  
• Routes are tied to the ship/transponder they were recorded with  
• Ships do not phase through blocks or ignore collisions  
• Chunk unload handling is improved, but full unloaded route progression is not implemented  
• Docking still requires player-built redstone wiring  
• Cargo handling depends on existing Create / Create Simulated docking systems  
• This is not yet a full live-piloted autopilot system

 

## Basic Setup

1.  Build and assemble a stable airship.
2.  Place Airship Stations at each destination and give them clear names.
3.  Place a Ship Transponder on the ship and name it.
4.  Open the transponder and use the **top-right toggle** to switch to `Route Recording Mode`.
5.  Press `+`, then choose the origin and destination stations.
6.  Fly manually to the destination and save the route segment from the transponder.
7.  Repeat for each route leg you want to automate.
8.  Use the transponder’s **Stops** screen to reorder stops or set wait/dock logic if needed.
9.  Start the route from any valid station already present in the route chain.

 

## Optional Docking Setup

Docking Connectors still need redstone activation.

For docking-enabled stops:

• Place one Docking Connector on the ship  
• Place one Docking Connector near the station  
• Link the ship dock from the Ship Transponder  
• Link the station dock from the Airship Station  
• Wire the station/transponder redstone outputs into your docks

 

## Design Goal

Automated Logistics is meant to feel like a physics-aware logistics layer for Create Aeronautics.

Instead of placing path markers, you prove the route by flying it once. The automation then replays that recorded route and handles stop order, waits, docking signals, and route flow from the transponder.

 

***

 

This is an unofficial Create Aeronautics addon and is not affiliated with or endorsed by the Create or Create Aeronautics teams.

Some UI elements are styled to match Create’s schedule interface. Create and Create Aeronautics belong to their respective authors.

Requires Create, Create Aeronautics, and Sable runtime support.
