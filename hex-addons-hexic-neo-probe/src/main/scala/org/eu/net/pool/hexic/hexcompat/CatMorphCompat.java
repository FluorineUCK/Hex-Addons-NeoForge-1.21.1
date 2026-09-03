package org.eu.net.pool.hexic.hexcompat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import org.eu.net.pool.hexic.CatHolder;

/**
 * Loader-neutral decision seam shared by the client renderer mixins and the
 * server regression probe.
 */
public final class CatMorphCompat {
    private CatMorphCompat() {
    }

    public static Entity replacementForRender(Entity original) {
        Cat cat = CatHolder.getSyncedCat(original);
        return cat != null ? cat : original;
    }

    public static boolean hidesPlayerHands(Player player) {
        return CatHolder.getCat(player) != null;
    }
}
