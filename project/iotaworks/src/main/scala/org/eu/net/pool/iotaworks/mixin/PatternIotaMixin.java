package org.eu.net.pool.iotaworks.mixin;

import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.text.Text;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import org.eu.net.pool.iotaworks.Extern;
import scala.collection.mutable.StringBuilder;

import org.eu.net.pool.iotaworks.HexPatternAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PatternIota.class)
public class PatternIotaMixin {
    @WrapMethod(method = "display(Lat/petrak/hexcasting/api/casting/math/HexPattern;)V")
    private static Text wrappedDisplay(HexPattern pat, Operation<Text> original) {
        int level = ((HexPatternAccessor) (Object) pat).depth();
        StringBuilder buf = new StringBuilder();
        boolean negative = level < 0;
        if (negative) level *= -1;
        while (level > 0) {
            switch (level % 10) {
                case 0: buf.addOne('⁰'); break;
                case 1: buf.addOne('¹'); break;
                case 2: buf.addOne('²'); break;
                case 3: buf.addOne('³'); break;
                case 4: buf.addOne('⁴'); break;
                case 5: buf.addOne('⁵'); break;
                case 6: buf.addOne('⁶'); break;
                case 7: buf.addOne('⁷'); break;
                case 8: buf.addOne('⁸'); break;
                case 9: buf.addOne('⁹'); break;
            }
            level /= 10;
        }
        if (negative) buf.addOne('⁻');
        return ((MutableText) original.call(pat)).append(buf.reverse().toString());
    }

    @WrapMethod(method = "execute")
    CastResult wrappedExecute(CastingVM vm, ServerWorld world, SpellContinuation continuation, Operation<CastResult> original) {
        return Extern.handleExecute((PatternIota) (Object) this, vm, world, continuation, original::call);
    }
}