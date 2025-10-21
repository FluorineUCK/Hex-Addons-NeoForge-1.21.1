package org.eu.net.pool.hexic;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;

public class WarCrime extends BlockPos {
    public final @NotNull Entity e;

    public WarCrime(Entity e) {
        super(e.getBlockPos());
        this.e = e;
    }
}
