package org.net.eu.pool.mica.mixin;

import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.ActionResult;
import org.net.eu.pool.mica.JackBlack;
import org.net.eu.pool.mica.JackBlack$;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.item.Item.class)
public class ItemMixin {
    @Shadow @Final private RegistryEntry.Reference<Item> registryEntry;

    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    void useOnBlock(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (registryEntry.isIn(ItemTags.PICKAXES)) {
            JackBlack$.MODULE$.pickaxe(cir, context);
        }
    }
}
