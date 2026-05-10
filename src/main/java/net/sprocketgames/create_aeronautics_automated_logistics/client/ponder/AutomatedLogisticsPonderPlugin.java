package net.sprocketgames.create_aeronautics_automated_logistics.client.ponder;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;

public class AutomatedLogisticsPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return CreateAeronauticsAutomatedLogistics.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        ModPonderScenes.register(helper);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
    }
}
