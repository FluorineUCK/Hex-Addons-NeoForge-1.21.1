package org.net.eu.pool.mica.mixin;

import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import org.net.eu.pool.mica.JackBlack$;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import scala.runtime.BoxedUnit;
import scala.util.boundary;
import scala.util.boundary$;

@Mixin(FlintAndSteelItem.class)
public class FlintAndSteelItemMixin {
	@Inject(method = {"useOnBlock", "method_7884"}, at = @At("HEAD"), cancellable = true)
	void startRunes(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
		JackBlack$.MODULE$.cir2label(label -> {
			JackBlack$.MODULE$.flintAndSTEEL_$bang$bang(label, context);
			return BoxedUnit.UNIT;
		}, cir);
	}
}
