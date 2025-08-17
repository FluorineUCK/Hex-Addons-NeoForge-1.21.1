package org.net.eu.pool.mica.mixin;

import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import org.net.eu.pool.mica.JackBlack$;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlintAndSteelItem.class)
public class FlintAndSteelItemMixin {
    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    void startRunes(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        JackBlack$.MODULE$.flintAndSTEEL_$bang$bang(cir, context);
    }
}
