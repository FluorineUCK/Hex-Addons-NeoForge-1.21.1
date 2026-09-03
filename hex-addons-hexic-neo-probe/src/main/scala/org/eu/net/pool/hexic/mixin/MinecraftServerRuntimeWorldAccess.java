package org.eu.net.pool.hexic.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.Executor;

@Mixin(MinecraftServer.class)
public interface MinecraftServerRuntimeWorldAccess {
    @Accessor("executor")
    Executor hexic$getExecutor();

    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess hexic$getStorageSource();
}
