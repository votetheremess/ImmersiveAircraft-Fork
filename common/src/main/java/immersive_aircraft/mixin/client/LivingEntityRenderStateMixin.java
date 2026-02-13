package immersive_aircraft.mixin.client;

import immersive_aircraft.client.PassengerLivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements PassengerLivingEntityRenderState {
    @Unique
    private float ia$vehiclePitch;

    @Unique
    private float ia$vehicleRoll;

    @Override
    public float ia$getVehiclePitch() {
        return ia$vehiclePitch;
    }

    @Override
    public void ia$setVehiclePitch(float pitch) {
        ia$vehiclePitch = pitch;
    }

    @Override
    public float ia$getVehicleRoll() {
        return ia$vehicleRoll;
    }

    @Override
    public void ia$setVehicleRoll(float roll) {
        ia$vehicleRoll = roll;
    }
}
