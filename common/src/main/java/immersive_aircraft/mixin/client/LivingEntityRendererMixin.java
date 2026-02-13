package immersive_aircraft.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import immersive_aircraft.client.PassengerLivingEntityRenderState;
import immersive_aircraft.entity.VehicleEntity;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"),
            require = 0
    )
    private void ia$extractVehicleTilt(LivingEntity entity, LivingEntityRenderState renderState, float tickDelta, CallbackInfo ci) {
        PassengerLivingEntityRenderState passengerState = (PassengerLivingEntityRenderState) renderState;
        if (entity.getRootVehicle() != entity && entity.getRootVehicle() instanceof VehicleEntity vehicle) {
            passengerState.ia$setVehiclePitch(-vehicle.getViewXRot(tickDelta));
            passengerState.ia$setVehicleRoll(-vehicle.getRoll(tickDelta));
            return;
        }
        passengerState.ia$setVehiclePitch(0.0f);
        passengerState.ia$setVehicleRoll(0.0f);
    }

    @Inject(
            method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("TAIL"),
            require = 0
    )
    private void ia$applyVehicleTiltV1(LivingEntityRenderState renderState, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
        ia$applyVehicleTilt(renderState, poseStack);
    }

    private static void ia$applyVehicleTilt(LivingEntityRenderState renderState, PoseStack poseStack) {
        PassengerLivingEntityRenderState passengerState = (PassengerLivingEntityRenderState) renderState;
        float pitch = passengerState.ia$getVehiclePitch();
        float roll = passengerState.ia$getVehicleRoll();
        if (pitch != 0.0f || roll != 0.0f) {
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
            poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
        }
    }
}
