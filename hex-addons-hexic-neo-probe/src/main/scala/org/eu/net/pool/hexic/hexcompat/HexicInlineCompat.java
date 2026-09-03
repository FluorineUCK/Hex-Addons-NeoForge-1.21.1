package org.eu.net.pool.hexic.hexcompat;

import java.util.regex.MatchResult;
import java.util.regex.Matcher;

import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.interop.inline.InlinePatternData;
import com.samsthenerd.inline.api.client.InlineClientAPI;
import com.samsthenerd.inline.api.matching.InlineMatch;
import com.samsthenerd.inline.api.matching.InlineMatcher;
import com.samsthenerd.inline.api.matching.MatcherInfo;
import com.samsthenerd.inline.api.matching.RegexMatcher;
import net.minecraft.resources.ResourceLocation;

/**
 * Restores Hexic's Scala-style inline pattern literal matcher on the 1.21.1
 * Inline API.
 */
public final class HexicInlineCompat {
    private static final ResourceLocation MATCHER_ID =
            ResourceLocation.fromNamespaceAndPath("hexic", "scala_pattern");
    private static final String SCALA_PATTERN_REGEX = "([ns]?[ew])\"([qweasd]*)\"";

    private HexicInlineCompat() {
    }

    public static synchronized void register() {
        if (InlineClientAPI.INSTANCE.getMatcher(MATCHER_ID) != null)
            return;

        InlineClientAPI.INSTANCE.addMatcher(new RegexMatcher.Simple(
                SCALA_PATTERN_REGEX,
                MATCHER_ID,
                HexicInlineCompat::createMatch,
                MatcherInfo.fromId(MATCHER_ID)));
    }

    private static InlineMatch createMatch(MatchResult match) {
        var direction = switch (match.group(1)) {
            case "ne" -> HexDir.NORTH_EAST;
            case "e" -> HexDir.EAST;
            case "se" -> HexDir.SOUTH_EAST;
            case "sw" -> HexDir.SOUTH_WEST;
            case "w" -> HexDir.WEST;
            case "nw" -> HexDir.NORTH_WEST;
            default -> throw new IllegalArgumentException("Unknown Hexic pattern direction: " + match.group(1));
        };
        return new InlineMatch.DataMatch(new InlinePatternData(
                HexPattern.fromAnglesUnchecked(match.group(2), direction)));
    }

    /**
     * Exercises the registered matcher rather than only checking that setup ran.
     */
    public static String probe() {
        InlineMatcher registered = InlineClientAPI.INSTANCE.getMatcher(MATCHER_ID);
        if (!(registered instanceof RegexMatcher regexMatcher))
            throw new IllegalStateException("Matcher is missing or has the wrong type: " + registered);

        Matcher matcher = regexMatcher.getRegex().matcher("ne\"qwe\"");
        if (!matcher.matches())
            throw new IllegalStateException("Scala-pattern regex rejected a valid literal");

        InlineMatch rendered = regexMatcher.getMatch(matcher.toMatchResult(), null);
        if (!(rendered instanceof InlineMatch.DataMatch dataMatch)
                || !(dataMatch.data instanceof InlinePatternData patternData)
                || patternData.pattern.getStartDir() != HexDir.NORTH_EAST
                || !"qwe".equals(patternData.pattern.anglesSignature())) {
            throw new IllegalStateException("Scala-pattern matcher produced the wrong InlinePatternData");
        }

        return "id=" + MATCHER_ID + " literal=ne\"qwe\" signature=qwe";
    }
}
