package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.ExecutionClientView;
import at.petrak.hexcasting.api.casting.eval.ResolvedPattern;
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.common.msgs.MsgNewSpellPatternC2S;
import at.petrak.hexcasting.common.msgs.MsgNewSpellPatternS2C;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import kotlin.Pair;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

import static net.minecraft.item.Items.ECHO_SHARD;

@Mixin(value = StaffCastEnv.class, remap = false)
public abstract class StaffCastEnvMixin {
    @Inject(method = "handleNewPatternOnServer", at = @At(value = "INVOKE", target = "Lat/petrak/hexcasting/api/casting/eval/vm/CastingVM;queueExecuteAndWrapIota(Lat/petrak/hexcasting/api/casting/iota/Iota;Lnet/minecraft/server/world/ServerWorld;)Lat/petrak/hexcasting/api/casting/eval/ExecutionClientView;"))
    private static void handleNewPattern(ServerPlayerEntity sender, MsgNewSpellPatternC2S msg, CallbackInfo ci, @Local CastingVM vm) {
        ItemStack stack = sender.getStackInHand(vm.getEnv().getOtherHand());
        if (stack.isOf(ECHO_SHARD)) {
            NbtCompound tag = stack.getOrCreateNbt();
            NbtList queuedPatterns = tag.getList("hexic:memory", NbtElement.COMPOUND_TYPE);
            queuedPatterns.add(IotaType.serialize(new PatternIota(msg.pattern())));
            tag.put("hexic:memory", queuedPatterns);
            sender.playSound(SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, SoundCategory.PLAYERS, 1.0f, 1.0f);
            final var descs = vm.generateDescs();
            IXplatAbstractions.INSTANCE.setStaffcastImage(sender, vm.getImage().withOverriddenUsedOps(0));
            final var resolvedPatterns = msg.resolvedPatterns();
            final var resolution = ResolvedPatternType.valueOf("HEXIC$ECHO_SHARD_ABSORBED");
            IXplatAbstractions.INSTANCE.sendPacketToPlayer(sender, new MsgNewSpellPatternS2C(new ExecutionClientView(false, resolution, descs.getFirst(), descs.getSecond()), resolvedPatterns.size() - 1));
            if (!resolvedPatterns.isEmpty()) {
                resolvedPatterns.get(resolvedPatterns.size() - 1).setType(resolution);
            }
            IXplatAbstractions.INSTANCE.setPatterns(sender, resolvedPatterns);
            ci.cancel();
        }
    }
}
