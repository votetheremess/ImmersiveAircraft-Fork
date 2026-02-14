package net.minecraft.resources;

/**
 * 1.21-style alias used by this branch's shared sources.
 * Backported to 1.20.1 by extending ResourceLocation.
 */
public class Identifier extends ResourceLocation {
    public Identifier(String namespace, String path) {
        super(namespace, path);
    }

    public static Identifier fromNamespaceAndPath(String namespace, String path) {
        return new Identifier(namespace, path);
    }

    public static Identifier parse(String id) {
        int split = id.indexOf(':');
        if (split >= 0) {
            return new Identifier(id.substring(0, split), id.substring(split + 1));
        }
        return new Identifier("minecraft", id);
    }
}
