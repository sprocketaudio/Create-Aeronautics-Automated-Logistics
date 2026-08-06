package net.sprocketgames.create_aeronautics_automated_logistics.drive;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Logical drive demands. These name what a player-built circuit should do,
 * never the specific Create Aeronautics or Sable component that does it.
 */
public enum DriveIntentType {
    LIFT(DriveEncodingType.STRENGTH_0_TO_15),
    DOCK(DriveEncodingType.ON_OFF),

    NORTH(DriveEncodingType.ON_OFF),
    SOUTH(DriveEncodingType.ON_OFF),
    EAST(DriveEncodingType.ON_OFF),
    WEST(DriveEncodingType.ON_OFF),
    NORTH_SOUTH(DriveEncodingType.SPLIT_ANALOG),
    EAST_WEST(DriveEncodingType.SPLIT_ANALOG),

    THRUST(DriveEncodingType.SPLIT_ANALOG),
    YAW(DriveEncodingType.SPLIT_ANALOG, DriveEncodingType.PULSE_NUDGE),
    STRAFE(DriveEncodingType.SPLIT_ANALOG),
    PITCH(DriveEncodingType.SPLIT_ANALOG),
    ROLL(DriveEncodingType.SPLIT_ANALOG),
    BRAKE_DRAG(DriveEncodingType.STRENGTH_0_TO_15, DriveEncodingType.ON_OFF),

    RUNNING(DriveEncodingType.ON_OFF),
    PRECISION_APPROACH(DriveEncodingType.ON_OFF),
    WAITING(DriveEncodingType.ON_OFF);

    private final Set<DriveEncodingType> supportedEncodings;

    DriveIntentType(DriveEncodingType firstEncoding, DriveEncodingType... additionalEncodings) {
        EnumSet<DriveEncodingType> encodings = EnumSet.of(firstEncoding, additionalEncodings);
        supportedEncodings = Collections.unmodifiableSet(encodings);
    }

    public Set<DriveEncodingType> supportedEncodings() {
        return supportedEncodings;
    }

    public boolean supports(DriveEncodingType encoding) {
        return supportedEncodings.contains(encoding);
    }
}
