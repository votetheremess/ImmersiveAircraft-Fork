package net.minecraft.network.protocol.common.custom;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * 1.21 payload API shim used by shared networking code.
 */
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    final class Type<T extends CustomPacketPayload> {
        private final ResourceLocation id;

        public Type(ResourceLocation id) {
            this.id = id;
        }

        public ResourceLocation id() {
            return id;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Type<?> other)) {
                return false;
            }
            return Objects.equals(this.id, other.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return "Type[" + id + "]";
        }
    }
}
