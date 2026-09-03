package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.common.casting.actions.spells.OpEdifySapling;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.eu.net.pool.hexic.Mediaweave;
import org.eu.net.pool.hexic.Stringworm;
import org.eu.net.pool.hexic.ducks.EdifySpellDuck;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restores Hexic's material variants of Edification:
 * wool -> matching mediaweave, carpet -> more matching mediaweave,
 * tripwire -> a random stringworm.
 */
@Mixin(OpEdifySapling.class)
public abstract class OpEdifySaplingMixin {
    private static final int HEXIC_NORMAL_SAPLING = 0;
    private static final int HEXIC_WOOL = 2;
    private static final int HEXIC_CARPET = 3;
    private static final int HEXIC_TRIPWIRE = -1;

    @WrapOperation(
        method = "execute",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z"
        )
    )
    private boolean hexic$acceptEdifyMaterial(
        BlockState state,
        TagKey<Block> requestedTag,
        Operation<Boolean> original,
        @Share("hexic$edifyMode") LocalIntRef mode
    ) {
        if (original.call(state, requestedTag)) {
            mode.set(HEXIC_NORMAL_SAPLING);
            return true;
        }
        if (original.call(state, BlockTags.WOOL)) {
            mode.set(HEXIC_WOOL);
            return true;
        }
        if (original.call(state, BlockTags.WOOL_CARPETS)) {
            mode.set(HEXIC_CARPET);
            return true;
        }
        if (state.is(Blocks.TRIPWIRE)) {
            mode.set(HEXIC_TRIPWIRE);
            return true;
        }
        return false;
    }

    @ModifyReturnValue(method = "execute", at = @At("RETURN"))
    private SpellAction.Result hexic$attachEdifyMode(
        SpellAction.Result original,
        @Share("hexic$edifyMode") LocalIntRef mode
    ) {
        if (mode.get() != HEXIC_NORMAL_SAPLING) {
            ((EdifySpellDuck) original.component1()).hexic$setEdifyMode(mode.get());
        }
        return original;
    }

    @Mixin(targets = "at.petrak.hexcasting.common.casting.actions.spells.OpEdifySapling$Spell")
    public abstract static class SpellMixin implements EdifySpellDuck {
        @Shadow
        @Final
        private BlockPos pos;

        @Unique
        private int hexic$edifyMode;

        @Override
        public void hexic$setEdifyMode(int mode) {
            this.hexic$edifyMode = mode;
        }

        @Override
        public int hexic$getEdifyMode() {
            return this.hexic$edifyMode;
        }

        @Inject(
            method = "cast(Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)V",
            at = @At("HEAD"),
            cancellable = true
        )
        private void hexic$castMaterialVariant(CastingEnvironment env, CallbackInfo callback) {
            if (this.hexic$edifyMode == HEXIC_NORMAL_SAPLING) {
                return;
            }

            ItemStack dropped;
            if (this.hexic$edifyMode == HEXIC_TRIPWIRE) {
                dropped = new ItemStack(Stringworm.randomFlavor(env.getWorld().getRandom()));
            } else {
                DyeColor color = hexic$findColor(env.getWorld().getBlockState(this.pos));
                if (color == null) {
                    return;
                }

                int count = Math.max(
                    env.getWorld().getRandom().nextIntBetweenInclusive(
                        this.hexic$edifyMode,
                        this.hexic$edifyMode * 3
                    ),
                    env.getWorld().getRandom().nextIntBetweenInclusive(
                        this.hexic$edifyMode,
                        this.hexic$edifyMode * 2
                    )
                );
                Item mediaweave = Mediaweave.colors().apply(color);
                dropped = new ItemStack(mediaweave, count);
            }

            env.getWorld().setBlockAndUpdate(this.pos, Blocks.AIR.defaultBlockState());
            Block.popResource(env.getWorld(), this.pos, dropped);
            callback.cancel();
        }

        @Unique
        private static DyeColor hexic$findColor(BlockState state) {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (blockId == null) {
                return null;
            }

            String path = blockId.getPath();
            for (DyeColor color : DyeColor.values()) {
                if (path.startsWith(color.getName() + "_")) {
                    return color;
                }
            }
            return null;
        }
    }
}
