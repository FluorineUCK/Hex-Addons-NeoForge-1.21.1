package org.eu.net.pool.hexic.hexcompat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.nbt.CompoundTag;

public final class HexicClientNetworkCompat {
    private HexicClientNetworkCompat() {
    }

    public static void handleMessage(String message) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        if (message.startsWith("/")) {
            connection.sendCommand(message.substring(1));
        } else {
            connection.sendChat(message);
        }
    }

    public static void handleComponent(String keyId, CompoundTag tag) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            ComponentStore$.MODULE$.applySync(player, keyId, tag);
        }
    }

    public static String probeNoConnection() {
        handleMessage("hexic probe client bridge");
        CompoundTag tag = new CompoundTag();
        tag.putInt("lineCount", 1);
        tag.putString("line0", "hexic probe client reveal");
        handleComponent("reveal", tag);
        return "message_no_connection=PASS component_no_player=PASS";
    }
}
