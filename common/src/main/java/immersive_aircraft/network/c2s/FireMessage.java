package immersive_aircraft.network.c2s;

import immersive_aircraft.cobalt.network.Message;
import immersive_aircraft.entity.InventoryVehicleEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

public class FireMessage extends Message {
    public static final StreamCodec<RegistryFriendlyByteBuf, FireMessage> STREAM_CODEC = StreamCodec.ofMember(FireMessage::encode, FireMessage::new);
    public static final CustomPacketPayload.Type<FireMessage> TYPE = Message.createType("fire");

    public CustomPacketPayload.Type<FireMessage> type() {
        return TYPE;
    }

    private final int slot;
    private final int index;
    public final Vector3f direction;
    @Nullable
    private final Vec3 target;

    public FireMessage(int slot, int index, Vector3f direction) {
        this(slot, index, direction, null);
    }

    public FireMessage(int slot, int index, Vector3f direction, @Nullable Vec3 target) {
        this.slot = slot;
        this.index = index;
        this.direction = direction;
        this.target = target;
    }

    public FireMessage(RegistryFriendlyByteBuf b) {
        slot = b.readInt();
        index = b.readInt();
        direction = new Vector3f(b.readFloat(), b.readFloat(), b.readFloat());
        if (b.readBoolean()) {
            target = new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
        } else {
            target = null;
        }
    }

    public int getSlot() {
        return slot;
    }

    public int getIndex() {
        return index;
    }

    @Nullable
    public Vec3 getTarget() {
        return target;
    }

    @Override
    public void encode(RegistryFriendlyByteBuf b) {
        b.writeInt(slot);
        b.writeInt(index);
        b.writeFloat(direction.x());
        b.writeFloat(direction.y());
        b.writeFloat(direction.z());
        b.writeBoolean(target != null);
        if (target != null) {
            b.writeDouble(target.x);
            b.writeDouble(target.y);
            b.writeDouble(target.z);
        }
    }

    @Override
    public void receiveServer(ServerPlayer e) {
        if (e.getVehicle() instanceof InventoryVehicleEntity vehicle) {
            vehicle.fireWeapon(slot, index, direction, target);
        }
    }
}
