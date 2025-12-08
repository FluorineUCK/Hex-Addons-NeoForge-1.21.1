package org.eu.net.pool.hexic.mixin;

import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.eu.net.pool.hexic.Extern;
import org.eu.net.pool.hexic.ServerInfoComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public class BiomeMixin {
    @Inject(at = @At("HEAD"), method = "getTemperature*", cancellable = true)
    void preGetTemperature(CallbackInfoReturnable<Float> ci) {
        World world = Extern.getWorld((Biome) (Object) this);
        if (world.getLevelProperties().getComponent(ServerInfoComponent.key()).endSnowTick() > world.getTime()) ci.setReturnValue(0.1f);
    }
}
