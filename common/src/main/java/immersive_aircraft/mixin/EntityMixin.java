package immersive_aircraft.mixin;

import immersive_aircraft.entity.VehicleEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow
    private float xRot;

    @Inject(method = "setXRot", at = @At("HEAD"), cancellable = true)
    private void immersive_aircraft$allowVehicleLoops(float pitch, CallbackInfo ci) {
        if ((Object) this instanceof VehicleEntity) {
            this.xRot = Mth.wrapDegrees(pitch);
            ci.cancel();
        }
    }
}
