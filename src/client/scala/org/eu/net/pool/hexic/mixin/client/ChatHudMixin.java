package org.eu.net.pool.hexic.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.eu.net.pool.hexic.PlayerInfoComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import scala.reflect.ClassTag;

import java.util.ArrayList;
import java.util.List;

import static scala.jdk.javaapi.CollectionConverters.*;

@Mixin(ChatHud.class)
public class ChatHudMixin {
    @ModifyExpressionValue(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/hud/ChatHud;visibleMessages:Ljava/util/List;"))
    List<ChatHudLine.Visible> modifyVisibleMessages(List<ChatHudLine.Visible> original, @Local(ordinal = 0, argsOnly = true) int currentTick) {
        ArrayList ls = new ArrayList();
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p != null)
            for (Text t: asJava(p.getComponent(PlayerInfoComponent.key()).chatLines()))
                ls.add(0, new ChatHudLine.Visible(currentTick, t.asOrderedText(), MessageIndicator.system(), true));
        ls.addAll(original);
        return ls;
    }
}
