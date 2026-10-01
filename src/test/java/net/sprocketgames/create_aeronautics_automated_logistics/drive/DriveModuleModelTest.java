package net.sprocketgames.create_aeronautics_automated_logistics.drive;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertTrue(new DriveModuleConfig(DriveIntentType.LIFT_TARGET_STRENGTH, DriveEncodingType.STRENGTH_0_TO_15).intent()
                .supports(DriveEncodingType.STRENGTH_0_TO_15));
        assertTrue(new DriveModuleConfig(DriveIntentType.DOCKED, DriveEncodingType.ON_OFF).intent()
                .supports(DriveEncodingType.ON_OFF));
        assertTrue(new DriveModuleConfig(DriveIntentType.FORWARD_BACK_DIRECTION, DriveEncodingType.ON_OFF).intent()
                .supports(DriveEncodingType.ON_OFF));
        assertTrue(new DriveModuleConfig(DriveIntentType.LEFT_RIGHT_TARGET_STRENGTH, DriveEncodingType.STRENGTH_0_TO_15).intent()
                .supports(DriveEncodingType.STRENGTH_0_TO_15));
        assertTrue(new DriveModuleConfig(DriveIntentType.FORWARD_BACK_INCREASE, DriveEncodingType.ON_OFF).intent()
                .supports(DriveEncodingType.ON_OFF));
        assertTrue(new DriveModuleConfig(DriveIntentType.TURN_TARGET_STRENGTH, DriveEncodingType.STRENGTH_0_TO_15).intent()
                .supports(DriveEncodingType.STRENGTH_0_TO_15));
    }

    @Test
    void rejectsUnsupportedIntentAndEncodingPairs() {
        assertThrows(IllegalArgumentException.class,
                () -> new DriveModuleConfig(DriveIntentType.DOCKED, DriveEncodingType.STRENGTH_0_TO_15));
    }

    @Test
    void supportsSignalInversionForDriveOutputsOnly() {
        assertTrue(new DriveModuleConfig(
                DriveIntentType.FORWARD_BACK_DIRECTION,
                DriveEncodingType.ON_OFF,
                true
        ).supportsInversion());
        assertFalse(new DriveModuleConfig(
                DriveIntentType.DOCKED,
                DriveEncodingType.ON_OFF
        ).supportsInversion());
        assertTrue(new DriveModuleConfig(
                DriveIntentType.ROTATIONAL_FORCE_ENABLED,
                DriveEncodingType.ON_OFF
        ).supportsInversion());
    }

    @Test
    void exposesOnlyTheAgreedFirstPassControls() {
        assertEquals(
                java.util.List.of(
                        DriveSignalCategory.LIFT,
                        DriveSignalCategory.FORWARD_BACK,
                        DriveSignalCategory.LEFT_RIGHT,
                        DriveSignalCategory.TURN,
                        DriveSignalCategory.ROTATIONAL_FORCE,
                        DriveSignalCategory.DOCKING
                ),
                java.util.List.of(DriveSignalCategory.values())
        );
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
