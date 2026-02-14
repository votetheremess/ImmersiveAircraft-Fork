package immersive_aircraft.client.render.entity.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import immersive_aircraft.Main;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Vector3f;

public final class BombImpactRingRenderer {
    private static final Identifier TEXTURE = Main.locate("textures/entity/trail.png");
    private static final int SEGMENTS = 48;
    private static final float RING_RADIUS = 1.35f;
    private static final float RING_HALF_WIDTH = 0.09f;
    private static final float RING_HEIGHT_OFFSET = 0.03f;
    private static final int LIGHT = 15728640;

    private BombImpactRingRenderer() {
    }

    public static void render(@Nullable Vec3 impactPoint,
                              SubmitNodeCollector submitNodeCollector,
                              PoseStack poseStack,
                              CameraRenderState cameraRenderState) {
        if (impactPoint == null) {
            return;
        }

        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(TEXTURE, true), (pose, vertexConsumer) -> {
            Matrix3f matrix = pose.normal();
            Vec3 cameraPos = cameraRenderState.pos;
            double ringY = impactPoint.y + RING_HEIGHT_OFFSET;

            for (int i = 0; i < SEGMENTS; i++) {
                double a0 = (Math.PI * 2.0 * i) / SEGMENTS;
                double a1 = (Math.PI * 2.0 * (i + 1)) / SEGMENTS;

                Vec3 outer0 = ringPoint(impactPoint.x, ringY, impactPoint.z, a0, RING_RADIUS + RING_HALF_WIDTH);
                Vec3 inner0 = ringPoint(impactPoint.x, ringY, impactPoint.z, a0, RING_RADIUS - RING_HALF_WIDTH);
                Vec3 inner1 = ringPoint(impactPoint.x, ringY, impactPoint.z, a1, RING_RADIUS - RING_HALF_WIDTH);
                Vec3 outer1 = ringPoint(impactPoint.x, ringY, impactPoint.z, a1, RING_RADIUS + RING_HALF_WIDTH);

                vertex(vertexConsumer, matrix, outer0, cameraPos, 1.0f, 0.95f, 0.2f, 0.7f, 0.0f, 0.0f);
                vertex(vertexConsumer, matrix, inner0, cameraPos, 1.0f, 0.95f, 0.2f, 0.7f, 0.0f, 1.0f);
                vertex(vertexConsumer, matrix, inner1, cameraPos, 1.0f, 0.95f, 0.2f, 0.7f, 1.0f, 1.0f);
                vertex(vertexConsumer, matrix, outer1, cameraPos, 1.0f, 0.95f, 0.2f, 0.7f, 1.0f, 0.0f);

                // Render the reverse winding as well to avoid culling artifacts.
                vertex(vertexConsumer, matrix, outer1, cameraPos, 1.0f, 0.95f, 0.2f, 0.7f, 1.0f, 0.0f);
                vertex(vertexConsumer, matrix, inner1, cameraPos, 1.0f, 0.95f, 0.2f, 0.7f, 1.0f, 1.0f);
                vertex(vertexConsumer, matrix, inner0, cameraPos, 1.0f, 0.95f, 0.2f, 0.7f, 0.0f, 1.0f);
                vertex(vertexConsumer, matrix, outer0, cameraPos, 1.0f, 0.95f, 0.2f, 0.7f, 0.0f, 0.0f);
            }
        });
    }

    private static Vec3 ringPoint(double centerX, double centerY, double centerZ, double angle, double radius) {
        return new Vec3(
                centerX + Math.cos(angle) * radius,
                centerY,
                centerZ + Math.sin(angle) * radius
        );
    }

    private static void vertex(VertexConsumer vertexConsumer,
                               Matrix3f matrix,
                               Vec3 worldPosition,
                               Vec3 cameraPosition,
                               float r,
                               float g,
                               float b,
                               float a,
                               float u,
                               float v) {
        Vector3f p = new Vector3f(
                (float) (worldPosition.x - cameraPosition.x),
                (float) (worldPosition.y - cameraPosition.y),
                (float) (worldPosition.z - cameraPosition.z)
        );
        matrix.transform(p);
        vertexConsumer.addVertex(p.x, p.y, p.z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LIGHT)
                .setNormal(0.0f, 1.0f, 0.0f);
    }
}
