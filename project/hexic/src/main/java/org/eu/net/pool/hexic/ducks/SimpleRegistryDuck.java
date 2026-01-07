package org.eu.net.pool.hexic.ducks;

import org.spongepowered.asm.mixin.gen.Accessor;

public interface SimpleRegistryDuck {
    void hexic$clear();
    void setFrozen(boolean frozen);
}
