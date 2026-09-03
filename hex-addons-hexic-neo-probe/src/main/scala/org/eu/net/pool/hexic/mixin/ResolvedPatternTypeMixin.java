package org.eu.net.pool.hexic.mixin;

import net.minecraft.world.item.DyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;

/**
 * Restores the ResolvedPatternType values that Hexic's Fabric early-riser
 * originally added with ClassTinkerers.
 *
 * <p>Injecting into the enum's synthetic {@code $values()} method keeps both
 * {@code $VALUES} and Kotlin's {@code $ENTRIES} in sync. The constructor
 * lookup executes as a method merged into ResolvedPatternType itself, so it
 * retains private-constructor access without opening Hex Casting's module or
 * shipping a Fabric enum-extension dependency.</p>
 */
@Mixin(targets = "at.petrak.hexcasting.api.casting.eval.ResolvedPatternType", remap = false)
public abstract class ResolvedPatternTypeMixin {
    @Unique
    private static final String HEXIC_ECHO_TYPE = "HEXIC$ECHO_SHARD_ABSORBED";

    @Inject(method = "$values", at = @At("RETURN"), cancellable = true, remap = false)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void hexic$appendResolvedPatternTypes(
        CallbackInfoReturnable callback
    ) {
        Object[] original = (Object[]) callback.getReturnValue();
        for (Object value : original) {
            if (((Enum<?>) value).name().equals(HEXIC_ECHO_TYPE)) {
                return;
            }
        }

        DyeColor[] colors = DyeColor.values();
        Object[] extended = Arrays.copyOf(original, original.length + 1 + colors.length);
        int ordinal = original.length;
        extended[ordinal] = hexic$newResolvedPatternType(
            HEXIC_ECHO_TYPE,
            ordinal,
            0x0a5060,
            0x29dfeb,
            true
        );
        ordinal++;

        for (DyeColor color : colors) {
            extended[ordinal] = hexic$newResolvedPatternType(
                "HEXIC$PEN_WITH_COLOR_" + color.getName(),
                ordinal,
                color.getMapColor().col,
                color.getTextColor(),
                true
            );
            ordinal++;
        }
        callback.setReturnValue(extended);
    }

    @Unique
    private static Object hexic$newResolvedPatternType(
        String name,
        int ordinal,
        int color,
        int fadeColor,
        boolean success
    ) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            var constructor = lookup.findConstructor(
                lookup.lookupClass(),
                MethodType.methodType(
                    void.class,
                    String.class,
                    int.class,
                    int.class,
                    int.class,
                    boolean.class
                )
            );
            return constructor.invoke(
                name,
                ordinal,
                color,
                fadeColor,
                success
            );
        } catch (Throwable failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}
