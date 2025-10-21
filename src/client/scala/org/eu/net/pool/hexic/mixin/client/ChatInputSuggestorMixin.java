package org.eu.net.pool.hexic.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import org.eu.net.pool.hexic.client.Hooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChatInputSuggestor.class)
public class ChatInputSuggestorMixin {
    @Shadow @Final TextFieldWidget textField;

    @WrapMethod(method = "provideRenderText")
    OrderedText provideRenderText(String string, int firstCharacterIndex, Operation<OrderedText> original) {
        return Hooks.provideRenderText(string, firstCharacterIndex, textField, original.call(string, firstCharacterIndex));
    }
}
