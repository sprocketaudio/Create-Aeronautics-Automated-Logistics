package net.sprocketgames.create_aeronautics_automated_logistics;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.sprocketgames.create_aeronautics_automated_logistics.route.PlaybackMode;

public class AutomatedLogisticsConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue SAMPLE_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue MIN_DISTANCE_BETWEEN_POINTS;
    public static final ModConfigSpec.IntValue MAX_ROUTE_POINTS;

    public static final ModConfigSpec.EnumValue<PlaybackMode> PLAYBACK_MODE;
    public static final ModConfigSpec.DoubleValue MAX_SPEED_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MAX_START_JOIN_DISTANCE;
    public static final ModConfigSpec.BooleanValue STOP_ON_COLLISION;
    public static final ModConfigSpec.BooleanValue STOP_ON_ERROR;

    public static final ModConfigSpec.BooleanValue SHOW_ROUTE_PREVIEW;
    public static final ModConfigSpec.IntValue PREVIEW_RANGE;

    public static final ModConfigSpec.IntValue STATION_DOCK_SEARCH_RADIUS;
    public static final ModConfigSpec.IntValue SHIP_DOCK_SEARCH_RADIUS;
    public static final ModConfigSpec.IntValue DOCK_LOCK_TIMEOUT_TICKS;
    public static final ModConfigSpec.IntValue DOCK_IDLE_TIMEOUT_TICKS;
    public static final ModConfigSpec.IntValue DOCK_CARGO_TIMEOUT_TICKS;

    public static final ModConfigSpec.IntValue MAX_ACTIVE_VEHICLES_PER_PLAYER;
    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING;

    static final ModConfigSpec SPEC;

    static {
        BUILDER.push("recording");
        SAMPLE_INTERVAL_TICKS = BUILDER
                .comment("Number of ticks between route recording samples.")
                .defineInRange("sampleIntervalTicks", 10, 1, 20 * 60);
        MIN_DISTANCE_BETWEEN_POINTS = BUILDER
                .comment("Minimum distance between saved route points.")
                .defineInRange("minDistanceBetweenPoints", 1.0D, 0.0D, 1024.0D);
        MAX_ROUTE_POINTS = BUILDER
                .comment("Maximum number of route points stored per route.")
                .defineInRange("maxRoutePoints", 5000, 2, 100000);
        BUILDER.pop();

        BUILDER.push("playback");
        PLAYBACK_MODE = BUILDER
                .comment("Default route playback mode.")
                .defineEnum("mode", PlaybackMode.PING_PONG);
        MAX_SPEED_MULTIPLIER = BUILDER
                .comment("Maximum playback speed multiplier.")
                .defineInRange("maxSpeedMultiplier", 1.0D, 0.1D, 10.0D);
        MAX_START_JOIN_DISTANCE = BUILDER
                .comment("Maximum distance from the nearest route endpoint allowed when beginning playback.")
                .defineInRange("maxStartJoinDistance", 24.0D, 0.0D, 512.0D);
        STOP_ON_COLLISION = BUILDER
                .comment("Stop automated playback when collision is detected.")
                .define("stopOnCollision", true);
        STOP_ON_ERROR = BUILDER
                .comment("Stop automated playback when an unrecoverable error is detected.")
                .define("stopOnError", true);
        BUILDER.pop();

        BUILDER.push("visuals");
        SHOW_ROUTE_PREVIEW = BUILDER
                .comment("Show route previews while viewing or editing a route.")
                .define("showRoutePreview", true);
        PREVIEW_RANGE = BUILDER
                .comment("Maximum route preview range in blocks.")
                .defineInRange("previewRange", 128, 0, 1024);
        BUILDER.pop();

        BUILDER.push("docking");
        STATION_DOCK_SEARCH_RADIUS = BUILDER
                .comment("Search radius in blocks for finding exactly one ground-side Docking Connector near an Airship Station.")
                .defineInRange("stationDockSearchRadius", 24, 1, 128);
        SHIP_DOCK_SEARCH_RADIUS = BUILDER
                .comment("Search radius in blocks for finding exactly one ship-side Docking Connector near a Ship Transponder on an assembled ship.")
                .defineInRange("shipDockSearchRadius", 24, 1, 128);
        DOCK_LOCK_TIMEOUT_TICKS = BUILDER
                .comment("Maximum ticks to wait for station and ship Docking Connectors to lock after a docking stop starts.")
                .defineInRange("dockLockTimeoutTicks", 20 * 30, 20, 20 * 60 * 10);
        DOCK_IDLE_TIMEOUT_TICKS = BUILDER
                .comment("Maximum ticks to wait for dock transfer activity to become idle before continuing.")
                .defineInRange("dockIdleTimeoutTicks", 20 * 120, 20, 20 * 60 * 30);
        DOCK_CARGO_TIMEOUT_TICKS = BUILDER
                .comment("Maximum ticks to wait for dock-visible cargo threshold conditions before failing playback.")
                .defineInRange("dockCargoTimeoutTicks", 20 * 120, 20, 20 * 60 * 30);
        BUILDER.pop();

        BUILDER.push("limits");
        MAX_ACTIVE_VEHICLES_PER_PLAYER = BUILDER
                .comment("Maximum number of simultaneously active automated vehicles per player.")
                .defineInRange("maxActiveVehiclesPerPlayer", 8, 0, 1024);
        BUILDER.pop();

        BUILDER.push("debug");
        DEBUG_LOGGING = BUILDER
                .comment("Enable verbose automated logistics debug logging.")
                .define("debugLogging", false);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static boolean debugLogging() {
        return DEBUG_LOGGING.get();
    }

}
