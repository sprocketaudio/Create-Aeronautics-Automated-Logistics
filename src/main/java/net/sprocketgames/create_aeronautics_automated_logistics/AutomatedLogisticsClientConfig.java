package net.sprocketgames.create_aeronautics_automated_logistics;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AutomatedLogisticsClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue RENDER_LOGISTICS_TERMINAL_TOP_PREVIEW;
    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("rendering");
        RENDER_LOGISTICS_TERMINAL_TOP_PREVIEW = BUILDER
                .comment("Render the passive route and vehicle preview on top of nearby Logistics Terminals.")
                .define("renderLogisticsTerminalTopPreview", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private AutomatedLogisticsClientConfig() {
    }
}
