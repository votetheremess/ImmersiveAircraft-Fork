package immersive_aircraft.entity.weapon;

import immersive_aircraft.Entities;
import immersive_aircraft.Main;
import immersive_aircraft.client.util.BombTrajectoryPredictor;
import immersive_aircraft.cobalt.network.NetworkHandler;
import immersive_aircraft.config.Config;
import immersive_aircraft.entity.AirplaneEntity;
import immersive_aircraft.entity.InventoryVehicleEntity;
import immersive_aircraft.entity.VehicleEntity;
import immersive_aircraft.entity.bullet.ImpactBombEntity;
import immersive_aircraft.entity.misc.WeaponMount;
import immersive_aircraft.network.c2s.FireMessage;
import immersive_aircraft.network.s2c.FireResponse;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static net.minecraft.world.entity.item.PrimedTnt.TAG_FUSE;

public class BombBay extends BulletWeapon {
    private static final float MAX_COOLDOWN = 1.0f;
    private static final float BARREL_LENGTH = 0.25f;
    private static final float BARREL_OFFSET_Y = -0.8f;
    private static final float RELEASE_EXTRA_SEPARATION = 0.7f;
    private static final float BELLY_CLEARANCE = 0.4f;
    private static final double MIN_SPEED_EPSILON = 1.0E-4;
    private static final String DEBUG_TAG = "[IA-BOMB-DEBUG]";
    private static final boolean DEBUG_LOGGING = true;
    private float cooldown = 0.0f;

    public record BigBombReleaseData(
            Vec3 position,
            Vec3 velocity,
            Vector3f releaseDirection,
            Vec3 planeDeltaVelocity,
            Vec3 planeTrackedVelocity,
            double selectedSpeed
    ) {
    }

    public BombBay(VehicleEntity entity, ItemStack stack, WeaponMount mount, int slot) {
        super(entity, stack, mount, slot);
    }

    @Override
    protected float getBarrelLength() {
        return BARREL_LENGTH;
    }

    @Override
    protected Vector4f getBarrelOffset() {
        return new Vector4f(0.0f, BARREL_OFFSET_Y, 0.0f, 1.0f);
    }

    public float getVelocity() {
        return 0.0f;
    }

    @Override
    protected Entity getBullet(Vector4f position, Vector3f direction) {
        Vector3f vel = direction.mul(getVelocity(), new Vector3f());

        ItemStack stack = getAmmoStack();
        String string = stack != null ? BuiltInRegistries.ITEM.getKey(stack.getItem()).toString() : "minecraft:tnt";
        String identifier = Config.getInstance().bombBayEntity.getOrDefault(string, "immersive_aircraft:tiny_tnt");
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString("id", identifier);
        compoundTag.putInt(TAG_FUSE, 80);
        return EntityType.loadEntityRecursive(compoundTag, getEntity().level(), EntitySpawnReason.TRIGGERED, (e) -> {
            e.snapTo(position.x(), position.y(), position.z(), e.getYRot(), e.getXRot());
            e.setDeltaMovement(vel.x(), vel.y(), vel.z());
            return e;
        });
    }

    @Override
    public void tick() {
        cooldown -= 1.0f / 20.0f;
    }

    @Override
    public void fire(Vector3f direction) {
        fire(direction, null);
    }

    public void fire(Vector3f direction, @Nullable Vec3 targetPoint) {
        if (isBigBombEnabled()) {
            fireBigBomb(targetPoint);
            return;
        }
        if (spentAmmo(Config.getInstance().bombBayAmmunition, 20)) {
            super.fire(direction);
        }
    }

    @Override
    public void clientFire(int index) {
        if (getEntity() instanceof AirplaneEntity airplane && airplane.isPilotBigBombEnabled() && index > 0) {
            return;
        }
        if (cooldown <= 0.0f) {
            cooldown = MAX_COOLDOWN;
            Vec3 requestedTarget = null;
            if (isBigBombEnabled() && getEntity() instanceof InventoryVehicleEntity inventoryVehicle) {
                BigBombReleaseData releaseData = computeBigBombReleaseData(inventoryVehicle, getSlot(), getMount().transform());
                if (releaseData != null) {
                    requestedTarget = BombTrajectoryPredictor.predictImpact(
                            getEntity().level(),
                            getEntity(),
                            releaseData.position(),
                            releaseData.velocity()
                    );
                }
            }
            NetworkHandler.sendToServer(new FireMessage(getSlot(), index, getDirection(), requestedTarget));
        }
    }

    private Vector3f getDirection() {
        return getDirection(getMount().transform());
    }

    private boolean isBigBombEnabled() {
        return getEntity() instanceof AirplaneEntity airplane && airplane.isPilotBigBombEnabled();
    }

    public static int findFirstBombBaySlot(InventoryVehicleEntity vehicle) {
        return getBombBaySlots(vehicle).stream().findFirst().orElse(-1);
    }

    public static List<Integer> getBombBaySlots(InventoryVehicleEntity vehicle) {
        ArrayList<Integer> slots = new ArrayList<>();
        for (Map.Entry<Integer, List<Weapon>> entry : vehicle.getWeapons().entrySet()) {
            boolean hasBombBay = entry.getValue().stream().anyMatch(BombBay.class::isInstance);
            if (hasBombBay) {
                slots.add(entry.getKey());
            }
        }
        slots.sort(Comparator.naturalOrder());
        return slots;
    }

    @Nullable
    public static BigBombReleaseData computeBigBombReleaseData(InventoryVehicleEntity vehicle, int slot) {
        return computeBigBombReleaseData(vehicle, slot, null);
    }

    @Nullable
    private static BigBombReleaseData computeBigBombReleaseData(InventoryVehicleEntity vehicle, int slot, @Nullable Matrix4f fallbackMountTransform) {
        Matrix4f centerMountTransform = getCenterMountTransform(vehicle, slot, fallbackMountTransform);
        if (centerMountTransform == null) {
            return null;
        }

        Vector3f releaseDirection = getReleaseDirection(vehicle, centerMountTransform);
        Vec3 releasePosition = getReleasePosition(vehicle, centerMountTransform, releaseDirection);
        Vec3 planeDeltaVelocity = vehicle.getDeltaMovement();
        Vec3 planeTrackedVelocity = vehicle.getSpeedVector();
        Vec3 releaseVelocity = getReleaseVelocity(vehicle, releaseDirection, planeDeltaVelocity, planeTrackedVelocity);
        double selectedSpeed = releaseVelocity.length();

        return new BigBombReleaseData(
                releasePosition,
                releaseVelocity,
                new Vector3f(releaseDirection),
                planeDeltaVelocity,
                planeTrackedVelocity,
                selectedSpeed
        );
    }

    @Nullable
    private static Matrix4f getCenterMountTransform(InventoryVehicleEntity vehicle, int slot, @Nullable Matrix4f fallbackMountTransform) {
        List<WeaponMount> mounts = vehicle.getWeaponMounts(slot);
        if (mounts.isEmpty()) {
            return fallbackMountTransform == null ? null : new Matrix4f(fallbackMountTransform);
        }

        Matrix4f centerMountTransform = fallbackMountTransform == null
                ? new Matrix4f(mounts.get(0).transform())
                : new Matrix4f(fallbackMountTransform);
        float centerX = 0.0f;
        float centerY = 0.0f;
        float centerZ = 0.0f;
        for (WeaponMount weaponMount : mounts) {
            Matrix4f mountTransform = weaponMount.transform();
            centerX += mountTransform.m30();
            centerY += mountTransform.m31();
            centerZ += mountTransform.m32();
        }

        float invCount = 1.0f / mounts.size();
        centerMountTransform.m30(centerX * invCount);
        centerMountTransform.m31(centerY * invCount);
        centerMountTransform.m32(centerZ * invCount);
        return centerMountTransform;
    }

    private static Vector3f getDirection(VehicleEntity entity, Matrix4f mountTransform) {
        Vector3f direction = new Vector3f(0, 1.0f, 0);
        direction.mul(new Matrix3f(mountTransform));
        direction.mul(entity.getVehicleNormalTransform());
        return direction.normalize();
    }

    private Vector3f getDirection(Matrix4f mountTransform) {
        return getDirection(getEntity(), mountTransform);
    }

    private static Vector3f getReleaseDirection(VehicleEntity entity, Matrix4f mountTransform) {
        Vector3f direction = getDirection(entity, mountTransform);
        Vector3f vehicleDown = new Vector3f(0.0f, -1.0f, 0.0f);
        vehicleDown.mul(entity.getVehicleNormalTransform());
        if (direction.dot(vehicleDown) < 0.0f) {
            direction.negate();
        }
        return direction.normalize();
    }

    private static Vec3 getReleasePosition(VehicleEntity entity, Matrix4f centerMountTransform, Vector3f releaseDirection) {
        Vector4f position = new Vector4f(0.0f, BARREL_OFFSET_Y, 0.0f, 1.0f);
        position.mul(centerMountTransform);
        position.mul(entity.getVehicleTransform());

        float releaseSeparation = BARREL_LENGTH + RELEASE_EXTRA_SEPARATION;
        position.add(
                releaseDirection.x() * releaseSeparation,
                releaseDirection.y() * releaseSeparation,
                releaseDirection.z() * releaseSeparation,
                0.0f
        );
        position.y = Math.min(position.y, (float) (entity.getBoundingBox().minY - BELLY_CLEARANCE));
        return new Vec3(position.x, position.y, position.z);
    }

    private static Vec3 getReleaseVelocity(VehicleEntity entity, Vector3f releaseDirection, Vec3 planeDeltaVelocity, Vec3 planeTrackedVelocity) {
        double deltaSpeed = planeDeltaVelocity.length();
        double trackedSpeed = planeTrackedVelocity.length();
        double selectedSpeed = Math.max(deltaSpeed, trackedSpeed);
        if (selectedSpeed <= MIN_SPEED_EPSILON) {
            return Vec3.ZERO;
        }

        Vec3 preferredDirection = trackedSpeed >= deltaSpeed
                ? normalizedOrNull(planeTrackedVelocity)
                : normalizedOrNull(planeDeltaVelocity);
        if (preferredDirection == null) {
            preferredDirection = new Vec3(releaseDirection.x(), releaseDirection.y(), releaseDirection.z());
        }
        return preferredDirection.scale(selectedSpeed);
    }

    @Nullable
    private static Vec3 normalizedOrNull(Vec3 vector) {
        double len = vector.length();
        if (len <= MIN_SPEED_EPSILON) {
            return null;
        }
        return vector.scale(1.0 / len);
    }

    private void fireBigBomb(@Nullable Vec3 requestedTarget) {
        if (!spentAmmo(Config.getInstance().bombBayAmmunition, 40)) {
            return;
        }

        if (!(getEntity() instanceof InventoryVehicleEntity inventoryVehicle)) {
            return;
        }

        BigBombReleaseData releaseData = computeBigBombReleaseData(inventoryVehicle, getSlot(), getMount().transform());
        if (releaseData == null) {
            if (DEBUG_LOGGING) {
                Main.LOGGER.warn("{} fire aborted: could not compute release data for slot={} vehicle={}",
                        DEBUG_TAG, getSlot(), inventoryVehicle.getType().toShortString());
            }
            return;
        }

        VehicleEntity entity = getEntity();
        ImpactBombEntity bomb = new ImpactBombEntity(Entities.IMPACT_BOMB.get(), entity.level());
        bomb.snapTo(releaseData.position().x, releaseData.position().y, releaseData.position().z, bomb.getYRot(), bomb.getXRot());
        bomb.setSourceVehicleId(entity.getId());
        bomb.setDeltaMovement(releaseData.velocity());
        Vec3 strikeTarget = isFiniteVec(requestedTarget) ? requestedTarget : null;
        if (Config.getInstance().targetGuidedBigBomb && strikeTarget != null) {
            bomb.configureGuidedStrike(strikeTarget, releaseData.velocity());
        }
        int debugId = bomb.getDebugId();
        entity.level().addFreshEntity(bomb);

        if (DEBUG_LOGGING && !entity.level().isClientSide()) {
            String planeRotation = String.format("(yaw=%.2f,pitch=%.2f,roll=%.2f)",
                    entity.getYRot(), entity.getXRot(), entity.getRoll());
            Vector3f forwardDirection = entity.getForwardDirection();
            Main.LOGGER.info(
                    "{} bomb={} FIRE slot={} vehicle={} plane_pos={} plane_rot={} plane_delta_vel={} plane_tracked_vel={} forward_dir={} release_dir={} release_pos={} release_vel={} selected_speed={} strike_target={}",
                    DEBUG_TAG,
                    debugId,
                    getSlot(),
                    entity.getType().toShortString(),
                    formatVec(entity.position()),
                    planeRotation,
                    formatVec(releaseData.planeDeltaVelocity()),
                    formatVec(releaseData.planeTrackedVelocity()),
                    formatVec(new Vec3(forwardDirection.x(), forwardDirection.y(), forwardDirection.z())),
                    formatVec(new Vec3(releaseData.releaseDirection().x(), releaseData.releaseDirection().y(), releaseData.releaseDirection().z())),
                    formatVec(releaseData.position()),
                    formatVec(releaseData.velocity()),
                    String.format("%.4f", releaseData.selectedSpeed()),
                    strikeTarget == null ? "none" : formatVec(strikeTarget)
            );
        }

        Vector3f particleVelocity = new Vector3f(releaseData.releaseDirection()).mul(0.25f);
        particleVelocity.add((float) releaseData.velocity().x, (float) releaseData.velocity().y, (float) releaseData.velocity().z);
        Vector4f particleOrigin = new Vector4f((float) releaseData.position().x, (float) releaseData.position().y, (float) releaseData.position().z, 1.0f);
        FireResponse fireMessage = new FireResponse(particleOrigin, particleVelocity);
        if (entity.level() instanceof ServerLevel serverLevel) {
            for (ServerPlayer player : serverLevel.players()) {
                NetworkHandler.sendToPlayer(fireMessage, player);
            }
        }

        entity.playSound(getSound(), 1.0f, entity.getRandom().nextFloat() * 0.2f + 0.9f);
    }

    private static String formatVec(Vec3 vec) {
        return String.format("(%.4f, %.4f, %.4f)", vec.x, vec.y, vec.z);
    }

    private static boolean isFiniteVec(@Nullable Vec3 vec) {
        if (vec == null) {
            return false;
        }
        return Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z);
    }
}
