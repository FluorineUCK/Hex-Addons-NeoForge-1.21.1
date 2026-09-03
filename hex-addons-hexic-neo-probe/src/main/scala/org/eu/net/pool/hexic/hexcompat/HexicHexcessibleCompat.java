package org.eu.net.pool.hexic.hexcompat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eu.net.pool.hexic.MacroDefinition;
import org.eu.net.pool.hexic.macros$package;

import at.petrak.hexcasting.api.casting.math.HexAngle;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import dev.tizu.hexcessible.entries.BookEntries;
import dev.tizu.hexcessible.entries.PatternEntries;
import dev.tizu.hexcessible.smartsig.SmartSig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import scala.Option;
import scala.Tuple2;

/**
 * Optional Hexcessible bridge for uniquely named equipment macros.
 *
 * <p>The caller must only link this class when Hexcessible is loaded.</p>
 */
public final class HexicHexcessibleCompat {
    private static final EquipmentMacroSmartSig SMART_SIG = new EquipmentMacroSmartSig();

    private HexicHexcessibleCompat() {
    }

    public static synchronized void register() {
        SmartSig.SmartSigRegistry.register(SMART_SIG);
    }

    /**
     * Verifies registry attachment, entry conversion, signature lookup, and the
     * ambiguity rule with synthetic macro data.
     */
    public static String probe() {
        if (!SmartSig.SmartSigRegistry.isRegistered(SMART_SIG))
            throw new IllegalStateException("Hexic equipment-macro SmartSig is not registered");

        var pattern = HexPattern.fromAnglesUnchecked("qwe", HexDir.NORTH_EAST);
        var definition = new MacroDefinition(pattern, Option.apply("hexic-probe"), new CompoundTag());
        var source = new MacroSource(
                ResourceLocation.fromNamespaceAndPath("minecraft", "diamond_chestplate"),
                definition);

        var queryEntries = EquipmentMacroSmartSig.entriesForQuery(List.of(source));
        var signatureEntry = EquipmentMacroSmartSig.entryForSignature(List.of(source), pattern.getAngles());
        var ambiguousEntries = EquipmentMacroSmartSig.entriesForQuery(List.of(source, source));
        var ambiguousSignature = EquipmentMacroSmartSig.entryForSignature(
                List.of(source, source), pattern.getAngles());

        if (queryEntries.size() != 1
                || signatureEntry == null
                || !"hexic-probe".equals(signatureEntry.rawName())
                || !ambiguousEntries.isEmpty()
                || ambiguousSignature != null) {
            throw new IllegalStateException(
                    "Equipment-macro SmartSig failed conversion or ambiguity semantics");
        }

        return "registered=true named=1 signature=1 ambiguous=0";
    }

    private record MacroSource(ResourceLocation itemId, MacroDefinition definition) {
    }

    private static final class EquipmentMacroSmartSig implements SmartSig {
        @Override
        public List<PatternEntries.Entry> get(String query) {
            var player = Minecraft.getInstance().player;
            return player == null ? List.of() : entriesForQuery(collect(player));
        }

        @Override
        public PatternEntries.Entry get(List<HexAngle> signature) {
            var player = Minecraft.getInstance().player;
            return player == null ? null : entryForSignature(collect(player), signature);
        }

        private static List<MacroSource> collect(Player player) {
            var result = new ArrayList<MacroSource>();
            var iterator = macros$package.getMacros(player).iterator();
            while (iterator.hasNext()) {
                Tuple2<ItemStack, MacroDefinition> macro = iterator.next();
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(macro._1().getItem());
                if (itemId != null)
                    result.add(new MacroSource(itemId, macro._2()));
            }
            return result;
        }

        private static Map<String, List<MacroSource>> bySignature(List<MacroSource> macros) {
            var result = new LinkedHashMap<String, List<MacroSource>>();
            for (var macro : macros) {
                result.computeIfAbsent(
                        macro.definition().pattern().anglesSignature(),
                        ignored -> new ArrayList<>()).add(macro);
            }
            return result;
        }

        private static List<PatternEntries.Entry> entriesForQuery(List<MacroSource> macros) {
            var result = new ArrayList<PatternEntries.Entry>();
            for (var sameSignature : bySignature(macros).values()) {
                if (sameSignature.size() != 1)
                    continue;
                var entry = toEntry(sameSignature.getFirst());
                if (entry != null)
                    result.add(entry);
            }
            return result;
        }

        private static PatternEntries.Entry entryForSignature(
                List<MacroSource> macros, List<HexAngle> signature) {
            var sameSignature = bySignature(macros).values().stream()
                    .filter(group -> group.getFirst().definition().pattern().getAngles().equals(signature))
                    .findFirst()
                    .orElse(List.of());
            return sameSignature.size() == 1 ? toEntry(sameSignature.getFirst()) : null;
        }

        private static PatternEntries.Entry toEntry(MacroSource source) {
            var definition = source.definition();
            if (definition.name().isEmpty())
                return null;

            var pattern = definition.pattern();
            var itemId = source.itemId();
            return new PatternEntries.Entry(
                    "hexic/equipment_macro/" + itemId.getNamespace() + "/"
                            + itemId.getPath() + "/" + pattern.anglesSignature(),
                    definition.name().get(),
                    () -> false,
                    pattern.getStartDir(),
                    List.of(pattern.getAngles()),
                    List.<BookEntries.Entry>of(),
                    0);
        }
    }
}
