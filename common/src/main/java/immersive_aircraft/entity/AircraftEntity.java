package immersive_aircraft.entity;

import com.mojang.math.Axis;
import immersive_aircraft.Items;
import immersive_aircraft.config.Config;
import immersive_aircraft.entity.inventory.VehicleInventoryDescription;
import immersive_aircraft.entity.misc.Trail;
import immersive_aircraft.entity.misc.TrailDescriptor;
import immersive_aircraft.item.upgrade.VehicleStat;
import immersive_aircraft.item.upgrade.VehicleUpgrade;
import immersive_aircraft.item.upgrade.VehicleUpgradeRegistry;
import immersive_aircraft.util.Utils;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract aircraft, which performs basic physics
 */
public abstract class AircraftEntity extends EngineVehicle {
    private static final float STABILIZER_ANGLE_LIMIT = 30.0f;
    private static final float STABILIZER_SOFT_LIMIT = 15.0f;
    private static final float STABILIZER_SOFT_RESISTANCE = 0.35f;
    private static final int STABILIZER_REENABLE_DELAY_TICKS = 30;
    private static final int STABILIZER_MESSAGE_TICKS = 40;
    private static final int STABILIZER_BEEP_CYCLE_TICKS = 10;
    private static final int STABILIZER_BEEP_INTERVAL_TICKS = Math.max(1, STABILIZER_BEEP_CYCLE_TICKS / 2);
    private static final float ADVANCED_ROLL_SPEED_SCALE = 0.25f;
    private static final float ADVANCED_PILOT_YAW_DEADZONE = 10.0f;
    private static final float ADVANCED_PILOT_YAW_TRACK_SPEED = 1.1f;
    protected double lastY;
    public float inWaterLevel;
    private boolean hasGyroscopeUpgrade;
    private boolean gyroscopeHudInstalled;
    private boolean gyroscopeStabilizerEnabled = true;
    private boolean gyroscopeStabilizerManuallyEnabled = true;
    private boolean gyroscopeStateInitialized;
    private float stabilizerWithGyroscope;
    private float stabilizerWithoutGyroscope;
    private float windWithGyroscope;
    private float windWithoutGyroscope;
    private int gyroscopeReenableTicks;
    private int queuedGyroscopeBeeps;
    private int gyroscopeBeepCooldown;
    private float gyroscopeBeepPitch;
    private boolean pilotAdvancedControlsEnabled;
    private boolean pilotMouseYawEnabled = true;

    public AircraftEntity(EntityType<? extends AircraftEntity> entityType, Level world, boolean canExplodeOnCrash) {
        super(entityType, world, canExplodeOnCrash);
    }

    private List<Trail> trails = Collections.emptyList();

    public List<Trail> getTrails() {
        if (getVehicleData().getTrails().size() != trails.size()) {
            trails = new ArrayList<>(getVehicleData().getTrails().size());
            for (TrailDescriptor trail : getVehicleData().getTrails()) {
                trails.add(new Trail(trail.length(), trail.gray()));
            }
        }
        return trails;
    }

    protected float getBaseTrailWidth(Matrix4f transform, int index, TrailDescriptor trail) {
        return 1.0f;
    }

    private void recordTrail(Matrix4f transform, int index, TrailDescriptor trail) {
        Matrix4f t = new Matrix4f(transform);
        t.translate(trail.x(), trail.y(), trail.z());
        if (trail.rotate() != 0.0) {
            t.rotate(Axis.ZP.rotationDegrees(engineRotation.getSmooth() * trail.rotate()));
        }

        Vector4f p0 = mulXVec(t, -trail.size());
        Vector4f p1 = mulXVec(t, trail.size());

        float trailStrength = Math.max(0.0f, Math.min(1.0f, getBaseTrailWidth(t, index, trail)));
        getTrails().get(index).add(p0, p1, trailStrength);
    }

    private Vector4f mulXVec(Matrix4fc mat, float x) {
        return new Vector4f(
                Math.fma(mat.m00(), x, mat.m30()),
                Math.fma(mat.m01(), x, mat.m31()),
                Math.fma(mat.m02(), x, mat.m32()),
                Math.fma(mat.m03(), x, mat.m33())
        );
    }

    @Override
    public void tick() {
        // rolling interpolation
        prevRoll = roll;
        updateRollFromControls();

        // Fixes broken states
        if (Double.isNaN(getDeltaMovement().x) || Double.isNaN(getDeltaMovement().y) || Double.isNaN(getDeltaMovement().z)) {
            setDeltaMovement(0, 0, 0);
        }

        // Trails
        List<TrailDescriptor> trailDescriptors = getVehicleData().getTrails();
        if (!trailDescriptors.isEmpty()) {
            Matrix4f vehicleTransform = getVehicleTransform();
            for (int i = 0; i < trailDescriptors.size(); i++) {
                TrailDescriptor trail = trailDescriptors.get(i);
                recordTrail(vehicleTransform, i, trail);
            }
        }

        // Water
        if (wasTouchingWater) {
            inWaterLevel = Math.min(1.0f, inWaterLevel + 0.05f);
        } else {
            inWaterLevel = Math.max(0.0f, inWaterLevel - 0.05f);
        }

        updateGyroscopeAssistState();
        tickGyroscopeBeeps();

        super.tick();
    }

    private boolean shouldUseGyroscopeAssist() {
        return this instanceof AirplaneEntity;
    }

    private boolean supportsAdvancedControls() {
        return this instanceof AirplaneEntity;
    }

    public boolean isPilotAdvancedControlsEnabled() {
        return supportsAdvancedControls() && pilotAdvancedControlsEnabled;
    }

    public void setPilotAdvancedControlsEnabled(boolean enabled) {
        if (!supportsAdvancedControls()) {
            return;
        }
        pilotAdvancedControlsEnabled = enabled;
    }

    public void togglePilotAdvancedControls() {
        if (!supportsAdvancedControls()) {
            return;
        }
        pilotAdvancedControlsEnabled = !pilotAdvancedControlsEnabled;
    }

    public boolean isPilotMouseYawEnabled() {
        return supportsAdvancedControls() && pilotMouseYawEnabled;
    }

    public void setPilotMouseYawEnabled(boolean enabled) {
        if (!supportsAdvancedControls()) {
            return;
        }
        pilotMouseYawEnabled = enabled;
    }

    public void togglePilotMouseYaw() {
        if (!supportsAdvancedControls()) {
            return;
        }
        pilotMouseYawEnabled = !pilotMouseYawEnabled;
    }

    private void updateRollFromControls() {
        if (onGround()) {
            setZRot(roll * 0.9f);
            return;
        }

        if (!isPilotAdvancedControlsEnabled()) {
            setZRot(-pressingInterpolatedX.getSmooth() * getProperties().get(VehicleStat.ROLL_FACTOR) * (1.0f - inWaterLevel));
            return;
        }

        float rollDelta = -pressingInterpolatedX.getSmooth()
                * getProperties().get(VehicleStat.ROLL_FACTOR)
                * ADVANCED_ROLL_SPEED_SCALE
                * (1.0f - inWaterLevel);
        setZRot(getRoll() + rollDelta);
    }

    private boolean isWithinStabilizerEnvelope() {
        return Math.abs(Mth.wrapDegrees(getXRot())) <= STABILIZER_ANGLE_LIMIT;
    }

    private boolean isGyroscopeItem(ItemStack stack) {
        return stack.getItem() == Items.GYROSCOPE.get()
                || stack.getItem() == Items.GYROSCOPE_HUD.get()
                || stack.getItem() == Items.GYROSCOPE_DIALS.get();
    }

    /**
     * Mirrors InventoryVehicleEntity#getTotalUpgrade while allowing gyroscope filtering.
     */
    private float getUpgradeMultiplier(VehicleStat stat, boolean includeGyroscopeItems) {
        float value = 1.0f;
        List<ItemStack> upgrades = getSlots(VehicleInventoryDescription.UPGRADE);
        for (int step = 0; step < 2; step++) {
            for (ItemStack stack : upgrades) {
                if (stack.isEmpty() || (!includeGyroscopeItems && isGyroscopeItem(stack))) {
                    continue;
                }

                VehicleUpgrade upgrade = VehicleUpgradeRegistry.INSTANCE.getUpgrade(stack.getItem());
                if (upgrade == null) {
                    continue;
                }

                float u = upgrade.get(stat);
                if (u > 0 && step == 1) {
                    value += u;
                } else if (u < 0 && step == 0) {
                    value *= (u + 1.0f);
                }
            }
        }
        return Math.max(0.0f, value);
    }

    private void clearGyroscopeWarnings() {
        cautions.put(Cautions.STABILIZER_ON, 0);
        cautions.put(Cautions.STABILIZER_OFF, 0);
    }

    private void notifyGyroscopeState(boolean enabled) {
        if (level().isClientSide() && getControllingPassenger() instanceof Player player && player.isLocalPlayer()) {
            queuedGyroscopeBeeps = enabled ? 1 : 2;
            gyroscopeBeepCooldown = 0;
            gyroscopeBeepPitch = enabled ? 1.3f : 0.8f;
        }

        if (gyroscopeHudInstalled) {
            cautions.put(enabled ? Cautions.STABILIZER_ON : Cautions.STABILIZER_OFF, STABILIZER_MESSAGE_TICKS);
            cautions.put(enabled ? Cautions.STABILIZER_OFF : Cautions.STABILIZER_ON, 0);
        } else {
            clearGyroscopeWarnings();
        }
    }

    private void updateGyroscopeAssistState() {
        if (!shouldUseGyroscopeAssist()) {
            hasGyroscopeUpgrade = false;
            gyroscopeHudInstalled = false;
            gyroscopeStabilizerEnabled = true;
            gyroscopeStateInitialized = false;
            stabilizerWithGyroscope = 0.0f;
            stabilizerWithoutGyroscope = 0.0f;
            windWithGyroscope = 0.0f;
            windWithoutGyroscope = 0.0f;
            gyroscopeReenableTicks = 0;
            queuedGyroscopeBeeps = 0;
            gyroscopeBeepCooldown = 0;
            clearGyroscopeWarnings();
            return;
        }

        boolean hasGyro = false;
        boolean hasHudGyro = false;
        for (ItemStack upgrade : getSlots(VehicleInventoryDescription.UPGRADE)) {
            if (upgrade.isEmpty()) {
                continue;
            }
            if (isGyroscopeItem(upgrade)) {
                hasGyro = true;
                if (upgrade.getItem() == Items.GYROSCOPE_HUD.get()) {
                    hasHudGyro = true;
                }
            }
        }

        hasGyroscopeUpgrade = hasGyro;
        gyroscopeHudInstalled = hasHudGyro;

        float baseStabilizer = getVehicleData().getProperties().getOrDefault(VehicleStat.STABILIZER, 0.0f);
        float baseWind = getVehicleData().getProperties().getOrDefault(VehicleStat.WIND, 0.0f);
        float stabilizerMultiplierWithGyro = getUpgradeMultiplier(VehicleStat.STABILIZER, true);
        float stabilizerMultiplierWithoutGyro = getUpgradeMultiplier(VehicleStat.STABILIZER, false);
        float windMultiplierWithGyro = getUpgradeMultiplier(VehicleStat.WIND, true);
        float windMultiplierWithoutGyro = getUpgradeMultiplier(VehicleStat.WIND, false);
        stabilizerWithGyroscope = baseStabilizer + stabilizerMultiplierWithGyro - 1.0f;
        stabilizerWithoutGyroscope = baseStabilizer + stabilizerMultiplierWithoutGyro - 1.0f;
        windWithGyroscope = baseWind * windMultiplierWithGyro;
        windWithoutGyroscope = baseWind * windMultiplierWithoutGyro;

        if (!gyroscopeHudInstalled) {
            clearGyroscopeWarnings();
        }

        if (!hasGyroscopeUpgrade) {
            gyroscopeStabilizerEnabled = true;
            gyroscopeStateInitialized = false;
            stabilizerWithGyroscope = 0.0f;
            stabilizerWithoutGyroscope = 0.0f;
            windWithGyroscope = 0.0f;
            windWithoutGyroscope = 0.0f;
            gyroscopeReenableTicks = 0;
            queuedGyroscopeBeeps = 0;
            gyroscopeBeepCooldown = 0;
            clearGyroscopeWarnings();
            return;
        }

        boolean insideEnvelope = isWithinStabilizerEnvelope();
        if (!gyroscopeStateInitialized) {
            gyroscopeStabilizerEnabled = gyroscopeStabilizerManuallyEnabled && insideEnvelope;
            gyroscopeStateInitialized = true;
            gyroscopeReenableTicks = 0;
            return;
        }

        if (!gyroscopeStabilizerManuallyEnabled) {
            gyroscopeReenableTicks = 0;
            if (gyroscopeStabilizerEnabled) {
                gyroscopeStabilizerEnabled = false;
                notifyGyroscopeState(false);
            }
            return;
        }

        if (!insideEnvelope) {
            gyroscopeReenableTicks = 0;
            if (gyroscopeStabilizerEnabled) {
                gyroscopeStabilizerEnabled = false;
                notifyGyroscopeState(false);
            }
            return;
        }

        if (gyroscopeStabilizerEnabled) {
            gyroscopeReenableTicks = 0;
            return;
        }

        if (gyroscopeReenableTicks <= 0) {
            gyroscopeReenableTicks = STABILIZER_REENABLE_DELAY_TICKS;
            return;
        }

        gyroscopeReenableTicks--;
        if (gyroscopeReenableTicks <= 0) {
            gyroscopeStabilizerEnabled = true;
            notifyGyroscopeState(true);
        }
    }

    private void tickGyroscopeBeeps() {
        if (!level().isClientSide() || queuedGyroscopeBeeps <= 0) {
            return;
        }
        if (!(getControllingPassenger() instanceof Player player) || !player.isLocalPlayer()) {
            queuedGyroscopeBeeps = 0;
            return;
        }
        if (gyroscopeBeepCooldown > 0) {
            gyroscopeBeepCooldown--;
            return;
        }

        level().playLocalSound(getX(), getY() + getBbHeight() * 0.5, getZ(),
                SoundEvents.NOTE_BLOCK_BIT.value(),
                getSoundSource(), 0.8f, gyroscopeBeepPitch, false);
        queuedGyroscopeBeeps--;
        gyroscopeBeepCooldown = Math.max(0, STABILIZER_BEEP_INTERVAL_TICKS - 1);
    }

    public void togglePilotStabilizer() {
        if (!shouldUseGyroscopeAssist()) {
            return;
        }

        gyroscopeStabilizerManuallyEnabled = !gyroscopeStabilizerManuallyEnabled;
        gyroscopeReenableTicks = 0;

        if (!hasGyroscopeUpgrade) {
            clearGyroscopeWarnings();
            return;
        }

        if (!gyroscopeStabilizerManuallyEnabled) {
            if (gyroscopeStabilizerEnabled) {
                gyroscopeStabilizerEnabled = false;
                notifyGyroscopeState(false);
            }
            return;
        }

        if (isWithinStabilizerEnvelope() && !gyroscopeStabilizerEnabled) {
            gyroscopeStabilizerEnabled = true;
            notifyGyroscopeState(true);
        }
    }

    public boolean isPilotStabilizerManuallyEnabled() {
        return shouldUseGyroscopeAssist() && gyroscopeStabilizerManuallyEnabled;
    }

    public void setPilotStabilizerManuallyEnabled(boolean enabled) {
        if (!shouldUseGyroscopeAssist()) {
            return;
        }
        if (gyroscopeStabilizerManuallyEnabled != enabled) {
            togglePilotStabilizer();
        }
    }

    private boolean isGyroscopeStabilizerActive() {
        return shouldUseGyroscopeAssist() && hasGyroscopeUpgrade && gyroscopeStabilizerEnabled;
    }

    private boolean isGyroscopeWindReductionActive() {
        return shouldUseGyroscopeAssist() && hasGyroscopeUpgrade && isWithinStabilizerEnvelope();
    }

    private float applyStabilizerPitchResistance(float pitchDelta) {
        if (pitchDelta == 0.0f || !isGyroscopeStabilizerActive()) {
            return pitchDelta;
        }

        float currentPitch = Math.abs(Mth.wrapDegrees(getXRot()));
        float nextPitch = Math.abs(Mth.wrapDegrees(getXRot() + pitchDelta));
        if (nextPitch <= currentPitch || nextPitch <= STABILIZER_SOFT_LIMIT) {
            return pitchDelta;
        }

        float t = Mth.clamp((nextPitch - STABILIZER_SOFT_LIMIT) / (STABILIZER_ANGLE_LIMIT - STABILIZER_SOFT_LIMIT), 0.0f, 1.0f);
        return pitchDelta * (1.0f - STABILIZER_SOFT_RESISTANCE * t);
    }

    private void applyAdvancedPitchAndTurn(float pitchDelta) {
        float adjustedPitchDelta = applyStabilizerPitchResistance(pitchDelta);
        if (adjustedPitchDelta == 0.0f) {
            return;
        }

        float rollRadians = getRoll() * Mth.DEG_TO_RAD;
        float pitchContribution = adjustedPitchDelta * Mth.cos(rollRadians);
        float yawContribution = -adjustedPitchDelta * Mth.sin(rollRadians);

        setXRot(getXRot() + pitchContribution);
        setYRot(getYRot() + yawContribution);
        normalizeAdvancedAttitudeForCamera();
    }

    private void normalizeAdvancedAttitudeForCamera() {
        float pitch = Mth.wrapDegrees(getXRot());
        if (pitch > 90.0f) {
            setXRot(180.0f - pitch);
            setYRot(getYRot() + 180.0f);
            setZRot(getRoll() + 180.0f);
        } else if (pitch < -90.0f) {
            setXRot(-180.0f - pitch);
            setYRot(getYRot() + 180.0f);
            setZRot(getRoll() + 180.0f);
        }
    }

    private void applyAdvancedPilotFacingYawControl() {
        if (!isPilotMouseYawEnabled() || !(getControllingPassenger() instanceof Player player)) {
            return;
        }

        float yawError = Mth.wrapDegrees(player.getYRot() - getYRot());
        float absYawError = Math.abs(yawError);
        if (absYawError <= ADVANCED_PILOT_YAW_DEADZONE) {
            return;
        }

        float yawBeyondDeadzone = absYawError - ADVANCED_PILOT_YAW_DEADZONE;
        float yawDelta = Mth.clamp(
                Math.copySign(yawBeyondDeadzone, yawError),
                -ADVANCED_PILOT_YAW_TRACK_SPEED,
                ADVANCED_PILOT_YAW_TRACK_SPEED);
        setYRot(getYRot() + yawDelta);
    }

    protected void convertPower(Vec3 direction) {
        Vec3 velocity = getDeltaMovement();
        double drag = Math.abs(direction.dot(velocity.normalize()));
        setDeltaMovement(velocity.normalize()
                .lerp(direction, getProperties().get(VehicleStat.LIFT))
                .scale(velocity.length() * (drag * getProperties().get(VehicleStat.FRICTION) + (1.0 - getProperties().get(VehicleStat.FRICTION)))));
    }

    @Override
    protected float getGroundDecay() {
        float gravity = Math.min(1.0f, Math.max(0.0f, (float) getGravity() / (-0.04f)));
        float upgrade = Math.min(1.0f, getProperties().get(VehicleStat.ACCELERATION) * 0.5f);
        return (super.getGroundDecay() * gravity + (1.0f - gravity)) * (1.0f - upgrade) + upgrade;
    }

    @Override
    protected void updateController() {
        // left-right
        if (!isPilotAdvancedControlsEnabled()) {
            setYRot(getYRot() - getProperties().get(VehicleStat.YAW_SPEED) * pressingInterpolatedX.getSmooth());
        } else {
            applyAdvancedPilotFacingYawControl();
        }

        // forwards-backwards
        if (!onGround()) {
            if (isPilotAdvancedControlsEnabled()) {
                if (movementZ != 0.0f) {
                    float pitchDelta = getProperties().get(VehicleStat.PITCH_SPEED) * pressingInterpolatedZ.getSmooth();
                    applyAdvancedPitchAndTurn(pitchDelta);
                }
            } else {
                float pitchDelta = getProperties().get(VehicleStat.PITCH_SPEED) * pressingInterpolatedZ.getSmooth();
                setXRot(getXRot() + applyStabilizerPitchResistance(pitchDelta));
            }
        }
        float stabilizer = hasGyroscopeUpgrade
                ? (isGyroscopeStabilizerActive() ? stabilizerWithGyroscope : stabilizerWithoutGyroscope)
                : getProperties().getAdditive(VehicleStat.STABILIZER);
        setXRot(getXRot() * (1.0f - Math.max(0.0f, stabilizer)));
    }

    @Override
    protected void updateVelocity() {
        // get the direction
        Vector3f direction = getForwardDirection();

        // glide
        float diff = (float) (lastY - getY());
        if (lastY != 0.0 && getProperties().get(VehicleStat.GLIDE_FACTOR) > 0 && diff != 0.0) {
            setDeltaMovement(getDeltaMovement().add(toVec3d(direction).scale(diff * getProperties().get(VehicleStat.GLIDE_FACTOR) * (1.0f - Math.abs(direction.y)))));
        }
        lastY = (float) getY();

        // convert power
        convertPower(toVec3d(direction));

        // friction
        applyFriction();

        if (onGround()) {
            // Landing
            setXRot((getXRot() + getProperties().get(VehicleStat.GROUND_PITCH)) * 0.9f - getProperties().get(VehicleStat.GROUND_PITCH));
        } else if (!wasTouchingWater) {
            // Wind
            Vector3f effect = getWindEffect();
            setXRot(getXRot() + effect.x);
            setYRot(getYRot() + effect.z);

            float offsetStrength = 0.005f;
            setDeltaMovement(getDeltaMovement().add(effect.x * offsetStrength, 0.0f, effect.z * offsetStrength));
        }
    }

    public void chill() {
        lastY = 0.0f;
    }

    public float getWindStrength() {
        float sensitivity = hasGyroscopeUpgrade
                ? (isGyroscopeWindReductionActive() ? windWithGyroscope : windWithoutGyroscope)
                : getProperties().get(VehicleStat.WIND);
        float thundering = level().getRainLevel(0.0f);
        float raining = level().getThunderLevel(0.0f);
        float weather = (float) ((Config.getInstance().windClearWeather + getDeltaMovement().length()) + thundering * Config.getInstance().windThunderWeather + raining * Config.getInstance().windRainWeather);
        return weather * sensitivity;
    }

    public Vector3f getWindEffect() {
        float wind = getWindStrength();
        float nx = (float) (Utils.cosNoise(tickCount / 20.0 / getProperties().get(VehicleStat.MASS)) * wind);
        float nz = (float) (Utils.cosNoise(tickCount / 21.0 / getProperties().get(VehicleStat.MASS)) * wind);
        return new Vector3f(nx, 0.0f, nz);
    }
}
