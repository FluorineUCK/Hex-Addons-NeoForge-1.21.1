package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.eval.ExecutionClientView;
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.common.msgs.MsgNewSpellPatternC2S;
import at.petrak.hexcasting.common.msgs.MsgNewSpellPatternS2C;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.DyeColor;
import org.eu.net.pool.hexic.Pen;
import org.eu.net.pool.hexic.PenAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.minecraft.item.Items.ECHO_SHARD;

@Mixin(value = StaffCastEnv.class, remap = false)
public abstract class StaffCastEnvMixin implements PenAccess {
    @Unique
    private Map<DyeColor, List<HexPattern>> penArt;

    @SuppressWarnings("AddedMixinMembersNamePattern")
    @Override
    public List<HexPattern> getPen(DyeColor color) {
        return penArt.computeIfAbsent(color, c -> new ArrayList<>());
    }

    @Inject(method = "handleNewPatternOnServer", at = @At(value = "INVOKE", target = "Lat/petrak/hexcasting/api/casting/eval/vm/CastingVM;queueExecuteAndWrapIota(Lat/petrak/hexcasting/api/casting/iota/Iota;Lnet/minecraft/server/world/ServerWorld;)Lat/petrak/hexcasting/api/casting/eval/ExecutionClientView;"), cancellable = true)
    private static void handleNewPattern(ServerPlayerEntity sender, MsgNewSpellPatternC2S msg, CallbackInfo ci, @Local CastingVM vm) {
        // FIXME: this is probably complex enough to become Extern
        ItemStack ownStack = sender.getStackInHand(vm.getEnv().getCastingHand());
        if (ownStack.getItem() instanceof Pen p) {
            //noinspection DataFlowIssue
            ((StaffCastEnvMixin) (Object) vm.getEnv()).getPen(p.color()).add(msg.pattern());
            final var descs = vm.generateDescs();
            IXplatAbstractions.INSTANCE.setStaffcastImage(sender, vm.getImage().withOverriddenUsedOps(0));
            final var resolvedPatterns = msg.resolvedPatterns();
            final var resolution = ResolvedPatternType.valueOf("HEXIC$PEN_WITH_COLOR_" + p.color().asString());
            IXplatAbstractions.INSTANCE.sendPacketToPlayer(sender, new MsgNewSpellPatternS2C(new ExecutionClientView(false, resolution, descs.getFirst(), descs.getSecond()), resolvedPatterns.size() - 1));
            if (!resolvedPatterns.isEmpty()) {
                resolvedPatterns.get(resolvedPatterns.size() - 1).setType(resolution);
            }
            IXplatAbstractions.INSTANCE.setPatterns(sender, resolvedPatterns);
            ci.cancel();
            return;
        }
        // TODO: consider whether shards should intercept pen patterns
        ItemStack offhandStack = sender.getStackInHand(vm.getEnv().getOtherHand());
        if (offhandStack.isOf(ECHO_SHARD)) {
            NbtCompound tag = offhandStack.getOrCreateNbt();
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
