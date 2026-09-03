package org.eu.net.pool
package iotaworks

import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.registries.RegisterEvent
import org.eu.net.pool.phlib.{DeferredHexRegistries, DeferredRegistryWrites}

@Mod("iotaworks")
class IotaworksNeo(modBus: IEventBus):
  DeferredHexRegistries.registerBus("iotaworks", modBus)
  DeferredRegistryWrites.registerBus("iotaworks", modBus)
  init()
  NeoForge.EVENT_BUS.addListener((event: ServerStartedEvent) =>
    IotaworksProbeValidation.onServerStarted(event)
  )
  modBus.addListener((event: RegisterEvent) =>
    DeferredHexRegistries.onRegister(event)
    DeferredRegistryWrites.onRegister(event)
  )
