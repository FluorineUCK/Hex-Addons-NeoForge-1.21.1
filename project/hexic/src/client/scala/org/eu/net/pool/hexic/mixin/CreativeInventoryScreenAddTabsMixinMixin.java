package org.eu.net.pool.hexic.mixin;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.item.ItemGroup;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.eu.net.pool.hexic.Interop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value=CreativeInventoryScreen.class, priority=1050)
public class CreativeInventoryScreenAddTabsMixinMixin {
    @Shadow private static ItemGroup selectedTab;

    @ModifyArg(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)I", ordinal = 0))
    @TargetHandler(mixin = "de.dafuqs.fractal.mixin.client.CreativeInventoryScreenAddTabsMixin", name = "fractal$render")
    Text modifyDisplayName(Text text, @Local LocalRef<ItemGroup> child) {
        if (child.get() != null && child.get().getDisplayName().getContent() instanceof TranslatableTextContent ttc && ttc.getKey().startsWith("itemGroup.hexic.sub.")) {
            child.set(null);
            return Text.translatable(ttc.getKey() + ".tab");
        } else {
            return text;
        }
    }
}
