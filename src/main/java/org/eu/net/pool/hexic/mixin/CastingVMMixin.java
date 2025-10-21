package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.ExecutionClientView;
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import kotlin.Pair;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.dynamic.Codecs;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

import static net.minecraft.item.Items.ECHO_SHARD;

@Mixin(value = CastingVM.class, remap = false)
public abstract class CastingVMMixin {
    @Shadow private @Final CastingEnvironment env;
    abstract @Shadow public Pair<List<NbtCompound>, @Nullable NbtCompound> generateDescs();

    @WrapMethod(method = "queueExecuteAndWrapIotas")
    ExecutionClientView hookForSculkShardStorage(List<? extends Iota> iotas, ServerWorld world, Operation<ExecutionClientView> original) {
        if (env.getCastingEntity() instanceof ServerPlayerEntity p) {
            ItemStack stack = p.getStackInHand(env.getOtherHand());
            if (stack.isOf(ECHO_SHARD)) {
                NbtCompound tag = stack.getOrCreateNbt();
                NbtList queuedPatterns = tag.getList("hexic:memory", NbtElement.COMPOUND_TYPE);
                for (Iota i: iotas)
                    queuedPatterns.add(IotaType.serialize(i));
                tag.put("hexic:memory", queuedPatterns);
                p.playSound(SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, SoundCategory.PLAYERS, 1.0f, 1.0f);
                var descs = generateDescs();
                return new ExecutionClientView(false, ResolvedPatternType.valueOf("HEXIC$ECHO_SHARD_ABSORBED"), descs.getFirst(), descs.getSecond());
            }
        }
        return original.call(iotas, world);
    }
}
