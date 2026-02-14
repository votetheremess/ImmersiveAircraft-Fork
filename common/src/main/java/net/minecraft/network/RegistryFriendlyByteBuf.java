package net.minecraft.network;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 1.21-style buffer shim for cross-version message code.
 */
public class RegistryFriendlyByteBuf extends FriendlyByteBuf {
    @Nullable
    private final RegistryAccess registryAccess;

    public RegistryFriendlyByteBuf(ByteBuf source, @Nullable RegistryAccess registryAccess) {
        super(source);
        this.registryAccess = registryAccess;
    }

    public RegistryFriendlyByteBuf(ByteBuf source) {
        this(source, null);
    }

    @Nullable
    public RegistryAccess registryAccess() {
        return registryAccess;
    }

    public Identifier readIdentifier() {
        return Identifier.parse(readUtf());
    }

    public void writeIdentifier(ResourceLocation id) {
        writeUtf(id.toString());
    }

    public <T> void writeJsonWithCodec(Codec<T> codec, T value) {
        Tag tag = codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow(false, s -> {
        });
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("value", tag);
        writeNbt(wrapper);
    }

    public <T> T readLenientJsonWithCodec(Codec<T> codec) {
        CompoundTag wrapper = readNbt();
        if (wrapper == null || !wrapper.contains("value")) {
            throw new IllegalStateException("Expected encoded value but buffer had no tag");
        }
        Tag tag = wrapper.get("value");
        return codec.parse(NbtOps.INSTANCE, tag).getOrThrow(false, s -> {
        });
    }
}
