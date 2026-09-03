package org.eu.net.pool.hexic.hexcompat;

import at.petrak.hexcasting.xplat.IClientXplatAbstractions;

/**
 * Stabilizes Hex Casting's client service bootstrap on NeoForge development
 * launches. Hex pre-39 uses {@code ServiceLoader.load(Class)}, which consults
 * the current thread context class loader. Parallel client setup can run on a
 * worker whose context loader is the application loader while the service
 * interface itself belongs to ModLauncher's transforming loader. Loading the
 * provider through those two class identities produces "not a subtype".
 *
 * <p>This adapter initializes Hex's public singleton once, on Hexic's
 * client-only construction path, while explicitly supplying the interface's
 * defining loader as the thread context loader. The original loader is always
 * restored.</p>
 */
public final class HexClientXplatClassLoaderCompat {
    private HexClientXplatClassLoaderCompat() {
    }

    public static String preload() {
        ClassLoader serviceLoader = IClientXplatAbstractions.class.getClassLoader();
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(serviceLoader);
            IClientXplatAbstractions implementation = IClientXplatAbstractions.INSTANCE;
            if (implementation == null || !IClientXplatAbstractions.class.isInstance(implementation)) {
                throw new IllegalStateException("Hex Casting client xplat service did not initialize");
            }
            return "service=" + loaderName(serviceLoader)
                + " implementation=" + loaderName(implementation.getClass().getClassLoader());
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private static String loaderName(ClassLoader loader) {
        return loader == null ? "bootstrap" : loader.getClass().getName();
    }
}
