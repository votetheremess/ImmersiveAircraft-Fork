package immersive_aircraft.network.c2s;

import immersive_aircraft.cobalt.network.Message;
import immersive_aircraft.entity.AircraftEntity;
import immersive_aircraft.entity.AirplaneEntity;
import immersive_aircraft.entity.VehicleEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class CommandMessage extends Message {
    public static final StreamCodec<RegistryFriendlyByteBuf, CommandMessage> STREAM_CODEC = StreamCodec.ofMember(CommandMessage::encode, CommandMessage::new);
    public static final CustomPacketPayload.Type<CommandMessage> TYPE = Message.createType("command");

    public CustomPacketPayload.Type<CommandMessage> type() {
        return TYPE;
    }

    private final Key key;
    private final double fx;
    private final double fy;
    private final double fz;

    public CommandMessage(Key key, Vec3 velocity) {
        this.key = key;
        this.fx = velocity.x;
        this.fy = velocity.y;
        this.fz = velocity.z;
    }

    public CommandMessage(RegistryFriendlyByteBuf b) {
        key = Key.values()[b.readInt()];
        fx = b.readDouble();
        fy = b.readDouble();
        fz = b.readDouble();
    }

    @Override
    public void encode(RegistryFriendlyByteBuf b) {
        b.writeInt(key.ordinal());
        b.writeDouble(fx);
        b.writeDouble(fy);
        b.writeDouble(fz);
    }

    @Override
    public void receiveServer(ServerPlayer e) {
        if (e.getRootVehicle() instanceof VehicleEntity vehicle) {
            if (key == Key.DISMOUNT) {
                e.stopRiding();
                e.setJumping(false);
                vehicle.chill();
                vehicle.setDeltaMovement(fx, fy, fz);
            } else if (key == Key.BOOST) {
                if (vehicle.canBoost()) {
                    vehicle.boost();
                }
            } else if (key == Key.STABILIZER && vehicle instanceof AircraftEntity aircraft) {
                aircraft.togglePilotStabilizer();
            } else if (key == Key.ADVANCED_CONTROLS && vehicle instanceof AirplaneEntity airplane) {
                airplane.togglePilotAdvancedControls();
            } else if (key == Key.STABILIZER_SET_ON && vehicle instanceof AirplaneEntity airplane) {
                airplane.setPilotStabilizerManuallyEnabled(true);
            } else if (key == Key.STABILIZER_SET_OFF && vehicle instanceof AirplaneEntity airplane) {
                airplane.setPilotStabilizerManuallyEnabled(false);
            } else if (key == Key.ADVANCED_CONTROLS_SET_ON && vehicle instanceof AirplaneEntity airplane) {
                airplane.setPilotAdvancedControlsEnabled(true);
            } else if (key == Key.ADVANCED_CONTROLS_SET_OFF && vehicle instanceof AirplaneEntity airplane) {
                airplane.setPilotAdvancedControlsEnabled(false);
            } else if (key == Key.ADVANCED_MOUSE_YAW_SET_ON && vehicle instanceof AirplaneEntity airplane) {
                airplane.setPilotMouseYawEnabled(true);
            } else if (key == Key.ADVANCED_MOUSE_YAW_SET_OFF && vehicle instanceof AirplaneEntity airplane) {
                airplane.setPilotMouseYawEnabled(false);
            }
        }
    }

    public enum Key {
        DISMOUNT,
        BOOST,
        STABILIZER,
        DAMAGE,
        ADVANCED_CONTROLS,
        STABILIZER_SET_ON,
        STABILIZER_SET_OFF,
        ADVANCED_CONTROLS_SET_ON,
        ADVANCED_CONTROLS_SET_OFF,
        ADVANCED_MOUSE_YAW_SET_ON,
        ADVANCED_MOUSE_YAW_SET_OFF
    }
}
