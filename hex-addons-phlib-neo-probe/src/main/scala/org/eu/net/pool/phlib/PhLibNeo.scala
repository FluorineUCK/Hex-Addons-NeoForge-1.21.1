package org.eu.net.pool
package phlib

import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.registries.RegisterEvent

object NeoCommandHooks:
  private val listeners = java.util.concurrent.CopyOnWriteArrayList[RegisterCommandsEvent => Unit]()

  def register(listener: RegisterCommandsEvent => Unit): Unit =
    listeners.add(listener)

  def onRegisterCommands(event: RegisterCommandsEvent): Unit =
    listeners.forEach(listener => listener(event))

@Mod("phlib")
class PhLibNeo(modBus: IEventBus):
  DeferredHexRegistries.registerBus("phlib", modBus)
  DeferredRegistryWrites.registerBus("phlib", modBus)
  init()
  modBus.addListener((event: RegisterEvent) =>
    DeferredHexRegistries.onRegister(event)
    DeferredRegistryWrites.onRegister(event)
  )
  NeoForge.EVENT_BUS.addListener((event: RegisterCommandsEvent) => NeoCommandHooks.onRegisterCommands(event))
  NeoForge.EVENT_BUS.addListener((event: ServerStartedEvent) => PhLibProbeValidation.onServerStarted(event))
