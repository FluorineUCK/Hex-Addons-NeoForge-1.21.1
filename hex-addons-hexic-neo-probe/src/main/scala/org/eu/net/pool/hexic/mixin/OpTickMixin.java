package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.eu.net.pool.hexic.Interop;
import org.eu.net.pool.hexic.cfg;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ram.talia.hexal.common.casting.actions.spells.great.OpTick;

import java.util.List;

/**
 * Restores Hexic's optional Hexal Accelerate extensions.
 *
 * <p>The two switches deliberately retain their original property names:
 * {@code hexic.hexal.accelerateEntities} and
 * {@code hexic.hexal.fixAccelerateCost}.</p>
 */
@Pseudo
@Mixin(OpTick.class)
public abstract class OpTickMixin {
    @WrapOperation(
            method = "executeWithUserdata",
            at = @At(
                    value = "INVOKE",
                    target = "Lat/petrak/hexcasting/api/casting/OperatorUtils;getBlockPos(Ljava/util/List;II)Lnet/minecraft/core/BlockPos;"
            )
    )
    private BlockPos hexic$acceptEntityTarget(
            List<? extends Iota> args,
            int idx,
            int argc,
            Operation<BlockPos> original,
            @Local(argsOnly = true) CastingEnvironment env
    ) {
        if (!cfg.flag("hexal.accelerateEntities")) {
            return original.call(args, idx, argc);
        }

        try {
            return original.call(args, idx, argc);
        } catch (MishapInvalidIota blockMishap) {
            try {
                return new Interop(OperatorUtils.getEntity(args, env.getWorld(), idx, argc));
            } catch (MishapInvalidIota entityMishap) {
                throw MishapInvalidIota.ofType(
                        blockMishap.getPerpetrator(),
                        blockMishap.getReverseIdx(),
                        "vector_or_entity"
                );
            }
        }
    }

    @WrapOperation(
            method = "executeWithUserdata",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/CompoundTag;getInt(Ljava/lang/String;)I"
            )
    )
    private int hexic$advanceAccelerateCostLedger(
            CompoundTag instance,
            String key,
            Operation<Integer> original
    ) {
        int previous = original.call(instance, key);
        if (cfg.flag("hexal.fixAccelerateCost")) {
            instance.putInt(key, previous + 1);
        }
        return previous;
    }

    @Pseudo
    @Mixin(targets = "ram.talia.hexal.common.casting.actions.spells.great.OpTick$Spell")
    public abstract static class SpellMixin {
        @Shadow
        @Final
        private BlockPos pos;

        @Inject(
                method = "cast(Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;Lat/petrak/hexcasting/api/casting/eval/vm/CastingImage;)Lat/petrak/hexcasting/api/casting/eval/vm/CastingImage;",
                at = @At("HEAD"),
                cancellable = true
        )
        private void hexic$tickEntityInsteadOfBlock(
                CastingEnvironment env,
                CastingImage image,
                CallbackInfoReturnable<CastingImage> cir
        ) {
            if (pos instanceof Interop entityTarget) {
                CompoundTag userData = image.getUserData().copy();
                CompoundTag timesTicked =
                        userData.getCompound(OpTick.TAG_TIMES_TICKED);
                String ledgerKey = pos.toShortString();
                timesTicked.putInt(
                        ledgerKey,
                        timesTicked.getInt(ledgerKey) + 1
                );
                userData.put(OpTick.TAG_TIMES_TICKED, timesTicked);
                CastingImage newImage = new CastingImage(
                        image.getStack(),
                        image.getParenCount(),
                        image.getParenthesized(),
                        image.getEscapeNext(),
                        image.getSimulateNext(),
                        image.getOpsConsumed(),
                        userData
                );
                entityTarget.e.tick();
                cir.setReturnValue(newImage);
            }
        }
    }
}
