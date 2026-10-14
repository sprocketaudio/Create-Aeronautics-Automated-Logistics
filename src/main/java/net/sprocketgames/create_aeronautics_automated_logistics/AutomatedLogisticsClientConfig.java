package net.sprocketgames.create_aeronautics_automated_logistics;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AutomatedLogisticsClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue RENDER_LOGISTICS_TERMINAL_TOP_PREVIEW;
    public static final ModConfigSpec.BooleanValue ENABLE_FTB_CHUNKS_INTEGRATION;
    public static final ModConfigSpec.BooleanValue ENABLE_JOURNEYMAP_INTEGRATION;
    public static final ModConfigSpec.BooleanValue ENABLE_XAERO_MAPS_INTEGRATION;
    public static final ModConfigSpec.IntValue MAP_MARKER_ICON_SCALE_PERCENT;
    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("rendering");
        RENDER_LOGISTICS_TERMINAL_TOP_PREVIEW = BUILDER
                .comment("Render the passive route and vehicle preview on top of nearby Logistics Terminals.")
                .define("renderLogisticsTerminalTopPreview", true);
        BUILDER.pop();

        BUILDER.push("mapIntegrations");
        ENABLE_FTB_CHUNKS_INTEGRATION = BUILDER
                .comment("Show automated vehicle markers in FTB Chunks when it is installed.")
                .define("enableFtbChunks", true);
        ENABLE_JOURNEYMAP_INTEGRATION = BUILDER
                .comment("Show automated vehicle markers in JourneyMap when it is installed.")
                .define("enableJourneyMap", true);
        ENABLE_XAERO_MAPS_INTEGRATION = BUILDER
                .comment("Show automated vehicle markers in Xaero's World Map and Minimap when either is installed.")
                .define("enableXaeroMaps", true);
        MAP_MARKER_ICON_SCALE_PERCENT = BUILDER
                .comment("Scale automated vehicle map icons in JourneyMap, Xaero Maps, and FTB Chunks. Labels follow this setting gently, staying between 65% and 125% for readability.")
                .defineInRange("markerIconScalePercent", 100, 25, 300);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private AutomatedLogisticsClientConfig() {
    }

    public static boolean ftbChunksIntegrationEnabled() {
        return safeBoolean(ENABLE_FTB_CHUNKS_INTEGRATION);
    }

    public static boolean journeyMapIntegrationEnabled() {
        return safeBoolean(ENABLE_JOURNEYMAP_INTEGRATION);
    }

    public static boolean xaeroMapsIntegrationEnabled() {
        return safeBoolean(ENABLE_XAERO_MAPS_INTEGRATION);
    }

    public static float mapMarkerIconScale() {
        try {
            return MAP_MARKER_ICON_SCALE_PERCENT.get() / 100.0F;
        } catch (IllegalStateException ignored) {
            return 1.0F;
        }
    }

    public static float mapMarkerLabelScale() {
        return Math.clamp(mapMarkerIconScale(), 0.65F, 1.25F);
    }

    private static boolean safeBoolean(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException ignored) {
            return true;
        }
    }
}
