package immersive_aircraft.entity;

import immersive_aircraft.Main;
import immersive_aircraft.item.upgrade.VehicleStat;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Implements airplane like physics properties and accelerated towards
 */
public abstract class AirplaneEntity extends AircraftEntity {
    private static final int FLIGHT_LOG_INTERVAL_TICKS = 10;
    private static final String FLIGHT_LOG_TAG = "[IA-FLIGHT-LOG]";
    private static final double MAX_FLIGHT_ALTITUDE = 350.0;
    private static final double CLIMB_SLOWDOWN_START_ALTITUDE = 250.0;
    private static final float MAX_HORIZONTAL_SPEED_MULTIPLIER = 1.6f;
    private static final float MAX_DIVE_AIRSPEED_AS = 60.0f;
    private static final double MAX_DIVE_FORWARD_SPEED = MAX_DIVE_AIRSPEED_AS / 20.0d;
    private static final float FULL_DIVE_ASSIST_ANGLE_DEGREES = 25.0f;
    private static final float DIVE_ASSIST_ANGLE_FROM_STRAIGHT_DOWN_DEGREES = 40.0f;
    private static final float SLOW_DIVE_ASSIST_SCALE_AT_LIMIT = 0.8f;
    private static final float MIN_DIVE_ASSIST_ANGLE = Mth.cos(DIVE_ASSIST_ANGLE_FROM_STRAIGHT_DOWN_DEGREES * Mth.DEG_TO_RAD);
    private static final double MAX_DIVE_ASSIST_PER_TICK = 0.05d;
    private boolean wasFlightLoggingActive;

    public AirplaneEntity(EntityType<? extends AircraftEntity> entityType, Level world, boolean canExplodeOnCrash) {
        super(entityType, world, canExplodeOnCrash);
    }

    private float getAltitudeProgress() {
        return Mth.clamp((float) (getY() / MAX_FLIGHT_ALTITUDE), 0.0f, 1.0f);
    }

    private float getHorizontalSpeedMultiplier() {
        return 1.0f + (MAX_HORIZONTAL_SPEED_MULTIPLIER - 1.0f) * getAltitudeProgress();
    }

    private double getHorizontalSpeedCap() {
        float friction = getProperties().get(VehicleStat.FRICTION);
        float horizontalDecay = getProperties().get(VehicleStat.HORIZONTAL_DECAY);
        double damping = (1.0 - friction) * horizontalDecay;
        double denominator = Math.max(0.0001, 1.0 - damping);
        double baseMaxHorizontalSpeed = getProperties().get(VehicleStat.ENGINE_SPEED) / denominator;
        return baseMaxHorizontalSpeed * getHorizontalSpeedMultiplier();
    }

    private void applyAltitudeFlightLimits() {
        Vec3 velocity = getDeltaMovement();

        // Hard flight ceiling.
        if (getY() >= MAX_FLIGHT_ALTITUDE && velocity.y > 0.0) {
            velocity = new Vec3(velocity.x, 0.0, velocity.z);
        } else if (velocity.y > 0.0) {
            // Approaching the ceiling, climb gets progressively weaker.
            double climbRange = Math.max(1.0, MAX_FLIGHT_ALTITUDE - CLIMB_SLOWDOWN_START_ALTITUDE);
            float t = Mth.clamp((float) ((getY() - CLIMB_SLOWDOWN_START_ALTITUDE) / climbRange), 0.0f, 1.0f);
            float climbMultiplier = 1.0f - t;
            velocity = new Vec3(velocity.x, velocity.y * climbMultiplier, velocity.z);
        }

        // Prevent single-tick overshoot through the ceiling.
        if (velocity.y > 0.0 && getY() + velocity.y > MAX_FLIGHT_ALTITUDE) {
            velocity = new Vec3(velocity.x, Math.max(0.0, MAX_FLIGHT_ALTITUDE - getY()), velocity.z);
        }

        // Horizontal speed scales with altitude, but is capped at 1.6x baseline max.
        double horizontalSpeedCap = getHorizontalSpeedCap();
        double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (horizontalSpeed > horizontalSpeedCap && horizontalSpeed > 0.0) {
            double factor = horizontalSpeedCap / horizontalSpeed;
            velocity = new Vec3(velocity.x * factor, velocity.y, velocity.z * factor);
        }

        setDeltaMovement(velocity);
    }

    private void applyDiveAirspeedCap(Vector3f direction) {
        if (direction.y >= 0.0f) {
            return;
        }

        Vec3 velocity = getDeltaMovement();
        Vec3 forward = new Vec3(direction.x, direction.y, direction.z);
        double forwardSpeed = velocity.dot(forward);
        if (forwardSpeed <= MAX_DIVE_FORWARD_SPEED) {
            return;
        }

        double excessForwardSpeed = forwardSpeed - MAX_DIVE_FORWARD_SPEED;
        setDeltaMovement(velocity.subtract(forward.scale(excessForwardSpeed)));
    }

    private void applyDiveAssistAcceleration(Vector3f direction) {
        float diveAngle = Mth.clamp(-direction.y, 0.0f, 1.0f);
        if (diveAngle <= MIN_DIVE_ASSIST_ANGLE) {
            return;
        }

        Vec3 velocity = getDeltaMovement();
        Vec3 forward = new Vec3(direction.x, direction.y, direction.z);
        float diveFactor = Mth.clamp((diveAngle - MIN_DIVE_ASSIST_ANGLE) / (1.0f - MIN_DIVE_ASSIST_ANGLE), 0.0f, 1.0f);
        float angleFromStraightDown = (float) Math.toDegrees(Math.acos(diveAngle));
        float angleScale = 1.0f;
        if (angleFromStraightDown > FULL_DIVE_ASSIST_ANGLE_DEGREES) {
            float t = Mth.clamp((angleFromStraightDown - FULL_DIVE_ASSIST_ANGLE_DEGREES)
                    / (DIVE_ASSIST_ANGLE_FROM_STRAIGHT_DOWN_DEGREES - FULL_DIVE_ASSIST_ANGLE_DEGREES), 0.0f, 1.0f);
            angleScale = Mth.lerp(t, 1.0f, SLOW_DIVE_ASSIST_SCALE_AT_LIMIT);
        }

        double forwardSpeed = velocity.dot(forward);
        double speedGap = Math.max(0.0, MAX_DIVE_FORWARD_SPEED - forwardSpeed);
        if (speedGap <= 0.0) {
            return;
        }

        // 0-25 degrees from straight down keeps full ramp; 26-40 degrees ramps slower.
        double diveAssist = Math.min(MAX_DIVE_ASSIST_PER_TICK, speedGap * (0.05 + 0.10 * diveFactor)) * angleScale;
        setDeltaMovement(velocity.add(forward.scale(diveAssist)));
    }

    private boolean shouldLogFlightData() {
        if (!level().isClientSide()) {
            return false;
        }
        if (!(this instanceof BiplaneEntity || this instanceof BambooHopperEntity)) {
            return false;
        }
        if (!(getControllingPassenger() instanceof Player player) || !player.isLocalPlayer()) {
            return false;
        }
        return isVehicle();
    }

    private void tickFlightDebugLog(Vector3f direction) {
        boolean active = shouldLogFlightData();
        if (active != wasFlightLoggingActive) {
            Main.LOGGER.info("{} {} {} logging {}", FLIGHT_LOG_TAG, tickCount, getType().toShortString(), active ? "START" : "STOP");
            wasFlightLoggingActive = active;
        }

        if (!active || tickCount % FLIGHT_LOG_INTERVAL_TICKS != 0) {
            return;
        }

        Vec3 velocity = getDeltaMovement();
        Vec3 forward = new Vec3(direction.x, direction.y, direction.z);
        double forwardAs = velocity.dot(forward) * 20.0;
        double totalAs = velocity.length() * 20.0;
        double horizontalAs = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) * 20.0;
        String msg = String.format(
                "t=%d plane=%s y=%.2f pitch=%.2f roll=%.2f fwd_as=%.2f total_as=%.2f horiz_as=%.2f vy=%.3f",
                tickCount,
                getType().toShortString(),
                getY(),
                getXRot(),
                getRoll(),
                forwardAs,
                totalAs,
                horizontalAs,
                velocity.y
        );
        Main.LOGGER.info("{} {}", FLIGHT_LOG_TAG, msg);
    }

    @Override
    protected boolean useAirplaneControls() {
        return true;
    }

    @Override
    protected double getDefaultGravity() {
        Vector3f direction = getForwardDirection();
        float speed = (float) getDeltaMovement().length() * (1.0f - Math.abs(direction.y));
        return Math.max(0.0f, 1.0f - speed * 1.5f) * super.getDefaultGravity();
    }

    protected float getBrakeFactor() {
        return 0.95f;
    }

    @Override
    protected void updateController() {
        if (!isVehicle()) {
            if (wasFlightLoggingActive) {
                Main.LOGGER.info("{} {} {} logging STOP", FLIGHT_LOG_TAG, tickCount, getType().toShortString());
                wasFlightLoggingActive = false;
            }
            return;
        }

        super.updateController();
        boolean advancedControls = isPilotAdvancedControlsEnabled();

        // engine control
        if (movementY != 0) {
            setEngineTarget(Math.max(0.0f, Math.min(1.0f, getEngineTarget() + 0.1f * movementY)));
            if (movementY < 0 && !advancedControls) {
                setDeltaMovement(getDeltaMovement().scale(getBrakeFactor()));
            }
        }
        if (advancedControls && advancedSlowDownInput) {
            setDeltaMovement(getDeltaMovement().scale(getBrakeFactor()));
        }

        // get the direction
        Vector3f direction = getForwardDirection();

        // speed
        float thrust = (float) (Math.pow(getEnginePower(), 2.0) * getProperties().get(VehicleStat.ENGINE_SPEED));
        if (onGround() && getEngineTarget() < 1.0) {
            thrust = getProperties().get(VehicleStat.PUSH_SPEED) / (1.0f + (float) getDeltaMovement().length() * 5.0f) * pressingInterpolatedZ.getSmooth() * (1.0f - getEnginePower());
        }

        // accelerate
        float horizontalMultiplier = getHorizontalSpeedMultiplier();
        setDeltaMovement(getDeltaMovement().add(direction.x * thrust * horizontalMultiplier, direction.y * thrust, direction.z * thrust * horizontalMultiplier));
        applyDiveAssistAcceleration(direction);
        applyAltitudeFlightLimits();
        applyDiveAirspeedCap(direction);
        tickFlightDebugLog(direction);
    }
}
