package net.sprocketgames.create_aeronautics_automated_logistics.client.ponder;

import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.client.ponder.scenes.AirshipStationPonderScenes;

public final class ModPonderScenes {
    private ModPonderScenes() {
    }

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(id("airship_station"))
                .addStoryBoard("airship_station/recording_between_stations", AirshipStationPonderScenes::recordingBetweenStations);
        helper.forComponents(id("ship_transponder"))
                .addStoryBoard("ship_transponder/installing_and_running_a_schedule", AirshipStationPonderScenes::installingAndRunningASchedule)
                .addStoryBoard("ship_transponder/docking_waits", AirshipStationPonderScenes::dockingWaits);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, path);
    }
}
