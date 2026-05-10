package net.sprocketgames.create_aeronautics_automated_logistics.client.ponder.scenes;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlock;
import dev.simulated_team.simulated.ponder.instructions.LinkDockingConnectorsInstruction;
import dev.simulated_team.simulated.ponder.instructions.ToggleConnectorLockInstruction;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.block.AirshipStationBlock;
import net.sprocketgames.create_aeronautics_automated_logistics.block.ShipTransponderBlock;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlocks;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModItems;

public final class AirshipStationPonderScenes {
    private static final ResourceLocation DOCKING_CONNECTOR_ID = ResourceLocation.fromNamespaceAndPath("simulated", "docking_connector");
    private static final ResourceLocation REDSTONE_LINK_ID = ResourceLocation.fromNamespaceAndPath("create", "redstone_link");

    private AirshipStationPonderScenes() {
    }

    public static void recordingBetweenStations(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("recording_between_stations", "Recording routes between Airship Stations");
        scene.configureBasePlate(0, 0, 10);
        scene.scaleSceneView(.72f);
        scene.setSceneOffsetY(-1);

        BlockPos stationA = util.grid().at(1, 1, 3);
        BlockPos stationB = util.grid().at(8, 1, 3);
        BlockPos transponder = util.grid().at(2, 2, 6);
        Selection stationASelection = util.select().position(stationA);
        Selection stationBSelection = util.select().position(stationB);
        Selection shipSelection = util.select().fromTo(1, 1, 5, 3, 2, 7);
        Vec3 stationATop = util.vector().topOf(stationA);
        Vec3 stationBTop = util.vector().topOf(stationB);
        Vec3 transponderTop = util.vector().topOf(transponder);
        Vec3 routeVector = util.vector().of(5, 0, 0);

        scene.world().showIndependentSection(util.select().layer(0), Direction.UP);
        scene.idle(10);

        scene.world().showSection(stationASelection, Direction.EAST);
        scene.idle(12);
        scene.overlay().showText(45)
                .text("Station A")
                .pointAt(stationATop)
                .placeNearTarget()
                .colored(PonderPalette.BLUE);
        scene.idle(28);

        scene.world().showSection(stationBSelection, Direction.WEST);
        scene.idle(12);
        scene.overlay().showText(45)
                .text("Station B")
                .pointAt(stationBTop)
                .placeNearTarget()
                .colored(PonderPalette.BLUE);
        scene.idle(45);

        ElementLink<WorldSectionElement> ship = scene.world().showIndependentSection(shipSelection, Direction.DOWN);
        scene.idle(20);
        scene.overlay().showText(55)
                .text("The ship carries a Ship Transponder")
                .pointAt(transponderTop)
                .placeNearTarget()
                .colored(PonderPalette.GREEN);
        scene.idle(62);

        scene.overlay().showControls(stationATop, Pointing.DOWN, 45)
                .rightClick()
                .withItem(ModBlocks.AIRSHIP_STATION.asItem().getDefaultInstance());
        scene.idle(10);
        scene.overlay().showText(80)
                .text("At Station A, select the airship by name, then start recording")
                .pointAt(stationATop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.effects().indicateSuccess(stationA);
        scene.idle(86);

        scene.overlay().showText(55)
                .text("Fly the ship manually to Station B")
                .pointAt(transponderTop)
                .placeNearTarget();
        scene.world().moveSection(ship, routeVector, 80);
        scene.idle(88);

        scene.overlay().showControls(stationBTop, Pointing.DOWN, 45)
                .rightClick()
                .withItem(ModItems.AIRSHIP_STATION.get().getDefaultInstance());
        scene.idle(10);
        scene.overlay().showText(75)
                .text("Finish recording at Station B to save Station A -> Station B")
                .pointAt(stationBTop)
                .placeNearTarget()
                .attachKeyFrame()
                .colored(PonderPalette.GREEN);
        scene.effects().indicateSuccess(stationB);
        scene.idle(82);

        scene.overlay().showText(70)
                .text("Routes are directional, so record the return leg separately")
                .pointAt(stationBTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.effects().indicateSuccess(stationB);
        scene.idle(76);

        scene.overlay().showText(55)
                .text("Fly back to Station A")
                .pointAt(transponderTop.add(routeVector))
                .placeNearTarget();
        scene.world().moveSection(ship, routeVector.scale(-1), 80);
        scene.idle(88);

        scene.overlay().showControls(stationATop, Pointing.DOWN, 45)
                .rightClick()
                .withItem(ModItems.AIRSHIP_STATION.get().getDefaultInstance());
        scene.idle(10);
        scene.overlay().showText(75)
                .text("Finish at Station A to save Station B -> Station A")
                .pointAt(stationATop)
                .placeNearTarget()
                .colored(PonderPalette.GREEN);
        scene.effects().indicateSuccess(stationA);
        scene.idle(82);

        scene.overlay().showText(80)
                .text("With both segments saved, schedules can repeat the trip")
                .pointAt(util.vector().of(4.5, 2.1, 6.5))
                .placeNearTarget()
                .attachKeyFrame()
                .colored(PonderPalette.BLUE);
        scene.world().moveSection(ship, routeVector, 75);
        scene.idle(85);
        scene.world().moveSection(ship, routeVector.scale(-1), 75);
        scene.idle(85);
    }

    public static void installingAndRunningASchedule(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("installing_and_running_a_schedule", "Installing and running an Airship Schedule");
        scene.configureBasePlate(0, 0, 10);
        scene.scaleSceneView(.72f);
        scene.setSceneOffsetY(-1);

        BlockPos stationA = util.grid().at(1, 1, 3);
        BlockPos stationB = util.grid().at(8, 1, 3);
        BlockPos transponder = util.grid().at(2, 2, 6);
        Selection stationASelection = util.select().position(stationA);
        Selection stationBSelection = util.select().position(stationB);
        Selection shipSelection = util.select().fromTo(1, 1, 5, 3, 2, 7);
        Vec3 stationATop = util.vector().topOf(stationA);
        Vec3 stationBTop = util.vector().topOf(stationB);
        Vec3 transponderTop = util.vector().topOf(transponder);
        Vec3 routeVector = util.vector().of(5, 0, 0);

        scene.world().showIndependentSection(util.select().layer(0), Direction.UP);
        scene.idle(10);

        scene.world().showSection(stationASelection, Direction.EAST);
        scene.idle(10);
        scene.overlay().showText(40)
                .text("Station A")
                .pointAt(stationATop)
                .placeNearTarget()
                .colored(PonderPalette.BLUE);
        scene.idle(25);

        scene.world().showSection(stationBSelection, Direction.WEST);
        scene.idle(10);
        scene.overlay().showText(40)
                .text("Station B")
                .pointAt(stationBTop)
                .placeNearTarget()
                .colored(PonderPalette.BLUE);
        scene.idle(30);

        ElementLink<WorldSectionElement> ship = scene.world().showIndependentSection(shipSelection, Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(85)
                .text("Make sure you have recorded your routes first using the Airship Stations")
                .pointAt(stationATop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(92);

        scene.overlay().showControls(transponderTop, Pointing.DOWN, 45)
                .rightClick()
                .withItem(ModItems.AIRSHIP_SCHEDULE.get().getDefaultInstance());
        scene.idle(10);
        scene.overlay().showText(95)
                .text("Create a schedule to specify which stations, routes and wait conditions your ship should use")
                .pointAt(transponderTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(102);

        scene.overlay().showText(75)
                .text("Put your completed schedule into the Ship Transponder")
                .pointAt(transponderTop)
                .placeNearTarget()
                .colored(PonderPalette.GREEN);
        scene.effects().indicateSuccess(transponder);
        scene.idle(82);

        scene.overlay().showControls(stationATop, Pointing.DOWN, 45)
                .rightClick()
                .withItem(ModBlocks.AIRSHIP_STATION.asItem().getDefaultInstance());
        scene.idle(10);
        scene.overlay().showText(95)
                .text("Make sure your ship is at the start station, then you can start the journey from the station")
                .pointAt(stationATop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.effects().indicateSuccess(stationA);
        scene.idle(102);

        scene.overlay().showControls(transponderTop, Pointing.DOWN, 45)
                .rightClick()
                .withItem(ModBlocks.SHIP_TRANSPONDER.asItem().getDefaultInstance());
        scene.idle(10);
        scene.overlay().showText(70)
                .text("You can also start it from the transponder")
                .pointAt(transponderTop)
                .placeNearTarget()
                .colored(PonderPalette.GREEN);
        scene.effects().indicateSuccess(transponder);
        scene.idle(78);

        scene.overlay().showText(60)
                .text("Once started, the ship follows its recorded route automatically")
                .pointAt(transponderTop)
                .placeNearTarget();
        scene.world().moveSection(ship, routeVector, 80);
        scene.idle(88);

        scene.overlay().showText(65)
                .text("The schedule continues from station to station using the installed route plan")
                .pointAt(stationBTop)
                .placeNearTarget()
                .attachKeyFrame()
                .colored(PonderPalette.BLUE);
        scene.idle(70);
    }

    public static void dockingWaits(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("docking_waits", "Docking waits");
        scene.configureBasePlate(0, 0, 10);
        scene.scaleSceneView(.72f);
        scene.setSceneOffsetY(-1);

        BlockPos stationA = util.grid().at(1, 1, 2);
        BlockPos stationB = util.grid().at(6, 1, 2);
        BlockPos transponder = util.grid().at(2, 2, 6);
        BlockPos shipDock = util.grid().at(2, 2, 5);
        BlockPos stationDock = util.grid().at(7, 2, 2);
        BlockPos stationDockSupport = util.grid().at(7, 1, 2);
        BlockPos stationDockLink = util.grid().at(7, 2, 1);
        BlockPos stationBLink = util.grid().at(6, 1, 1);
        Selection stationASelection = util.select().position(stationA);
        Selection stationBSelection = util.select().position(stationB);
        Selection stationDockSelection = util.select().position(stationDock)
                .add(util.select().position(stationDockSupport))
                .add(util.select().position(stationDockLink))
                .add(util.select().position(stationBLink));
        Selection shipSelection = util.select().fromTo(1, 1, 5, 3, 2, 7);
        Vec3 stationATop = util.vector().topOf(stationA);
        Vec3 stationBTop = util.vector().topOf(stationB);
        Vec3 transponderTop = util.vector().topOf(transponder);
        Vec3 shipDockTop = util.vector().topOf(shipDock);
        Vec3 stationDockTop = util.vector().topOf(stationDock);
        Vec3 routeVector = util.vector().of(5, 0, 0);
        BlockPos visibleTransponder = transponder.east(5);

        scene.world().showIndependentSection(util.select().layer(0), Direction.UP);
        scene.idle(10);

        scene.world().showSection(stationASelection, Direction.EAST);
        scene.idle(8);
        scene.world().showSection(stationBSelection.add(stationDockSelection), Direction.WEST);
        scene.idle(18);

        ElementLink<WorldSectionElement> ship = scene.world().showIndependentSection(shipSelection, Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(60)
                .text("The nearest dock to the assigned station is used")
                .pointAt(stationDockTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(68);

        scene.overlay().showText(60)
                .text("The nearest dock to the Ship Transponder is used")
                .pointAt(shipDockTop)
                .placeNearTarget();
        scene.idle(68);

        scene.overlay().showText(70)
                .text("A docking wait in the schedule prepares both sides for docking")
                .pointAt(transponderTop)
                .placeNearTarget()
                .colored(PonderPalette.BLUE);
        scene.idle(78);

        scene.overlay().showText(55)
                .text("The ship follows its recorded route to the destination station")
                .pointAt(stationBTop)
                .placeNearTarget();
        scene.world().moveSection(ship, routeVector, 80);
        scene.idle(88);

        scene.world().setBlock(stationB, poweredStationState(), false);
        scene.world().setBlock(transponder, poweredTransponderState(), false);
        scene.world().setBlock(shipDock, poweredDockState(Direction.NORTH), false);
        scene.world().setBlock(stationDock, poweredDockState(Direction.SOUTH), false);
        scene.world().setBlock(stationDockLink, poweredLinkState(), false);
        scene.world().setBlock(stationBLink, poweredLinkState(), false);
        scene.effects().indicateRedstone(stationB);
        scene.effects().indicateRedstone(visibleTransponder);
        scene.idle(15);

        scene.overlay().showText(90)
                .text("During docking stages, the station and transponder emit redstone so your wiring can power the connectors")
                .pointAt(stationBTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(98);

        scene.addInstruction(new ToggleConnectorLockInstruction(shipDock, true));
        scene.addInstruction(new ToggleConnectorLockInstruction(stationDock, true));
        scene.addInstruction(new LinkDockingConnectorsInstruction(shipDock, stationDock));
        scene.idle(20);
        scene.overlay().showText(65)
                .text("Once the connectors lock, the ship waits at the station")
                .pointAt(stationDockTop)
                .placeNearTarget()
                .attachKeyFrame()
                .colored(PonderPalette.GREEN);
        scene.idle(76);

        scene.overlay().showText(70)
                .text("After the wait condition completes, the dock output turns off and the ship departs")
                .pointAt(stationBTop)
                .placeNearTarget()
                .colored(PonderPalette.BLUE);
        scene.addInstruction(new ToggleConnectorLockInstruction(shipDock, false));
        scene.addInstruction(new ToggleConnectorLockInstruction(stationDock, false));
        scene.world().setBlock(stationB, unpoweredStationState(), false);
        scene.world().setBlock(transponder, unpoweredTransponderState(), false);
        scene.world().setBlock(shipDock, unpoweredDockState(Direction.NORTH), false);
        scene.world().setBlock(stationDock, unpoweredDockState(Direction.SOUTH), false);
        scene.world().setBlock(stationDockLink, unpoweredLinkState(), false);
        scene.world().setBlock(stationBLink, unpoweredLinkState(), false);
        scene.idle(45);
        scene.world().moveSection(ship, routeVector.scale(-1), 80);
        scene.idle(88);
    }

    private static BlockState poweredStationState() {
        return ModBlocks.AIRSHIP_STATION.get().defaultBlockState()
                .setValue(AirshipStationBlock.FACING, Direction.SOUTH)
                .setValue(AirshipStationBlock.POWERED, true);
    }

    private static BlockState unpoweredStationState() {
        return ModBlocks.AIRSHIP_STATION.get().defaultBlockState()
                .setValue(AirshipStationBlock.FACING, Direction.SOUTH)
                .setValue(AirshipStationBlock.POWERED, false);
    }

    private static BlockState poweredTransponderState() {
        return ModBlocks.SHIP_TRANSPONDER.get().defaultBlockState()
                .setValue(ShipTransponderBlock.FACING, Direction.EAST)
                .setValue(ShipTransponderBlock.POWERED, true);
    }

    private static BlockState unpoweredTransponderState() {
        return ModBlocks.SHIP_TRANSPONDER.get().defaultBlockState()
                .setValue(ShipTransponderBlock.FACING, Direction.EAST)
                .setValue(ShipTransponderBlock.POWERED, false);
    }

    private static BlockState poweredDockState(Direction facing) {
        return BuiltInRegistries.BLOCK.get(DOCKING_CONNECTOR_ID).defaultBlockState()
                .setValue(DockingConnectorBlock.FACING, facing)
                .setValue(DockingConnectorBlock.POWERED, true)
                .setValue(DockingConnectorBlock.EXTENDED, true);
    }

    private static BlockState unpoweredDockState(Direction facing) {
        return BuiltInRegistries.BLOCK.get(DOCKING_CONNECTOR_ID).defaultBlockState()
                .setValue(DockingConnectorBlock.FACING, facing)
                .setValue(DockingConnectorBlock.POWERED, false)
                .setValue(DockingConnectorBlock.EXTENDED, false);
    }

    private static BlockState poweredLinkState() {
        return BuiltInRegistries.BLOCK.get(REDSTONE_LINK_ID).defaultBlockState()
                .setValue(RedstoneLinkBlock.FACING, Direction.NORTH)
                .setValue(RedstoneLinkBlock.RECEIVER, false)
                .setValue(RedstoneLinkBlock.POWERED, true);
    }

    private static BlockState unpoweredLinkState() {
        return BuiltInRegistries.BLOCK.get(REDSTONE_LINK_ID).defaultBlockState()
                .setValue(RedstoneLinkBlock.FACING, Direction.NORTH)
                .setValue(RedstoneLinkBlock.RECEIVER, false)
                .setValue(RedstoneLinkBlock.POWERED, false);
    }
}
