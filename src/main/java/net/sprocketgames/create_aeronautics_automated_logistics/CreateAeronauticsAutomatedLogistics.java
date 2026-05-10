package net.sprocketgames.create_aeronautics_automated_logistics;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.sprocketgames.create_aeronautics_automated_logistics.compat.CreateAeronauticsCompat;
import net.sprocketgames.create_aeronautics_automated_logistics.network.ModNetworking;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlockEntities;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlocks;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModCreativeTabs;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModItems;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModMenus;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AutomationVisualServerEvents;
import net.sprocketgames.create_aeronautics_automated_logistics.service.DockLinkInteractionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RecordingServerEvents;
import org.slf4j.Logger;

@Mod(CreateAeronauticsAutomatedLogistics.MOD_ID)
public class CreateAeronauticsAutomatedLogistics {
    public static final String MOD_ID = "create_aeronautics_automated_logistics";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static void debugLog(String message, Object... args) {
        if (AutomatedLogisticsConfig.debugLogging()) {
            LOGGER.info(message, args);
        }
    }

    public static void debugWarn(String message, Object... args) {
        if (AutomatedLogisticsConfig.debugLogging()) {
            LOGGER.warn(message, args);
        }
    }

    public CreateAeronauticsAutomatedLogistics(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(ModNetworking::register);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, AutomatedLogisticsConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(RecordingServerEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(RecordingServerEvents::onServerStopping);
        NeoForge.EVENT_BUS.addListener(RecordingServerEvents::onServerStopped);
        NeoForge.EVENT_BUS.addListener(AutomationVisualServerEvents::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(DockLinkInteractionService::onRightClickBlock);
        LOGGER.info("Create Aeronautics dependency state: {}", CreateAeronauticsCompat.describeLoadedState());
    }
}
