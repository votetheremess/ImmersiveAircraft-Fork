package net.minecraft.util;

/**
 * Small 1.21 ARGB helper shim for 1.20.1.
 */
public final class ARGB {
    private ARGB() {
    }

    public static int color(int a, int r, int g, int b) {
        return ((a & 0xFF) << 24)
                | ((r & 0xFF) << 16)
                | ((g & 0xFF) << 8)
                | (b & 0xFF);
    }

    public static int colorFromFloat(float a, float r, float g, float b) {
        return color(
                (int) (a * 255.0f),
                (int) (r * 255.0f),
                (int) (g * 255.0f),
                (int) (b * 255.0f)
        );
    }
}
