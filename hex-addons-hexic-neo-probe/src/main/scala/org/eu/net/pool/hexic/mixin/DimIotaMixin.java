package org.eu.net.pool.hexic.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.beholderface.oneironaut.casting.iotatypes.DimIota;
import net.minecraft.network.chat.Component;
import org.eu.net.pool.hexic.Extern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Gives generated Hexic demiplanes their original deterministic display name. */
@Pseudo
@Mixin(DimIota.class)
public abstract class DimIotaMixin {
    @Shadow
    public abstract String getDimString();

    @ModifyReturnValue(method = "display", at = @At("RETURN"))
    private Component hexic$nameDemiplane(Component original) {
        String dimension = getDimString();
        if (!dimension.startsWith("hexic:fresh-")) {
            return original;
        }
        return Extern.getPocketName(dimension)
                .setStyle(original.getStyle());
    }
}
