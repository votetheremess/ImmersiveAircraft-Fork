package net.minecraft.network.codec;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Minimal subset used by this project for 1.20.1.
 */
public interface StreamCodec<B, T> {
    T decode(B buffer);

    void encode(B buffer, T value);

    static <B, T> StreamCodec<B, T> ofMember(BiConsumer<T, B> encoder, Function<B, T> decoder) {
        return new StreamCodec<>() {
            @Override
            public T decode(B buffer) {
                return decoder.apply(buffer);
            }

            @Override
            public void encode(B buffer, T value) {
                encoder.accept(value, buffer);
            }
        };
    }
}
