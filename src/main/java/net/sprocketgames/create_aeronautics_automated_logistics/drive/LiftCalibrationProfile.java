package net.sprocketgames.create_aeronautics_automated_logistics.drive;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record LiftCalibrationProfile(
        double neutralStrength,
        int ascendingStrength,
        double ascentRate,
        int descendingStrength,
        double descentRate,
        int responseTicks,
        long calibratedGameTime,
        boolean relativeControl,
        int takeoffIncreaseTicks,
        int increasePulseTicks,
        int decreasePulseTicks,
        int stopDecreaseTicks
) {
    private static final String NEUTRAL_STRENGTH = "neutralStrength";
    private static final String ASCENDING_STRENGTH = "ascendingStrength";
    private static final String ASCENT_RATE = "ascentRate";
    private static final String DESCENDING_STRENGTH = "descendingStrength";
    private static final String DESCENT_RATE = "descentRate";
    private static final String RESPONSE_TICKS = "responseTicks";
    private static final String CALIBRATED_GAME_TIME = "calibratedGameTime";
    private static final String RELATIVE_CONTROL = "relativeControl";
    private static final String TAKEOFF_INCREASE_TICKS = "takeoffIncreaseTicks";
    private static final String INCREASE_PULSE_TICKS = "increasePulseTicks";
    private static final String DECREASE_PULSE_TICKS = "decreasePulseTicks";
    private static final String STOP_DECREASE_TICKS = "stopDecreaseTicks";
    private static final double DEFAULT_RATE = 0.02D;
    private static final int MAX_RELATIVE_COMMAND_TICKS = 20 * 120;

    public LiftCalibrationProfile(
            double neutralStrength,
            int ascendingStrength,
            double ascentRate,
            int descendingStrength,
            double descentRate,
            int responseTicks,
            long calibratedGameTime
    ) {
        this(
                neutralStrength,
                ascendingStrength,
                ascentRate,
                descendingStrength,
                descentRate,
                responseTicks,
                calibratedGameTime,
                false,
                0,
                0,
                0,
                0
        );
    }

    public LiftCalibrationProfile {
        neutralStrength = Math.clamp(neutralStrength, 1.0D, 15.0D);
        ascendingStrength = Math.clamp(ascendingStrength, 1, 15);
        ascentRate = Math.clamp(Math.abs(ascentRate), 0.0001D, 1.0D);
        descendingStrength = Math.clamp(descendingStrength, 1, 15);
        descentRate = Math.clamp(Math.abs(descentRate), 0.0001D, 1.0D);
        responseTicks = Math.clamp(responseTicks, 40, 240);
        if (relativeControl) {
            takeoffIncreaseTicks = Math.clamp(takeoffIncreaseTicks, 1, MAX_RELATIVE_COMMAND_TICKS);
            increasePulseTicks = Math.clamp(increasePulseTicks, 1, responseTicks);
            decreasePulseTicks = Math.clamp(decreasePulseTicks, 1, responseTicks);
            stopDecreaseTicks = Math.clamp(stopDecreaseTicks, 1, MAX_RELATIVE_COMMAND_TICKS);
        } else {
            takeoffIncreaseTicks = 0;
            increasePulseTicks = 0;
            decreasePulseTicks = 0;
            stopDecreaseTicks = 0;
        }
    }

    public static LiftCalibrationProfile relative(
            double ascentRate,
            double descentRate,
            int responseTicks,
            long calibratedGameTime,
            int takeoffIncreaseTicks,
            int increasePulseTicks,
            int decreasePulseTicks,
            int stopDecreaseTicks
    ) {
        return new LiftCalibrationProfile(
                8.0D,
                9,
                ascentRate,
                7,
                descentRate,
                responseTicks,
                calibratedGameTime,
                true,
                takeoffIncreaseTicks,
                increasePulseTicks,
                decreasePulseTicks,
                stopDecreaseTicks
        );
    }

    public LiftCalibrationProfile withNeutralStrength(double adjustedNeutralStrength, long gameTime) {
        return new LiftCalibrationProfile(
                adjustedNeutralStrength,
                ascendingStrength,
                ascentRate,
                descendingStrength,
                descentRate,
                responseTicks,
                gameTime,
                relativeControl,
                takeoffIncreaseTicks,
                increasePulseTicks,
                decreasePulseTicks,
                stopDecreaseTicks
        );
    }

    public LiftCalibrationProfile withStopDecreaseTicks(int ticks) {
        return new LiftCalibrationProfile(
                neutralStrength,
                ascendingStrength,
                ascentRate,
                descendingStrength,
                descentRate,
                responseTicks,
                calibratedGameTime,
                relativeControl,
                takeoffIncreaseTicks,
                increasePulseTicks,
                decreasePulseTicks,
                ticks
        );
    }

    public double strengthForVerticalVelocity(double velocity) {
        if (velocity > 0.0D) {
            double fraction = Math.clamp(velocity / ascentRate, 0.0D, 1.0D);
            return neutralStrength + (ascendingStrength - neutralStrength) * fraction;
        }
        if (velocity < 0.0D) {
            double fraction = Math.clamp(-velocity / descentRate, 0.0D, 1.0D);
            return neutralStrength + (descendingStrength - neutralStrength) * fraction;
        }
        return neutralStrength;
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble(NEUTRAL_STRENGTH, neutralStrength);
        tag.putInt(ASCENDING_STRENGTH, ascendingStrength);
        tag.putDouble(ASCENT_RATE, ascentRate);
        tag.putInt(DESCENDING_STRENGTH, descendingStrength);
        tag.putDouble(DESCENT_RATE, descentRate);
        tag.putInt(RESPONSE_TICKS, responseTicks);
        tag.putLong(CALIBRATED_GAME_TIME, calibratedGameTime);
        tag.putBoolean(RELATIVE_CONTROL, relativeControl);
        if (relativeControl) {
            tag.putInt(TAKEOFF_INCREASE_TICKS, takeoffIncreaseTicks);
            tag.putInt(INCREASE_PULSE_TICKS, increasePulseTicks);
            tag.putInt(DECREASE_PULSE_TICKS, decreasePulseTicks);
            tag.putInt(STOP_DECREASE_TICKS, stopDecreaseTicks);
        }
        return tag;
    }

    public static boolean isComplete(CompoundTag tag) {
        return tag.contains(ASCENDING_STRENGTH, Tag.TAG_INT)
                && tag.contains(ASCENT_RATE, Tag.TAG_DOUBLE)
                && tag.contains(DESCENDING_STRENGTH, Tag.TAG_INT)
                && tag.contains(DESCENT_RATE, Tag.TAG_DOUBLE);
    }

    public static LiftCalibrationProfile read(CompoundTag tag) {
        double neutralStrength = tag.getDouble(NEUTRAL_STRENGTH);
        return new LiftCalibrationProfile(
                neutralStrength,
                tag.contains(ASCENDING_STRENGTH, Tag.TAG_INT)
                        ? tag.getInt(ASCENDING_STRENGTH)
                        : Math.min(15, (int) Math.ceil(neutralStrength) + 1),
                tag.contains(ASCENT_RATE, Tag.TAG_DOUBLE) ? tag.getDouble(ASCENT_RATE) : DEFAULT_RATE,
                tag.contains(DESCENDING_STRENGTH, Tag.TAG_INT)
                        ? tag.getInt(DESCENDING_STRENGTH)
                        : Math.max(1, (int) Math.floor(neutralStrength) - 1),
                tag.contains(DESCENT_RATE, Tag.TAG_DOUBLE) ? tag.getDouble(DESCENT_RATE) : DEFAULT_RATE,
                tag.getInt(RESPONSE_TICKS),
                tag.getLong(CALIBRATED_GAME_TIME),
                tag.getBoolean(RELATIVE_CONTROL),
                tag.getInt(TAKEOFF_INCREASE_TICKS),
                tag.getInt(INCREASE_PULSE_TICKS),
                tag.getInt(DECREASE_PULSE_TICKS),
                tag.getInt(STOP_DECREASE_TICKS)
        );
    }
}
