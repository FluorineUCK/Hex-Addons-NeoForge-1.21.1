package org.eu.net.pool.hexic.mixin;

import net.minecraft.world.World;
import org.eu.net.pool.hexic.Extern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import scala.ref.ReferenceQueue;
import scala.ref.WeakReference;

@Mixin(World.class)
public class WorldMixin {
    @Inject(at = @At("TAIL"), method = "<init>")
    void postConstruct(CallbackInfo ci) {
        Extern.worlds().$plus$eq(new WeakReference(this));
    }
}
