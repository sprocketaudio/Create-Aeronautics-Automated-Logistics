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
    public static final ModConfigSpec.BooleanValue ALLOW_ONE_WAY_ROUTE_PLANS;
    public static final ModConfigSpec.BooleanValue STOP_ON_COLLISION;
    public static final ModConfigSpec.DoubleValue SEGMENT_OVERRUN_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MIN_MEANINGFUL_PROGRESS_DISTANCE;
    public static final ModConfigSpec.IntValue STUCK_TIMEOUT_TICKS;

    public static final ModConfigSpec.IntValue STATION_DOCK_SEARCH_RADIUS;
    public static final ModConfigSpec.IntValue DOCK_LOCK_TIMEOUT_TICKS;
    public static final ModConfigSpec.IntValue DOCK_IDLE_TIMEOUT_TICKS;
    public static final ModConfigSpec.DoubleValue DOCK_RESERVATION_CLEARANCE_DISTANCE;
    public static final ModConfigSpec.BooleanValue FORCE_LOAD_STATION_CHUNKS;
    public static final ModConfigSpec.IntValue STATION_INTERACTION_CHUNK_RADIUS;

    public static final ModConfigSpec.IntValue MAX_ACTIVE_VEHICLES_PER_PLAYER;
    public static final ModConfigSpec.BooleanValue RESTRICT_TRANSPONDER_CONTROL_TO_OWNER;
    public static final ModConfigSpec.BooleanValue REQUIRE_CROUCH_TO_BREAK_ROUTE_BLOCKS;
    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING;
    public static final ModConfigSpec.BooleanValue DEBUG_PLAYBACK;
    public static final ModConfigSpec.BooleanValue DEBUG_VEHICLE;
    public static final ModConfigSpec.BooleanValue DEBUG_DOCKING;
    public static final ModConfigSpec.BooleanValue DEBUG_CARGO;
    public static final ModConfigSpec.BooleanValue DEBUG_UI_SYNC;

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
        ALLOW_ONE_WAY_ROUTE_PLANS = BUILDER
                .comment("Allow a single recorded leg / single stop plan to count as a valid runnable route.")
                .define("allowOneWayRoutePlans", false);
        STOP_ON_COLLISION = BUILDER
                .comment("Stop automated playback when collision is detected.")
                .define("stopOnCollision", true);
        SEGMENT_OVERRUN_MULTIPLIER = BUILDER
                .comment("How many times longer than the recorded segment duration playback may take before overdue stuck monitoring begins.")
                .defineInRange("segmentOverrunMultiplier", 3.0D, 1.0D, 20.0D);
        MIN_MEANINGFUL_PROGRESS_DISTANCE = BUILDER
                .comment("Minimum net distance in blocks an overdue segment must gain toward its target before the overdue stuck timer is reset.")
                .defineInRange("minMeaningfulProgressDistance", 0.25D, 0.01D, 32.0D);
        STUCK_TIMEOUT_TICKS = BUILDER
                .comment("Maximum ticks an overdue segment may continue without meaningful net progress before playback pauses in a fault hold.")
                .defineInRange("stuckTimeoutTicks", 200, 20, 20 * 60 * 30);
        BUILDER.pop();

        BUILDER.push("docking");
        STATION_DOCK_SEARCH_RADIUS = BUILDER
                .comment("Search radius in blocks for finding exactly one ground-side Docking Connector near an Airship Station.")
                .defineInRange("stationDockSearchRadius", 24, 1, 128);
        DOCK_LOCK_TIMEOUT_TICKS = BUILDER
                .comment("Maximum ticks to wait for station and ship Docking Connectors to lock after a docking stop starts.")
                .defineInRange("dockLockTimeoutTicks", 20 * 10, 20, 20 * 60 * 10);
        DOCK_IDLE_TIMEOUT_TICKS = BUILDER
                .comment("Maximum ticks to wait for dock transfer activity to become idle before continuing.")
                .defineInRange("dockIdleTimeoutTicks", 20 * 120, 20, 20 * 60 * 30);
        DOCK_RESERVATION_CLEARANCE_DISTANCE = BUILDER
                .comment("Recorded route distance in blocks kept clear around a reserved dock.")
                .comment("Incoming ships queue before this much inbound path remains to the docking stop; the holder releases after clearing this much outbound path after the stop.")
                .defineInRange("dockReservationClearanceDistance", 80.0D, 1.0D, 512.0D);
        FORCE_LOAD_STATION_CHUNKS = BUILDER
                .comment("Keep Airship Station chunks force-loaded so route starts, docking, and stop context remain available even when players move away.")
                .comment("Disable this if you prefer to manage loading with another chunk-loader mod.")
                .define("forceLoadStationChunks", true);
        STATION_INTERACTION_CHUNK_RADIUS = BUILDER
                .comment("Extra station-centered chunk radius to temporarily force-load while a ship is docking.")
                .comment("0 loads only the station chunk, 1 loads a 3x3 chunk square, and 2 loads a 5x5 chunk square.")
                .defineInRange("stationInteractionChunkRadius", 0, 0, 2);
        BUILDER.pop();

        BUILDER.push("limits");
        MAX_ACTIVE_VEHICLES_PER_PLAYER = BUILDER
                .comment("Maximum number of simultaneously active automated vehicles per player.")
                .defineInRange("maxActiveVehiclesPerPlayer", 8, 0, 1024);
        RESTRICT_TRANSPONDER_CONTROL_TO_OWNER = BUILDER
                .comment("Restrict Ship Transponder and Airship Station control actions to the owner and server operators.")
                .define("restrictTransponderControlToOwner", true);
        REQUIRE_CROUCH_TO_BREAK_ROUTE_BLOCKS = BUILDER
                .comment("Require players to crouch while mining Airship Stations and Ship Transponders, because breaking them removes related routes.")
                .define("requireCrouchToBreakRouteBlocks", true);
        BUILDER.pop();

        BUILDER.push("debug");
        DEBUG_LOGGING = BUILDER
                .comment("Master switch for automated logistics debug logging.")
                .comment("When false, all category debug logs below are disabled.")
                .define("debugLogging", false);
        DEBUG_PLAYBACK = BUILDER
                .comment("Enable playback/runtime debug logs, including unloaded-transit progress and restore.")
                .comment("Only used when debugLogging is true.")
                .define("playback", true);
        DEBUG_VEHICLE = BUILDER
                .comment("Enable low-level vehicle/Sable controller debug logs.")
                .comment("Only used when debugLogging is true.")
                .define("vehicle", true);
        DEBUG_DOCKING = BUILDER
                .comment("Enable docking connector discovery and docking-runtime debug logs.")
                .comment("Only used when debugLogging is true.")
                .define("docking", true);
        DEBUG_CARGO = BUILDER
                .comment("Enable cargo endpoint, cargo wait, and cargo saved-data debug logs.")
                .comment("Only used when debugLogging is true.")
                .define("cargo", true);
        DEBUG_UI_SYNC = BUILDER
                .comment("Enable station/transponder menu, sync, and state-refresh debug logs.")
                .comment("Only used when debugLogging is true.")
                .define("uiSync", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static boolean debugLogging() {
        return DEBUG_LOGGING.get();
    }

    public static boolean debugPlayback() {
        return debugLogging() && DEBUG_PLAYBACK.get();
    }

    public static boolean debugVehicle() {
        return debugLogging() && DEBUG_VEHICLE.get();
    }

    public static boolean debugDocking() {
        return debugLogging() && DEBUG_DOCKING.get();
    }

    public static boolean debugCargo() {
        return debugLogging() && DEBUG_CARGO.get();
    }

    public static boolean debugUiSync() {
        return debugLogging() && DEBUG_UI_SYNC.get();
    }

    public static boolean requireCrouchToBreakRouteBlocks() {
        return REQUIRE_CROUCH_TO_BREAK_ROUTE_BLOCKS.get();
    }

    public static boolean allowOneWayRoutePlans() {
        return ALLOW_ONE_WAY_ROUTE_PLANS.get();
    }

    public static boolean forceLoadStationChunks() {
        return FORCE_LOAD_STATION_CHUNKS.get();
    }

    public static int stationInteractionChunkRadius() {
        return STATION_INTERACTION_CHUNK_RADIUS.get();
    }

}
