package net.sprocketgames.create_aeronautics_automated_logistics;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.sprocketgames.create_aeronautics_automated_logistics.route.PlaybackMode;
import net.sprocketgames.create_aeronautics_automated_logistics.service.ActiveVehicleLimitMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AutomatedLogisticsConfig {
    private static final String LEGACY_OWNERSHIP_KEY = "restrictTransponderControlToOwner";
    private static final String OWNERSHIP_KEY = "enableOwnershipPermissions";
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue SAMPLE_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue MIN_DISTANCE_BETWEEN_POINTS;
    public static final ModConfigSpec.IntValue MAX_ROUTE_POINTS;

    public static final ModConfigSpec.EnumValue<PlaybackMode> PLAYBACK_MODE;
    public static final ModConfigSpec.DoubleValue MAX_PLAYBACK_SPEED_BLOCKS_PER_SECOND;
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
    public static final ModConfigSpec.EnumValue<ActiveVehicleLimitMode> ACTIVE_VEHICLE_LIMIT_MODE;
    public static final ModConfigSpec.BooleanValue ENABLE_OWNERSHIP_PERMISSIONS;
    public static final ModConfigSpec.BooleanValue ALLOW_TEAM_STATION_USE;
    public static final ModConfigSpec.BooleanValue ALLOW_ALLIED_STATION_USE;
    public static final ModConfigSpec.BooleanValue ALLOW_TEAM_STATION_CONTROL;
    public static final ModConfigSpec.BooleanValue ALLOW_ALLIED_STATION_CONTROL;
    public static final ModConfigSpec.BooleanValue ALLOW_TEAM_TRANSPONDER_CONTROL;
    public static final ModConfigSpec.BooleanValue ALLOW_ALLIED_TRANSPONDER_CONTROL;
    public static final ModConfigSpec.EnumValue<TerminalAudience> LOGISTICS_TERMINAL_PREVIEW_VISIBILITY;
    public static final ModConfigSpec.EnumValue<TerminalAudience> LOGISTICS_TERMINAL_ACCESS;
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
        MAX_PLAYBACK_SPEED_BLOCKS_PER_SECOND = BUILDER
                .comment("Maximum automated playback speed in blocks per second.")
                .comment("Default is 60. Higher values can cause overshoot, unstable docking, unloaded-travel issues, reload/recovery faults, or other automation failures on very fast vehicles.")
                .comment("If higher values break something, please report it at https://github.com/sprocketaudio/Create-Aeronautics-Automated-Logistics/issues")
                .defineInRange("maxPlaybackSpeedBlocksPerSecond", 60.0D, 1.0D, 1000.0D);
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
                .defineInRange("stationInteractionChunkRadius", 1, 0, 2);
        BUILDER.pop();

        BUILDER.push("limits");
        MAX_ACTIVE_VEHICLES_PER_PLAYER = BUILDER
                .comment("Maximum number of simultaneously active automated vehicles in each owner or team limit bucket.")
                .defineInRange("maxActiveVehiclesPerPlayer", 8, 0, 1024);
        ACTIVE_VEHICLE_LIMIT_MODE = BUILDER
                .comment("How active automated ship limits are pooled: PER_OWNER or PER_TEAM.")
                .comment("PER_TEAM falls back to PER_OWNER when FTB Teams is not installed or the owner has no team.")
                .defineEnum("activeVehicleLimitMode", ActiveVehicleLimitMode.PER_TEAM);
        BUILDER.pop();

        BUILDER.push("permissions");
        ENABLE_OWNERSHIP_PERMISSIONS = BUILDER
                .comment("Enable ownership-based permission checks for Ship Transponders, Airship Stations, and related logistics controls.")
                .comment("When true, owner, team, ally, and operator rules are enforced.")
                .comment("When false, these blocks behave as unrestricted public controls.")
                .define("enableOwnershipPermissions", true);
        ALLOW_TEAM_STATION_USE = BUILDER
                .comment("Allow FTB Teams members to use stations owned by another member of their team without granting station admin control.")
                .comment("Use includes landing, queueing, docking, viewing their allowed routes, and starting or stopping their own allowed vehicles.")
                .comment("Set this to true and allowTeamStationControl to false if you want team members to use stations without editing dock or cargo links.")
                .define("allowTeamStationUse", true);
        ALLOW_ALLIED_STATION_USE = BUILDER
                .comment("Allow FTB Teams allies to use stations without granting station admin control.")
                .comment("Use includes landing, queueing, docking, viewing their allowed routes, and starting or stopping their own allowed vehicles.")
                .comment("Set this to true and allowAlliedStationControl to false if you want allies to use stations without editing dock or cargo links.")
                .define("allowAlliedStationUse", false);
        ALLOW_TEAM_STATION_CONTROL = BUILDER
                .comment("Allow FTB Teams members to fully control stations owned by another member of their team.")
                .comment("Control includes station admin actions such as changing dock links and cargo links.")
                .define("allowTeamStationControl", true);
        ALLOW_ALLIED_STATION_CONTROL = BUILDER
                .comment("Allow FTB Teams allies to fully control stations.")
                .comment("Control includes station admin actions such as changing dock links and cargo links.")
                .define("allowAlliedStationControl", false);
        ALLOW_TEAM_TRANSPONDER_CONTROL = BUILDER
                .comment("Allow FTB Teams members to control transponders owned by another member of their team.")
                .define("allowTeamTransponderControl", true);
        ALLOW_ALLIED_TRANSPONDER_CONTROL = BUILDER
                .comment("Allow FTB Teams allies to control transponders.")
                .define("allowAlliedTransponderControl", false);
        LOGISTICS_TERMINAL_PREVIEW_VISIBILITY = BUILDER
                .comment("Who can see the passive top-surface preview on nearby Logistics Terminals.")
                .comment("Options: OWNER_ONLY, OWNER_AND_TEAM, OWNER_TEAM_AND_ALLIES, PUBLIC.")
                .defineEnum("logisticsTerminalPreviewVisibility", TerminalAudience.OWNER_AND_TEAM);
        LOGISTICS_TERMINAL_ACCESS = BUILDER
                .comment("Who can open Logistics Terminals for the read-only network view.")
                .comment("Options: OWNER_ONLY, OWNER_AND_TEAM, OWNER_TEAM_AND_ALLIES, PUBLIC.")
                .defineEnum("logisticsTerminalAccess", TerminalAudience.OWNER_AND_TEAM);
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
        return safeBoolean(DEBUG_LOGGING, false);
    }

    public static boolean debugPlayback() {
        return debugLogging() && safeBoolean(DEBUG_PLAYBACK, true);
    }

    public static boolean debugVehicle() {
        return debugLogging() && safeBoolean(DEBUG_VEHICLE, true);
    }

    public static boolean debugDocking() {
        return debugLogging() && safeBoolean(DEBUG_DOCKING, true);
    }

    public static boolean debugCargo() {
        return debugLogging() && safeBoolean(DEBUG_CARGO, true);
    }

    public static boolean debugUiSync() {
        return debugLogging() && safeBoolean(DEBUG_UI_SYNC, true);
    }

    public static boolean requireCrouchToBreakRouteBlocks() {
        return REQUIRE_CROUCH_TO_BREAK_ROUTE_BLOCKS.get();
    }

    public static boolean ownershipPermissionsEnabled() {
        return ENABLE_OWNERSHIP_PERMISSIONS.get();
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

    public static TerminalAudience logisticsTerminalPreviewVisibility() {
        return LOGISTICS_TERMINAL_PREVIEW_VISIBILITY.get();
    }

    public static TerminalAudience logisticsTerminalAccess() {
        return LOGISTICS_TERMINAL_ACCESS.get();
    }

    private static boolean safeBoolean(ModConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (IllegalStateException ignored) {
            return fallback;
        }
    }

    public static void migrateLegacyOwnershipPermissions(Path configFile) {
        if (!Files.isRegularFile(configFile)) {
            return;
        }
        try {
            List<String> originalLines = Files.readAllLines(configFile, StandardCharsets.UTF_8);
            List<String> migratedLines = migrateLegacyOwnershipPermissions(originalLines);
            if (!migratedLines.equals(originalLines)) {
                Files.write(configFile, migratedLines, StandardCharsets.UTF_8);
            }
        } catch (IOException exception) {
            CreateAeronauticsAutomatedLogistics.LOGGER.warn("Failed to migrate legacy ownership permission config in {}", configFile, exception);
        }
    }

    private static List<String> migrateLegacyOwnershipPermissions(List<String> sourceLines) {
        List<String> lines = new ArrayList<>(sourceLines);
        lines = moveSectionBefore(lines, "[permissions]", "[debug]");
        int sectionStart = findSectionStart(lines, "[permissions]");
        if (sectionStart < 0) {
            return sourceLines;
        }
        int sectionEnd = findSectionEnd(lines, sectionStart + 1);
        int legacyIndex = findKeyIndex(lines, sectionStart + 1, sectionEnd, LEGACY_OWNERSHIP_KEY);
        if (legacyIndex < 0) {
            return reorderPermissionEntries(lines, sectionStart);
        }
        Boolean legacyValue = parseBooleanValue(lines.get(legacyIndex));
        if (legacyValue == null) {
            return reorderPermissionEntries(lines, sectionStart);
        }

        int existingNewIndex = findKeyIndex(lines, sectionStart + 1, sectionEnd, OWNERSHIP_KEY);
        if (existingNewIndex >= 0) {
            removeEntryWithLeadingComments(lines, existingNewIndex, sectionStart);
            sectionEnd = findSectionEnd(lines, sectionStart + 1);
            legacyIndex = findKeyIndex(lines, sectionStart + 1, sectionEnd, LEGACY_OWNERSHIP_KEY);
        }

        removeEntryWithLeadingComments(lines, legacyIndex, sectionStart);
        int insertIndex = sectionStart + 1;
        lines.add(insertIndex++, "\t#Enable ownership-based permission checks for Ship Transponders, Airship Stations, and related logistics controls.");
        lines.add(insertIndex++, "\t#When true, owner, team, ally, and operator rules are enforced.");
        lines.add(insertIndex++, "\t#When false, these blocks behave as unrestricted public controls.");
        lines.add(insertIndex, "\t" + OWNERSHIP_KEY + " = " + legacyValue);
        return reorderPermissionEntries(lines, sectionStart);
    }

    private static int findSectionStart(List<String> lines, String header) {
        for (int i = 0; i < lines.size(); i++) {
            if (header.equals(lines.get(i).trim())) {
                return i;
            }
        }
        return -1;
    }

    private static int findSectionEnd(List<String> lines, int fromIndex) {
        for (int i = fromIndex; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                return i;
            }
        }
        return lines.size();
    }

    private static int findKeyIndex(List<String> lines, int fromIndex, int toIndexExclusive, String key) {
        for (int i = fromIndex; i < toIndexExclusive; i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.startsWith(key + " =")) {
                return i;
            }
        }
        return -1;
    }

    private static Boolean parseBooleanValue(String line) {
        int separator = line.indexOf('=');
        if (separator < 0) {
            return null;
        }
        String value = line.substring(separator + 1).trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static void removeEntryWithLeadingComments(List<String> lines, int keyIndex, int sectionStart) {
        int removeStart = keyIndex;
        while (removeStart > sectionStart + 1 && lines.get(removeStart - 1).trim().startsWith("#")) {
            removeStart--;
        }
        for (int i = keyIndex; i >= removeStart; i--) {
            lines.remove(i);
        }
    }

    private static List<String> moveSectionBefore(List<String> sourceLines, String sectionHeader, String beforeHeader) {
        List<String> lines = new ArrayList<>(sourceLines);
        int sectionStart = findSectionStart(lines, sectionHeader);
        int beforeStart = findSectionStart(lines, beforeHeader);
        if (sectionStart < 0 || beforeStart < 0 || sectionStart < beforeStart) {
            return lines;
        }

        int sectionEnd = findSectionEnd(lines, sectionStart + 1);
        List<String> sectionLines = new ArrayList<>(lines.subList(sectionStart, sectionEnd));
        for (int i = sectionEnd - 1; i >= sectionStart; i--) {
            lines.remove(i);
        }

        int insertAt = findSectionStart(lines, beforeHeader);
        if (insertAt < 0) {
            return sourceLines;
        }
        lines.addAll(insertAt, sectionLines);
        return lines;
    }

    private static List<String> reorderPermissionEntries(List<String> sourceLines, int sectionStart) {
        int sectionEnd = findSectionEnd(sourceLines, sectionStart + 1);
        String[] orderedKeys = {
                OWNERSHIP_KEY,
                "allowTeamStationUse",
                "allowAlliedStationUse",
                "allowTeamStationControl",
                "allowAlliedStationControl",
                "allowTeamTransponderControl",
                "allowAlliedTransponderControl",
                "logisticsTerminalPreviewVisibility",
                "logisticsTerminalAccess",
                "requireCrouchToBreakRouteBlocks"
        };

        List<String> lines = new ArrayList<>(sourceLines);
        List<List<String>> entries = new ArrayList<>();
        for (String key : orderedKeys) {
            int keyIndex = findKeyIndex(lines, sectionStart + 1, sectionEnd, key);
            if (keyIndex < 0) {
                continue;
            }
            int entryStart = keyIndex;
            while (entryStart > sectionStart + 1 && lines.get(entryStart - 1).trim().startsWith("#")) {
                entryStart--;
            }
            List<String> entryLines = new ArrayList<>(lines.subList(entryStart, keyIndex + 1));
            entries.add(entryLines);
        }

        if (entries.isEmpty()) {
            return sourceLines;
        }

        for (int i = sectionEnd - 1; i > sectionStart; i--) {
            lines.remove(i);
        }

        int insertIndex = sectionStart + 1;
        for (List<String> entry : entries) {
            lines.addAll(insertIndex, entry);
            insertIndex += entry.size();
        }
        return lines;
    }

}
