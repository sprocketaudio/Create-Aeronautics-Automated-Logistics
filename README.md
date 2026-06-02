# Create Aeronautics: Logistics

Create Aeronautics: Logistics is add-on for recording and replaying airship routes.

Fly a route once, save it, then let the ship repeat it from its **Ship Transponder**. The transponder owns the route workflow: recording route legs, storing stops, running the ship's route plan, and coordinating waits, docking, and cargo-aware logic at each stop.

Ships follow recorded route data and still obey the physical limits of the craft, the route, and the world around them.

Primarily designed for Create Aeronautics airships. Other Sable-based vehicles may work experimentally, but ground vehicles are not an official target.

## What The Mod Does

- Lets you name **Airship Stations** and **Ship Transponders**.
- Lets you record real station-to-station route legs by flying them manually.
- Lets each ship build its own stop list and automation plan from those recorded legs.
- Adds schedule-style waits, docking logic, redstone integration, and cargo-aware stop conditions.
- Keeps the system grounded in physical playback rather than invisible correction or teleporting.

## Core Workflow

1. Build and assemble an airship.
2. Place and name Airship Stations at your destinations.
3. Place and name a Ship Transponder on the ship.
4. Use the transponder to record route legs between stations.
5. Build a stop plan on the transponder.
6. Start automation from a valid station in that route chain.

## Current Scope

The current release includes:

- Valid cargo and dock targets now pulse in-world while link mode is active
- Transponder-owned route recording and playback
- Stop editing, grouped wait-condition logic, and Skip Stop override during active waits
- Dock linking on both ships and stations
- Item and fluid cargo linking on both ships and stations
- Cargo-aware waits, redstone waits, and time-of-day waits
- In-world previews and Create-style UI feedback
- Basic ownership / permission support for multiplayer

## Supported Cargo Link Targets

Cargo link mode currently supports these target blocks and endpoint types:

- Vanilla Chests
- Vanilla Trapped Chests
- Vanilla Barrels
- Vanilla Shulker Boxes
- Create Item Vaults
- Create Fluid Tanks
- Storage Drawers drawers
- Storage Drawers Drawer Controllers
- Storage Drawers Controller Slaves
- Functional Storage drawers
- Functional Storage framed drawers
- Functional Storage Storage Controllers
- Sophisticated Storage blocks and controller-linked storage
- Placed Sophisticated Backpacks
- Iron Chests
- Mekanism Bins
- Mekanism Personal Chests
- Mekanism Personal Barrels
- Mekanism Fluid Tanks
- Mekanism QIO Drive Arrays
- Mekanism QIO Exporters
- Tom's Storage connector / interface / proxy / inventory endpoints
- Refined Storage 2 Controllers
- Refined Storage 2 Exporters
- Applied Energistics 2 Controllers
- Applied Energistics 2 valid multiblock Controllers
- Applied Energistics 2 ME Chests
- Applied Energistics 2 Sky Stone Tanks
- Applied Energistics 2 Cable Bus export parts

Known compatibility note:

- Functional Storage is not currently recommended on moving ships.
- Storage Drawers work on ships, but some on-ship interaction and display behavior is still limited by underlying Sable compatibility.

## Design Intent

Create Aeronautics: Logistics is meant to feel like a believable logistics layer for airships.

You prove a route by flying it once. The mod then automates the repetition of that route. If the ship, station setup, docking, cargo setup, or recorded route is wrong, the system should fail clearly rather than silently cheating around the problem.

## Important Limits

- No pathfinding
- No obstacle avoidance
- No automatic rerouting
- No teleporting or phasing through terrain
- No generic cross-ship route reuse
- No full live-control autopilot

## Dependencies

Requires:

- Create
- Create Aeronautics
- Sable runtime support

Create Simulated docking support is also required where docking features are used.

## Notes

This is an unofficial Create Aeronautics addon and is not affiliated with or endorsed by the Create or Create Aeronautics teams.

Feedback is welcome through the mod page or GitHub issues.
