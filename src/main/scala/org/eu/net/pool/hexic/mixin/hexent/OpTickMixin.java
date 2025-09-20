package org.eu.net.pool.hexic.mixin.hexent;

import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.mishaps.Mishap;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.eu.net.pool.hexic.Interop;
import org.eu.net.pool.hexic.cfg;
import org.eu.net.pool.hexic.cfg$;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ram.talia.hexal.common.casting.actions.spells.great.OpTick;
import scala.util.CommandLineParser;

import java.util.List;

@Pseudo
@Mixin(OpTick.class)
public class OpTickMixin {
    @WrapOperation(method = "executeWithUserdata", at = @At(value = "INVOKE", target = "Lat/petrak/hexcasting/api/casting/OperatorUtils;getBlockPos(Ljava/util/List;II)Lnet/minecraft/util/math/BlockPos;"))
    BlockPos wrapGetTarget(List<? extends Iota> args, int idx, int argc, Operation<BlockPos> original) throws Mishap {
        if (cfg.modFlag("hexent", "hexal.accelerateEntities")) {
            try {
                //noinspection ConstantValue
                if (false) throw new MishapInvalidIota(null, 0, null);
                return original.call(args, idx, argc);
            } catch (MishapInvalidIota i) {
                try {
                    //noinspection ConstantValue
                    if (false) throw new MishapInvalidIota(null, 0, null);
                    return new Interop(OperatorUtils.getEntity(args, idx, argc));
                } catch (MishapInvalidIota j) {
                    assert i.getPerpetrator() == j.getPerpetrator();
                    assert i.getReverseIdx() == j.getReverseIdx();
                    throw MishapInvalidIota.ofType(i.getPerpetrator(), i.getReverseIdx(), "vector_or_entity");
                }
            }
        } else {
            return original.call(args, idx, argc);
        }
    }
    @WrapOperation(method = "executeWithUserdata", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/NbtCompound;getInt(Ljava/lang/String;)I"))
    int updateCost(NbtCompound instance, String key, Operation<Integer> original) {
        int orig = original.call(instance, key);
        if (cfg.modFlag("hexent", "hexal.fixAccelerateCost")) {
            instance.putInt(key, orig + 1);
        }
        return orig;
    }
    @Mixin(targets = "ram/talia/hexal/common/casting/actions/spells/great/OpTick$Spell")
    public static class SpellMixin {
        @Shadow @Final private BlockPos pos;
        @Inject(method = "cast(Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;Lat/petrak/hexcasting/api/casting/eval/vm/CastingImage;)Lat/petrak/hexcasting/api/casting/eval/vm/CastingImage;",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;getBlockEntity(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/entity/BlockEntity;"),
                cancellable = true)
        void noBlockEntityForYou(CastingEnvironment env, CastingImage image, CallbackInfoReturnable<CastingImage> cir, @Local(ordinal = 1) CastingImage newImage) {
            if (pos instanceof Interop w) {
                w.e.tick();
                cir.setReturnValue(newImage);
            }
        }
    }
}
