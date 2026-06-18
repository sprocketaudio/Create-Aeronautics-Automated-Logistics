package net.sprocketgames.create_aeronautics_automated_logistics;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.sprocketgames.create_aeronautics_automated_logistics.command.AutomatedLogisticsCommands;
import net.sprocketgames.create_aeronautics_automated_logistics.compat.CreateAeronauticsCompat;
import net.sprocketgames.create_aeronautics_automated_logistics.network.ModNetworking;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlockEntities;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlocks;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModCreativeTabs;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModItems;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModMenus;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AutomationVisualServerEvents;
import net.sprocketgames.create_aeronautics_automated_logistics.service.CargoLinkInteractionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.DockLinkInteractionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RecordingServerEvents;
import net.sprocketgames.create_aeronautics_automated_logistics.service.StationChunkLoadingService;
import org.slf4j.Logger;

@Mod(CreateAeronauticsAutomatedLogistics.MOD_ID)
public class CreateAeronauticsAutomatedLogistics {
    public static final String MOD_ID = "create_aeronautics_automated_logistics";
    public static final Logger LOGGER = LogUtils.getLogger();

    private enum DebugChannel {
        PLAYBACK("playback"),
        VEHICLE("vehicle"),
        DOCKING("docking"),
        CARGO("cargo"),
        UI_SYNC("ui");

        private final String label;

        DebugChannel(String label) {
            this.label = label;
        }
    }

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

    public static void debugPlayback(String message, Object... args) {
        log(DebugChannel.PLAYBACK, false, message, args);
    }

    public static void debugPlaybackWarn(String message, Object... args) {
        log(DebugChannel.PLAYBACK, true, message, args);
    }

    public static void debugVehicle(String message, Object... args) {
        log(DebugChannel.VEHICLE, false, message, args);
    }

    public static void debugVehicleWarn(String message, Object... args) {
        log(DebugChannel.VEHICLE, true, message, args);
    }

    public static void debugDocking(String message, Object... args) {
        log(DebugChannel.DOCKING, false, message, args);
    }

    public static void debugDockingWarn(String message, Object... args) {
        log(DebugChannel.DOCKING, true, message, args);
    }

    public static void debugCargo(String message, Object... args) {
        log(DebugChannel.CARGO, false, message, args);
    }

    public static void debugCargoWarn(String message, Object... args) {
        log(DebugChannel.CARGO, true, message, args);
    }

    public static void debugUi(String message, Object... args) {
        log(DebugChannel.UI_SYNC, false, message, args);
    }

    public static void debugUiWarn(String message, Object... args) {
        log(DebugChannel.UI_SYNC, true, message, args);
    }

    private static void log(DebugChannel channel, boolean warn, String message, Object... args) {
        if (!AutomatedLogisticsConfig.debugLogging() || !channelEnabled(channel)) {
            return;
        }
        Object[] prefixedArgs = prependArg(channel.label, args);
        if (warn) {
            LOGGER.warn("[{}] " + message, prefixedArgs);
        } else {
            LOGGER.info("[{}] " + message, prefixedArgs);
        }
    }

    private static boolean channelEnabled(DebugChannel channel) {
        return switch (channel) {
            case PLAYBACK -> AutomatedLogisticsConfig.debugPlayback();
            case VEHICLE -> AutomatedLogisticsConfig.debugVehicle();
            case DOCKING -> AutomatedLogisticsConfig.debugDocking();
            case CARGO -> AutomatedLogisticsConfig.debugCargo();
            case UI_SYNC -> AutomatedLogisticsConfig.debugUiSync();
        };
    }

    private static Object[] prependArg(Object first, Object[] args) {
        Object[] prefixedArgs = new Object[args.length + 1];
        prefixedArgs[0] = first;
        System.arraycopy(args, 0, prefixedArgs, 1, args.length);
        return prefixedArgs;
    }

    public CreateAeronauticsAutomatedLogistics(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(ModNetworking::register);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, AutomatedLogisticsConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(AutomatedLogisticsCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(RecordingServerEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(RecordingServerEvents::onSablePrePhysicsTick);
        NeoForge.EVENT_BUS.addListener(RecordingServerEvents::onSablePostPhysicsTick);
        NeoForge.EVENT_BUS.addListener(RecordingServerEvents::onServerStopping);
        NeoForge.EVENT_BUS.addListener(RecordingServerEvents::onServerStopped);
        NeoForge.EVENT_BUS.addListener(StationChunkLoadingService::onServerStarted);
        NeoForge.EVENT_BUS.addListener(StationChunkLoadingService::onServerTickPre);
        NeoForge.EVENT_BUS.addListener(StationChunkLoadingService::onServerTick);
        NeoForge.EVENT_BUS.addListener(AutomationVisualServerEvents::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(DockLinkInteractionService::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(CargoLinkInteractionService::onRightClickBlock);
        LOGGER.info("Create Aeronautics dependency state: {}", CreateAeronauticsCompat.describeLoadedState());
    }
}
