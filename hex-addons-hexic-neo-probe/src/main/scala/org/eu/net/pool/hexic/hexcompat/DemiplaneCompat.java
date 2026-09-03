package org.eu.net.pool.hexic.hexcompat;

import net.minecraft.server.level.ServerLevel;

/** Small loader-neutral predicates shared by Hexic's demiplane integrations. */
public final class DemiplaneCompat {
    private DemiplaneCompat() {
    }

    public static boolean isDemiplane(ServerLevel level) {
        var id = level.dimension().location();
        return id.getNamespace().equals("hexic")
                && id.getPath().startsWith("fresh-");
    }
}
