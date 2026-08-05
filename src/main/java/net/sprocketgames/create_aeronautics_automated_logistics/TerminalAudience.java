package net.sprocketgames.create_aeronautics_automated_logistics;

import net.minecraft.util.StringRepresentable;

public enum TerminalAudience implements StringRepresentable {
    OWNER_ONLY("owner_only"),
    OWNER_AND_TEAM("owner_and_team"),
    OWNER_TEAM_AND_ALLIES("owner_team_and_allies"),
    PUBLIC("public");

    private final String serializedName;

    TerminalAudience(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
