package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CastingEnvironment.class)
public abstract class CastingEnvironmentMixin {
    @Shadow public abstract ServerWorld getWorld();

    @Inject(at = @At("HEAD"), method = "isVecInWorld", cancellable = true)
    void modifyVecInWorld(Vec3d vec, CallbackInfoReturnable<Boolean> cir) {
        var id = getWorld().getRegistryKey().getValue();
        if (id.getNamespace().equals("hexic") && id.getPath().startsWith("fresh-")) {
            cir.setReturnValue(vec.x >= 1 && vec.y >= 1 && vec.z >= 1 && vec.x < 10 && vec.y < 11 && vec.z < 11);
        }
    }
}
