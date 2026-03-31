package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.common.casting.actions.spells.OpEdifySapling;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DyedCarpetBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import org.eu.net.pool.hexic.Mediaweave$;
import org.eu.net.pool.hexic.Stringworm;
import org.eu.net.pool.hexic.duck.OpEdifySapling$SpellAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OpEdifySapling.class)
public abstract class OpEdifySaplingMixin {
    @WrapOperation(method = "execute", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;isIn(Lnet/minecraft/registry/tag/TagKey;)Z"))
    boolean isIn(BlockState instance, TagKey tagKey, Operation<Boolean> original, @Share("mediaweave") LocalIntRef mediaweave) {
        if (original.call(instance, tagKey)){
            mediaweave.set(0);
            return true;
        }
        if (original.call(instance, BlockTags.WOOL)) {
            mediaweave.set(3);
            return true;
        }
        if (original.call(instance, BlockTags.WOOL_CARPETS)) {
            mediaweave.set(2);
            return true;
        }
        if (instance.isOf(Blocks.TRIPWIRE)) {
            mediaweave.set(-1);
            return true;
        }
        return false;
    }

    @ModifyReturnValue(method = "execute", at = @At("RETURN"))
    SpellAction.Result execute(SpellAction.Result original, @Share("mediaweave") LocalIntRef mediaweave) {
        if (mediaweave.get() != 0) ((OpEdifySapling$SpellAccess) original.component1()).hexic$setMediaweave(mediaweave.get());
        return original;
    }

    @Mixin(targets = "at.petrak.hexcasting.common.casting.actions.spells.OpEdifySapling$Spell")
    public abstract static class Spell implements RenderedSpell, OpEdifySapling$SpellAccess {
        @Shadow @Final private BlockPos pos;

        @Unique
        int hexic$mediaweave;

        @Override
        public void hexic$setMediaweave(int value) {
            hexic$mediaweave = value;
        }

        @Inject(method = "cast(Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)V", at = @At("HEAD"), cancellable = true)
        void preCast(CastingEnvironment env, CallbackInfo ci) {
            if (hexic$mediaweave != 0) {
                ItemStack droppedStack;
                a: if (hexic$mediaweave == -1) {
                    droppedStack = new ItemStack(Stringworm.randomFlavor(env.getWorld().getRandom()));
                } else {
                    BlockState state = env.getWorld().getBlockState(pos);
                    String id = Registries.BLOCK.getId(state.getBlock()).getPath();
                    for (DyeColor color : DyeColor.values()) {
                        if (id.startsWith(color.asString())) {
                            int count = Math.max(
                                env.getWorld().getRandom().nextBetween(hexic$mediaweave, hexic$mediaweave * 3),
                                env.getWorld().getRandom().nextBetween(hexic$mediaweave, hexic$mediaweave * 2)
                            );
                            droppedStack = new ItemStack(Mediaweave$.MODULE$.colors().apply(color), count);
                            break a;
                        }
                    }
                    return;
                }
                env.getWorld().setBlockState(pos, Blocks.AIR.getDefaultState());
                Block.dropStack(env.getWorld(), pos, droppedStack);
                ci.cancel();
            }
        }
    }
}
