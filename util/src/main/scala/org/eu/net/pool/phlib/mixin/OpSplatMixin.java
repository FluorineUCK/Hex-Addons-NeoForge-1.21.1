package org.eu.net.pool.phlib.mixin;

import at.petrak.hexcasting.api.casting.SpellList;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.common.casting.actions.lists.OpSplat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.eu.net.pool.phlib.MapIota;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

// 2026-03-20 01:29 pool: this is *very* out-of-scope for phlib
@Mixin(OpSplat.class)
public class OpSplatMixin {
    @WrapOperation(at = @At(value = "INVOKE", target = "Lat/petrak/hexcasting/api/casting/OperatorUtils;getList(Ljava/util/List;II)Lat/petrak/hexcasting/api/casting/SpellList;"), method = "execute")
    SpellList redirectList(List<? extends Iota> stack, int it, int x, Operation<SpellList> original) {
        if (stack.get(it) instanceof MapIota map) {
            return new ListIota(map.toList()).getList();
        } else {
            return original.call(stack, it, x);
        }
    }
}
