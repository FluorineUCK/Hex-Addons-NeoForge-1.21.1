package org.eu.net.pool.hexic.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.EmptyChunk;
import org.eu.net.pool.hexic.Extern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import scala.ref.ReferenceQueue;
import scala.ref.WeakReference;

@Mixin(World.class)
public abstract class WorldMixin {
    @Shadow public abstract RegistryKey<World> getRegistryKey();

    @Shadow public abstract DynamicRegistryManager getRegistryManager();

    @Shadow public abstract BiomeAccess getBiomeAccess();

    @Inject(at = @At("TAIL"), method = "<init>")
    void postConstruct(CallbackInfo ci) {
        Extern.worlds().$plus$eq(new WeakReference(this));
    }

    @Inject(at = @At("HEAD"), method = "getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/Chunk;", cancellable = true)
    void preGetChunk(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create, CallbackInfoReturnable<Chunk> cir) {
        if (getRegistryKey().getValue().getNamespace().equals("hexic") && getRegistryKey().getValue().getPath().startsWith("fresh-") && (chunkX != 0 || chunkZ != 0)) {
            cir.setReturnValue(new EmptyChunk((World) (Object) this, new ChunkPos(chunkX, chunkZ), getRegistryManager().get(RegistryKeys.BIOME).getEntry(RegistryKey.of(RegistryKeys.BIOME,  Identifier.of("minecraft", "the_void"))).get()));
        }
    }
}
