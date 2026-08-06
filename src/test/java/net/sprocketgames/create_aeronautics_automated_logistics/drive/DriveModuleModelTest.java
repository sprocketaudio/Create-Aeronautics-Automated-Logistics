package net.sprocketgames.create_aeronautics_automated_logistics.drive;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DriveModuleModelTest {
    private static final UUID MODULE_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID CONTROLLER_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");

    @Test
    void acceptsTheFirstPassModuleContracts() {
        assertTrue(new DriveModuleConfig(DriveIntentType.LIFT, DriveEncodingType.STRENGTH_0_TO_15).intent()
                .supports(DriveEncodingType.STRENGTH_0_TO_15));
        assertTrue(new DriveModuleConfig(DriveIntentType.DOCK, DriveEncodingType.ON_OFF).intent()
                .supports(DriveEncodingType.ON_OFF));
        assertTrue(new DriveModuleConfig(DriveIntentType.NORTH_SOUTH, DriveEncodingType.SPLIT_ANALOG).intent()
                .supports(DriveEncodingType.SPLIT_ANALOG));
        assertTrue(new DriveModuleConfig(DriveIntentType.WEST, DriveEncodingType.ON_OFF).intent()
                .supports(DriveEncodingType.ON_OFF));
    }

    @Test
    void rejectsUnsupportedIntentAndEncodingPairs() {
        assertThrows(IllegalArgumentException.class,
                () -> new DriveModuleConfig(DriveIntentType.DOCK, DriveEncodingType.STRENGTH_0_TO_15));
    }

    @Test
    void keepsAnUnlinkedModuleFreeOfControllerConfiguration() {
        DriveModuleState state = DriveModuleState.unlinked(MODULE_ID);

        assertFalse(state.linked());
        assertTrue(state.config().isEmpty());
    }

    @Test
    void requiresLinkAndConfigurationToAppearTogether() {
        DriveModuleLink link = new DriveModuleLink(MODULE_ID, CONTROLLER_ID);

        assertThrows(IllegalArgumentException.class, () -> new DriveModuleState(
                MODULE_ID,
                Optional.of(link),
                Optional.empty(),
                DriveModuleHealth.LINKED
        ));
    }
}
