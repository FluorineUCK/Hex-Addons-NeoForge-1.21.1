package org.eu.net.pool.hexic.mixin;

import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import org.eu.net.pool.hexic.ducks.SimpleRegistryDuck;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(SimpleRegistry.class)
public abstract class SimpleRegistryMixin<T> implements SimpleRegistryDuck {
    @Shadow @Final private ObjectList<RegistryEntry.Reference<T>> rawIdToEntry;
    @Shadow @Final private Object2IntMap<T> entryToRawId;
    @Shadow @Final private Map<Identifier, RegistryEntry.Reference<T>> idToEntry;
    @Shadow @Final private Map<RegistryKey<T>, RegistryEntry.Reference<T>> keyToEntry;
    @Shadow @Final private Map<T, RegistryEntry.Reference<T>> valueToEntry;
    @Shadow @Final private Map<T, Lifecycle> entryToLifecycle;
    @Shadow private boolean frozen;

    @Override
    public void hexic$clear() {
        rawIdToEntry.clear();
        entryToRawId.clear();
        idToEntry.clear();
        keyToEntry.clear();
        valueToEntry.clear();
        entryToLifecycle.clear();
    }

    @Override
    @Accessor
    public abstract void setFrozen(boolean frozen);
}
