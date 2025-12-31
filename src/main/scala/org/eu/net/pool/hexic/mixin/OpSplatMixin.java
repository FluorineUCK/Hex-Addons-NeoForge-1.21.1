package org.eu.net.pool.hexic.mixin;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.common.casting.actions.lists.OpSplat;
import net.minecraft.text.Text;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(OpSplat.class)
public class OpSplatMixin {
    @WrapMethod(method = "execute")
    List<Iota> wrapExecute(List<Iota> args, CastingEnvironment env, Operation<List<Iota>> original) throws Throwable {
        return org.eu.net.pool.hexic.Extern$.MODULE$.splat((args1, env1) -> original.call(args1, env1), args, env);
    }
}
