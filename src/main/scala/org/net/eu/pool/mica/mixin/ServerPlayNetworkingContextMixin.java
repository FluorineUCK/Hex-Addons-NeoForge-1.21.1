package org.net.eu.pool.mica.mixin;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.net.eu.pool.mica.ClientExecutor;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerPlayNetworking.Context.class)
public class ServerPlayNetworkingContextMixin implements ClientExecutor {
}
