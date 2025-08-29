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
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import scala.runtime.BoxedUnit;
import scala.util.boundary;

@Mixin(net.minecraft.item.Item.class)
public abstract class ItemMixin {
    @Shadow @Deprecated public abstract RegistryEntry.Reference<Item> getRegistryEntry();

    @Inject(method = {"useOnBlock", "method_7884"}, at = @At("HEAD"), cancellable = true, remap = false)
    void useOnBlock(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (getRegistryEntry().isIn(ItemTags.PICKAXES)) {
            JackBlack$.MODULE$.cir2label(label -> {
                JackBlack$.MODULE$.pickaxe(label, context);
                return BoxedUnit.UNIT;
            }, cir);
        }
    }
}
