package immersive_aircraft.fabric.cobalt.network;

import immersive_aircraft.cobalt.network.Message;
import immersive_aircraft.cobalt.network.NetworkHandler;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class NetworkHandlerImpl extends NetworkHandler.Impl {
    @Override
    public <T extends Message> void registerMessage(String namespace, CustomPacketPayload.Type<T> type, StreamCodec<RegistryFriendlyByteBuf, T> codec, NetworkHandler.ClientHandler<T> clientHandler, NetworkHandler.ServerHandler<T> serverHandler) {
        ResourceLocation id = type.id();

        if (clientHandler != null && FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientProxy.register(id, codec, clientHandler);
        }

        if (serverHandler != null) {
            ServerPlayNetworking.registerGlobalReceiver(id, (server, player, handler, buffer, responseSender) -> {
                T payload = codec.decode(new RegistryFriendlyByteBuf(buffer));
                server.execute(() -> serverHandler.handle(payload, player));
            });
        }
    }

    @Override
    public void sendToServer(Message msg) {
        ClientProxy.sendToServer(msg);
    }

    @Override
    public void sendToPlayer(Message msg, ServerPlayer e) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RegistryFriendlyByteBuf wrapped = new RegistryFriendlyByteBuf(buf);
        msg.encode(wrapped);
        ServerPlayNetworking.send(e, msg.type().id(), buf);
    }

    @Override
    public void sendToTrackingPlayers(Message msg, Entity e) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RegistryFriendlyByteBuf wrapped = new RegistryFriendlyByteBuf(buf);
        msg.encode(wrapped);
        for (ServerPlayer player : PlayerLookup.tracking(e)) {
            ServerPlayNetworking.send(player, msg.type().id(), new FriendlyByteBuf(buf.copy()));
        }
    }

    // Prevent eager loading client side code
    private static final class ClientProxy {
        private ClientProxy() {
            // Nop
        }

        public static <T extends Message> void register(ResourceLocation id, StreamCodec<RegistryFriendlyByteBuf, T> codec, NetworkHandler.ClientHandler<T> handler) {
            ClientPlayNetworking.registerGlobalReceiver(id, (client, networkHandler, buffer, responseSender) -> {
                RegistryFriendlyByteBuf wrapped = new RegistryFriendlyByteBuf(buffer);
                T payload = codec.decode(wrapped);
                client.execute(() -> handler.handle(payload));
            });
        }

        public static void sendToServer(Message msg) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                RegistryFriendlyByteBuf wrapped = new RegistryFriendlyByteBuf(buf);
                msg.encode(wrapped);
                ClientPlayNetworking.send(msg.type().id(), buf);
            }
        }
    }
}
