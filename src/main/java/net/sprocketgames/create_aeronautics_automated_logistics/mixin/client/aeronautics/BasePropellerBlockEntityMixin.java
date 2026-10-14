package net.sprocketgames.create_aeronautics_automated_logistics.mixin.client.aeronautics;

import dev.eriksonn.aeronautics.content.blocks.propeller.small.BasePropellerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.AutomatedShipVisualClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BasePropellerBlockEntity.class)
abstract class BasePropellerBlockEntityMixin {
    private static final float AAL$VISUAL_SPEED = 16.0F;
    private static final float AAL$SPEED_CHANGE_PER_TICK = 2.0F;

    @Shadow public float rotationSpeed;
    @Shadow public abstract float getPreviousAngle();
    @Shadow public abstract void setPreviousAngle(float previousAngle);
    @Shadow public abstract float getAngle();
    @Shadow public abstract void setAngle(float angle);

    @Unique
    private float aal$visualSpeed;

    @Unique
    private boolean aal$visualWasAutomated;

    @Inject(method = "tick", at = @At("TAIL"))
    private void aal$spinForAutomatedPlayback(CallbackInfo ci) {
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        boolean automated = AutomatedShipVisualClientState.isAutomatedShipBlockEntity(blockEntity);
        if (!automated && !aal$visualWasAutomated) {
            return;
        }

        float targetSpeed = automated ? AAL$VISUAL_SPEED : 0.0F;
        aal$visualSpeed = approach(aal$visualSpeed, targetSpeed, AAL$SPEED_CHANGE_PER_TICK);
        float currentAngle = getAngle();
        setPreviousAngle(currentAngle);
        rotationSpeed = aal$visualSpeed;
        setAngle(currentAngle + aal$visualSpeed);
        aal$visualWasAutomated |= automated;
    }

    @Unique
    private static float approach(float value, float target, float change) {
        return value < target ? Math.min(value + change, target) : Math.max(value - change, target);
    }

    @Inject(method = "onActiveTick", at = @At("HEAD"), cancellable = true)
    private void aal$cancelFakeWindAndPush(CallbackInfo ci) {
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        if (AutomatedShipVisualClientState.isAutomatedShipBlockEntity(blockEntity)) {
            ci.cancel();
        }
    }
}
