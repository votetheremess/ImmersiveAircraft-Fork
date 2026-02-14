package immersive_aircraft.entity.bullet;

import immersive_aircraft.Main;
import immersive_aircraft.config.Config;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.AtomicInteger;

public class ImpactBombEntity extends PrimedTnt {
    private static final float EXPLOSION_RADIUS = 8.0f;
    private static final int ARMING_TICKS = 8;
    private static final double GRAVITY_ACCELERATION = 0.04;
    private static final double HORIZONTAL_DRAG = 0.9975;
    private static final double VERTICAL_DRAG = 0.9965;
    private static final int GUIDED_MIN_TICKS = 14;
    private static final int GUIDED_MAX_TICKS = 140;
    private static final double GUIDED_MIN_HORIZONTAL_SPEED = 0.18;
    private static final double GUIDED_END_DOWN_SPEED = 0.35;
    private static final AtomicInteger NEXT_DEBUG_ID = new AtomicInteger();
    private static final String DEBUG_TAG = "[IA-BOMB-DEBUG]";
    private static final boolean DEBUG_LOGGING = true;
    private int tickAge;
    private int sourceVehicleId = -1;
    private int debugId = -1;
    private boolean guidedStrike;
    private int guidedFlightTicks;
    private int guidedTicksElapsed;
    private Vec3 guidedStartPos = Vec3.ZERO;
    private Vec3 guidedTargetPos = Vec3.ZERO;
    private Vec3 guidedStartTangent = Vec3.ZERO;
    private Vec3 guidedEndTangent = Vec3.ZERO;

    public ImpactBombEntity(EntityType<? extends PrimedTnt> entityType, Level level) {
        super(entityType, level);
    }

    public void setSourceVehicleId(int sourceVehicleId) {
        this.sourceVehicleId = sourceVehicleId;
    }

    public int assignDebugId() {
        if (debugId < 0) {
            debugId = NEXT_DEBUG_ID.incrementAndGet();
        }
        return debugId;
    }

    public int getDebugId() {
        return assignDebugId();
    }

    public static int getArmingTicks() {
        return ARMING_TICKS;
    }

    public static double getGravityAcceleration() {
        return GRAVITY_ACCELERATION;
    }

    public static double getHorizontalDrag() {
        return HORIZONTAL_DRAG;
    }

    public static double getVerticalDrag() {
        return VERTICAL_DRAG;
    }

    public void configureGuidedStrike(Vec3 targetPoint, Vec3 initialVelocity) {
        if (!isFinite(targetPoint)) {
            return;
        }

        Vec3 startPos = position();
        Vec3 toTarget = targetPoint.subtract(startPos);
        double horizontalDistance = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        double horizontalSpeed = Math.sqrt(initialVelocity.x * initialVelocity.x + initialVelocity.z * initialVelocity.z);
        int flightTicks = (int) Math.ceil(horizontalDistance / Math.max(GUIDED_MIN_HORIZONTAL_SPEED, horizontalSpeed));
        flightTicks = Mth.clamp(flightTicks, GUIDED_MIN_TICKS, GUIDED_MAX_TICKS);

        Vec3 launchVelocity = initialVelocity.lengthSqr() > 1.0E-6
                ? initialVelocity
                : (toTarget.lengthSqr() > 1.0E-6 ? toTarget.normalize().scale(0.35) : Vec3.ZERO);
        Vec3 horizontalDirection = new Vec3(toTarget.x, 0.0, toTarget.z);
        if (horizontalDirection.lengthSqr() > 1.0E-6) {
            horizontalDirection = horizontalDirection.normalize();
        } else {
            horizontalDirection = Vec3.ZERO;
        }

        double endDown = Math.max(GUIDED_END_DOWN_SPEED, Math.abs(launchVelocity.y) + 0.25);
        this.guidedStrike = true;
        this.guidedFlightTicks = Math.max(1, flightTicks);
        this.guidedTicksElapsed = 0;
        this.guidedStartPos = startPos;
        this.guidedTargetPos = targetPoint;
        this.guidedStartTangent = launchVelocity.scale(this.guidedFlightTicks);
        this.guidedEndTangent = horizontalDirection.scale(0.12 * this.guidedFlightTicks)
                .add(0.0, -endDown * this.guidedFlightTicks, 0.0);

        if (DEBUG_LOGGING && !this.level().isClientSide()) {
            Main.LOGGER.info(
                    "{} bomb={} GUIDED_CONFIG start={} target={} initial_vel={} flight_ticks={}",
                    DEBUG_TAG,
                    getDebugId(),
                    formatVec(startPos),
                    formatVec(targetPoint),
                    formatVec(initialVelocity),
                    this.guidedFlightTicks
            );
        }
    }

    @Override
    public void tick() {
        if (guidedStrike) {
            tickGuidedStrike();
            return;
        }

        tickAge++;
        int bombId = assignDebugId();
        Vec3 positionBefore = position();
        Vec3 velocityBefore = this.getDeltaMovement();
        Vec3 velocityAfterGravity = velocityBefore;

        if (!this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, -GRAVITY_ACCELERATION, 0.0));
            velocityAfterGravity = this.getDeltaMovement();
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().multiply(HORIZONTAL_DRAG, VERTICAL_DRAG, HORIZONTAL_DRAG));
        Vec3 velocityAfterDrag = this.getDeltaMovement();
        Vec3 positionAfterMove = position();

        boolean armed = tickAge > ARMING_TICKS;
        boolean blockContact = hasBlockContact();
        boolean entityContact = armed && hasEntityContact();
        boolean shouldDetonate = armed && (blockContact || entityContact);

        if (DEBUG_LOGGING && !this.level().isClientSide()) {
            Main.LOGGER.info(
                    "{} bomb={} tick={} pos_before={} pos_after={} vel_before={} vel_after_gravity={} vel_after_drag={} onGround={} hCol={} vCol={} armed={} blockContact={} entityContact={} shouldDetonate={}",
                    DEBUG_TAG,
                    bombId,
                    tickAge,
                    formatVec(positionBefore),
                    formatVec(positionAfterMove),
                    formatVec(velocityBefore),
                    formatVec(velocityAfterGravity),
                    formatVec(velocityAfterDrag),
                    this.onGround(),
                    this.horizontalCollision,
                    this.verticalCollision,
                    armed,
                    blockContact,
                    entityContact,
                    shouldDetonate
            );
        }

        if (!this.level().isClientSide() && shouldDetonate) {
            explode(blockContact ? "block_contact" : "entity_contact");
            return;
        }

        this.updateInWaterStateAndDoFluidPushing();
        if (this.level().isClientSide()) {
            this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.5, this.getZ(), 0.0, 0.0, 0.0);
        }
    }

    private void tickGuidedStrike() {
        tickAge++;
        int bombId = assignDebugId();
        Vec3 positionBefore = position();
        Vec3 velocityBefore = this.getDeltaMovement();

        if (guidedFlightTicks <= 0) {
            guidedFlightTicks = 1;
        }

        int nextStep = Math.min(guidedTicksElapsed + 1, guidedFlightTicks);
        double t0 = (double) guidedTicksElapsed / guidedFlightTicks;
        double t1 = (double) nextStep / guidedFlightTicks;
        Vec3 prev = evaluateGuidedPosition(t0);
        Vec3 next = evaluateGuidedPosition(t1);
        Vec3 guidedVelocity = next.subtract(prev);
        setPos(next.x, next.y, next.z);
        setDeltaMovement(guidedVelocity);
        guidedTicksElapsed = nextStep;

        boolean shouldDetonate = guidedTicksElapsed >= guidedFlightTicks;

        if (DEBUG_LOGGING && !this.level().isClientSide()) {
            Main.LOGGER.info(
                    "{} bomb={} tick={} mode=guided pos_before={} pos_after={} vel_before={} vel_after={} progress={}/{} shouldDetonate={}",
                    DEBUG_TAG,
                    bombId,
                    tickAge,
                    formatVec(positionBefore),
                    formatVec(next),
                    formatVec(velocityBefore),
                    formatVec(guidedVelocity),
                    guidedTicksElapsed,
                    guidedFlightTicks,
                    shouldDetonate
            );
        }

        if (!this.level().isClientSide() && shouldDetonate) {
            setPos(guidedTargetPos.x, guidedTargetPos.y, guidedTargetPos.z);
            explode("guided_target");
            return;
        }

        this.updateInWaterStateAndDoFluidPushing();
        if (this.level().isClientSide()) {
            this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.5, this.getZ(), 0.0, 0.0, 0.0);
        }
    }

    private boolean hasBlockContact() {
        return this.onGround() || this.horizontalCollision || this.verticalCollision;
    }

    private boolean hasEntityContact() {
        return !this.level().getEntities(this, this.getBoundingBox().inflate(0.08), this::canDetonateOnTouch).isEmpty();
    }

    private boolean canDetonateOnTouch(Entity target) {
        if (target == this || !target.isAlive() || !target.isPickable()) {
            return false;
        }
        if (sourceVehicleId < 0) {
            return true;
        }
        if (target.getId() == sourceVehicleId) {
            return false;
        }
        Entity targetRoot = target.getRootVehicle();
        return targetRoot == null || targetRoot.getId() != sourceVehicleId;
    }

    private Vec3 evaluateGuidedPosition(double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        double h00 = 2.0 * t3 - 3.0 * t2 + 1.0;
        double h10 = t3 - 2.0 * t2 + t;
        double h01 = -2.0 * t3 + 3.0 * t2;
        double h11 = t3 - t2;

        return guidedStartPos.scale(h00)
                .add(guidedStartTangent.scale(h10))
                .add(guidedTargetPos.scale(h01))
                .add(guidedEndTangent.scale(h11));
    }

    private void explode(String reason) {
        if (DEBUG_LOGGING && !this.level().isClientSide()) {
            Main.LOGGER.info(
                    "{} bomb={} DETONATE reason={} tick={} pos={} vel={} sourceVehicleId={}",
                    DEBUG_TAG,
                    getDebugId(),
                    reason,
                    tickAge,
                    formatVec(position()),
                    formatVec(getDeltaMovement()),
                    sourceVehicleId
            );
        }
        this.discard();
        if (!this.level().isClientSide()) {
            this.level().explode(this, this.getX(), this.getY(0.0625), this.getZ(), EXPLOSION_RADIUS,
                    Config.getInstance().weaponsAreDestructive ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        }
    }

    private static String formatVec(Vec3 vec) {
        return String.format("(%.4f, %.4f, %.4f)", vec.x, vec.y, vec.z);
    }

    private static boolean isFinite(Vec3 vec) {
        return Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z);
    }
}
