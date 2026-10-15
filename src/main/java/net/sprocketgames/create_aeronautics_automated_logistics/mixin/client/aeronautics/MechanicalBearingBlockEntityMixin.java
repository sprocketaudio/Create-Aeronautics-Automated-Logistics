package net.sprocketgames.create_aeronautics_automated_logistics.mixin.client.aeronautics;

import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.AutomatedShipVisualClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MechanicalBearingBlockEntity.class)
abstract class MechanicalBearingBlockEntityMixin {
    private static final float AAL$VISUAL_DEGREES_PER_TICK = 72.0F;
    private static final float AAL$SPEED_CHANGE_PER_TICK = 6.0F;

    @Shadow
    protected ControlledContraptionEntity movedContraption;

    @Shadow
    protected float angle;

    @Unique
    private float aal$visualAngle;

    @Unique
    private float aal$visualSpeed;

    @Unique
    private boolean aal$visualWasActive;

    @Inject(method = "tick", at = @At("TAIL"))
    private void aal$animateAutomatedAssembly(CallbackInfo ci) {
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        boolean active = AutomatedShipVisualClientState.isAutomatedShipBlockEntity(blockEntity);
        if (active || aal$visualWasActive) {
            if (!aal$visualWasActive) {
                aal$visualAngle = angle;
            }
            aal$visualSpeed = approach(
                    aal$visualSpeed,
                    active ? AAL$VISUAL_DEGREES_PER_TICK : 0.0F,
                    AAL$SPEED_CHANGE_PER_TICK
            );
            aal$visualAngle = (aal$visualAngle + aal$visualSpeed) % 360.0F;
            if (movedContraption != null) {
                movedContraption.setAngle(aal$visualAngle);
            }
        } else if (movedContraption != null) {
            movedContraption.setAngle(angle);
        }
        aal$visualWasActive |= active;
    }

    @Inject(method = "getInterpolatedAngle", at = @At("HEAD"), cancellable = true)
    private void aal$useAutomatedVisualAngle(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (AutomatedShipVisualClientState.isAutomatedShipBlockEntity((BlockEntity) (Object) this) || aal$visualWasActive) {
            cir.setReturnValue((aal$visualAngle + aal$visualSpeed * partialTicks) % 360.0F);
        }
    }

    @Unique
    private static float approach(float value, float target, float change) {
        return value < target ? Math.min(value + change, target) : Math.max(value - change, target);
    }
}
