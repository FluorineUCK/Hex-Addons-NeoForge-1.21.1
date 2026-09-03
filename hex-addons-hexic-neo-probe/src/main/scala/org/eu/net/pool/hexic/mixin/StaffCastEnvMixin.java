package org.eu.net.pool.hexic.mixin;

import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.common.msgs.MsgNewSpellPatternC2S;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import org.eu.net.pool.hexic.PenAccess;
import org.eu.net.pool.hexic.hexcompat.StaffCastCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Restores Hexic's staff-drawing interception for crystal pens and echo
 * shards at the same production seam used by Hex Casting pre-2.
 */
@Mixin(StaffCastEnv.class)
public abstract class StaffCastEnvMixin implements PenAccess {
    @Unique
    private Map<DyeColor, List<HexPattern>> hexic$penArt;

    @Override
    public List<HexPattern> getPen(DyeColor color) {
        if (hexic$penArt == null) {
            hexic$penArt = new EnumMap<>(DyeColor.class);
        }
        return hexic$penArt.computeIfAbsent(color, ignored -> new ArrayList<>());
    }

    @Inject(
        method = "handleNewPatternOnServer",
        at = @At(
            value = "INVOKE",
            target = "Lat/petrak/hexcasting/api/casting/eval/vm/CastingVM;queueExecuteAndWrapIota(Lat/petrak/hexcasting/api/casting/iota/Iota;Lnet/minecraft/server/level/ServerLevel;)Lat/petrak/hexcasting/api/casting/eval/ExecutionClientView;"
        ),
        cancellable = true
    )
    private static void hexic$interceptStaffPattern(
        ServerPlayer sender,
        MsgNewSpellPatternC2S msg,
        CallbackInfo callback,
        @Local CastingVM vm
    ) {
        if (StaffCastCompat.intercept(sender, msg, vm)) {
            callback.cancel();
        }
    }
}
