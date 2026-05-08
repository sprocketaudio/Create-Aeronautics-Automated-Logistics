package net.sprocketgames.create_aeronautics_automated_logistics.compat;

import net.neoforged.fml.ModList;

public final class CreateAeronauticsCompat {
    public static final String AERONAUTICS_MOD_ID = "aeronautics";
    public static final String AERONAUTICS_BUNDLED_MOD_ID = "aeronautics_bundled";
    public static final String CREATE_MOD_ID = "create";
    public static final String SABLE_MOD_ID = "sable";
    public static final String SIMULATED_MOD_ID = "simulated";

    private CreateAeronauticsCompat() {
    }

    public static boolean isAeronauticsLoaded() {
        return ModList.get().isLoaded(AERONAUTICS_MOD_ID)
                || ModList.get().isLoaded(AERONAUTICS_BUNDLED_MOD_ID);
    }

    public static String describeLoadedState() {
        return "aeronautics=" + versionOrMissing(AERONAUTICS_MOD_ID)
                + ", bundled=" + versionOrMissing(AERONAUTICS_BUNDLED_MOD_ID)
                + ", create=" + versionOrMissing(CREATE_MOD_ID)
                + ", sable=" + versionOrMissing(SABLE_MOD_ID)
                + ", simulated=" + versionOrMissing(SIMULATED_MOD_ID);
    }

    private static String versionOrMissing(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("missing");
    }
}
