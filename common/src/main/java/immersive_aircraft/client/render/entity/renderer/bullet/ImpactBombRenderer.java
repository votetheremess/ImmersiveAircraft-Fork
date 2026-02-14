package immersive_aircraft.client.render.entity.renderer.bullet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import immersive_aircraft.entity.bullet.ImpactBombEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.TntMinecartRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

public class ImpactBombRenderer extends EntityRenderer<ImpactBombEntity, ImpactBombRenderState> {
    public ImpactBombRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.3f;
    }

    @Override
    public @NotNull ImpactBombRenderState createRenderState() {
        return new ImpactBombRenderState();
    }

    @Override
    public void submit(ImpactBombRenderState entity, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        poseStack.pushPose();
        poseStack.translate(0.0, 0.5, 0.0);
        poseStack.scale(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0f));
        poseStack.translate(-0.5, -0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
        TntMinecartRenderer.submitWhiteSolidBlock(
                Blocks.TNT.defaultBlockState(),
                poseStack,
                submitNodeCollector,
                entity.packedLight,
                false,
                ARGB.color(0, 255, 255, 255));
        poseStack.popPose();
        super.submit(entity, poseStack, submitNodeCollector, cameraRenderState);
    }

    @Override
    public void extractRenderState(ImpactBombEntity entity, ImpactBombRenderState entityRenderState, float partialTicks) {
        super.extractRenderState(entity, entityRenderState, partialTicks);
        entityRenderState.packedLight = this.getPackedLightCoords(entity, partialTicks);
    }
}
