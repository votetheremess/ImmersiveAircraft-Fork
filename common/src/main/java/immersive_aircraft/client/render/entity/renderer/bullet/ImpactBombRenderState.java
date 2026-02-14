package immersive_aircraft.client.render.entity.renderer.bullet;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

@Environment(EnvType.CLIENT)
public class ImpactBombRenderState extends EntityRenderState {
    public int packedLight;
}
