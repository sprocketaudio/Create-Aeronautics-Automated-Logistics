package net.sprocketgames.create_aeronautics_automated_logistics.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlockEntities;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModBlocks;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.LiftCalibrationProfile;
import net.sprocketgames.create_aeronautics_automated_logistics.drive.LiftCalibrationState;
import net.sprocketgames.create_aeronautics_automated_logistics.service.LiftCalibrationService;

import java.util.Optional;
import java.util.OptionalDouble;

public class AdvancedTransponderBlockEntity extends ShipTransponderBlockEntity {
    private static final String LIFT_CALIBRATION = "liftCalibration";
    private static final String LIFT_CALIBRATION_STATE = "liftCalibrationState";
    private static final String LIFT_CALIBRATION_PROGRESS = "liftCalibrationProgress";
    private static final String LIFT_CALIBRATION_DETAIL = "liftCalibrationDetail";
    private static final String RELATIVE_LIFT_ROTOR_AIRFLOW = "relativeLiftRotorAirflow";
    private static final String RELATIVE_LIFT_ROTOR_COUNT = "relativeLiftRotorCount";

    private Optional<LiftCalibrationProfile> liftCalibration = Optional.empty();
    private LiftCalibrationState liftCalibrationState = LiftCalibrationState.UNCALIBRATED;
    private int liftCalibrationProgress;
    private String liftCalibrationDetail = "";
    private double relativeLiftRotorAirflow = Double.NaN;
    private int relativeLiftRotorCount;

    public AdvancedTransponderBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ADVANCED_TRANSPONDER.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AdvancedTransponderBlockEntity transponder) {
        ShipTransponderBlockEntity.serverTick(level, pos, state, transponder);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            LiftCalibrationService.tick(serverLevel, transponder);
        }
    }

    public Optional<LiftCalibrationProfile> liftCalibration() {
        return liftCalibration;
    }

    public LiftCalibrationState liftCalibrationState() {
        return liftCalibrationState;
    }

    public int liftCalibrationProgress() {
        return liftCalibrationProgress;
    }

    public String liftCalibrationDetail() {
        return liftCalibrationDetail;
    }

    public void setLiftCalibration(LiftCalibrationProfile profile) {
        liftCalibration = Optional.of(profile);
        relativeLiftRotorAirflow = Double.NaN;
        relativeLiftRotorCount = 0;
        updateLiftCalibrationRuntime(LiftCalibrationState.CALIBRATED, 100, "");
        setChanged();
    }

    public OptionalDouble relativeLiftRotorAirflow() {
        return Double.isFinite(relativeLiftRotorAirflow) && relativeLiftRotorAirflow > 0.0D
                ? OptionalDouble.of(relativeLiftRotorAirflow)
                : OptionalDouble.empty();
    }

    public int relativeLiftRotorCount() {
        return relativeLiftRotorCount;
    }

    public void updateRelativeLiftRotorSnapshot(int rotorCount, double averageAirflow) {
        if (rotorCount <= 0 || !Double.isFinite(averageAirflow) || averageAirflow <= 0.0D) {
            return;
        }
        double changeThreshold = Double.isFinite(relativeLiftRotorAirflow)
                ? Math.max(0.005D, relativeLiftRotorAirflow * 0.01D)
                : 0.0D;
        if (relativeLiftRotorCount == rotorCount
                && Double.isFinite(relativeLiftRotorAirflow)
                && Math.abs(relativeLiftRotorAirflow - averageAirflow) < changeThreshold) {
            return;
        }
        relativeLiftRotorCount = rotorCount;
        relativeLiftRotorAirflow = averageAirflow;
        setChanged();
    }

    public void updateLearnedLiftNeutral(double neutralStrength, long gameTime) {
        liftCalibration.ifPresent(profile -> {
            double adjusted = Math.clamp(neutralStrength, 1.0D, 15.0D);
            if (Math.abs(adjusted - profile.neutralStrength()) < 0.005D) {
                return;
            }
            liftCalibration = Optional.of(profile.withNeutralStrength(adjusted, gameTime));
            setChanged();
        });
    }

    public void updateLiftCalibrationRuntime(LiftCalibrationState state, int progress, String detail) {
        LiftCalibrationState normalizedState = state == null ? LiftCalibrationState.UNCALIBRATED : state;
        int normalizedProgress = Math.clamp(progress, 0, 100);
        String normalizedDetail = detail == null ? "" : detail;
        if (liftCalibrationState == normalizedState
                && liftCalibrationProgress == normalizedProgress
                && liftCalibrationDetail.equals(normalizedDetail)) {
            return;
        }
        liftCalibrationState = normalizedState;
        liftCalibrationProgress = normalizedProgress;
        liftCalibrationDetail = normalizedDetail;
        setChanged();
        syncClientState();
    }

    @Override
    protected Block controllerBlock() {
        return ModBlocks.ADVANCED_TRANSPONDER.get();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.create_aeronautics_automated_logistics.advanced_transponder.title");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeLiftCalibration(tag, false);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        CompoundTag calibrationTag = tag.getCompound(LIFT_CALIBRATION);
        liftCalibration = tag.contains(LIFT_CALIBRATION, Tag.TAG_COMPOUND)
                && LiftCalibrationProfile.isComplete(calibrationTag)
                ? Optional.of(LiftCalibrationProfile.read(calibrationTag))
                : Optional.empty();
        liftCalibrationState = readCalibrationState(tag);
        liftCalibrationProgress = tag.getInt(LIFT_CALIBRATION_PROGRESS);
        liftCalibrationDetail = tag.getString(LIFT_CALIBRATION_DETAIL);
        relativeLiftRotorAirflow = tag.contains(RELATIVE_LIFT_ROTOR_AIRFLOW, Tag.TAG_DOUBLE)
                ? tag.getDouble(RELATIVE_LIFT_ROTOR_AIRFLOW)
                : Double.NaN;
        relativeLiftRotorCount = Math.max(0, tag.getInt(RELATIVE_LIFT_ROTOR_COUNT));
        if (liftCalibration.isEmpty() && liftCalibrationState == LiftCalibrationState.CALIBRATED) {
            liftCalibrationState = LiftCalibrationState.UNCALIBRATED;
            liftCalibrationProgress = 0;
            liftCalibrationDetail = "";
        }
        if (liftCalibrationState == LiftCalibrationState.CALIBRATING && level == null) {
            liftCalibrationState = liftCalibration.isPresent()
                    ? LiftCalibrationState.CALIBRATED
                    : LiftCalibrationState.UNCALIBRATED;
            liftCalibrationProgress = liftCalibration.isPresent() ? 100 : 0;
            liftCalibrationDetail = "";
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeLiftCalibration(tag, true);
        return tag;
    }

    private void writeLiftCalibration(CompoundTag tag, boolean includeRuntime) {
        liftCalibration.ifPresent(profile -> tag.put(LIFT_CALIBRATION, profile.write()));
        if (Double.isFinite(relativeLiftRotorAirflow) && relativeLiftRotorAirflow > 0.0D) {
            tag.putDouble(RELATIVE_LIFT_ROTOR_AIRFLOW, relativeLiftRotorAirflow);
            tag.putInt(RELATIVE_LIFT_ROTOR_COUNT, relativeLiftRotorCount);
        }
        if (includeRuntime) {
            tag.putString(LIFT_CALIBRATION_STATE, liftCalibrationState.name());
            tag.putInt(LIFT_CALIBRATION_PROGRESS, liftCalibrationProgress);
            tag.putString(LIFT_CALIBRATION_DETAIL, liftCalibrationDetail);
        }
    }

    private LiftCalibrationState readCalibrationState(CompoundTag tag) {
        if (!tag.contains(LIFT_CALIBRATION_STATE, Tag.TAG_STRING)) {
            return liftCalibration.isPresent() ? LiftCalibrationState.CALIBRATED : LiftCalibrationState.UNCALIBRATED;
        }
        try {
            return LiftCalibrationState.valueOf(tag.getString(LIFT_CALIBRATION_STATE));
        } catch (IllegalArgumentException ignored) {
            return liftCalibration.isPresent() ? LiftCalibrationState.CALIBRATED : LiftCalibrationState.UNCALIBRATED;
        }
    }

}
