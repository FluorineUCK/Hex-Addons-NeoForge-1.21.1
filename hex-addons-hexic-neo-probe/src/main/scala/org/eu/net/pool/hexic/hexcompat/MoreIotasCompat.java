package org.eu.net.pool.hexic.hexcompat;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.BitSet;
import java.util.function.IntSupplier;

/**
 * Isolates Hexic from MoreIotas' loader/config ABI. The integration remains
 * usable if MoreIotas moves its config value again: packet decoding then
 * safely falls back to the vanilla limit instead of linking a missing class.
 */
public final class MoreIotasCompat {
    public static final int VANILLA_CHAT_LIMIT = 256;
    public static final int MAX_NETWORK_CHAT_LIMIT = 32767;

    private static final Logger LOGGER = LoggerFactory.getLogger("hexic");
    private static volatile IntSupplier configuredLimit;

    private MoreIotasCompat() {
    }

    public static int configuredMaxStringSize() {
        IntSupplier supplier = configuredLimit;
        if (supplier == null) {
            synchronized (MoreIotasCompat.class) {
                supplier = configuredLimit;
                if (supplier == null) {
                    supplier = resolveConfiguredLimit();
                    configuredLimit = supplier;
                }
            }
        }

        try {
            return clamp(supplier.getAsInt(), 1, MAX_NETWORK_CHAT_LIMIT);
        } catch (Throwable failure) {
            LOGGER.warn("Unable to read MoreIotas maxStringSize; retaining vanilla chat limit", failure);
            return VANILLA_CHAT_LIMIT;
        }
    }

    public static int serverboundChatReadLimit(int originalLimit) {
        return Math.max(originalLimit, configuredMaxStringSize());
    }

    public static int serverboundChatWriteLimit(int originalLimit) {
        return Math.max(originalLimit, MAX_NETWORK_CHAT_LIMIT);
    }

    private static IntSupplier resolveConfiguredLimit() {
        if (!ModList.get().isLoaded("moreiotas")) {
            return () -> VANILLA_CHAT_LIMIT;
        }

        try {
            Class<?> configClass = Class.forName(
                "ram.talia.moreiotas.MoreIotasConfig",
                true,
                MoreIotasCompat.class.getClassLoader()
            );
            Field field = configClass.getField("maxStringSize");
            Object configValue = field.get(null);
            Method getter = configValue.getClass().getMethod("get");
            return () -> {
                try {
                    Object value = getter.invoke(configValue);
                    return value instanceof Number number
                        ? number.intValue()
                        : VANILLA_CHAT_LIMIT;
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException(failure);
                }
            };
        } catch (ReflectiveOperationException failure) {
            LOGGER.warn("MoreIotas is loaded but its maxStringSize config ABI was not found", failure);
            return () -> VANILLA_CHAT_LIMIT;
        }
    }

    /**
     * Exercises the transformed vanilla STREAM_CODEC. A message over 256
     * characters will fail decoding if the mixin did not apply.
     */
    public static ChatCodecProbe probeChatCodec() {
        int configured = configuredMaxStringSize();
        int readLimit = serverboundChatReadLimit(VANILLA_CHAT_LIMIT);
        int testLength = Math.min(readLimit, 1024);
        String message = "hexic".repeat((testLength + 4) / 5).substring(0, testLength);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        try {
            ServerboundChatPacket packet = new ServerboundChatPacket(
                message,
                Instant.EPOCH,
                0x48455849434cL,
                null,
                new LastSeenMessages.Update(0, new BitSet())
            );
            ServerboundChatPacket.STREAM_CODEC.encode(buffer, packet);
            int encodedBytes = buffer.readableBytes();
            ServerboundChatPacket decoded = ServerboundChatPacket.STREAM_CODEC.decode(buffer);
            boolean passed = decoded.message().equals(message)
                && decoded.message().length() == testLength
                && readLimit == Math.max(VANILLA_CHAT_LIMIT, configured);
            return new ChatCodecProbe(
                passed,
                configured,
                readLimit,
                testLength,
                encodedBytes,
                decoded.message().length(),
                ""
            );
        } catch (Throwable failure) {
            return new ChatCodecProbe(
                false,
                configured,
                readLimit,
                testLength,
                buffer.writerIndex(),
                -1,
                failure.getClass().getName() + ": " + String.valueOf(failure.getMessage())
            );
        } finally {
            buffer.release();
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    public record ChatCodecProbe(
        boolean passed,
        int configuredLimit,
        int readLimit,
        int testLength,
        int encodedBytes,
        int decodedLength,
        String failure
    ) {
    }
}
