package immersive_aircraft.client.util;

import immersive_aircraft.entity.bullet.ImpactBombEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class BombTrajectoryPredictor {
    private static final int DEFAULT_MAX_SIMULATION_TICKS = 320;

    private BombTrajectoryPredictor() {
    }

    @Nullable
    public static Vec3 predictImpact(Level level, Entity contextEntity, Vec3 startPosition, Vec3 startVelocity) {
        return predictImpact(level, contextEntity, startPosition, startVelocity, DEFAULT_MAX_SIMULATION_TICKS, ImpactBombEntity.getArmingTicks());
    }

    @Nullable
    public static Vec3 predictImpact(Level level,
                                     Entity contextEntity,
                                     Vec3 startPosition,
                                     Vec3 startVelocity,
                                     int maxTicks,
                                     int armingTicks) {
        Vec3 position = startPosition;
        Vec3 velocity = startVelocity;
        double gravity = ImpactBombEntity.getGravityAcceleration();
        double horizontalDrag = ImpactBombEntity.getHorizontalDrag();
        double verticalDrag = ImpactBombEntity.getVerticalDrag();

        for (int tick = 0; tick < maxTicks; tick++) {
            Vec3 velocityAfterGravity = velocity.add(0.0, -gravity, 0.0);
            Vec3 nextPosition = position.add(velocityAfterGravity);

            BlockHitResult blockHitResult = level.clip(new ClipContext(
                    position,
                    nextPosition,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    contextEntity
            ));
            boolean armed = (tick + 1) > armingTicks;
            if (armed && blockHitResult.getType() == HitResult.Type.BLOCK) {
                return blockHitResult.getLocation();
            }

            position = nextPosition;
            velocity = velocityAfterGravity.multiply(horizontalDrag, verticalDrag, horizontalDrag);
            if (position.y < level.getMinY() - 4.0) {
                break;
            }
        }

        return null;
    }
}
