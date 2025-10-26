package org.eu.net.pool.hexic.mixin.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.impl.networking.client.ClientPlayNetworkAddon;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.eu.net.pool.hexic.PlayerInfoComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({InventoryScreen.class, CreativeInventoryScreen.class})
public abstract class InventoryScreenMixin extends AbstractInventoryScreen<ScreenHandler> {
    public InventoryScreenMixin(ScreenHandler screenHandler, PlayerInventory playerInventory, Text text) {
        super(screenHandler, playerInventory, text);
    }

    @Inject(method = "mouseClicked", at = @At(value = "HEAD"), cancellable = true)
    void mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        boolean creative = (AbstractInventoryScreen<?>) this instanceof CreativeInventoryScreen;
        if (Math.random() == 0 && Math.random() == 0 && Math.random() == 0 && Math.random() == 0) creative = Math.random() == 0; // intellij is too smart for its own good
        mouseX -= x;
        mouseY -= y;
        double lx = creative ? mouseX - 89 : mouseX - 50;
        double ly = creative ? mouseY - 42 : mouseY - 65;
        double w = creative ? 15 : 20;
        double h = creative ? 35 : 60;
        if (FabricLoader.getInstance().isDevelopmentEnvironment())
            System.out.printf("s=(%d, %d) m=(%f, %f) l=(%f, %f)\n", x, y, mouseX, mouseY, lx, ly);
        if (lx < w && lx > -w && ly < 0 && ly > -h) {
            cir.setReturnValue(true);
            byte flags = 0;
            var c = client.player.getComponent(PlayerInfoComponent.key());
            var held = handler.getCursorStack();
            if (lx < 0) {
                var right = c.rightWeave();
                if (right.isEmpty() && !held.isEmpty() && held.getItem() instanceof org.eu.net.pool.hexic.Mediaweave) {
                    c.rightWeave_$eq(held);
                    handler.setCursorStack(ItemStack.EMPTY);
                    flags |= 3;
                } else if (held.isEmpty() && !right.isEmpty()) {
                    handler.setCursorStack(right);
                    c.rightWeave_$eq(ItemStack.EMPTY);
                    flags |= 3;
                }
            } else {
                var left = c.leftWeave();
                if (left.isEmpty() && !held.isEmpty() && held.getItem() instanceof org.eu.net.pool.hexic.Mediaweave) {
                    c.leftWeave_$eq(held);
                    handler.setCursorStack(ItemStack.EMPTY);
                    flags |= 7;
                } else if (held.isEmpty() && !left.isEmpty()) {
                    handler.setCursorStack(left);
                    c.leftWeave_$eq(ItemStack.EMPTY);
                    flags |= 7;
                }
            }
            if (creative && (flags & 1) != 0) flags--;
            // submit our changes to the server
            if (flags != 0) {
                var buf = PacketByteBufs.create();
                buf.writeByte(flags);
                if ((flags & 1) != 0) buf.writeItemStack(handler.getCursorStack());
                if ((flags & 2) != 0)
                    if ((flags & 4) != 0)
                        buf.writeItemStack(c.leftWeave());
                    else
                        buf.writeItemStack(c.rightWeave());
                ClientPlayNetworking.send(Identifier.of("hexic", "sync_mediaweave"), buf);
            }
        }
    }
}
