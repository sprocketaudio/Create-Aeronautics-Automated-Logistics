package net.sprocketgames.create_aeronautics_automated_logistics.mixin.client.simurail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

@Pseudo
@Mixin(targets = "com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity")
abstract class PhysicsBogeyBlockEntityMixin {
    private static final double VISUAL_SPEED_EPSILON = 0.01D;
    private static final double MOVEMENT_SPEED_OVERRIDE_THRESHOLD = 0.05D;
    private static final double VISUAL_SPEED_FALLBACK_RATIO = 0.5D;
    private static final double CARRIAGE_SPEED_EPSILON = 0.02D;
    private static final double TICKS_PER_SECOND = 20.0D;

    private static volatile boolean reflectionInitialized;
    private static Field visualSpeedField;
    private static Field movementSpeedField;
    private static Field distanceMovedField;
    private static Field optionsField;
    private static Field typeField;
    private static Method wheelRadiusMethod;
    private static Method directionMethod;

    @Unique
    private double aal$fallbackDistanceMoved;
    @Unique
    private double aal$fallbackSignedSpeed;
    @Unique
    private final Vector3d aal$lastCarriagePosition = new Vector3d();
    @Unique
    private boolean aal$hasLastCarriagePosition;

    @Inject(method = "getWheelAngle", at = @At("HEAD"), cancellable = true, remap = false)
    private void aal$useMovementSpeedWhenVisualSpeedIsBrakeSuppressed(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!initializeReflection()) {
            return;
        }
        try {
            Object self = this;
            double visualSpeed = visualSpeedField.getDouble(self);
            float movementSpeed = movementSpeedField.getFloat(self);
            if (!aal$shouldUseFallback(visualSpeed, movementSpeed, aal$fallbackSignedSpeed)) {
                return;
            }
            Object options = optionsField.get(self);
            if (options == null) {
                return;
            }
            Object type = typeField.get(options);
            if (type == null) {
                return;
            }
            double wheelRadius = ((Number) wheelRadiusMethod.invoke(type)).doubleValue();
            if (wheelRadius == 0.0D) {
                return;
            }
            double distance = Math.fma(aal$fallbackSignedSpeed * 0.05D, partialTick, aal$fallbackDistanceMoved);
            double angle = distance / wheelRadius;
            cir.setReturnValue((float) Math.toDegrees(angle) % 360.0F);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void aal$accumulateFallbackWheelDistance(CallbackInfo ci) {
        if (!initializeReflection()) {
            return;
        }
        try {
            Object self = this;
            double visualSpeed = visualSpeedField.getDouble(self);
            float movementSpeed = movementSpeedField.getFloat(self);
            aal$updateFallbackSignedSpeed(self, movementSpeed);
            if (!aal$shouldUseFallback(visualSpeed, movementSpeed, aal$fallbackSignedSpeed)) {
                aal$fallbackDistanceMoved = distanceMovedField.getDouble(self);
                return;
            }
            if (Math.abs(aal$fallbackSignedSpeed) <= CARRIAGE_SPEED_EPSILON) {
                return;
            }
            aal$fallbackDistanceMoved = Math.fma(aal$fallbackSignedSpeed, 0.05D, aal$fallbackDistanceMoved);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Unique
    private void aal$updateFallbackSignedSpeed(Object self, float movementSpeed) throws ReflectiveOperationException {
        double fallbackSpeed = 0.0D;
        boolean resolvedFromCarriageMotion = false;
        if (self instanceof BlockEntity blockEntity) {
            if (blockEntity.getLevel() != null && blockEntity.getLevel().isClientSide) {
                ClientSubLevel subLevel = Sable.HELPER.getContainingClient(blockEntity);
                if (subLevel != null) {
                    Pose3dc pose = subLevel.logicalPose();
                    Vector3dc currentPosition = pose.position();
                    if (aal$hasLastCarriagePosition) {
                        Vector3d delta = new Vector3d(currentPosition).sub(aal$lastCarriagePosition);
                        Object directionResult = directionMethod.invoke(self);
                        if (directionResult instanceof Vector3dc localDirection) {
                            Vector3d worldForward = pose.transformNormal(localDirection, new Vector3d()).normalize();
                            double signedDistance = delta.dot(worldForward);
                            fallbackSpeed = signedDistance * TICKS_PER_SECOND;
                            resolvedFromCarriageMotion = true;
                        }
                    }
                    aal$lastCarriagePosition.set(currentPosition);
                    aal$hasLastCarriagePosition = true;
                } else {
                    aal$hasLastCarriagePosition = false;
                    fallbackSpeed = movementSpeed;
                }
            } else {
                aal$hasLastCarriagePosition = false;
                fallbackSpeed = movementSpeed;
            }
        }

        if (!resolvedFromCarriageMotion && !aal$hasLastCarriagePosition) {
            fallbackSpeed = movementSpeed;
        }

        if (Math.abs(fallbackSpeed) <= CARRIAGE_SPEED_EPSILON) {
            aal$fallbackSignedSpeed = 0.0D;
        } else {
            aal$fallbackSignedSpeed = fallbackSpeed;
        }
    }

    @Unique
    private static boolean aal$shouldUseFallback(double visualSpeed, float movementSpeed, double fallbackSignedSpeed) {
        double movementMagnitude = Math.max(Math.abs(movementSpeed), Math.abs(fallbackSignedSpeed));
        if (movementMagnitude <= MOVEMENT_SPEED_OVERRIDE_THRESHOLD) {
            return false;
        }
        double visualMagnitude = Math.abs(visualSpeed);
        return visualMagnitude <= VISUAL_SPEED_EPSILON
                || visualMagnitude < movementMagnitude * VISUAL_SPEED_FALLBACK_RATIO;
    }

    private static boolean initializeReflection() {
        if (reflectionInitialized) {
            return visualSpeedField != null
                    && movementSpeedField != null
                    && distanceMovedField != null
                    && optionsField != null
                    && typeField != null
                    && wheelRadiusMethod != null
                    && directionMethod != null;
        }
        synchronized (PhysicsBogeyBlockEntityMixin.class) {
            if (reflectionInitialized) {
                return visualSpeedField != null
                        && movementSpeedField != null
                        && distanceMovedField != null
                        && optionsField != null
                        && typeField != null
                        && wheelRadiusMethod != null
                        && directionMethod != null;
            }
            try {
                Class<?> bogeyClass = Class.forName("com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity");
                visualSpeedField = accessibleField(bogeyClass, "visualSpeed");
                movementSpeedField = accessibleField(bogeyClass, "movementSpeed");
                distanceMovedField = accessibleField(bogeyClass, "distanceMoved");
                optionsField = accessibleField(bogeyClass, "options");
                Class<?> optionsClass = Class.forName("com.crystaelix.simurail.content.bogey.PhysicsBogeyOptions");
                typeField = accessibleField(optionsClass, "type");
                Class<?> renderedTypeClass = Class.forName("com.crystaelix.simurail.api.bogey.BogeyRenderedType");
                wheelRadiusMethod = renderedTypeClass.getMethod("wheelRadius");
                directionMethod = bogeyClass.getMethod("getDirection");
            } catch (ReflectiveOperationException ignored) {
                visualSpeedField = null;
                movementSpeedField = null;
                distanceMovedField = null;
                optionsField = null;
                typeField = null;
                wheelRadiusMethod = null;
                directionMethod = null;
            }
            reflectionInitialized = true;
            return visualSpeedField != null
                    && movementSpeedField != null
                    && distanceMovedField != null
                    && optionsField != null
                    && typeField != null
                    && wheelRadiusMethod != null
                    && directionMethod != null;
        }
    }

    private static Field accessibleField(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
