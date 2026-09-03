package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CastingEnvironment.class)
public abstract class CastingEnvironmentMixin {
    @Shadow public abstract ServerLevel getWorld();

    @Inject(at = @At("HEAD"), method = "isVecInWorld", cancellable = true)
    void modifyVecInWorld(Vec3 vec, CallbackInfoReturnable<Boolean> cir) {
        var id = getWorld().dimension().location();
        if (id.getNamespace().equals("hexic") && id.getPath().startsWith("fresh-")) {
            cir.setReturnValue(vec.x >= 1 && vec.y >= 1 && vec.z >= 1 && vec.x < 10 && vec.y < 11 && vec.z < 11);
        }
    }
}
