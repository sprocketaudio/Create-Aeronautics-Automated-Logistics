package net.sprocketgames.create_aeronautics_automated_logistics.drive;

import java.util.Arrays;
import java.util.List;

/**
 * Logical drive demands. These name what a player-built circuit should do,
 * never the specific Create Aeronautics or Sable component that does it.
 */
public enum DriveIntentType {
    LIFT_TARGET_STRENGTH(DriveSignalCategory.LIFT, DriveEncodingType.STRENGTH_0_TO_15),
    LIFT_INCREASE(DriveSignalCategory.LIFT, DriveEncodingType.ON_OFF),
    LIFT_DECREASE(DriveSignalCategory.LIFT, DriveEncodingType.ON_OFF),
    FORWARD_BACK_TARGET_STRENGTH(DriveSignalCategory.FORWARD_BACK, DriveEncodingType.STRENGTH_0_TO_15),
    FORWARD_BACK_INCREASE(DriveSignalCategory.FORWARD_BACK, DriveEncodingType.ON_OFF),
    FORWARD_BACK_DECREASE(DriveSignalCategory.FORWARD_BACK, DriveEncodingType.ON_OFF),
    FORWARD_BACK_FORWARD(DriveSignalCategory.FORWARD_BACK, DriveEncodingType.ON_OFF),
    FORWARD_BACK_BACKWARD(DriveSignalCategory.FORWARD_BACK, DriveEncodingType.ON_OFF),
    FORWARD_BACK_DIRECTION(DriveSignalCategory.FORWARD_BACK, DriveEncodingType.ON_OFF),
    LEFT_RIGHT_TARGET_STRENGTH(DriveSignalCategory.LEFT_RIGHT, DriveEncodingType.STRENGTH_0_TO_15),
    LEFT_RIGHT_INCREASE(DriveSignalCategory.LEFT_RIGHT, DriveEncodingType.ON_OFF),
    LEFT_RIGHT_DECREASE(DriveSignalCategory.LEFT_RIGHT, DriveEncodingType.ON_OFF),
    LEFT_RIGHT_LEFT(DriveSignalCategory.LEFT_RIGHT, DriveEncodingType.ON_OFF),
    LEFT_RIGHT_RIGHT(DriveSignalCategory.LEFT_RIGHT, DriveEncodingType.ON_OFF),
    LEFT_RIGHT_DIRECTION(DriveSignalCategory.LEFT_RIGHT, DriveEncodingType.ON_OFF),
    TURN_TARGET_STRENGTH(DriveSignalCategory.TURN, DriveEncodingType.STRENGTH_0_TO_15),
    TURN_INCREASE(DriveSignalCategory.TURN, DriveEncodingType.ON_OFF),
    TURN_DECREASE(DriveSignalCategory.TURN, DriveEncodingType.ON_OFF),
    TURN_CLOCKWISE(DriveSignalCategory.TURN, DriveEncodingType.ON_OFF),
    TURN_ANTICLOCKWISE(DriveSignalCategory.TURN, DriveEncodingType.ON_OFF),
    TURN_DIRECTION(DriveSignalCategory.TURN, DriveEncodingType.ON_OFF),
    ROTATIONAL_FORCE_ENABLED(DriveSignalCategory.ROTATIONAL_FORCE, DriveEncodingType.ON_OFF),
    DOCKED(DriveSignalCategory.DOCKING, DriveEncodingType.ON_OFF);

    private final DriveSignalCategory category;
    private final DriveEncodingType encoding;

    DriveIntentType(DriveSignalCategory category, DriveEncodingType encoding) {
        this.category = category;
        this.encoding = encoding;
    }

    public DriveSignalCategory category() {
        return category;
    }

    public DriveEncodingType encoding() {
        return encoding;
    }

    public boolean supports(DriveEncodingType candidate) {
        return encoding == candidate;
    }

    public static List<DriveIntentType> forCategory(DriveSignalCategory category) {
        return Arrays.stream(values()).filter(intent -> intent.category == category).toList();
    }
}
