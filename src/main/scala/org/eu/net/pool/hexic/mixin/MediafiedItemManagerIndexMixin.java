package org.eu.net.pool.hexic.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.eu.net.pool.hexic.ServerAware;
import org.eu.net.pool.hexic.ServerIDHaver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ram.talia.hexal.api.mediafieditems.MediafiedItemManager;

@Mixin(MediafiedItemManager.Index.class)
public class MediafiedItemManagerIndexMixin implements ServerIDHaver {
    private String hexic$serverId = null;
    @ModifyReturnValue(at = @At("TAIL"), method = "copy")
    MediafiedItemManager.Index copyServerId(MediafiedItemManager.Index original) {
        ((MediafiedItemManagerIndexMixin) (Object) original).hexic$serverId = this.hexic$serverId;
        return original;
    }

    @Override
    public String hexic$getServerId() {
        return hexic$serverId;
    }
}
