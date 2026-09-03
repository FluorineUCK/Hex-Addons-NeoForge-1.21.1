package org.eu.net.pool
package hexic.hexcompat

import at.petrak.hexcasting.api.casting.math.{HexDir, HexPattern}
import at.petrak.hexcasting.api.item.PigmentItem
import at.petrak.hexcasting.common.lib.HexDataComponents
import at.petrak.hexcasting.interop.inline.InlinePatternData
import com.mojang.blaze3d.vertex.PoseStack
import miyucomics.hexpose.hexcompat.ItemStackIotaSanitizer
import miyucomics.hexpose.iotas.{ItemStackIota as HexposeItemStackIota}
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.{GuiMessage, GuiMessageTag}
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.language.I18n
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.{DyeColor, ItemStack, Items}
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModList
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.{ClientChatEvent, ClientTickEvent, EntityRenderersEvent, RegisterColorHandlersEvent, ScreenEvent}
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent
import at.petrak.hexcasting.xplat.IXplatAbstractions
import org.eu.net.pool.hexic.*
import org.eu.net.pool.hexic.mixin.client.ChatScreenAccess
import org.slf4j.LoggerFactory

import java.util.ArrayList
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import java.util.function.Consumer
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal
import vazkii.patchouli.common.book.BookRegistry

object HexicClientEvents:
  private val log = LoggerFactory.getLogger("hexic")
  private var lastMurmur: Option[String] = None
  private var probeExitTicks = 0
  private var worldProbeTicks = 0
  private var worldProbeStarted = false
  private var worldProbeFinished = false
  private var worldProbeEnginePos: BlockPos = null
  private var worldProbeChiselPos: BlockPos = null
  private val worldProbeServerboundObserved = AtomicBoolean(false)
  private val worldProbeClientboundSent = AtomicBoolean(false)
  private val worldProbeSetupFailure = AtomicReference[String](null)
  private val worldProbeFailures = ArrayList[String]()
  private val worldProbeMarker = "hexic integrated-world probe"
  private val addonNamespaces = Set(
    "yaha",
    "phlib",
    "iotaworks",
    "hexic",
    "hexxytounge",
    "hexdebug",
    "hextweaks",
    "hexcessible",
    "hexresearch",
    "hexal",
    "hexoverpowered",
    "hexop",
    "hexical",
    "hexpose",
    "oneironaut",
    "hexkinetics",
    "hexcellular",
    "moreiotas"
  )
  private val patchouliEntryPaths = Vector(
    "addon/hexic/equipment_macros",
    "addon/hexic/greater_reveal",
    "addon/hexic/lists2",
    "addon/hexic/mediaweave",
    "addon/hexic/mediaweavecollar",
    "addon/hexic/media_bundle",
    "addon/hexic/pen",
    "addon/hexic/shard",
    "addon/hexic/staffcast",
    "addon/hexic/stringworms",
    "addon/hexic/world"
  )
  private val actionPatternIds = Vector(
    "reveal",
    "murmur",
    "mkmacro",
    "prop_fi",
    "prop_fo",
    "prop_li",
    "prop_lo",
    "where",
    "modulo",
    "spellmind/save",
    "spellmind/restore",
    "fox",
    "unfox",
    "collar",
    "decollar",
    "makeworld",
    "attachworld",
    "deleteworld",
    "omni_open",
    "omni_close",
    "staffcast_factory",
    "staffcast_factory/lazy",
    "get_other_caster",
    "blind",
    "erase",
    "rotate",
    "take",
    "drop",
    "grep",
    "connect",
    "extract",
    "make_cme",
    "findview",
    "conceptavailable",
    "conceptremaining",
    "moveconcept",
    "moveentity",
    "thinkaboutit",
    "engine/pos",
    "engine/terminate",
    "engine/suspend",
    "engine/sleep"
  )
  private val patchouliPatternIds = Vector(
    "attachworld",
    "collar",
    "connect",
    "decollar",
    "deleteworld",
    "drop",
    "extract",
    "grep",
    "makeworld",
    "mkmacro",
    "reveal",
    "rotate",
    "staffcast_factory",
    "staffcast_factory/lazy",
    "take",
    "where"
  )
  def register(modBus: IEventBus, gameBus: IEventBus): Unit =
    val xplatLoaders = HexClientXplatClassLoaderCompat.preload()
    log.info("[HEXIC-PROBE] client_xplat_classloader=PASS {}", xplatLoaders)
    // Do not touch main$package during mod construction. It owns registered
    // blocks and must first initialize inside NeoForge's registry event.
    modBus.addListener(classOf[FMLClientSetupEvent], new Consumer[FMLClientSetupEvent]:
      override def accept(event: FMLClientSetupEvent): Unit =
        clientPlayerGetter = () => Option(Minecraft.getInstance().player)
        event.enqueueWork(new Runnable:
          override def run(): Unit = initializeOptionalClientInterop()
        )
    )
    modBus.addListener(classOf[RegisterColorHandlersEvent.Item], new Consumer[RegisterColorHandlersEvent.Item]:
      override def accept(event: RegisterColorHandlersEvent.Item): Unit =
        registerItemColors(event)
    )
    modBus.addListener(classOf[EntityRenderersEvent.RegisterRenderers], new Consumer[EntityRenderersEvent.RegisterRenderers]:
      override def accept(event: EntityRenderersEvent.RegisterRenderers): Unit =
        HexicBlockEntityRenderers.register(event)
    )
    gameBus.addListener(classOf[ClientChatEvent], new Consumer[ClientChatEvent]:
      override def accept(event: ClientChatEvent): Unit =
        onClientChat(event)
    )
    gameBus.addListener(classOf[ClientTickEvent.Post], new Consumer[ClientTickEvent.Post]:
      override def accept(event: ClientTickEvent.Post): Unit =
        tickChat()
        if java.lang.Boolean.getBoolean("hexic.probe.validateClientWorld") then
          tickWorldProbe()
        else
          tickProbeExit()
    )
    gameBus.addListener(classOf[ScreenEvent.Opening], new Consumer[ScreenEvent.Opening]:
      override def accept(event: ScreenEvent.Opening): Unit =
        if java.lang.Boolean.getBoolean("hexic.probe.validateClientWorld") then
          val current = Option(event.getCurrentScreen).map(_.getClass.getName).getOrElse("null")
          val next = Option(event.getNewScreen).map(_.getClass.getName).getOrElse("null")
          log.info("[HEXIC-PROBE] client_world_screen current={} new={}", current, next)
    )
    gameBus.addListener(classOf[ItemTooltipEvent], new Consumer[ItemTooltipEvent]:
      override def accept(event: ItemTooltipEvent): Unit =
        appendHexicTooltips(event.getItemStack, event.getToolTip)
    )
    if java.lang.Boolean.getBoolean("hexic.probe.validateClientHooks") then
      log.info("[HEXIC-PROBE] client_chat_hooks=PASS events=ClientChatEvent,ClientTickEvent.Post mixin=ChatComponentMixin")
      val chatAccessorApplied =
        classOf[ChatScreenAccess].isAssignableFrom(classOf[ChatScreen])
      if chatAccessorApplied then
        log.info("[HEXIC-PROBE] chat_input_accessor=PASS mixin=ChatScreenAccess")
      else
        log.error("[HEXIC-PROBE] chat_input_accessor=FAIL mixin_not_applied")
      try
        log.info("[HEXIC-PROBE] client_network_bridge=PASS {}", HexicClientNetworkCompat.probeNoConnection())
        NetworkCompat.probeClientboundDispatchNoConnection() match
          case Right(details) =>
            log.info("[HEXIC-PROBE] client_network_payload_dispatch=PASS {}", details)
          case Left(message) =>
            log.error("[HEXIC-PROBE] client_network_payload_dispatch=FAIL {}", message)
      catch
        case NonFatal(t) =>
          log.error("[HEXIC-PROBE] client_network_bridge=FAIL exception", t)

  private def initializeOptionalClientInterop(): Unit =
    try
      HexicInlineCompat.register()
      if java.lang.Boolean.getBoolean("hexic.probe.validateClientHooks") then
        log.info("[HEXIC-PROBE] inline_matcher=PASS {}", HexicInlineCompat.probe())
    catch
      case t: LinkageError =>
        log.error("[HEXIC-PROBE] inline_matcher=FAIL linkage", t)
      case NonFatal(t) =>
        log.error("[HEXIC-PROBE] inline_matcher=FAIL exception", t)

    if ModList.get().isLoaded("hexcessible") then
      try
        HexicHexcessibleCompat.register()
        if java.lang.Boolean.getBoolean("hexic.probe.validateClientHooks") then
          log.info(
            "[HEXIC-PROBE] hexcessible_macro_smartsig=PASS {}",
            HexicHexcessibleCompat.probe()
          )
      catch
        case t: LinkageError =>
          log.error("[HEXIC-PROBE] hexcessible_macro_smartsig=FAIL linkage", t)
        case NonFatal(t) =>
          log.error("[HEXIC-PROBE] hexcessible_macro_smartsig=FAIL exception", t)
    else if java.lang.Boolean.getBoolean("hexic.probe.validateClientHooks") then
      log.info("[HEXIC-PROBE] hexcessible_macro_smartsig=SKIP mod_not_loaded")

  private def registerItemColors(event: RegisterColorHandlersEvent.Item): Unit =
    event.register((stack, index) =>
      stack.getSubNbt("pigment") match
        case null => 0xFFFFFFFF
        case tag =>
          val client = Minecraft.getInstance()
          val gameTime = Option(client.level).map(_.getGameTime.toFloat).getOrElse(0f)
          val partialTick = client.getTimer.getGameTimeDeltaPartialTick(true)
          frozenPigmentFromNbt(tag).getColorProvider.getColor(
            gameTime + partialTick,
            Vec3.directionFromRotation(index * 360f / 32f, 0f)
          )
    , dyedStringworm)
    for (color, item) <- Pen.instances do
      event.register((_, index) => if index == 1 then color.getTextColor else 0xFFFFFFFF, item)

  private def onClientChat(event: ClientChatEvent): Unit =
    val client = Minecraft.getInstance()
    val player = client.player
    if player != null && player.validMediaweave.isDefined then
      val buf = NetworkCompat.buffer()
      buf.writeByte(0)
      buf.writeUtf(event.getMessage)
      try
        NetworkCompat.sendToServer("message", buf)
        event.setCanceled(true)
      catch
        case _: IllegalStateException =>
        case NonFatal(t) =>
          log.warn("Failed to forward Hexic mediaweave chat message", t)

  private def tickChat(): Unit =
    val currentMurmur = currentChatText()
    if currentMurmur != lastMurmur then
      lastMurmur = currentMurmur
      val buf = NetworkCompat.buffer()
      buf.writeBoolean(currentMurmur.isDefined)
      currentMurmur.foreach(buf.writeUtf)
      try
        NetworkCompat.sendToServer("murmur", buf)
      catch
        case _: IllegalStateException =>
        case NonFatal(t) =>
          log.warn("Failed to forward Hexic murmur chat state", t)

  private def tickProbeExit(): Unit =
    if java.lang.Boolean.getBoolean("hexic.probe.exitAfterClientStartup") then
      probeExitTicks += 1
      if probeExitTicks == 120 then
        val client = Minecraft.getInstance()
        validateTooltipHooks()
        validateHexposeRecursiveSanitizer()
        validateItemModels()
        validateTranslations()
        validateAddonRegistrySurface()
        validatePatchouliLang()
        val screenName = Option(client.screen).map(_.getClass.getName).getOrElse("null")
        log.info("[HEXIC-PROBE] client_startup_exit=PASS ticks={} screen={}", probeExitTicks, screenName)
        client.stop()

  private def tickWorldProbe(): Unit =
    if worldProbeFinished then
      return

    worldProbeTicks += 1
    val client = Minecraft.getInstance()
    val server = client.getSingleplayerServer
    val player = client.player
    val level = client.level

    if !worldProbeStarted && server != null && player != null && level != null then
      worldProbeStarted = true
      worldProbeEnginePos = player.blockPosition().above(2).east(2)
      worldProbeChiselPos = worldProbeEnginePos.east()
      val playerId = player.getUUID
      val enginePos = worldProbeEnginePos
      val chiselPos = worldProbeChiselPos

      server.execute(new Runnable:
        override def run(): Unit =
          try
            val serverPlayer = server.getPlayerList.getPlayer(playerId)
            if serverPlayer == null then
              throw IllegalStateException(s"Integrated server player missing: $playerId")
            val serverLevel = serverPlayer.serverLevel()
            serverLevel.setBlockAndUpdate(enginePos, CastingEngine.defaultBlockState())
            serverLevel.setBlockAndUpdate(chiselPos, ChiselTable.defaultBlockState())
            ChiselTable.findEntity(serverLevel, chiselPos) match
              case Some(chisel) =>
                chisel.bit(0, 0) = true
                val state = serverLevel.getBlockState(chiselPos)
                serverLevel.sendBlockUpdated(chiselPos, state, state, 3)
              case None =>
                throw IllegalStateException(s"Chisel table block entity missing at $chiselPos")
          catch
            case t: Throwable =>
              worldProbeSetupFailure.compareAndSet(
                null,
                s"${t.getClass.getName}: ${Option(t.getMessage).getOrElse("")}"
              )
      )

      val murmur = NetworkCompat.buffer()
      murmur.writeBoolean(true)
      murmur.writeUtf(worldProbeMarker)
      NetworkCompat.sendToServer("murmur", murmur)
      log.info(
        "[HEXIC-PROBE] client_world_entered=PASS dimension={} engine_pos={} chisel_pos={}",
        level.dimension().location(),
        worldProbeEnginePos,
        worldProbeChiselPos
      )

    if worldProbeStarted && server != null && worldProbeTicks % 10 == 0 then
      val playerId = player.getUUID
      server.execute(new Runnable:
        override def run(): Unit =
          val serverPlayer = server.getPlayerList.getPlayer(playerId)
          if serverPlayer != null &&
            serverPlayer.component[MurmurCache].value.contains(worldProbeMarker)
          then
            worldProbeServerboundObserved.set(true)
            if worldProbeClientboundSent.compareAndSet(false, true) then
              val tag = CompoundTag()
              tag.putInt("lineCount", 1)
              tag.putString("line0", worldProbeMarker)
              NetworkCompat.sendComponent(serverPlayer, "reveal", tag)
      )

    val setupFailure = worldProbeSetupFailure.get()
    if setupFailure != null then
      worldProbeFailures.add("server_setup")
      log.error("[HEXIC-PROBE] client_world_server_setup=FAIL {}", setupFailure)
      finishWorldProbe()
      return

    if worldProbeStarted && level != null && player != null then
      val engineEntity = level.getBlockEntity(worldProbeEnginePos)
      val chiselEntity = level.getBlockEntity(worldProbeChiselPos)
      val chiselSynced =
        ChiselTable.findEntity(level, worldProbeChiselPos, logMissing = false)
          .exists(_.bits.nonEmpty)
      val revealSynced =
        player.component[RevealComponent].lines.exists(_.getString == worldProbeMarker)
      // Keep this test-only client alive long enough for every add-on's
      // post-login/resource probe (HexGuide uses tick 120).
      if worldProbeTicks >= 140 &&
        engineEntity != null &&
        chiselEntity != null &&
        chiselSynced &&
        worldProbeServerboundObserved.get() &&
        revealSynced
      then
        runWorldCheck("client_world_models")(checkWorldModels())
        runWorldCheck("client_world_renderers")(
          checkWorldRenderers(engineEntity, chiselEntity)
        )
        runWorldCheck("client_world_network")(
          s"serverbound_murmur=${worldProbeServerboundObserved.get()} clientbound_reveal=$revealSynced"
        )
        runWorldCheck("patchouli_book")(checkPatchouliBook())
        finishWorldProbe()
      else if worldProbeTicks >= 1200 then
        worldProbeFailures.add("client_world_timeout")
        log.error(
          "[HEXIC-PROBE] client_world=FAIL timeout ticks={} engine={} chisel={} chisel_bits={} serverbound={} clientbound={}",
          Integer.valueOf(worldProbeTicks),
          Boolean.box(engineEntity != null),
          Boolean.box(chiselEntity != null),
          Boolean.box(chiselSynced),
          Boolean.box(worldProbeServerboundObserved.get()),
          Boolean.box(revealSynced)
        )
        finishWorldProbe()

  private def runWorldCheck(name: String)(check: => String): Unit =
    try
      log.info("[HEXIC-PROBE] {}=PASS {}", name, check)
    catch
      case t: Throwable =>
        worldProbeFailures.add(name)
        log.error(s"[HEXIC-PROBE] $name=FAIL", t)

  private def finishWorldProbe(): Unit =
    if !worldProbeFinished then
      worldProbeFinished = true
      if worldProbeFailures.isEmpty then
        log.info(
          "[HEXIC-PROBE] client_world_aggregate=PASS world=true models=true renderers=true network=true patchouli=true ticks={}",
          Integer.valueOf(worldProbeTicks)
        )
      else
        log.error(
          "[HEXIC-PROBE] client_world_aggregate=FAIL failure_count={} failures={} ticks={}",
          Integer.valueOf(worldProbeFailures.size()),
          worldProbeFailures.asScala.mkString(","),
          Integer.valueOf(worldProbeTicks)
        )
      Minecraft.getInstance().stop()

  private def checkWorldModels(): String =
    val client = Minecraft.getInstance()
    val missingModel = client.getModelManager.getMissingModel
    val itemEntries = BuiltInRegistries.ITEM.entrySet().asScala
      .filter(_.getKey.location().getNamespace == "hexic")
      .toVector
    val unresolvedItems = itemEntries.collect:
      case entry
          if client.getItemRenderer.getModel(
            ItemStack(entry.getValue),
            client.level,
            client.player,
            0
          ) eq missingModel =>
        entry.getKey.location().toString
    val blockEntries = BuiltInRegistries.BLOCK.entrySet().asScala
      .filter(_.getKey.location().getNamespace == "hexic")
      .toVector
    val unresolvedBlocks = blockEntries.collect:
      case entry
          if client.getBlockRenderer.getBlockModel(entry.getValue.defaultBlockState()) eq missingModel =>
        entry.getKey.location().toString
    if unresolvedItems.nonEmpty || unresolvedBlocks.nonEmpty then
      throw IllegalStateException(
        s"Missing world models: items=${unresolvedItems.mkString(",")} blocks=${unresolvedBlocks.mkString(",")}"
      )
    s"items=${itemEntries.size} blocks=${blockEntries.size} level=${client.level.dimension().location()}"

  private def checkWorldRenderers(
    engineEntity: BlockEntity,
    chiselEntity: BlockEntity
  ): String =
    val client = Minecraft.getInstance()
    val dispatcher = client.getBlockEntityRenderDispatcher
    val engineRenderer = Option(dispatcher.getRenderer(engineEntity))
      .getOrElse(throw IllegalStateException("Missing casting-engine renderer"))
      .asInstanceOf[BlockEntityRenderer[BlockEntity]]
    val chiselRenderer = Option(dispatcher.getRenderer(chiselEntity))
      .getOrElse(throw IllegalStateException("Missing chisel-table renderer"))
      .asInstanceOf[BlockEntityRenderer[BlockEntity]]
    val buffers = client.renderBuffers().bufferSource()
    engineRenderer.render(
      engineEntity,
      0.0f,
      PoseStack(),
      buffers,
      LightTexture.FULL_BRIGHT,
      OverlayTexture.NO_OVERLAY
    )
    chiselRenderer.render(
      chiselEntity,
      0.0f,
      PoseStack(),
      buffers,
      LightTexture.FULL_BRIGHT,
      OverlayTexture.NO_OVERLAY
    )
    buffers.endBatch()
    s"engine=${engineRenderer.getClass.getName} chisel=${chiselRenderer.getClass.getName} chisel_bits=nonempty"

  private def checkPatchouliBook(): String =
    val bookId = ResourceLocation.fromNamespaceAndPath("hexcasting", "thehexbook")
    val book = BookRegistry.INSTANCE.books.get(bookId)
    if book == null then
      throw IllegalStateException(s"Patchouli book not loaded: $bookId")
    val contents = book.getContents()
    if contents == null then
      throw IllegalStateException(s"Patchouli contents are null: $bookId")
    if contents.isErrored then
      throw IllegalStateException(s"Patchouli failed to build $bookId", contents.getException)
    val expected = patchouliEntryPaths.map(path =>
      ResourceLocation.fromNamespaceAndPath("hexcasting", path)
    )
    val missing = expected.filterNot(contents.entries.containsKey)
    if missing.nonEmpty then
      throw IllegalStateException(s"Missing Hexic Patchouli entries: ${missing.mkString(",")}")
    val empty = expected.filter(id => contents.entries.get(id).getPages.isEmpty)
    if empty.nonEmpty then
      throw IllegalStateException(s"Hexic Patchouli entries without pages: ${empty.mkString(",")}")
    s"book=$bookId entries=${expected.size} errored=false"

  private def validatePatchouliLang(): Unit =
    val missingAction = patchouliPatternIds.filterNot(id => I18n.exists(s"hexcasting.action.hexic:$id"))
    val missingBook = patchouliPatternIds.filterNot(id => I18n.exists(s"hexcasting.action.book.hexic:$id"))
    if missingAction.isEmpty && missingBook.isEmpty then
      log.info(
        "[HEXIC-PROBE] patchouli_lang=PASS normal={} book={}",
        Integer.valueOf(patchouliPatternIds.size),
        Integer.valueOf(patchouliPatternIds.size)
      )
    else
      log.error(
        "[HEXIC-PROBE] patchouli_lang=FAIL missing_normal={} missing_book={}",
        missingAction.mkString(","),
        missingBook.mkString(",")
      )

  private def validateTranslations(): Unit =
    val expectedActions =
      actionPatternIds ++ Option.when(ModList.get().isLoaded("hexical"))("dye_offpaw")
    val missingActions =
      expectedActions.filterNot(id => I18n.exists(s"hexcasting.action.hexic:$id"))
    val missingSpecial =
      Vector("hexcasting.action.hexic:parenthesize").filterNot(I18n.exists)
    val hexicItems = BuiltInRegistries.ITEM.entrySet().asScala
      .filter(_.getKey.location().getNamespace == "hexic")
      .toVector
    val missingItems = hexicItems
      .map(_.getValue.getDescriptionId)
      .distinct
      .filterNot(I18n.exists)
    val pigmentStringwormKeys = BuiltInRegistries.ITEM.asScala.collect:
      case pigment: PigmentItem =>
        s"item.hexic.stringworm.${pigment.asInstanceOf[net.minecraft.world.item.Item].getDescriptionId}"
    val distinctPigmentStringwormKeys = pigmentStringwormKeys.toVector.distinct
    val missingPigmentStringworms = distinctPigmentStringwormKeys.filterNot(I18n.exists)

    if missingActions.isEmpty && missingSpecial.isEmpty && missingItems.isEmpty && missingPigmentStringworms.isEmpty then
      log.info(
        "[HEXIC-PROBE] translations=PASS actions={} special={} items={} pigment_stringworms={}",
        Integer.valueOf(expectedActions.size),
        Integer.valueOf(1),
        Integer.valueOf(hexicItems.size),
        Integer.valueOf(distinctPigmentStringwormKeys.size)
      )
    else
      log.error(
        "[HEXIC-PROBE] translations=FAIL actions={} special={} items={} pigment_stringworms={}",
        missingActions.mkString(","),
        missingSpecial.mkString(","),
        missingItems.mkString(","),
        missingPigmentStringworms.mkString(",")
      )

  private def validateAddonRegistrySurface(): Unit =
    val addonItems = BuiltInRegistries.ITEM.entrySet().asScala
      .filter(entry => addonNamespaces.contains(entry.getKey.location().getNamespace))
      .toVector
    val addonBlocks = BuiltInRegistries.BLOCK.entrySet().asScala
      .filter(entry => addonNamespaces.contains(entry.getKey.location().getNamespace))
      .toVector
    val addonEntities = BuiltInRegistries.ENTITY_TYPE.entrySet().asScala
      .filter(entry => addonNamespaces.contains(entry.getKey.location().getNamespace))
      .toVector
    val addonEffects = BuiltInRegistries.MOB_EFFECT.entrySet().asScala
      .filter(entry => addonNamespaces.contains(entry.getKey.location().getNamespace))
      .toVector
    val addonTabs = BuiltInRegistries.CREATIVE_MODE_TAB.entrySet().asScala
      .filter(entry => addonNamespaces.contains(entry.getKey.location().getNamespace))
      .toVector
    val addonActions = IXplatAbstractions.INSTANCE.getActionRegistry.keySet().asScala
      .filter(id => addonNamespaces.contains(id.getNamespace))
      .toVector
    val addonIotaTypes = IXplatAbstractions.INSTANCE.getIotaTypeRegistry.keySet().asScala
      .filter(id => addonNamespaces.contains(id.getNamespace))
      .toVector

    val missingItemTranslations = addonItems
      .map(_.getValue.getDescriptionId)
      .distinct
      .filterNot(I18n.exists)
    val missingBlockTranslations = addonBlocks
      .map(_.getValue.getDescriptionId)
      .distinct
      .filterNot(I18n.exists)
    val missingEntityTranslations = addonEntities
      .map(_.getValue.getDescriptionId)
      .distinct
      .filterNot(I18n.exists)
    val missingEffectTranslations = addonEffects
      .map(_.getValue.getDescriptionId)
      .distinct
      .filterNot(I18n.exists)
    val missingActionTranslations = addonActions
      .map(id => s"hexcasting.action.$id")
      .distinct
      .filterNot(I18n.exists)
    val missingIotaTranslations = addonIotaTypes
      .map(id => s"hexcasting.iota.$id")
      .distinct
      .filterNot(I18n.exists)
    val missingIotaDescriptionTranslations = addonIotaTypes
      .map(id => s"hexcasting.iota.$id.desc")
      .distinct
      .filterNot(I18n.exists)
    val missingTabTranslations = addonTabs.flatMap(entry =>
      entry.getValue.getDisplayName.getContents match
        case contents: TranslatableContents => Some(contents.getKey)
        case _ => None
    ).distinct.filterNot(I18n.exists)

    val client = Minecraft.getInstance()
    val missingModel = client.getModelManager.getMissingModel
    val missingItemModels = addonItems.collect:
      case entry if client.getItemRenderer.getModel(new ItemStack(entry.getValue), null, null, 0) eq missingModel =>
        entry.getKey.location().toString

    if missingItemTranslations.isEmpty
      && missingBlockTranslations.isEmpty
      && missingEntityTranslations.isEmpty
      && missingEffectTranslations.isEmpty
      && missingActionTranslations.isEmpty
      && missingIotaTranslations.isEmpty
      && missingIotaDescriptionTranslations.isEmpty
      && missingTabTranslations.isEmpty
      && missingItemModels.isEmpty
    then
      log.info(
        "[HEXIC-PROBE] addon_registry_surface=PASS items={} blocks={} entities={} effects={} tabs={} actions={} iotas={} models={}",
        Integer.valueOf(addonItems.size),
        Integer.valueOf(addonBlocks.size),
        Integer.valueOf(addonEntities.size),
        Integer.valueOf(addonEffects.size),
        Integer.valueOf(addonTabs.size),
        Integer.valueOf(addonActions.size),
        Integer.valueOf(addonIotaTypes.size),
        Integer.valueOf(addonItems.size)
      )
    else
      log.error(
        "[HEXIC-PROBE] addon_registry_surface=FAIL item_lang={} block_lang={} entity_lang={} effect_lang={} tab_lang={} action_lang={} iota_lang={} iota_desc_lang={} models={}",
        missingItemTranslations.mkString(","),
        missingBlockTranslations.mkString(","),
        missingEntityTranslations.mkString(","),
        missingEffectTranslations.mkString(","),
        missingTabTranslations.mkString(","),
        missingActionTranslations.mkString(","),
        missingIotaTranslations.mkString(","),
        missingIotaDescriptionTranslations.mkString(","),
        missingItemModels.mkString(",")
      )

  private def validateItemModels(): Unit =
    val client = Minecraft.getInstance()
    val missingModel = client.getModelManager.getMissingModel
    val hexicItems = BuiltInRegistries.ITEM.entrySet().asScala
      .filter(_.getKey.location().getNamespace == "hexic")
      .toVector
    val unresolved = hexicItems.collect:
      case entry if client.getItemRenderer.getModel(new ItemStack(entry.getValue), null, null, 0) eq missingModel =>
        entry.getKey.location().toString
    if unresolved.isEmpty then
      log.info("[HEXIC-PROBE] item_models=PASS resolved={}", Integer.valueOf(hexicItems.size))
    else
      log.error("[HEXIC-PROBE] item_models=FAIL missing={}", unresolved.mkString(","))

  private def currentChatText(): Option[String] =
    Minecraft.getInstance().screen match
      case screen: ChatScreen =>
        Option(screen.asInstanceOf[ChatScreenAccess].hexic$getInput()).map(_.getValue)
      case _ => None

  private lazy val untiePatternText: Component =
    InlinePatternData(
      HexPattern.fromAnglesUnchecked("aqeqqqwqqqqqaqwqa", HexDir.SOUTH_WEST)
    ).asText(false)

  private def appendHexicTooltips(stack: ItemStack, tooltip: java.util.List[Component]): Unit =
    EchoShardCompat.appendTooltip(stack, tooltip)

    stack.getItem match
      case _: Mediaweave if Option(stack.getNbt).exists(_.contains("lock")) =>
        tooltip.add(
          Component.translatable("hexic.tooltip.mediaweave.tied")
            .withStyle(style => style.withColor(0x782fe0))
        )
        tooltip.add(
          Component.translatable("hexic.tooltip.mediaweave.tied_description")
            .withStyle(style => style.withColor(0x4b1d8c))
        )
        tooltip.add(
          Component.translatable("hexic.tooltip.mediaweave.untie", untiePatternText)
            .withStyle(style => style.withColor(0x4b1d8c))
        )
      case _ =>

    val macros = stack.getMacros
    if macros.nonEmpty then
      tooltip.add(
        Component.translatable("hexic.tooltip.macros")
          .withStyle(style => style.withColor(0xf59b14))
      )
      val (namedMacros, unnamedMacros) = macros.partition(_.name.isDefined)

      def render(definition: MacroDefinition): Component =
        InlinePatternData(definition.pattern).asText(true)

      if namedMacros.nonEmpty then
        namedMacros.toSeq.sortBy(_.name.get).foreach: definition =>
          tooltip.add(
            Component.literal("• ")
              .withStyle(style => style.withColor(0xf59b14))
              .append(
                Component.translatable(
                  "hexic.tooltip.macro.named",
                  render(definition),
                  definition.name.get
                )
              )
          )
        if unnamedMacros.nonEmpty then
          val line = Component.literal("• ")
            .withStyle(style => style.withColor(0xf59b14))
          unnamedMacros.toSeq
            .sortBy(_.pattern.anglesSignature)
            .foreach(definition => line.append(render(definition)))
          tooltip.add(line)
      else
        val line = Component.empty()
        unnamedMacros.toSeq
          .sortBy(_.pattern.anglesSignature)
          .foreach(definition => line.append(render(definition)))
        tooltip.add(line)

  private def validateTooltipHooks(): Unit =
    try
      val tied = ItemStack(Mediaweave.colors(DyeColor.PURPLE))
      tied.getOrCreateNbt().put("lock", CompoundTag())
      val tiedLines = ArrayList[Component]()
      appendHexicTooltips(tied, tiedLines)

      val macroStack = ItemStack(Items.DIAMOND)
      macroStack.putMacro(
        MacroDefinition(
          HexPattern.fromAnglesUnchecked("aq", HexDir.EAST),
          Some("hexic-probe"),
          CompoundTag()
        )
      )
      val macroLines = ArrayList[Component]()
      appendHexicTooltips(macroStack, macroLines)

      if tiedLines.size() == 3 && macroLines.size() == 2 then
        log.info(
          "[HEXIC-PROBE] tooltip_hooks=PASS tied_lines={} macro_lines={}",
          Integer.valueOf(tiedLines.size()),
          Integer.valueOf(macroLines.size())
        )
      else
        log.error(
          "[HEXIC-PROBE] tooltip_hooks=FAIL tied_lines={} macro_lines={}",
          Integer.valueOf(tiedLines.size()),
          Integer.valueOf(macroLines.size())
        )
    catch
      case NonFatal(t) =>
        log.error("[HEXIC-PROBE] tooltip_hooks=FAIL exception", t)

  private def validateHexposeRecursiveSanitizer(): Unit =
    try
      val recursiveCarrier = ItemStack(Items.PAPER)
      recursiveCarrier.set(DataComponents.CUSTOM_NAME, Component.literal("sanitizer marker"))
      recursiveCarrier.set(
        HexDataComponents.IOTA_HOLDER_IOTA.get(),
        new HexposeItemStackIota(ItemStack(Items.DIAMOND, 2))
      )
      val sanitizedCarrier = ItemStackIotaSanitizer.sanitizeCopy(recursiveCarrier)
      val nestedSanitized = sanitizedCarrier.get(HexDataComponents.IOTA_HOLDER_IOTA.get()) match
        case nested: HexposeItemStackIota => nested.getStack.isEmpty
        case _ => false
      val componentsPreserved =
        Option(sanitizedCarrier.get(DataComponents.CUSTOM_NAME))
          .exists(_.getString == "sanitizer marker")

      if nestedSanitized && componentsPreserved then
        log.info(
          "[PRE2-ADDON-PROBE] hexpose_recursive_sanitizer=PASS nested_empty=true components_preserved=true"
        )
      else
        log.error(
          "[PRE2-ADDON-PROBE] hexpose_recursive_sanitizer=FAIL nested_empty={} components_preserved={}",
          Boolean.box(nestedSanitized),
          Boolean.box(componentsPreserved)
        )
    catch
      case NonFatal(t) =>
        log.error("[PRE2-ADDON-PROBE] hexpose_recursive_sanitizer=FAIL exception", t)

  def patchRevealMessages(original: java.util.List[GuiMessage.Line]): java.util.List[GuiMessage.Line] =
    val client = Minecraft.getInstance()
    val player = client.player
    if player == null then
      original
    else
      val lines = player.component[RevealComponent].lines
      if lines.isEmpty then
        original
      else
        val patched = java.util.ArrayList[GuiMessage.Line](lines.size + original.size)
        val tick = client.gui.getGuiTicks
        lines.reverseIterator.foreach: line =>
          patched.add(GuiMessage.Line(tick, line.getVisualOrderText, GuiMessageTag.system(), true))
        patched.addAll(original)
        patched
