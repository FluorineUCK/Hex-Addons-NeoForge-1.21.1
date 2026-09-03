package org.eu.net.pool
package hexic

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModList
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.registration.NetworkRegistry
import net.neoforged.neoforge.registries.{DeferredRegister, RegisterEvent}
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.core.dispenser.BlockSource
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.{BuiltInRegistries, Registries}
import net.minecraft.core.Registry
import net.minecraft.nbt.{CompoundTag, ListTag, NbtOps}
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.{Component as ChatComponent}
import net.minecraft.network.Connection
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.resources.{RegistryOps, ResourceLocation}
import net.minecraft.server.level.{ClientInformation, ServerLevel, ServerPlayer}
import net.minecraft.server.network.{CommonListenerCookie, ServerGamePacketListenerImpl}
import net.minecraft.sounds.{SoundEvent, SoundEvents}
import net.minecraft.world.{InteractionHand, ItemInteractionResult}
import net.minecraft.world.entity.{Entity, EntityType, ExperienceOrb, Pose, SlotAccess}
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.tags.BlockTags
import net.minecraft.world.item.{BlockItem, DyeColor, ItemStack, Items}
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.inventory.ClickAction
import net.minecraft.world.level.{ChunkPos, GameType, Level}
import net.minecraft.world.level.block.{AbstractFurnaceBlock, Block, Blocks, DispenserBlock}
import net.minecraft.world.level.block.entity.DispenserBlockEntity
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.entity.Visibility
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.{AABB, BlockHitResult, Vec3}
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent
import net.neoforged.neoforge.fluids.{FluidStack, FluidType}
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.items.IItemHandler
import at.petrak.hexcasting.api.casting.eval.{ResolvedPattern, ResolvedPatternType}
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect
import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame
import at.petrak.hexcasting.api.casting.eval.vm.{CastingImage, CastingVM, FrameFinishEval, SpellContinuation}
import at.petrak.hexcasting.api.casting.castables.Action
import at.petrak.hexcasting.api.casting.iota.{BooleanIota, DoubleIota, EntityIota, Iota, IotaType, ListIota, NullIota, PatternIota, Vec3Iota}
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota
import at.petrak.hexcasting.api.casting.math.{HexCoord, HexDir, HexPattern}
import at.petrak.hexcasting.api.item.IotaHolderItem
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.api.mod.HexTags
import at.petrak.hexcasting.api.pigment.FrozenPigment
import at.petrak.hexcasting.api.utils.TreeList
import at.petrak.hexcasting.common.casting.actions.spells.OpEdifySapling

import at.petrak.hexcasting.common.items.magic.ItemPackagedHex
import at.petrak.hexcasting.common.items.magic.ItemMediaHolder
import at.petrak.hexcasting.common.lib.{HexBlocks, HexDataComponents, HexItems, HexRegistries}
import at.petrak.hexcasting.common.msgs.MsgNewSpellPatternC2S
import at.petrak.hexcasting.xplat.IXplatAbstractions
import com.mojang.authlib.GameProfile
import net.beholderface.oneironaut.casting.iotatypes.DimIota
import net.beholderface.oneironaut.casting.patterns.spells.great.OpDimTeleport
import miyucomics.hexical.features.animated_scrolls.AnimatedScrollEntity
import miyucomics.hexical.features.dyes.{DyeIota, DyeOption}
import miyucomics.hexical.features.pigments.PigmentIota
import miyucomics.hexpose.hexcompat.ItemStackIotaSanitizer
import miyucomics.hexpose.iotas.{DisplayIota as HexposeDisplayIota, IdentifierIota as HexposeIdentifierIota, ItemStackIota as HexposeItemStackIota}
import org.eu.net.pool.hexic.ducks.EdifySpellDuck
import org.eu.net.pool.hexic.hexcompat.{CatMorphCompat, ComponentKey, ComponentStore, CuriosCompat, EchoShardCompat, HexicalHopperCompat, HexicViewApi, MoreIotasCompat, NetworkCompat, StackCountCompat, StringIota, component, frozenPigmentFromNbt, getNbt, getOrCreateNbt, getSubNbt, syncComponent}
import org.eu.net.pool.hexic.hexcompat.runtimeworld.{Fantasy, RuntimeWorldConfig, RuntimeWorldHandle}
import org.eu.net.pool.hexic.mixin.OpDimTeleportSpellAccess
import org.eu.net.pool.phlib.{DeferredHexRegistries, DeferredRegistryWrites}
import org.slf4j.LoggerFactory
import ram.talia.hexal.api.casting.iota.MoteIota
import ram.talia.hexal.api.mediafieditems.MediafiedItemManager
import ram.talia.hexal.common.blocks.entity.BlockEntityMediafiedStorage
import ram.talia.hexal.common.casting.actions.spells.great.OpTick
import ram.talia.hexal.common.lib.HexalBlocks
import ram.talia.moreiotas.api.casting.iota.{EntityTypeIota, IotaTypeIota, ItemStackIota, ItemTypeIota, StringIota as MoreIotasStringIota}
  import miyucomics.hexcellular.{PropertyIota, StateStorage}
import top.theillusivec4.curios.api.CuriosApi

import java.util.UUID
import java.util.Collections
import java.util.function.Supplier
import scala.jdk.CollectionConverters.*

@Mod("hexic")
class HexicNeo(modBus: IEventBus):
  private val log = LoggerFactory.getLogger("hexic")
  private var initialized = false

  private val builtinBlocks: DeferredRegister[Block] = DeferredRegister.create(Registries.BLOCK, "hexic")
  builtinBlocks.register("engine", new Supplier[Block]:
    override def get(): Block = CastingEngine
  )
  builtinBlocks.register("chisel_table", new Supplier[Block]:
    override def get(): Block = ChiselTable
  )
  builtinBlocks.register("void_air", new Supplier[Block]:
    override def get(): Block = Interop.VOID_AIR
  )
  builtinBlocks.register("border", new Supplier[Block]:
    override def get(): Block = border
  )
  builtinBlocks.register(modBus)

  DeferredHexRegistries.registerBus("hexic", modBus)
  DeferredRegistryWrites.registerBus(
    "hexic",
    modBus,
    HexRegistries.ARITHMETIC,
    HexRegistries.CONTINUATION_TYPE,
    HexRegistries.SPECIAL_HANDLER,
    Registries.ITEM,
    Registries.BLOCK_ENTITY_TYPE,
    Registries.CREATIVE_MODE_TAB
  )

  ComponentStore.registerLifecycle(NeoForge.EVENT_BUS)
  Fantasy.registerLifecycle(NeoForge.EVENT_BUS)

  if FMLEnvironment.dist.isClient then
    try
      Class
        .forName("org.eu.net.pool.hexic.hexcompat.HexicClientEvents")
        .getMethod("register", classOf[IEventBus], classOf[IEventBus])
        .invoke(null, modBus, NeoForge.EVENT_BUS)
    catch
      case t: Throwable =>
        log.error("Failed to register Hexic client event hooks", t)

  NeoForge.EVENT_BUS.addListener((event: ServerStartedEvent) =>
    HexicProbeValidation.onServerStarted(event)
  )
  NeoForge.EVENT_BUS.addListener((event: LivingDropsEvent) =>
    HexicDeathDrops.onLivingDrops(event)
  )
  NeoForge.EVENT_BUS.addListener((event: top.theillusivec4.curios.api.event.CurioChangeEvent) =>
    HexicCatEvents.onCurioChange(event)
  )
  NeoForge.EVENT_BUS.addListener((event: net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem) =>
    EchoShardCompat.onRightClick(event)
  )

  modBus.addListener((event: RegisterPayloadHandlersEvent) =>
    NetworkCompat.registerPayloadHandlers(event)
  )

  modBus.addListener((event: RegisterEvent) =>
    if !initialized && event.getRegistryKey == Registries.BLOCK then
      initialized = true
      init()
    DeferredHexRegistries.onRegister(event)
    DeferredRegistryWrites.onRegister(event)
  )

private object HexicDeathDrops:
  def onLivingDrops(event: LivingDropsEvent): Unit =
    event.getEntity match
      case player: Player if !player.level().isClientSide =>
        val extras = java.util.ArrayList[ItemStack]()
        Interop.playerDeathHook.accept(player, extras)
        extras.asScala
          .filterNot(_.isEmpty)
          .foreach: stack =>
            val drop = ItemEntity(player.level(), player.getX, player.getY, player.getZ, stack)
            drop.setDefaultPickUpDelay()
            event.getDrops.add(drop)
      case _ =>

private object HexicCatEvents:
  private def isInstantCat(stack: ItemStack): Boolean =
    stack != null &&
      !stack.isEmpty &&
      stack.getItem.isInstanceOf[Mediaweave] &&
      stack.has(DataComponents.CUSTOM_NAME) &&
      stack.getHoverName.getString.equalsIgnoreCase("instant cat")

  def onCurioChange(event: top.theillusivec4.curios.api.event.CurioChangeEvent): Unit =
    if isInstantCat(event.getFrom) || isInstantCat(event.getTo) then
      event.getEntity.refreshDimensions()

private object HexicProbeValidation:
  private final class EngineProbeSignal(val kind: String) extends RuntimeException(kind)
  private val registryProperty = "hexic.probe.validateRegistries"
  private val chiselProperty = "hexic.probe.validateChiselTable"
  private val log = LoggerFactory.getLogger("hexic")
  private val chiselTableId = ResourceLocation.fromNamespaceAndPath("hexic", "chisel_table")
  private val probePos = BlockPos(0, 80, 0)
  private val inventoryProbePos = BlockPos(1, 80, 0)
  private val inventorySumProbeFirstPos = BlockPos(3, 80, 0)
  private val inventorySumProbeSecondPos = BlockPos(5, 80, 0)
  private val fluidProbePos = BlockPos(7, 80, 0)
  private val fluidStateProbePos = BlockPos(9, 80, 0)
  private val fluidSumProbeFirstPos = BlockPos(27, 80, 0)
  private val fluidSumProbeSecondPos = BlockPos(29, 80, 0)
  private val blockPickFallbackProbePos = BlockPos(11, 80, 0)
  private val heatProbePos = BlockPos(13, 80, 0)
  private val mediaProbePos = BlockPos(53, 80, 0)
  private val moteProbePos = BlockPos(55, 80, 0)
  private val viewExtensionProbePos = BlockPos(57, 80, 0)
  private val transferProbeSourcePos = BlockPos(59, 80, 0)
  private val transferProbeTargetPos = BlockPos(61, 80, 0)
  private val engineLootProbePos = BlockPos(63, 80, 0)
  private val enginePlacementProbePos = BlockPos(65, 80, 0)
  private val deathDropProbePos = BlockPos(15, 80, 0)
  private val entityViewSourcePos = BlockPos(17, 80, 0)
  private val entityViewTargetPos = BlockPos(19, 80, 0)
  private val entityViewEntityTargetPos = BlockPos(21, 80, 0)
  private val entityViewLivingTargetPos = BlockPos(23, 80, 0)
  private val entityViewMobTargetPos = BlockPos(25, 80, 0)
  private val entityViewPlayerTargetPos = BlockPos(31, 80, 0)
  private val edifyWoolPos = BlockPos(33, 80, 0)
  private val edifyCarpetPos = BlockPos(37, 80, 0)
  private val edifyTripwirePos = BlockPos(41, 80, 0)
  private val edifySaplingPos = BlockPos(45, 80, 0)
  private val edifyInvalidPos = BlockPos(49, 80, 0)

  private final class CatProbePlayer(level: ServerLevel, profile: GameProfile)
      extends net.neoforged.neoforge.common.util.FakePlayer(level, profile):
    var capturedSound: SoundEvent | Null = null

    override def playSound(sound: SoundEvent, volume: Float, pitch: Float): Unit =
      capturedSound = sound

  private final class TickProbeArmorStand(
      level: ServerLevel,
      x: Double,
      y: Double,
      z: Double
  ) extends ArmorStand(level, x, y, z):
    var directTickCalls = 0

    override def tick(): Unit =
      directTickCalls += 1
      super.tick()

  private def id(path: String): ResourceLocation =
    ResourceLocation.fromNamespaceAndPath("hexic", path)

  private val colorNames = DyeColor.values.toSeq.map(_.getName)
  private val recipeIds =
    Seq(id("engine")) ++
      colorNames.flatMap(color => Seq(id(s"pen/$color"), id(s"small_${color}_bundle")))
  private val itemIds =
    colorNames.map(color => id(s"${color}_mediaweave")) ++
      colorNames.flatMap(color => Seq(id(s"small_${color}_bundle"), id(s"large_${color}_bundle"), id(s"pen/$color"))) ++
      Seq("pure", "action", "hex", "media", "thing").map(flavor => id(s"stringworm_$flavor")) ++
      Seq(
        "stringworm_pigmented",
        "engine",
        "engine/gear_delegate",
        "wizard",
        "cut",
        "chisel_table",
        "chisel"
      ).map(id)
  private val blockIds = Seq("engine", "chisel_table", "void_air", "border").map(id)
  private val blockEntityIds = Seq("engine", "chisel_table").map(id)
  private val creativeTabIds = Seq(id("group"), id("cosmetic")) ++ ItemGroups.wip.toSeq.map(_ => id("wip"))
  private val actionIds = Seq(
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
  ).map(id)
  private def expectedActionIds: Seq[ResourceLocation] =
    actionIds ++ Option.when(ModList.get().isLoaded("hexical"))(id("dye_offpaw"))
  private val iotaTypeIds = Seq("access", "string", "transfer_type", "inventory_view").map(id)
  private val continuationTypeIds = Seq("send_message", "filter", "connect").map(id)
  private val arithmeticIds = Seq("good_modulo", "null_abs", "list_math", "view").map(id)
  private val specialHandlerIds = Seq(id("tuple"))

  def onServerStarted(event: ServerStartedEvent): Unit =
    val validateRegistries = java.lang.Boolean.getBoolean(registryProperty)
    val validateChiselTable = java.lang.Boolean.getBoolean(chiselProperty) || validateRegistries
    if validateRegistries || validateChiselTable then
      var failures = 0
      val server = event.getServer
      val actions = expectedActionIds
      try
        if validateRegistries then
          val access = server.registryAccess()
          failures += checkRegistry("items", BuiltInRegistries.ITEM, itemIds)
          failures += checkRegistry("blocks", BuiltInRegistries.BLOCK, blockIds)
          failures += checkRegistry("block_entities", BuiltInRegistries.BLOCK_ENTITY_TYPE, blockEntityIds)
          failures += checkRegistry("creative_tabs", BuiltInRegistries.CREATIVE_MODE_TAB, creativeTabIds)
          failures += checkRegistry("hex_actions", access.registryOrThrow(HexRegistries.ACTION), actions)
          failures += checkActionTags(server)
          failures += checkDatagenParity(server)
          failures += checkCastingEngineLoot(server)
          failures += checkRegistry("iota_types", access.registryOrThrow(HexRegistries.IOTA_TYPE), iotaTypeIds)
          failures += checkRegistry("continuation_types", access.registryOrThrow(HexRegistries.CONTINUATION_TYPE), continuationTypeIds)
          failures += checkRegistry("arithmetics", access.registryOrThrow(HexRegistries.ARITHMETIC), arithmeticIds)
          failures += checkArithmeticBehaviors(server)
          failures += checkRegistry("special_handlers", access.registryOrThrow(HexRegistries.SPECIAL_HANDLER), specialHandlerIds)
          failures += checkMessageFrameCodec()
          failures += checkParenthesizeMishap(server)
          failures += checkMoreIotasChatCodec()
          failures += checkEdifyMaterials(server)
          failures += checkCatMorph(server)
          failures += checkStaffPenAndEchoMemory(server)
          failures += checkSpellmindCompat()
          failures += checkSurrogateDemiplane(server)
          failures += checkDemiplaneActions(server)
          failures += checkHexalInterop(server)
          failures += checkHexicalHopperInterop(server)
          failures += checkOneironautInterop(server)
          failures += checkVariantFluidCodec(server)
          failures += checkVariantItemComponents(server)
          failures += checkLegacyViewAndVariantCodecs(server)
          failures += checkConfiguredStackCounts(server)
          failures += checkThinkAboutInterop(server)
          failures += checkPackagedHexPigmentCompat()
          failures += checkDyeOffpawAction(server)
          failures += checkPlayerActionSemantics(server)
          failures += checkMacroAndEntitySpellActions(server)
          failures += checkStaffcastAndConcurrentActions(server)
          failures += checkCoreActionSemantics(server)
          failures += checkEngineActionSemantics(server)
          failures += checkPropertyAccessIotaCodec(server.overworld())
          failures += checkPipelineFrameCodecs()
          failures += checkNetworkCompat()
          failures += checkNetworkClientboundSend(server)
          failures += checkComponentStore(server)
          failures += checkCuriosData(server)
          failures += checkNetworkReceivers(server)
          failures += checkMessageFrameEvaluation(server)
          failures += checkInventoryViews(server)
          failures += checkViewExtensions(server)
          failures += checkConceptTransfer(server)
          failures += checkEntityViews(server)
          failures += checkEntityVoidAndDemiplaneBounds(server)
          failures += checkFluidViews(server)
          failures += checkBlockPickFallback(server)
          failures += checkHeatViews(server)
          failures += checkMediaViews(server)
          failures += checkMediaBundleBehavior(server)
          failures += checkPlayerDeathDrops(server)
          failures += checkAddonRegistryParity(server)
          failures += checkAddonIotaCodecs(server)
          failures += checkHexicalAnimatedScrolls(server)
        if validateChiselTable then
          failures += checkChiselTable(event)
        if validateRegistries then
          if failures == 0 then
            log.info(
              "[HEXIC-PROBE] registries=PASS items={} blocks={} block_entities={} creative_tabs={} actions={} action_tags=PASS datagen_parity=PASS engine_loot=PASS iota_types={} continuation_types={} arithmetics={} special_handlers={} arithmetic_behaviors=PASS message_frame_codec=PASS parenthesize_mishap=PASS moreiotas_chat_codec=PASS edify_materials=PASS cat_morph=PASS staff_pen_echo=PASS spellmind=PASS runtime_demiplane=PASS demiplane_actions=PASS hexal_interop=PASS hexical_hopper=PASS oneironaut_interop=PASS variant_fluid_codec=PASS variant_item_components=PASS legacy_view_variant_codecs=PASS configured_stack_counts=PASS thinkaboutit_interop=PASS packaged_hex_pigment=PASS dye_offpaw=PASS player_actions=PASS macro_entity_spells=PASS staffcast_concurrent=PASS core_actions=PASS engine_actions=PASS block_pick_fallback=PASS heat_views=PASS media_views=PASS media_bundle=PASS player_death_drops=PASS property_access_iota_codec=PASS pipeline_frame_codecs=PASS network_payloads=PASS network_clientbound_send=PASS component_store=PASS curios_slots=PASS curios_equipment=PASS mediaweave_dispenser=PASS network_receivers=PASS message_frame_eval=PASS inventory_views=PASS view_extensions=PASS concept_transfer=PASS entity_views=PASS entity_void_bounds=PASS fluid_views=PASS addon_registry_parity=PASS addon_iota_codecs=PASS hexical_animated_scrolls=PASS chisel_table_entity=PASS",
              itemIds.size,
              blockIds.size,
              blockEntityIds.size,
              creativeTabIds.size,
              actions.size,
              iotaTypeIds.size,
              continuationTypeIds.size,
              arithmeticIds.size,
              specialHandlerIds.size
            )
          else
            log.error("[HEXIC-PROBE] registries=FAIL failure_count={}", failures)
      catch
        case t: Throwable =>
          failures += 1
          if validateRegistries then
            log.error("[HEXIC-PROBE] registries=FAIL exception", t)
          else
            log.error("[HEXIC-PROBE] chisel_table_entity=FAIL exception", t)
      finally
        // NeoForge can finish the dedicated-server shutdown while a library
        // thread still keeps the probe JVM alive. Keep the normal graceful
        // halt, but make validation runs self-terminating if that happens.
        val exitCode = if failures == 0 then 0 else 1
        val hardStop = new Thread(
          () =>
            try
              Thread.sleep(15000L)
              Runtime.getRuntime.halt(exitCode)
            catch
              case _: InterruptedException => (),
          "hexic-probe-hard-stop"
        )
        hardStop.setDaemon(true)
        hardStop.start()
        server.halt(false)

  private def checkRegistry[T](label: String, registry: Registry[T], ids: Seq[ResourceLocation]): Int =
    val missing = ids.filterNot(id => registry.containsKey(id))
    if missing.isEmpty then
      log.info("[HEXIC-PROBE] {}=PASS count={}", label, ids.size)
    else
      log.error("[HEXIC-PROBE] {}=FAIL missing={}", label, missing.mkString(","))
    missing.size

  private def checkNamespaceCount[T](
      label: String,
      registry: Registry[T],
      namespace: String,
      expected: Int
  ): Int =
    val ids = registry
      .keySet()
      .asScala
      .filter(_.getNamespace == namespace)
      .toSeq
      .sortBy(_.toString)
    if ids.size == expected then
      log.info("[PRE2-ADDON-PROBE] {}=PASS namespace={} count={}", label, namespace, ids.size)
      0
    else
      log.error(
        "[PRE2-ADDON-PROBE] {}=FAIL namespace={} expected={} actual={} ids={}",
        label,
        namespace,
        expected,
        ids.size,
        ids.mkString(",")
      )
      1

  private def checkAddonRegistryParity(server: net.minecraft.server.MinecraftServer): Int =
    try
      val access = server.registryAccess()
      val actions = access.registryOrThrow(HexRegistries.ACTION)
      val iotaTypes = access.registryOrThrow(HexRegistries.IOTA_TYPE)
      val arithmetics = access.registryOrThrow(HexRegistries.ARITHMETIC)
      var failures = 0

      failures += checkNamespaceCount("hexpose_actions", actions, "hexpose", 107)
      failures += checkNamespaceCount("hexpose_iota_types", iotaTypes, "hexpose", 3)
      failures += checkNamespaceCount("hexpose_arithmetics", arithmetics, "hexpose", 1)

      failures += checkNamespaceCount("hexical_actions", actions, "hexical", 149)
      failures += checkNamespaceCount("hexical_iota_types", iotaTypes, "hexical", 2)
      failures += checkNamespaceCount("hexical_items", BuiltInRegistries.ITEM, "hexical", 45)
      failures += checkNamespaceCount("hexical_blocks", BuiltInRegistries.BLOCK, "hexical", 8)
      failures += checkNamespaceCount("hexical_block_entities", BuiltInRegistries.BLOCK_ENTITY_TYPE, "hexical", 5)
      failures += checkNamespaceCount("hexical_entities", BuiltInRegistries.ENTITY_TYPE, "hexical", 5)
      failures += checkNamespaceCount("hexical_particles", BuiltInRegistries.PARTICLE_TYPE, "hexical", 2)
      failures += checkNamespaceCount("hexical_sounds", BuiltInRegistries.SOUND_EVENT, "hexical", 10)
      failures += checkNamespaceCount("hexical_triggers", BuiltInRegistries.TRIGGER_TYPES, "hexical", 7)
      failures += checkNamespaceCount("hexical_creative_tabs", BuiltInRegistries.CREATIVE_MODE_TAB, "hexical", 1)

      failures += checkNamespaceCount("moreiotas_actions", actions, "moreiotas", 45)
      failures += checkNamespaceCount("moreiotas_iota_types", iotaTypes, "moreiotas", 6)
      failures += checkNamespaceCount("moreiotas_arithmetics", arithmetics, "moreiotas", 3)

      failures += checkNamespaceCount("mediaworks_actions_with_hexpose", actions, "mediaworks", 10)
      failures += checkNamespaceCount("hexxysable_actions", actions, "hexxysable", 23)

      if ModList.get().isLoaded("hexoverpowered") then
        failures += checkNamespaceCount("hexoverpowered_actions", actions, "hexoverpowered", 10)
        failures += checkNamespaceCount(
          "hexoverpowered_attributes",
          BuiltInRegistries.ATTRIBUTE,
          "hexoverpowered",
          3
        )

      if failures == 0 then
        log.info(
          "[PRE2-ADDON-PROBE] registry_parity=PASS hexpose_actions=107 hexical_actions=149 moreiotas_actions=45 mediaworks_actions=10 hexxysable_actions=23 hexoverpowered_loaded={}",
          ModList.get().isLoaded("hexoverpowered")
        )
      else
        log.error("[PRE2-ADDON-PROBE] registry_parity=FAIL failure_count={}", failures)
      failures
    catch
      case t: Throwable =>
        log.error("[PRE2-ADDON-PROBE] registry_parity=FAIL exception", t)
        1

  private def checkAddonIotaCodecs(server: net.minecraft.server.MinecraftServer): Int =
    try
      val access = server.registryAccess()
      val ops = RegistryOps.create(NbtOps.INSTANCE, access)

      def roundTrips(original: Iota): (Boolean, Boolean, Iota, Iota) =
        val encoded = IotaType.TYPED_CODEC.encodeStart(ops, original).getOrThrow
        val decoded = IotaType.TYPED_CODEC.parse(ops, encoded).getOrThrow
        val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), access)
        try
          IotaType.TYPED_STREAM_CODEC.encode(buffer, original)
          buffer.readerIndex(0)
          val decodedStream = IotaType.TYPED_STREAM_CODEC.decode(buffer)
          (
            Iota.tolerates(original, decoded),
            Iota.tolerates(original, decodedStream),
            decoded,
            decodedStream
          )
        finally
          buffer.release()

      def describe(iota: Iota): String =
        iota match
          case itemType: ItemTypeIota =>
            s"${itemType.getClass.getName}:${ItemTypeIota.turnIntoString(itemType)}:${itemType.getEither}"
          case _ =>
            s"${iota.getClass.getName}:type=${iota.getType}"

      val namedDiamond = ItemStack(Items.DIAMOND, 3)
      namedDiamond.set(DataComponents.CUSTOM_NAME, ChatComponent.literal("pre2 addon probe"))
      val tests = Seq[(String, Iota)](
        "hexpose_identifier" -> new HexposeIdentifierIota(
          ResourceLocation.fromNamespaceAndPath("minecraft", "diamond")
        ),
        "hexpose_display" -> new HexposeDisplayIota(
          ChatComponent.literal("pre2 display").withStyle(net.minecraft.ChatFormatting.AQUA)
        ),
        "hexpose_item_stack" -> new HexposeItemStackIota(namedDiamond.copy()),
        "hexical_dye" -> new DyeIota(DyeOption.PURPLE),
        "hexical_pigment" -> new PigmentIota(FrozenPigment.DEFAULT.get()),
        "moreiotas_string" -> MoreIotasStringIota.makeUnchecked("pre2 matrix landing"),
        "moreiotas_item_type" -> new ItemTypeIota(Items.DIAMOND),
        "moreiotas_entity_type" -> new EntityTypeIota(EntityType.CAT),
        "moreiotas_item_stack" -> ItemStackIota.createFiltered(namedDiamond),
        "moreiotas_iota_type" -> new IotaTypeIota(MoreIotasStringIota.TYPE)
      )
      val results = tests.map: (label, iota) =>
        val (nbtOk, streamOk, decoded, decodedStream) = roundTrips(iota)
        (label, iota, nbtOk, streamOk, decoded, decodedStream)
      val failed = results.collect:
        case (label, original, nbtOk, streamOk, decoded, decodedStream)
            if !nbtOk || !streamOk =>
          log.error(
            "[PRE2-ADDON-PROBE] iota_codec_case=FAIL label={} nbt={} stream={} original={} decoded_nbt={} decoded_stream={}",
            label,
            Boolean.box(nbtOk),
            Boolean.box(streamOk),
            describe(original),
            describe(decoded),
            describe(decodedStream)
          )
          label

      if failed.isEmpty then
        log.info(
          "[PRE2-ADDON-PROBE] iota_codecs=PASS typed={} stream={} cases={}",
          tests.size,
          tests.size,
          tests.map(_._1).mkString(",")
        )
        0
      else
        log.error(
          "[PRE2-ADDON-PROBE] iota_codecs=FAIL failed={}",
          failed.mkString(",")
        )
        failed.size
    catch
      case t: Throwable =>
        log.error("[PRE2-ADDON-PROBE] iota_codecs=FAIL exception", t)
        1

  private def checkHexicalAnimatedScrolls(server: net.minecraft.server.MinecraftServer): Int =
    try
      val level = server.overworld()
      val ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess())
      val pattern = HexPattern.fromAngles("aqae", HexDir.SOUTH_EAST)
      val patternIota = PatternIota(pattern)
      val scrollId = ResourceLocation.fromNamespaceAndPath("hexical", "animated_scroll_small")
      val scrollItem = BuiltInRegistries.ITEM.get(scrollId)
      val stack = ItemStack(scrollItem)

      def containsPattern(iota: Iota): Boolean =
        iota match
          case list: ListIota =>
            list.getList.iterator.asScala.toSeq match
              case Seq(value: PatternIota) => value.getPattern == pattern
              case _ => false
          case _ => false

      val holder = scrollItem.asInstanceOf[IotaHolderItem]
      val writable = holder.writeable(stack) && holder.canWrite(stack, patternIota)
      holder.writeDatum(stack, patternIota)
      val itemRead = containsPattern(holder.readIota(stack))

      val encodedStack = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow
      val decodedStack = ItemStack.CODEC.parse(ops, encodedStack).getOrThrow
      val persistedRead = containsPattern(holder.readIota(decodedStack))

      val entityType = BuiltInRegistries.ENTITY_TYPE
        .get(ResourceLocation.fromNamespaceAndPath("hexical", "animated_scroll"))
        .asInstanceOf[EntityType[AnimatedScrollEntity]]
      val entity = entityType.create(level)
      entity.setScroll(stack.copy())
      val simulateAccepted = entity.writeIota(patternIota, true)
      val simulateUnchanged = entity.getPatterns.isEmpty
      val entityAccepted = entity.writeIota(patternIota, false)
      val entityRead = containsPattern(entity.readIota())

      val saved = CompoundTag()
      entity.addAdditionalSaveData(saved)
      val restored = entityType.create(level)
      restored.readAdditionalSaveData(saved)
      val entityPersisted = containsPattern(restored.readIota())
      val cleared = restored.writeIota(NullIota(), false) && restored.getPatterns.isEmpty

      val valid =
        writable &&
          itemRead &&
          persistedRead &&
          simulateAccepted &&
          simulateUnchanged &&
          entityAccepted &&
          entityRead &&
          entityPersisted &&
          cleared
      if valid then
        log.info(
          "[PRE2-ADDON-PROBE] hexical_animated_scrolls=PASS item_read={} item_persisted={} simulate={} entity_read={} entity_persisted={} cleared={}",
          itemRead,
          persistedRead,
          simulateAccepted && simulateUnchanged,
          entityRead,
          entityPersisted,
          cleared
        )
        0
      else
        log.error(
          "[PRE2-ADDON-PROBE] hexical_animated_scrolls=FAIL writable={} item_read={} item_persisted={} simulate_accepted={} simulate_unchanged={} entity_accepted={} entity_read={} entity_persisted={} cleared={}",
          writable,
          itemRead,
          persistedRead,
          simulateAccepted,
          simulateUnchanged,
          entityAccepted,
          entityRead,
          entityPersisted,
          cleared
        )
        1
    catch
      case t: Throwable =>
        log.error("[PRE2-ADDON-PROBE] hexical_animated_scrolls=FAIL exception", t)
        1

  private def checkActionTags(server: net.minecraft.server.MinecraftServer): Int =
    val registry = server.registryAccess().registryOrThrow(HexRegistries.ACTION)
    val expected = Seq("moveentity", "makeworld", "attachworld", "deleteworld")
    def missing(tag: net.minecraft.tags.TagKey[at.petrak.hexcasting.api.casting.ActionRegistryEntry]): Seq[String] =
      expected.filter: path =>
        val entry = registry.get(id(path))
        entry == null || !registry.wrapAsHolder(entry).is(tag)
    val missingPerWorld = missing(HexTags.Actions.PER_WORLD_PATTERN)
    val missingEnlightenment = missing(HexTags.Actions.REQUIRES_ENLIGHTENMENT)
    if missingPerWorld.isEmpty && missingEnlightenment.isEmpty then
      log.info(
        "[HEXIC-PROBE] action_tags=PASS per_world={} requires_enlightenment={}",
        expected.mkString(","),
        expected.mkString(",")
      )
      0
    else
      log.error(
        "[HEXIC-PROBE] action_tags=FAIL missing_per_world={} missing_requires_enlightenment={}",
        missingPerWorld.mkString(","),
        missingEnlightenment.mkString(",")
      )
      missingPerWorld.size + missingEnlightenment.size

  private def checkDatagenParity(server: net.minecraft.server.MinecraftServer): Int =
    try
      val missingRecipes = recipeIds.filter(server.getRecipeManager.byKey(_).isEmpty)
      val missingStaves =
        DyeColor.values.toSeq.filterNot: color =>
          BuiltInRegistries.ITEM
            .wrapAsHolder(Pen.instances(color))
            .is(HexTags.Items.STAVES)
      val engineMineable =
        BuiltInRegistries.BLOCK
          .wrapAsHolder(CastingEngine)
          .is(BlockTags.MINEABLE_WITH_PICKAXE)
      val ok =
        missingRecipes.isEmpty &&
          missingStaves.isEmpty &&
          engineMineable
      if ok then
        log.info(
          "[HEXIC-PROBE] datagen_parity=PASS recipes={} staves={} engine_mineable={}",
          recipeIds.size,
          DyeColor.values.length,
          engineMineable
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] datagen_parity=FAIL missing_recipes={} missing_staves={} engine_mineable={}",
          missingRecipes.mkString(","),
          missingStaves.map(_.getName).mkString(","),
          engineMineable
        )
        missingRecipes.size + missingStaves.size + (if engineMineable then 0 else 1)
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] datagen_parity=FAIL exception", t)
        1

  private def checkCastingEngineLoot(server: net.minecraft.server.MinecraftServer): Int =
    val level = server.overworld()
    val probeIota = ListIota(
      Seq[Iota](DoubleIota(42.25), BooleanIota(true), StringIota("engine-loot-probe")).asJava
    )
    try
      level.setBlockAndUpdate(engineLootProbePos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(enginePlacementProbePos, Blocks.AIR.defaultBlockState())
      val sourcePlaced =
        level.setBlockAndUpdate(engineLootProbePos, CastingEngine.defaultBlockState())
      val source = level.getBlockEntity(engineLootProbePos) match
        case entity: CastingEngine.Entity => entity
        case other =>
          throw IllegalStateException(
            s"expected CastingEngine.Entity at $engineLootProbePos, got ${Option(other).fold("null")(_.getClass.getName)}"
          )

      val userData = CompoundTag()
      userData.putString("hexic_engine_probe", "roundtrip")
      val probeImage = CastingImage(
        TreeList.from(Seq[Iota](DoubleIota(7.5), BooleanIota(false)).asJava),
        0,
        TreeList.empty(),
        false,
        false,
        3,
        userData
      )
      val wroteIota = source.writeIota(probeIota, false)
      source.state = Some(
        (
          source.Suspension(probeImage, Seq(FrameFinishEval.INSTANCE)),
          None
        )
      )
      source.markDirty()

      val sourceData = source.createNbt
      val sourceKeys =
        sourceData.contains("hex", net.minecraft.nbt.Tag.TAG_COMPOUND) &&
          sourceData.contains("image", net.minecraft.nbt.Tag.TAG_COMPOUND) &&
          sourceData.contains("frames", net.minecraft.nbt.Tag.TAG_LIST) &&
          !sourceData.getList("frames", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty

      val drops =
        Block
          .getDrops(
            level.getBlockState(engineLootProbePos),
            level,
            engineLootProbePos,
            source
          )
          .asScala
          .toSeq
      val dropped = drops.find(_.getItem == CastingEngine.item).orNull
      val legacyBefore =
        dropped != null &&
          Option(dropped.getNbt)
            .exists(_.contains("BlockEntityTag", net.minecraft.nbt.Tag.TAG_COMPOUND))
      val modernBefore =
        dropped != null && dropped.has(DataComponents.BLOCK_ENTITY_DATA)
      val copiedBefore =
        Option(dropped)
          .flatMap(stack => Option(CastingEngine.item.blockEntityData(stack)))
          .orNull
      val copiedKeys =
        copiedBefore != null &&
          copiedBefore.contains("hex", net.minecraft.nbt.Tag.TAG_COMPOUND) &&
          copiedBefore.contains("image", net.minecraft.nbt.Tag.TAG_COMPOUND) &&
          copiedBefore.contains("frames", net.minecraft.nbt.Tag.TAG_LIST) &&
          !copiedBefore.getList("frames", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty
      val itemIota =
        if dropped == null then null
        else CastingEngine.item.readIota(dropped)
      val itemIotaOk =
        itemIota != null && Iota.tolerates(probeIota, itemIota)

      val normalized =
        if dropped == null then null
        else CastingEngine.item.normalizeBlockEntityData(dropped)
      val modernAfter =
        if dropped == null then null
        else dropped.get(DataComponents.BLOCK_ENTITY_DATA)
      val normalizedKeys =
        normalized != null &&
          modernAfter != null &&
          Seq("hex", "image", "frames").forall(key => normalized.contains(key)) &&
          Seq("hex", "image", "frames").forall(key => modernAfter.copyTag().contains(key))
      val legacyRemoved =
        dropped != null &&
          Option(dropped.getNbt)
            .forall(!_.contains("BlockEntityTag", net.minecraft.nbt.Tag.TAG_COMPOUND))

      val targetPlaced =
        level.setBlockAndUpdate(enginePlacementProbePos, CastingEngine.defaultBlockState())
      val applied =
        dropped != null &&
          BlockItem.updateCustomBlockEntityTag(
            level,
            FakePlayerFactory.getMinecraft(level),
            enginePlacementProbePos,
            dropped
          )
      val target = level.getBlockEntity(enginePlacementProbePos) match
        case entity: CastingEngine.Entity => entity
        case _ => null
      val placedIota = Option(target).map(_.readIota()).orNull
      val placedIotaOk =
        placedIota != null && Iota.tolerates(probeIota, placedIota)
      val placedStateOk =
        target != null &&
          target.state.exists: (suspension, _) =>
            suspension.imageData.getCompound("userData")
              .getString("hexic_engine_probe") == "roundtrip" &&
              suspension.frameData.nonEmpty

      val ok =
        sourcePlaced &&
          wroteIota &&
          sourceKeys &&
          dropped != null &&
          (legacyBefore || modernBefore) &&
          copiedKeys &&
          itemIotaOk &&
          normalizedKeys &&
          legacyRemoved &&
          targetPlaced &&
          applied &&
          placedIotaOk &&
          placedStateOk
      if ok then
        log.info(
          "[HEXIC-PROBE] engine_loot=PASS drops={} legacy_before={} modern_before={} copied_keys={} item_iota={} normalized={} legacy_removed={} applied={} placed_iota={} placed_state={}",
          drops.size,
          legacyBefore,
          modernBefore,
          copiedKeys,
          itemIotaOk,
          normalizedKeys,
          legacyRemoved,
          applied,
          placedIotaOk,
          placedStateOk
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] engine_loot=FAIL source_placed={} wrote_iota={} source_keys={} drops={} dropped={} legacy_before={} modern_before={} copied={} copied_keys={} item_iota={} normalized={} modern_after={} normalized_keys={} legacy_removed={} target_placed={} applied={} target={} placed_iota={} placed_state={}",
          sourcePlaced,
          wroteIota,
          sourceKeys,
          drops.mkString(","),
          dropped,
          legacyBefore,
          modernBefore,
          copiedBefore,
          copiedKeys,
          itemIotaOk,
          normalized,
          modernAfter,
          normalizedKeys,
          legacyRemoved,
          targetPlaced,
          applied,
          target,
          placedIotaOk,
          placedStateOk
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] engine_loot=FAIL exception", t)
        1
    finally
      level.setBlockAndUpdate(engineLootProbePos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(enginePlacementProbePos, Blocks.AIR.defaultBlockState())

  private def checkArithmeticBehaviors(server: net.minecraft.server.MinecraftServer): Int =
    def close(a: Double, b: Double): Boolean = math.abs(a - b) < 1.0e-9
    def closeVec(v: Vec3, expected: Vec3): Boolean =
      close(v.x, expected.x) && close(v.y, expected.y) && close(v.z, expected.z)
    try
      val arithmetic = server.registryAccess().registryOrThrow(HexRegistries.ARITHMETIC).get(id("good_modulo"))
      if arithmetic == null then
        log.error("[HEXIC-PROBE] arithmetic_behaviors=FAIL missing good_modulo arithmetic")
        1
      else
        val player = FakePlayerFactory.getMinecraft(server.overworld())
        val env = StaffCastEnv(player, InteractionHand.MAIN_HAND)
        val operator = arithmetic.getOperator(good_modulo)
        def run(args: Seq[Iota]): Seq[Iota] =
          val image = CastingImage(
            TreeList.from(args.asJava),
            0,
            TreeList.empty(),
            false,
            false,
            0,
            CompoundTag()
          )
          operator.operate(env, image, SpellContinuation.Done.INSTANCE).getNewImage.getStack.asScala.toSeq
        val scalar = run(Seq(DoubleIota(-8.0), DoubleIota(3.0))).collectFirst:
          case d: DoubleIota => d.getDouble
        val vector = run(Seq(Vec3Iota(Vec3(-8.0, 5.0, -1.0)), Vec3Iota(Vec3(3.0, 2.0, 4.0)))).collectFirst:
          case v: Vec3Iota => v.getVec3
        val mixed = run(Seq(Vec3Iota(Vec3(-8.0, 5.0, -1.0)), DoubleIota(3.0))).collectFirst:
          case v: Vec3Iota => v.getVec3
        val ok =
          scalar.exists(close(_, 1.0)) &&
            vector.exists(closeVec(_, Vec3(1.0, 1.0, 3.0))) &&
            mixed.exists(closeVec(_, Vec3(1.0, 2.0, 2.0)))
        if ok then
          log.info(
            "[HEXIC-PROBE] arithmetic_behaviors=PASS scalar={} vector={} mixed={}",
            scalar.get,
            vector.get,
            mixed.get
          )
          0
        else
          log.error(
            "[HEXIC-PROBE] arithmetic_behaviors=FAIL scalar={} vector={} mixed={}",
            scalar.map(_.toString).getOrElse("missing"),
            vector.map(_.toString).getOrElse("missing"),
            mixed.map(_.toString).getOrElse("missing")
          )
          1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] arithmetic_behaviors=FAIL exception", t)
        1

  private def checkMessageFrameCodec(): Int =
    try
      val playerId = UUID.fromString("8ab44b23-f4db-45a8-bc31-0112c0f3f247")
      val text = ChatComponent.literal("hexic probe mediaweave")
      val frame = MessageFrame(playerId, text, null)
      val encoded = ContinuationFrame.Type.getTYPED_CODEC.encodeStart(NbtOps.INSTANCE, frame).getOrThrow
      val decoded = ContinuationFrame.Type.getTYPED_CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow
      decoded match
        case decodedFrame: MessageFrame if decodedFrame.id == playerId && decodedFrame.text.getString == text.getString =>
          log.info("[HEXIC-PROBE] message_frame_codec=PASS encoded_type={} text={}", encoded.getClass.getName, decodedFrame.text.getString)
          0
        case other =>
          log.error("[HEXIC-PROBE] message_frame_codec=FAIL decoded_type={} decoded={}", Option(other).fold("null")(_.getClass.getName), other)
          1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] message_frame_codec=FAIL exception", t)
        1

  private def checkParenthesizeMishap(server: net.minecraft.server.MinecraftServer): Int =
    try
      val player = FakePlayerFactory.getMinecraft(server.overworld())
      // "eadedae" is Hexic's custom parenthesis-inspection pattern. Vanilla
      // Hex Casting does not resolve it, so a successful evaluated result here
      // proves that CastingVMMixin is active at the production call path.
      val inspectVm = CastingVM(CastingImage(), StaffCastEnv(player, InteractionHand.MAIN_HAND))
      val inspect = PatternIota(HexPattern.fromAngles("eadedae", HexDir.EAST))
      val inspectResult = inspectVm.executeInner(inspect, server.overworld(), SpellContinuation.Done.INSTANCE)
      val inspectStack = Option(inspectResult.getNewData)
        .map(_.getStack.asScala.toSeq)
        .getOrElse(Seq.empty)
      val inspectOk =
        inspectResult.getResolutionType == ResolvedPatternType.EVALUATED &&
          (inspectStack match
            case Seq(_: ListIota, count: DoubleIota) => count.getDouble == 0.0
            case _ => false)

      val mishapVm = CastingVM(CastingImage(), StaffCastEnv(player, InteractionHand.MAIN_HAND))
      val intro = PatternIota(HexPattern.fromAngles("qdq", HexDir.EAST))
      val mishapResult = mishapVm.executeInner(intro, server.overworld(), SpellContinuation.Done.INSTANCE)
      val returned = mishapResult.getNewData != null
      val resolution = mishapResult.getResolutionType.toString
      // Hexic deliberately performs this custom mishap on a safe VM and then
      // returns its errored image. The effect is therefore already consumed
      // and is not required to remain in CastResult.sideEffects.
      val mishapOk = mishapResult.getResolutionType == ResolvedPatternType.ERRORED
      val ok =
        inspectOk && mishapOk
      if ok then
        log.info(
          "[HEXIC-PROBE] parenthesize_mishap=PASS hook_resolution={} hook_stack={} returned={} mishap_resolution={}",
          inspectResult.getResolutionType,
          inspectStack.map(_.getClass.getSimpleName).mkString(","),
          returned,
          resolution
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] parenthesize_mishap=FAIL hook_resolution={} hook_stack={} returned={} mishap_resolution={}",
          inspectResult.getResolutionType,
          inspectStack.map(_.getClass.getSimpleName).mkString(","),
          returned,
          resolution
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] parenthesize_mishap=FAIL exception", t)
        1

  private def checkMoreIotasChatCodec(): Int =
    val result = MoreIotasCompat.probeChatCodec()
    if result.passed() && result.testLength() > MoreIotasCompat.VANILLA_CHAT_LIMIT then
      log.info(
        "[HEXIC-PROBE] moreiotas_chat_codec=PASS configured_limit={} read_limit={} test_length={} encoded_bytes={} decoded_length={}",
        result.configuredLimit(),
        result.readLimit(),
        result.testLength(),
        result.encodedBytes(),
        result.decodedLength()
      )
      0
    else
      log.error(
        "[HEXIC-PROBE] moreiotas_chat_codec=FAIL configured_limit={} read_limit={} test_length={} encoded_bytes={} decoded_length={} failure={}",
        result.configuredLimit(),
        result.readLimit(),
        result.testLength(),
        result.encodedBytes(),
        result.decodedLength(),
        result.failure()
      )
      1

  private def checkEdifyMaterials(server: net.minecraft.server.MinecraftServer): Int =
    val level = server.overworld()
    val positions = Seq(edifyWoolPos, edifyCarpetPos, edifyTripwirePos, edifySaplingPos, edifyInvalidPos)
    val probeChunks = positions.map(pos => (pos.getX >> 4, pos.getZ >> 4)).distinct
    var newlyForcedChunks = Seq.empty[(Int, Int)]

    def setEntityChunkVisibility(chunkX: Int, chunkZ: Int): Unit =
      val field = classOf[ServerLevel].getDeclaredField("entityManager")
      field.setAccessible(true)
      val entityManager = field.get(level)
      entityManager
        .getClass
        .getMethod("updateChunkStatus", classOf[ChunkPos], classOf[Visibility])
        .invoke(entityManager, ChunkPos(chunkX, chunkZ), Visibility.TICKING)

    def dropsAt(pos: BlockPos): Seq[ItemEntity] =
      val box = AABB.ofSize(Vec3.atCenterOf(pos), 2.0, 2.0, 2.0)
      level.getAllEntities.asScala.collect:
        case item: ItemEntity if !item.isRemoved && box.intersects(item.getBoundingBox) => item
      .toSeq

    def clearAt(pos: BlockPos): Unit =
      dropsAt(pos).foreach(_.discard())
      level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())

    def executeAt(pos: BlockPos, state: net.minecraft.world.level.block.state.BlockState):
        (Int, Boolean, Seq[ItemStack]) =
      clearAt(pos)
      level.setBlockAndUpdate(pos, state)
      val player = FakePlayerFactory.get(
        level,
        GameProfile(
          UUID.fromString("41263b76-1aa0-43ec-8cf2-9bb13c13026c"),
          "HexicEdifyProbe"
        )
      )
      player.setPos(pos.getX + 0.5, pos.getY, pos.getZ + 0.5)
      val env = StaffCastEnv(player, InteractionHand.MAIN_HAND)
      val result = OpEdifySapling.INSTANCE.execute(
        Seq[Iota](Vec3Iota(Vec3.atCenterOf(pos))).asJava,
        env
      )
      val effect = result.component1()
      val mode = effect.asInstanceOf[EdifySpellDuck].hexic$getEdifyMode()
      effect.cast(env)
      (mode, level.getBlockState(pos).isAir, dropsAt(pos).map(_.getItem.copy()))

    try
      probeChunks.foreach: (chunkX, chunkZ) =>
        val chunkKey = ChunkPos.asLong(chunkX, chunkZ)
        val wasForced = level.getForcedChunks.toLongArray.exists(_ == chunkKey)
        if !wasForced then
          level.setChunkForced(chunkX, chunkZ, true)
          newlyForcedChunks :+= (chunkX, chunkZ)
        level.getChunkSource.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true)
        setEntityChunkVisibility(chunkX, chunkZ)

      val (woolMode, woolAir, woolDrops) =
        executeAt(edifyWoolPos, Blocks.BLUE_WOOL.defaultBlockState())
      val woolCount =
        woolDrops
          .filter(_.getItem == Mediaweave.colors(DyeColor.BLUE))
          .map(_.getCount)
          .sum
      val woolOk = woolMode == 2 && woolAir && woolCount >= 2 && woolCount <= 6

      val (carpetMode, carpetAir, carpetDrops) =
        executeAt(edifyCarpetPos, Blocks.RED_CARPET.defaultBlockState())
      val carpetCount =
        carpetDrops
          .filter(_.getItem == Mediaweave.colors(DyeColor.RED))
          .map(_.getCount)
          .sum
      val carpetOk = carpetMode == 3 && carpetAir && carpetCount >= 3 && carpetCount <= 9

      val stringwormItems = Stringworm.items.values.toSet
      val (tripwireMode, tripwireAir, tripwireDrops) =
        executeAt(edifyTripwirePos, Blocks.TRIPWIRE.defaultBlockState())
      val tripwireCount =
        tripwireDrops
          .filter(stack => stringwormItems.exists(_ == stack.getItem))
          .map(_.getCount)
          .sum
      val tripwireOk = tripwireMode == -1 && tripwireAir && tripwireCount == 1

      clearAt(edifySaplingPos)
      level.setBlockAndUpdate(edifySaplingPos, Blocks.OAK_SAPLING.defaultBlockState())
      val saplingPlayer = FakePlayerFactory.getMinecraft(level)
      saplingPlayer.setPos(
        edifySaplingPos.getX + 0.5,
        edifySaplingPos.getY,
        edifySaplingPos.getZ + 0.5
      )
      val saplingResult = OpEdifySapling.INSTANCE.execute(
        Seq[Iota](Vec3Iota(Vec3.atCenterOf(edifySaplingPos))).asJava,
        StaffCastEnv(saplingPlayer, InteractionHand.MAIN_HAND)
      )
      val saplingMode =
        saplingResult.component1().asInstanceOf[EdifySpellDuck].hexic$getEdifyMode()
      val saplingOk =
        saplingMode == 0 &&
          level.getBlockState(edifySaplingPos).is(Blocks.OAK_SAPLING)

      clearAt(edifyInvalidPos)
      level.setBlockAndUpdate(edifyInvalidPos, Blocks.STONE.defaultBlockState())
      val invalidPlayer = FakePlayerFactory.getMinecraft(level)
      invalidPlayer.setPos(
        edifyInvalidPos.getX + 0.5,
        edifyInvalidPos.getY,
        edifyInvalidPos.getZ + 0.5
      )
      val invalidRejected =
        try
          OpEdifySapling.INSTANCE.execute(
            Seq[Iota](Vec3Iota(Vec3.atCenterOf(edifyInvalidPos))).asJava,
            StaffCastEnv(invalidPlayer, InteractionHand.MAIN_HAND)
          )
          false
        catch
          case _: at.petrak.hexcasting.api.casting.mishaps.MishapBadBlock => true

      if woolOk && carpetOk && tripwireOk && saplingOk && invalidRejected then
        log.info(
          "[HEXIC-PROBE] edify_materials=PASS wool_mode={} wool_count={} carpet_mode={} carpet_count={} tripwire_mode={} tripwire_count={} sapling_mode={} invalid_rejected={}",
          woolMode,
          woolCount,
          carpetMode,
          carpetCount,
          tripwireMode,
          tripwireCount,
          saplingMode,
          invalidRejected
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] edify_materials=FAIL wool_mode={} wool_air={} wool_count={} wool_drops={} carpet_mode={} carpet_air={} carpet_count={} carpet_drops={} tripwire_mode={} tripwire_air={} tripwire_count={} tripwire_drops={} sapling_mode={} sapling_ok={} invalid_rejected={}",
          woolMode,
          woolAir,
          woolCount,
          woolDrops.mkString(","),
          carpetMode,
          carpetAir,
          carpetCount,
          carpetDrops.mkString(","),
          tripwireMode,
          tripwireAir,
          tripwireCount,
          tripwireDrops.mkString(","),
          saplingMode,
          saplingOk,
          invalidRejected
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] edify_materials=FAIL exception", t)
        1
    finally
      positions.foreach(clearAt)
      newlyForcedChunks.foreach: (chunkX, chunkZ) =>
        try level.setChunkForced(chunkX, chunkZ, false)
        catch case _: Throwable => ()

  private def checkCatMorph(server: net.minecraft.server.MinecraftServer): Int =
    try
      val level = server.overworld()
      val player = CatProbePlayer(
        level,
        GameProfile(
          UUID.fromString("f09ff2b8-e301-4ef0-8606-024788453163"),
          "HexicCatProbe"
        )
      )
      player.setPos(53.5, 80.0, 0.5)
      player.xOld = 52.75
      player.yOld = 79.75
      player.zOld = 0.25
      player.setYRot(73.0f)
      player.yRotO = 61.0f
      player.setXRot(17.0f)
      player.xRotO = 11.0f
      player.setYBodyRot(67.0f)
      player.yBodyRotO = 59.0f
      player.setYHeadRot(77.0f)
      player.yHeadRotO = 69.0f
      player.hurtTime = 4
      player.swinging = true
      player.swingingArm = InteractionHand.OFF_HAND
      player.swingTime = 3
      player.oAttackAnim = 0.25f
      player.attackAnim = 0.5f
      player.setPose(Pose.CROUCHING)
      val playerWalk = player.walkAnimation.asInstanceOf[hexic.mixin.LimbAnimatorAccess]
      playerWalk.prevSpeed = 0.2f
      playerWalk.speed = 0.4f
      playerWalk.pos = 1.75f

      val weave = ItemStack(Mediaweave.colors(DyeColor.BLUE), 1)
      weave.set(DataComponents.CUSTOM_NAME, ChatComponent.literal("instant cat"))
      val inserted = CuriosCompat.insertIntoFirstEmptySlot(player, weave)
      NeoForge.EVENT_BUS.post(
        top.theillusivec4.curios.api.event.CurioChangeEvent(
          player,
          "hexic_mediaweave",
          0,
          ItemStack.EMPTY,
          weave
        )
      )

      val cat = CatHolder.getSyncedCat(player)
      val replacement = CatMorphCompat.replacementForRender(player)
      val dimensions = player.getDimensions(Pose.CROUCHING)
      val cachedHeight = player.getBbHeight
      val catDimensions = Option(cat).map(_.getDimensions(Pose.CROUCHING)).orNull
      val catWalk =
        Option(cat)
          .map(_.walkAnimation.asInstanceOf[hexic.mixin.LimbAnimatorAccess])
          .orNull

      val normalPlayer = CatProbePlayer(
        level,
        GameProfile(
          UUID.fromString("c96b5301-16fc-475e-9781-0965149cc01d"),
          "HexicNotCatProbe"
        )
      )
      val normalReplacement = CatMorphCompat.replacementForRender(normalPlayer)

      def close(left: Double, right: Double): Boolean =
        math.abs(left - right) < 1.0e-5

      val dimensionsOk =
        catDimensions != null &&
          dimensions == catDimensions &&
          close(cachedHeight, catDimensions.height()) &&
          dimensions.height() < EntityType.PLAYER.getDimensions.height()
      val syncedTransform =
        cat != null &&
          close(cat.getX, player.getX) &&
          close(cat.getY, player.getY) &&
          close(cat.getZ, player.getZ) &&
          close(cat.xOld, player.xOld) &&
          close(cat.yOld, player.yOld) &&
          close(cat.zOld, player.zOld) &&
          close(cat.getYRot, player.getYRot) &&
          close(cat.yRotO, player.yRotO) &&
          close(cat.getXRot, player.getXRot) &&
          close(cat.xRotO, player.xRotO) &&
          close(cat.yBodyRot, player.yBodyRot) &&
          close(cat.yBodyRotO, player.yBodyRotO) &&
          close(cat.yHeadRot, player.yHeadRot) &&
          close(cat.yHeadRotO, player.yHeadRotO)
      val syncedAnimation =
        cat != null &&
          cat.swinging == player.swinging &&
          cat.swingingArm == player.swingingArm &&
          cat.swingTime == player.swingTime &&
          close(cat.oAttackAnim, player.oAttackAnim) &&
          close(cat.attackAnim, player.attackAnim) &&
          catWalk != null &&
          close(catWalk.prevSpeed, playerWalk.prevSpeed) &&
          close(catWalk.speed, playerWalk.speed) &&
          close(catWalk.pos, playerWalk.pos)
      val stateOk =
        cat != null &&
          cat.getCollarColor == DyeColor.BLUE &&
          cat.isInSittingPose &&
          cat.getPose == Pose.STANDING &&
          cat.isTame &&
          cat.isNoAi &&
          cat.isInvulnerable
      val renderDecisionOk =
        cat != null &&
          (replacement eq cat) &&
          CatMorphCompat.hidesPlayerHands(player) &&
          (normalReplacement eq normalPlayer) &&
          !CatMorphCompat.hidesPlayerHands(normalPlayer)

      // Damage handling advances player animation state. Keep the render-state
      // parity assertion above on the exact snapshot consumed by syncCat, then
      // exercise the hurt-sound redirect independently.
      player.handleDamageEvent(level.damageSources().generic())
      val hurtSoundOk = player.capturedSound == SoundEvents.CAT_HURT

      if inserted && dimensionsOk && syncedTransform && syncedAnimation && stateOk && renderDecisionOk && hurtSoundOk then
        log.info(
          "[HEXIC-PROBE] cat_morph=PASS inserted={} dimensions={} cached_height={} collar={} sitting={} transform_synced={} animation_synced={} hurt_sound={} render_replacement={} hide_hands={}",
          inserted,
          dimensions,
          cachedHeight,
          cat.getCollarColor,
          cat.isInSittingPose,
          syncedTransform,
          syncedAnimation,
          player.capturedSound,
          replacement.getType,
          CatMorphCompat.hidesPlayerHands(player)
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] cat_morph=FAIL inserted={} cat={} dimensions_ok={} dimensions={} cat_dimensions={} cached_height={} transform_synced={} animation_synced={} cat_swinging={} player_swinging={} cat_arm={} player_arm={} cat_swing_time={} player_swing_time={} cat_old_attack={} player_old_attack={} cat_attack={} player_attack={} cat_walk_old={} player_walk_old={} cat_walk_speed={} player_walk_speed={} cat_walk_pos={} player_walk_pos={} state_ok={} render_decision={} hurt_sound_ok={} captured_sound={} replacement={} normal_replacement={} hide_hands={}",
          inserted,
          cat,
          dimensionsOk,
          dimensions,
          catDimensions,
          cachedHeight,
          syncedTransform,
          syncedAnimation,
          Option(cat).exists(_.swinging),
          player.swinging,
          Option(cat).map(_.swingingArm).orNull,
          player.swingingArm,
          Option(cat).map(_.swingTime).getOrElse(-1),
          player.swingTime,
          Option(cat).map(_.oAttackAnim).getOrElse(Float.NaN),
          player.oAttackAnim,
          Option(cat).map(_.attackAnim).getOrElse(Float.NaN),
          player.attackAnim,
          Option(catWalk).map(_.prevSpeed).getOrElse(Float.NaN),
          playerWalk.prevSpeed,
          Option(catWalk).map(_.speed).getOrElse(Float.NaN),
          playerWalk.speed,
          Option(catWalk).map(_.pos).getOrElse(Float.NaN),
          playerWalk.pos,
          stateOk,
          renderDecisionOk,
          hurtSoundOk,
          player.capturedSound,
          replacement,
          normalReplacement,
          CatMorphCompat.hidesPlayerHands(player)
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] cat_morph=FAIL exception", t)
        1

  private def checkStaffPenAndEchoMemory(server: net.minecraft.server.MinecraftServer): Int =
    var channel: EmbeddedChannel = null
    try
      val resolvedTypes = ResolvedPatternType.values().toSeq.map(value => value.name() -> value).toMap
      val echoTypeName = "HEXIC$ECHO_SHARD_ABSORBED"
      val expectedPenTypes = DyeColor.values().toSeq.map: color =>
        s"HEXIC$$PEN_WITH_COLOR_${color.getName}" ->
          (color.getMapColor.col, color.getTextColor)
      val missingTypes =
        (echoTypeName +: expectedPenTypes.map(_._1)).filterNot(resolvedTypes.contains)
      val mismatchedTypes =
        expectedPenTypes.flatMap: (name, expectedColors) =>
          resolvedTypes.get(name) match
            case Some(value)
                if value.getColor != expectedColors._1 ||
                  value.getFadeColor != expectedColors._2 ||
                  !value.getSuccess =>
              Some(
                s"$name(actual=${value.getColor}/${value.getFadeColor}/${value.getSuccess}," +
                  s"expected=${expectedColors._1}/${expectedColors._2}/true)"
              )
            case _ => None
      val echoType = resolvedTypes.get(echoTypeName)
      val echoTypeOk = echoType.exists: value =>
        value.getColor == 0x0a5060 &&
          value.getFadeColor == 0x29dfeb &&
          value.getSuccess
      val enumTypesOk = missingTypes.isEmpty && mismatchedTypes.isEmpty && echoTypeOk
      val lightBluePenType = resolvedTypes.get("HEXIC$PEN_WITH_COLOR_light_blue")

      val profile = GameProfile(
        UUID.fromString("109e082f-a702-4c35-907c-15a65f5f8b78"),
        "HexicStaffEcho"
      )
      val player = ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault())
      player.setGameMode(GameType.SURVIVAL)
      val connection = Connection(PacketFlow.SERVERBOUND)
      channel = EmbeddedChannel()
      val channelField = classOf[Connection].getDeclaredField("channel")
      channelField.setAccessible(true)
      channelField.set(connection, channel)
      NetworkRegistry.configureMockConnection(connection)
      val listener = ServerGamePacketListenerImpl(
        server,
        connection,
        player,
        CommonListenerCookie.createInitial(profile, false)
      )
      val connected = player.connection eq listener

      val pattern = HexPattern.fromAngles("aqae", HexDir.SOUTH_EAST)
      def message(): (MsgNewSpellPatternC2S, ResolvedPattern) =
        val resolved = ResolvedPattern(pattern, HexCoord(0, 0), ResolvedPatternType.UNRESOLVED)
        val resolvedPatterns = java.util.ArrayList[ResolvedPattern]()
        resolvedPatterns.add(resolved)
        MsgNewSpellPatternC2S(InteractionHand.MAIN_HAND, pattern, resolvedPatterns) -> resolved

      IXplatAbstractions.INSTANCE.clearCastingData(player)
      player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Pen.instances(DyeColor.LIGHT_BLUE)))
      player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
      val (penMessage, penResolved) = message()
      StaffCastEnv.handleNewPatternOnServer(player, penMessage)
      val penImage = IXplatAbstractions.INSTANCE.getStaffcastVM(player, InteractionHand.MAIN_HAND).getImage
      val penUi = IXplatAbstractions.INSTANCE.getPatternsSavedInUi(player)
      val penOk =
        penImage.getStack.isEmpty &&
          lightBluePenType.contains(penResolved.getType) &&
          penUi.size == 1 &&
          lightBluePenType.contains(penUi.getFirst.getType)

      IXplatAbstractions.INSTANCE.clearCastingData(player)
      player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(HexItems.STAFF_OAK.get()))
      val shard = ItemStack(Items.ECHO_SHARD)
      val marker = CompoundTag()
      marker.putString("probe:keep", "preserved")
      CustomData.set(DataComponents.CUSTOM_DATA, shard, marker)
      shard.set(DataComponents.CUSTOM_NAME, ChatComponent.literal("Hexic probe shard"))
      player.setItemInHand(InteractionHand.OFF_HAND, shard)
      val (echoMessage, echoResolved) = message()
      StaffCastEnv.handleNewPatternOnServer(player, echoMessage)

      val captureImage = IXplatAbstractions.INSTANCE.getStaffcastVM(player, InteractionHand.MAIN_HAND).getImage
      val tooltip = java.util.ArrayList[ChatComponent]()
      EchoShardCompat.appendTooltip(shard, tooltip)
      val echoCaptured =
        captureImage.getStack.isEmpty &&
          EchoShardCompat.memoryCount(shard) == 1 &&
          echoType.contains(echoResolved.getType) &&
          tooltip.size == 1 &&
          tooltip.getFirst.getString.nonEmpty

      val useEvent = net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem(
        player,
        InteractionHand.OFF_HAND
      )
      EchoShardCompat.onRightClick(useEvent)
      val remainder = player.getItemInHand(InteractionHand.OFF_HAND)
      val castStack =
        IXplatAbstractions.INSTANCE
          .getStaffcastVM(player, InteractionHand.OFF_HAND)
          .getImage
          .getStack
          .asScala
          .toSeq
      val executedTrue = castStack.lastOption.exists:
        case boolean: BooleanIota => boolean.getBool
        case _ => false
      val customDataPreserved =
        Option(remainder.get(DataComponents.CUSTOM_DATA))
          .exists(_.copyTag().getString("probe:keep") == "preserved")
      val namePreserved =
        Option(remainder.get(DataComponents.CUSTOM_NAME))
          .exists(_.getString == "Hexic probe shard")
      val echoUseOk =
        useEvent.isCanceled &&
          useEvent.getCancellationResult == net.minecraft.world.InteractionResult.CONSUME &&
          executedTrue &&
          remainder.is(Items.ECHO_SHARD) &&
          remainder.getCount == 1 &&
          !EchoShardCompat.hasMemory(remainder) &&
          customDataPreserved &&
          namePreserved

      if connected && enumTypesOk && penOk && echoCaptured && echoUseOk then
        log.info(
          "[HEXIC-PROBE] staff_pen_echo=PASS connected={} resolved_type_extensions={} pen_intercepted={} pen_type={} echo_captured={} echo_type={} echo_executed={} memory_after={} custom_data_preserved={} custom_name_preserved={}",
          connected,
          enumTypesOk,
          penOk,
          penResolved.getType.name(),
          echoCaptured,
          echoResolved.getType.name(),
          executedTrue,
          EchoShardCompat.memoryCount(remainder),
          customDataPreserved,
          namePreserved
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] staff_pen_echo=FAIL connected={} enum_types_ok={} missing_types={} mismatched_types={} echo_type_ok={} pen_ok={} pen_type={} pen_stack={} pen_ui={} echo_captured={} echo_type={} echo_memory={} tooltip={} event_canceled={} cancellation={} cast_stack={} remainder={} custom_data_preserved={} custom_name_preserved={}",
          connected,
          enumTypesOk,
          missingTypes.mkString(","),
          mismatchedTypes.mkString(","),
          echoTypeOk,
          penOk,
          penResolved.getType.name(),
          penImage.getStack.size,
          penUi.size,
          echoCaptured,
          echoResolved.getType.name(),
          EchoShardCompat.memoryCount(shard),
          tooltip.size,
          useEvent.isCanceled,
          useEvent.getCancellationResult,
          castStack.map(_.getClass.getSimpleName).mkString(","),
          remainder,
          customDataPreserved,
          namePreserved
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] staff_pen_echo=FAIL exception", t)
        1
    finally
      if channel != null then
        channel.close()
        ()

  private def checkSpellmindCompat(): Int =
    try
      val original = CastingImage(
        TreeList.from(Seq[Iota](DoubleIota(7.0)).asJava),
        0,
        TreeList.empty(),
        false,
        false,
        3,
        CompoundTag()
      )
      val saved = SpellmindCompat.save(original)
      val changed = CastingImage(
        TreeList.from(Seq[Iota](DoubleIota(99.0)).asJava),
        0,
        TreeList.empty(),
        false,
        false,
        9,
        saved.getUserData
      )
      val restored = SpellmindCompat.restore(changed)
      val restoredStackOk = restored.exists: image =>
        image.getStack.asScala.toSeq match
          case Seq(value: DoubleIota) => value.getDouble == 7.0
          case _ => false
      val restoredOpsOk = restored.exists(_.getOpsConsumed == 3)
      val savedKeyOk = SpellmindCompat.hasSavedMind(saved)
      val restoreKeyOk = restored.exists(SpellmindCompat.hasSavedMind)
      val missingOk = SpellmindCompat.restore(original).isEmpty
      if restoredStackOk && restoredOpsOk && savedKeyOk && restoreKeyOk && missingOk then
        log.info(
          "[HEXIC-PROBE] spellmind=PASS saved_key={} restore_key={} missing_empty={} restored_ops={}",
          savedKeyOk,
          restoreKeyOk,
          missingOk,
          restored.map(_.getOpsConsumed).getOrElse(-1L)
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] spellmind=FAIL restored_stack={} restored_ops={} saved_key={} restore_key={} missing_empty={}",
          restoredStackOk,
          restoredOpsOk,
          savedKeyOk,
          restoreKeyOk,
          missingOk
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] spellmind=FAIL exception", t)
        1

  private def checkSurrogateDemiplane(server: net.minecraft.server.MinecraftServer): Int =
    try
      val planeId = UUID.fromString("b46969ff-542f-49d5-bd7a-77f47c4575ed")
      val location = ResourceLocation.fromNamespaceAndPath("hexic", s"fresh-${planeId.toString.replace("-", "")}")
      val handle = Fantasy.get(server).getOrOpenPersistentWorld(location, RuntimeWorldConfig())
      val secondPlaneId = UUID.fromString("a7e68f1c-4f95-45d3-9c85-157df5fb5f71")
      val secondLocation = ResourceLocation.fromNamespaceAndPath("hexic", s"fresh-${secondPlaneId.toString.replace("-", "")}")
      val secondHandle = Fantasy.get(server).getOrOpenPersistentWorld(secondLocation, RuntimeWorldConfig())
      val expectedParent = Level.OVERWORLD -> BlockPos(21, 80, 21)
      val translated = handle.planePos(BlockPos(1, 2, 3))
      handle.parentInfo = Some(expectedParent)
      val trueRuntimeLevelsOk =
        handle.usesTrueRuntimeLevel &&
          secondHandle.usesTrueRuntimeLevel &&
          handle.asWorld.dimension() == handle.asKey &&
          secondHandle.asWorld.dimension() == secondHandle.asKey &&
          (handle.asWorld ne secondHandle.asWorld) &&
          !Option(server.getLevel(RuntimeWorldHandle.CellLevelKey)).contains(handle.asWorld) &&
          !Option(server.getLevel(RuntimeWorldHandle.CellLevelKey)).contains(secondHandle.asWorld)
      val keyOk = handle.asKey.location() == location
      val originOk = handle.origin == BlockPos.ZERO && secondHandle.origin == BlockPos.ZERO
      val persistentWorldOk =
        Fantasy.get(server).getOrOpenPersistentWorld(location, RuntimeWorldConfig()).asWorld eq handle.asWorld
      val distinctWorldOk =
        handle.asKey != secondHandle.asKey && (handle.asWorld ne secondHandle.asWorld)
      val translationOk = translated == BlockPos(1, 2, 3)
      val parentOk = handle.parentInfo.contains(expectedParent)
      val parentPersistedOk =
        Fantasy.get(server).getOrOpenPersistentWorld(location, RuntimeWorldConfig()).parentInfo.contains(expectedParent)
      val dimensionIota = DimIota(handle.asKey)
      val iotaOps = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess())
      val encodedDimension = IotaType.TYPED_CODEC.encodeStart(iotaOps, dimensionIota).getOrThrow
      val decodedDimension = IotaType.TYPED_CODEC.parse(iotaOps, encodedDimension).getOrThrow
      val oneironautIotaOk = decodedDimension match
        case value: DimIota =>
          value.getWorldKey == handle.asKey && (value.toWorld(server) eq handle.asWorld)
        case _ => false
      def flushEntityManager(level: ServerLevel): Unit =
        val field = classOf[net.minecraft.server.level.ServerLevel].getDeclaredField("entityManager")
        field.setAccessible(true)
        val manager = field.get(level)
        manager.getClass.getMethod("tick").invoke(manager)
      def setEntityChunkVisibility(level: ServerLevel, chunkX: Int, chunkZ: Int, visibility: Visibility): Unit =
        val field = classOf[net.minecraft.server.level.ServerLevel].getDeclaredField("entityManager")
        field.setAccessible(true)
        val manager = field.get(level)
        manager
          .getClass
          .getMethod("updateChunkStatus", classOf[ChunkPos], classOf[Visibility])
          .invoke(manager, ChunkPos(chunkX, chunkZ), visibility)
      // addFreshEntity may accept an entity while its section is still hidden.
      // Deletion must be tested against a genuinely loaded/tracked entity,
      // which is the state reached when a player has entered a demiplane.
      handle.asWorld.getChunkSource.getChunk(0, 0, ChunkStatus.FULL, true)
      setEntityChunkVisibility(handle.asWorld, 0, 0, Visibility.TICKING)
      val residualEntity = ArmorStand(
        handle.asWorld,
        handle.origin.getX + 4.5,
        handle.origin.getY + 1.0,
        handle.origin.getZ + 4.5
      )
      val residualEntityId = residualEntity.getUUID
      val residualPlayer = ServerPlayer(
        server,
        handle.asWorld,
        GameProfile(UUID.fromString("b2914ac8-3218-4b5f-bae8-e7052352d55a"), "HexicDirectDelete"),
        ClientInformation.createDefault()
      )
      residualPlayer.moveTo(
        handle.origin.getX + 5.5,
        handle.origin.getY + 1.0,
        handle.origin.getZ + 5.5,
        0.0f,
        0.0f
      )
      handle.probeEvacuateResidualPlayer(residualPlayer)
      val directDeleteEntityAdded = handle.asWorld.addFreshEntity(residualEntity)
      val directDeletePlayerProbeRan = true
      flushEntityManager(handle.asWorld)
      handle.delete()
      flushEntityManager(handle.asWorld)
      val directDeleteEntityRemoved = residualEntity.isRemoved
      val directDeleteEntityStillTracked =
        handle.entities.exists(entity => entity.getUUID == residualEntityId && !entity.isRemoved)
      val directDeleteEntitiesClearedOk =
        directDeleteEntityRemoved && !directDeleteEntityStillTracked
      val directDeletePlayersClearedOk =
        directDeletePlayerProbeRan &&
          residualPlayer.isRemoved &&
          math.abs(residualPlayer.getX - expectedParent._2.getX - 0.5) < 0.001 &&
           math.abs(residualPlayer.getY - expectedParent._2.getY) < 0.001 &&
           math.abs(residualPlayer.getZ - expectedParent._2.getZ - 0.5) < 0.001
      secondHandle.delete()
      Fantasy.get(server).drainPendingForProbe()
      val worldsRemovedOk =
        server.getLevel(handle.asKey) == null && server.getLevel(secondHandle.asKey) == null
      if trueRuntimeLevelsOk && keyOk && originOk && persistentWorldOk && distinctWorldOk && translationOk && parentOk && parentPersistedOk && oneironautIotaOk && directDeleteEntityAdded && directDeleteEntitiesClearedOk && directDeletePlayerProbeRan && directDeletePlayersClearedOk && worldsRemovedOk then
        log.info(
          "[HEXIC-PROBE] runtime_demiplane=PASS true_levels={} key={} distinct_world={} origin={} translated={} parent_persisted={} oneironaut_iota={} worlds_removed={} direct_delete_entity_added={} direct_delete_entities_cleared={} direct_delete_player_probe={} direct_delete_players_cleared={}",
          trueRuntimeLevelsOk,
          handle.asKey.location(),
          distinctWorldOk,
          handle.origin,
          translated,
          parentPersistedOk,
          oneironautIotaOk,
          worldsRemovedOk,
          directDeleteEntityAdded,
          directDeleteEntitiesClearedOk,
          directDeletePlayerProbeRan,
          directDeletePlayersClearedOk
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] runtime_demiplane=FAIL true_levels={} key_ok={} origin_ok={} persistent_world={} distinct_world={} translation_ok={} parent_ok={} parent_persisted={} oneironaut_iota={} worlds_removed={} direct_delete_entity_added={} direct_delete_entities_cleared={} direct_delete_entity_removed={} direct_delete_entity_still_tracked={} direct_delete_removal_reason={} direct_delete_player_probe={} direct_delete_players_cleared={}",
          trueRuntimeLevelsOk,
          keyOk,
          originOk,
          persistentWorldOk,
          distinctWorldOk,
          translationOk,
          parentOk,
          parentPersistedOk,
          oneironautIotaOk,
          worldsRemovedOk,
          directDeleteEntityAdded,
          directDeleteEntitiesClearedOk,
          directDeleteEntityRemoved,
          directDeleteEntityStillTracked,
          residualEntity.getRemovalReason,
          directDeletePlayerProbeRan,
          directDeletePlayersClearedOk
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] runtime_demiplane=FAIL exception", t)
        1

  private def checkDemiplaneActions(server: net.minecraft.server.MinecraftServer): Int =
    var cleanupLocation: Option[ResourceLocation] = None
    var parentChunkForced = false
    try
      val actionRegistry = server.registryAccess().registryOrThrow(HexRegistries.ACTION)
      val makeAction = actionRegistry.get(id("makeworld")).action()
      val attachAction = actionRegistry.get(id("attachworld")).action()
      val deleteAction = actionRegistry.get(id("deleteworld")).action()
      def setEntityChunkVisibility(level: ServerLevel, chunkX: Int, chunkZ: Int, visibility: Visibility): Unit =
        val field = classOf[net.minecraft.server.level.ServerLevel].getDeclaredField("entityManager")
        field.setAccessible(true)
        val manager = field.get(level)
        manager
          .getClass
          .getMethod("updateChunkStatus", classOf[ChunkPos], classOf[Visibility])
          .invoke(manager, ChunkPos(chunkX, chunkZ), visibility)
      val parentLevel = server.overworld()
      val parentChunkX = 21 >> 4
      val parentChunkZ = 21 >> 4
      parentLevel.setChunkForced(parentChunkX, parentChunkZ, true)
      parentChunkForced = true
      parentLevel.getChunkSource.getChunk(parentChunkX, parentChunkZ, ChunkStatus.FULL, true)
      setEntityChunkVisibility(parentLevel, parentChunkX, parentChunkZ, Visibility.TICKING)
      val player = FakePlayerFactory.get(
        parentLevel,
        GameProfile(UUID.fromString("2a43d5d7-d14c-4447-870f-62e7e3c1e843"), "HexicPlaneProbe")
      )
      player.setGameMode(GameType.CREATIVE)
      player.moveTo(21.5, 80.0, 21.5, 0.0f, 0.0f)
      val env = StaffCastEnv(player, InteractionHand.MAIN_HAND)
      def probeImage(stack: Seq[Iota]): CastingImage =
        CastingImage(
          TreeList.from(stack.asJava),
          0,
          TreeList.empty(),
          false,
          false,
          0,
          CompoundTag()
        )
      def operateRegistered(action: Action, stack: Seq[Iota]) =
        action.operate(env, probeImage(stack), SpellContinuation.Done.INSTANCE)
      def castSpellSideEffect(action: Action, stack: Seq[Iota]): Boolean =
        operateRegistered(action, stack).getSideEffects.asScala.collectFirst:
          case attempt: OperatorSideEffect.AttemptSpell =>
            attempt.getSpell.cast(env)
            true
        .getOrElse(false)
      val makeResult = operateRegistered(makeAction, Seq.empty).getNewImage.getStack
      val plane = makeResult.asScala.collectFirst:
        case d: DimIota => d
      val location: Option[ResourceLocation] = plane.map(_.getWorldKey.location())
      cleanupLocation = location
      val border = BuiltInRegistries.BLOCK.get(id("border"))
      val madeHandle = location.map(loc => Fantasy.get(server).getOrOpenPersistentWorld(loc, RuntimeWorldConfig()))
      val origin = madeHandle.map(_.origin)
      val planeWorld = madeHandle.map(_.asWorld)
      val trueRuntimeDimensionOk = madeHandle.exists(handle =>
        handle.usesTrueRuntimeLevel &&
          handle.asWorld.dimension() == handle.asKey &&
          !Option(server.getLevel(RuntimeWorldHandle.CellLevelKey)).contains(handle.asWorld)
      )
      val borderAfterMake = madeHandle.exists(handle => handle.asWorld.getBlockState(handle.origin).is(border))
      def cellBox(pos: BlockPos): AABB =
        AABB(
          pos.getX.toDouble,
          pos.getY.toDouble,
          pos.getZ.toDouble,
          pos.getX + 11.0,
          pos.getY + 11.0,
          pos.getZ + 11.0
        )
      def allEntities(level: ServerLevel, box: AABB): Seq[Entity] =
        val predicate = new java.util.function.Predicate[Entity]:
          override def test(entity: Entity): Boolean = true
        (level.getEntities(null.asInstanceOf[Entity], box, predicate).asScala ++
          level.getAllEntities.asScala.filter(entity => box.intersects(entity.getBoundingBox)))
          .foldLeft(Vector.empty[Entity]): (entities, entity) =>
            if entities.exists(_.getUUID == entity.getUUID) then entities else entities :+ entity
      def flushEntityManager(level: ServerLevel): Unit =
        val field = classOf[net.minecraft.server.level.ServerLevel].getDeclaredField("entityManager")
        field.setAccessible(true)
        val manager = field.get(level)
        manager.getClass.getMethod("tick").invoke(manager)
      val attached = plane match
        case Some(dim) =>
          castSpellSideEffect(attachAction, Seq[Iota](dim, Vec3Iota(Vec3(21.5, 80.0, 21.5))))
        case None =>
          throw IllegalStateException("makeworld did not return a DimIota")
      val parentPersistedAfterAttach = location.exists: loc =>
        Fantasy.get(server).getOrOpenPersistentWorld(loc, RuntimeWorldConfig()).parentInfo.contains(Level.OVERWORLD -> BlockPos(21, 80, 21))
      var deleteProbeLiving: Option[ArmorStand] = None
      val dumpSeeded = madeHandle.exists: handle =>
        val level = handle.asWorld
        val pos = handle.origin
        val item = ItemEntity(
          level,
          pos.getX + 5.5,
          pos.getY + 5.5,
          pos.getZ + 5.5,
          ItemStack(Items.EMERALD, 4)
        )
        val xp = ExperienceOrb(
          level,
          pos.getX + 6.5,
          pos.getY + 5.5,
          pos.getZ + 5.5,
          7
        )
        val living = ArmorStand(
          level,
          pos.getX + 7.5,
          pos.getY + 5.5,
          pos.getZ + 5.5
        )
        deleteProbeLiving = Some(living)
        val itemAdded = level.addFreshEntity(item)
        val xpAdded = level.addFreshEntity(xp)
        val livingAdded = level.addFreshEntity(living)
        flushEntityManager(level)
        itemAdded && xpAdded && livingAdded
      val deleted = plane match
        case Some(dim) =>
          castSpellSideEffect(deleteAction, Seq[Iota](dim))
        case None =>
          throw IllegalStateException("makeworld did not return a DimIota")
      parentLevel.tick(new java.util.function.BooleanSupplier:
        override def getAsBoolean: Boolean = true
      )
      planeWorld.foreach(flushEntityManager)
      parentLevel.getChunkSource.getChunk(parentChunkX, parentChunkZ, ChunkStatus.FULL, true)
      flushEntityManager(parentLevel)
      val tetherBox = AABB.ofSize(Vec3(21.5, 80.5, 21.5), 5.0, 5.0, 5.0)
      val tetherEntities = allEntities(parentLevel, tetherBox)
      val dumpedItemOk = tetherEntities.collect:
        case item: ItemEntity if item.getItem.getItem == Items.EMERALD => item.getItem.getCount
      .sum >= 4
      val dumpedXpOk = tetherEntities.collect:
        case orb: ExperienceOrb => orb.getValue
      .sum >= 7
      tetherEntities.foreach:
        case item: ItemEntity if item.getItem.getItem == Items.EMERALD => item.discard()
        case orb: ExperienceOrb => orb.discard()
        case _ =>
      val planeEntitiesCleared = madeHandle.exists(handle => allEntities(handle.asWorld, cellBox(handle.origin)).isEmpty)
      val livingRemovedOk = deleteProbeLiving.exists(_.isRemoved)
      deleteProbeLiving.filterNot(_.isRemoved).foreach(_.discard())
      Fantasy.get(server).drainPendingForProbe()
      val runtimeWorldRemoved = madeHandle.exists(handle => server.getLevel(handle.asKey) == null)
      val headlessPlayer = ServerPlayer(
        server,
        parentLevel,
        GameProfile(UUID.fromString("0e7adf48-fd46-481a-8f89-dd154930df6b"), "HexicPlaneHeadless"),
        ClientInformation.createDefault()
      )
      JavaPlaneAccess.shatterDemiplanePlayer(headlessPlayer, parentLevel, Vec3(21.5, 80.5, 21.5))
      val headlessPlayerCleanupOk =
        headlessPlayer.isRemoved &&
          math.abs(headlessPlayer.getX - 21.5) < 0.001 &&
          math.abs(headlessPlayer.getY - 80.5) < 0.001 &&
          math.abs(headlessPlayer.getZ - 21.5) < 0.001
      val connectedProfile = GameProfile(UUID.fromString("f8c6eb8d-e563-424e-81d0-0de7b1f916aa"), "HexicPlaneConnected")
      val connectedPlayer = ServerPlayer(
        server,
        parentLevel,
        connectedProfile,
        ClientInformation.createDefault()
      )
      connectedPlayer.setGameMode(GameType.SURVIVAL)
      connectedPlayer.moveTo(20.5, 80.0, 20.5, 0.0f, 0.0f)
      val connectedConnection = new Connection(PacketFlow.SERVERBOUND)
      val connectedListener = ServerGamePacketListenerImpl(
        server,
        connectedConnection,
        connectedPlayer,
        CommonListenerCookie.createInitial(connectedProfile, false)
      )
      JavaPlaneAccess.shatterDemiplanePlayer(connectedPlayer, parentLevel, Vec3(21.5, 80.5, 21.5))
      val connectedPlayerBranchOk =
        (connectedPlayer.connection eq connectedListener)
          && math.abs(connectedPlayer.getX - 21.5) < 0.001
          && math.abs(connectedPlayer.getY - 80.5) < 0.001
          && math.abs(connectedPlayer.getZ - 21.5) < 0.001
          && (connectedPlayer.isRemoved || connectedPlayer.isDeadOrDying || connectedPlayer.getHealth <= 0.0f)
      val keyOk = location.exists(loc => loc.getNamespace == "hexic" && loc.getPath.startsWith("fresh-"))
      if keyOk && trueRuntimeDimensionOk && borderAfterMake && attached && parentPersistedAfterAttach && dumpSeeded && deleted && dumpedItemOk && dumpedXpOk && livingRemovedOk && planeEntitiesCleared && runtimeWorldRemoved && headlessPlayerCleanupOk && connectedPlayerBranchOk then
        log.info(
          "[HEXIC-PROBE] demiplane_actions=PASS key={} true_runtime_dimension={} border_after_make={} attached={} parent_persisted={} dump_seeded={} deleted={} dumped_item={} dumped_xp={} living_removed={} plane_entities_cleared={} runtime_world_removed={} headless_player_cleanup={} connected_player_branch={}",
          location.get,
          trueRuntimeDimensionOk,
          borderAfterMake,
          attached,
          parentPersistedAfterAttach,
          dumpSeeded,
          deleted,
          dumpedItemOk,
          dumpedXpOk,
          livingRemovedOk,
          planeEntitiesCleared,
          runtimeWorldRemoved,
          headlessPlayerCleanupOk,
          connectedPlayerBranchOk
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] demiplane_actions=FAIL key_ok={} true_runtime_dimension={} border_after_make={} attached={} parent_persisted={} dump_seeded={} deleted={} dumped_item={} dumped_xp={} living_removed={} plane_entities_cleared={} runtime_world_removed={} headless_player_cleanup={} connected_player_branch={} key={}",
          keyOk,
          trueRuntimeDimensionOk,
          borderAfterMake,
          attached,
          parentPersistedAfterAttach,
          dumpSeeded,
          deleted,
          dumpedItemOk,
          dumpedXpOk,
          livingRemovedOk,
          planeEntitiesCleared,
          runtimeWorldRemoved,
          headlessPlayerCleanupOk,
          connectedPlayerBranchOk,
          location.map(_.toString).getOrElse("missing")
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] demiplane_actions=FAIL exception", t)
        1
    finally
      cleanupLocation.foreach: loc =>
        try
          Fantasy.get(server).getOrOpenPersistentWorld(loc, RuntimeWorldConfig()).delete()
          Fantasy.get(server).drainPendingForProbe()
        catch case _: Throwable => ()
      if parentChunkForced then
        try server.overworld().setChunkForced(21 >> 4, 21 >> 4, false)
        catch case _: Throwable => ()

  private def checkHexalInterop(server: net.minecraft.server.MinecraftServer): Int =
    val accelerateProperty = "hexic.hexal.accelerateEntities"
    val costProperty = "hexic.hexal.fixAccelerateCost"
    val oldAccelerate = Option(System.getProperty(accelerateProperty))
    val oldCost = Option(System.getProperty(costProperty))
    var target: TickProbeArmorStand = null
    try
      val level = server.overworld()
      val player = FakePlayerFactory.get(
        level,
        GameProfile(UUID.fromString("8d346169-4182-48aa-860c-d88db78fd5da"), "HexicHexalProbe")
      )
      player.setGameMode(GameType.CREATIVE)
      player.moveTo(61.5, 80.0, 0.5, 0.0f, 0.0f)
      val env = StaffCastEnv(player, InteractionHand.MAIN_HAND)
      level.getChunkSource.getChunk(62 >> 4, 0, ChunkStatus.FULL, true)
      val entityManagerField = classOf[ServerLevel].getDeclaredField("entityManager")
      entityManagerField.setAccessible(true)
      val entityManager = entityManagerField.get(level)
      entityManager
        .getClass
        .getMethod("updateChunkStatus", classOf[ChunkPos], classOf[Visibility])
        .invoke(entityManager, ChunkPos(62 >> 4, 0), Visibility.TICKING)
      target = TickProbeArmorStand(level, 62.5, 80.0, 0.5)
      val entityAdded = level.addFreshEntity(target)
      entityManager.getClass.getMethod("tick").invoke(entityManager)
      val entityArgs = java.util.List.of[Iota](EntityIota(target))
      val action = OpTick.INSTANCE

      System.setProperty(accelerateProperty, "false")
      val disabledRejectsEntity =
        try
          action.executeWithUserdata(entityArgs, env, CompoundTag())
          false
        catch
          case _: MishapInvalidIota => true

      System.setProperty(accelerateProperty, "true")
      System.setProperty(costProperty, "false")
      val disabledCostData = CompoundTag()
      val disabledCostLedger = CompoundTag()
      disabledCostData.put(OpTick.TAG_TIMES_TICKED, disabledCostLedger)
      val disabledCostResult = action.executeWithUserdata(entityArgs, env, disabledCostData)
      val ledgerKey = target.blockPosition().toShortString
      val disabledCostLeavesLedger = disabledCostLedger.getInt(ledgerKey) == 0

      System.setProperty(costProperty, "true")
      val enabledData = CompoundTag()
      val enabledLedger = CompoundTag()
      enabledData.put(OpTick.TAG_TIMES_TICKED, enabledLedger)
      val directTicksBefore = target.directTickCalls
      val enabledResult = action.executeWithUserdata(entityArgs, env, enabledData)
      val executeLedger = enabledLedger.getInt(ledgerKey)
      val getEffectPos = enabledResult.getEffect.getClass.getDeclaredMethod("getPos")
      getEffectPos.setAccessible(true)
      val effectPos = getEffectPos.invoke(enabledResult.getEffect)
      val effectRetainedEntityTarget = effectPos.isInstanceOf[Interop]
      val image = CastingImage(
        TreeList.empty(),
        0,
        TreeList.empty(),
        false,
        false,
        0,
        enabledData
      )
      val castImage = enabledResult.getEffect.cast(env, image)
      val castLedger =
        castImage.getUserData
          .getCompound(OpTick.TAG_TIMES_TICKED)
          .getInt(ledgerKey)
      val entityTickedExactlyOnce = target.directTickCalls == directTicksBefore + 1
      val currentTargetResolved = level.getEntity(target.getUUID) eq target
      val costPreserved = enabledResult.getCost == disabledCostResult.getCost
      val ok =
        entityAdded &&
          currentTargetResolved &&
          disabledRejectsEntity &&
          disabledCostLeavesLedger &&
          executeLedger == 1 &&
          castLedger == 2 &&
          effectRetainedEntityTarget &&
          entityTickedExactlyOnce &&
          costPreserved
      if ok then
        log.info(
          "[HEXIC-PROBE] hexal_interop=PASS entity_added={} disabled_rejects_entity={} disabled_cost_ledger={} execute_ledger={} cast_ledger={} effect_entity_target={} entity_tick_delta={} cost_preserved={}",
          entityAdded,
          disabledRejectsEntity,
          disabledCostLedger.getInt(ledgerKey),
          executeLedger,
          castLedger,
          effectRetainedEntityTarget,
          target.directTickCalls - directTicksBefore,
          costPreserved
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] hexal_interop=FAIL entity_added={} target_resolved={} disabled_rejects_entity={} disabled_cost_ledger={} execute_ledger={} cast_ledger={} effect_entity_target={} effect_pos_type={} entity_tick_delta={} cost_preserved={}",
          entityAdded,
          currentTargetResolved,
          disabledRejectsEntity,
          disabledCostLedger.getInt(ledgerKey),
          executeLedger,
          castLedger,
          effectRetainedEntityTarget,
          effectPos.getClass.getName,
          target.directTickCalls - directTicksBefore,
          costPreserved
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] hexal_interop=FAIL exception", t)
        1
    finally
      if target != null && !target.isRemoved then target.discard()
      oldAccelerate match
        case Some(value) => System.setProperty(accelerateProperty, value)
        case None => System.clearProperty(accelerateProperty)
      oldCost match
        case Some(value) => System.setProperty(costProperty, value)
        case None => System.clearProperty(costProperty)

  private def checkHexicalHopperInterop(server: net.minecraft.server.MinecraftServer): Int =
    var target: ServerPlayer = null
    var channel: EmbeddedChannel = null
    try
      val level = server.overworld()
      val caster = FakePlayerFactory.get(
        level,
        GameProfile(
          UUID.fromString("5e0feaf1-ecea-46f0-bf89-758f67421872"),
          "HexicHexicalCaster"
        )
      )
      caster.moveTo(71.5, 80.0, 0.5, 0.0f, 0.0f)
      val targetProfile = GameProfile(
        UUID.fromString("9da92a68-95ba-44d3-a284-a36ed63fbf55"),
        "HexicHexicalTarget"
      )
      target = ServerPlayer(server, level, targetProfile, ClientInformation.createDefault())
      target.moveTo(72.5, 80.0, 0.5, 0.0f, 0.0f)
      val connection = Connection(PacketFlow.SERVERBOUND)
      channel = EmbeddedChannel()
      val channelField = classOf[Connection].getDeclaredField("channel")
      channelField.setAccessible(true)
      channelField.set(connection, channel)
      NetworkRegistry.configureMockConnection(connection)
      val listener = ServerGamePacketListenerImpl(
        server,
        connection,
        target,
        CommonListenerCookie.createInitial(targetProfile, false)
      )
      if !(target.connection eq listener) then
        throw IllegalStateException("Hexical hopper probe player connection was not installed")
      level.addNewPlayer(target)

      val env = StaffCastEnv(caster, InteractionHand.MAIN_HAND)
      val targetIota = EntityIota(target)
      val direct = HexicalHopperCompat.resolveForProbe(targetIota, env, Integer.valueOf(-1))
      val registered =
        HexicalHopperCompat.resolveThroughRegistryForProbe(targetIota, env, Integer.valueOf(-1))
      val directWrongSlot =
        HexicalHopperCompat.resolveForProbe(targetIota, env, Integer.valueOf(0))
      val registeredWrongSlot =
        HexicalHopperCompat.resolveThroughRegistryForProbe(targetIota, env, Integer.valueOf(0))

      val expectedClass =
        "miyucomics.hexical.features.hopper.targets.WristpocketEndpoint"
      val directTypeOk = direct != null && direct.getClass.getName == expectedClass
      val registeredTypeOk =
        registered != null && registered.getClass.getName == expectedClass

      val inserted = ItemStack(Items.DIAMOND, 3)
      val remainder =
        registered
          .getClass
          .getMethod("deposit", classOf[ItemStack])
          .invoke(registered, inserted)
          .asInstanceOf[ItemStack]
      val contents =
        registered
          .getClass
          .getMethod("getItems")
          .invoke(registered)
          .asInstanceOf[java.util.List[ItemStack]]
      val stored =
        contents.asScala.exists(stack =>
          stack.is(Items.DIAMOND) && stack.getCount == 3
        )
      val behaviorOk = remainder.isEmpty && stored
      val slotGuardOk = directWrongSlot == null && registeredWrongSlot == null
      val ok =
        HexicalHopperCompat.isRegistered &&
          directTypeOk &&
          registeredTypeOk &&
          behaviorOk &&
          slotGuardOk

      if ok then
        log.info(
          "[HEXIC-PROBE] hexical_hopper=PASS registered={} direct_type={} registry_type={} deposited={} wrong_slot_rejected={}",
          HexicalHopperCompat.isRegistered,
          direct.getClass.getName,
          registered.getClass.getName,
          stored,
          slotGuardOk
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] hexical_hopper=FAIL registered={} direct_type={} registry_type={} remainder={} contents={} direct_wrong_slot={} registry_wrong_slot={}",
          HexicalHopperCompat.isRegistered,
          Option(direct).fold("null")(_.getClass.getName),
          Option(registered).fold("null")(_.getClass.getName),
          remainder,
          contents,
          directWrongSlot,
          registeredWrongSlot
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] hexical_hopper=FAIL exception", t)
        1
    finally
      if target != null && !target.isRemoved then target.discard()
      if channel != null then channel.close()

  private def checkOneironautInterop(server: net.minecraft.server.MinecraftServer): Int =
    val manager = Fantasy.get(server)
    var cleanupHandles = Vector.empty[RuntimeWorldHandle]
    var probePlayer: ServerPlayer = null
    var channel: EmbeddedChannel = null
    val overworld = server.overworld()
    val parentPos = BlockPos(67, 80, 67)
    val floorPos = parentPos.below()
    val headPos = parentPos.above()
    val oldFloor = overworld.getBlockState(floorPos)
    val oldBody = overworld.getBlockState(parentPos)
    val oldHead = overworld.getBlockState(headPos)
    try
      def close(a: Double, b: Double): Boolean = math.abs(a - b) < 0.001
      def closeVec(actual: Vec3, expected: Vec3): Boolean =
        close(actual.x, expected.x) &&
          close(actual.y, expected.y) &&
          close(actual.z, expected.z)
      def setEntityChunkVisibility(level: ServerLevel, chunkX: Int, chunkZ: Int): Unit =
        val field = classOf[ServerLevel].getDeclaredField("entityManager")
        field.setAccessible(true)
        val entityManager = field.get(level)
        entityManager
          .getClass
          .getMethod("updateChunkStatus", classOf[ChunkPos], classOf[Visibility])
          .invoke(entityManager, ChunkPos(chunkX, chunkZ), Visibility.TICKING)

      val firstLocation = ResourceLocation.fromNamespaceAndPath(
        "hexic",
        "fresh-f31bf41778e84956be2b43201e55e11b"
      )
      val secondLocation = ResourceLocation.fromNamespaceAndPath(
        "hexic",
        "fresh-a46a43f75e61420791e1522598229648"
      )
      val first = manager.getOrOpenPersistentWorld(firstLocation, RuntimeWorldConfig())
      val second = manager.getOrOpenPersistentWorld(secondLocation, RuntimeWorldConfig())
      cleanupHandles :+= first
      cleanupHandles :+= second
      first.parentInfo = Some(Level.OVERWORLD -> parentPos)
      second.parentInfo = Some(Level.OVERWORLD -> parentPos)
      first.asWorld.getChunkSource.getChunk(0, 0, ChunkStatus.FULL, true)
      second.asWorld.getChunkSource.getChunk(0, 0, ChunkStatus.FULL, true)
      setEntityChunkVisibility(first.asWorld, 0, 0)
      setEntityChunkVisibility(second.asWorld, 0, 0)
      first.asWorld.setBlockAndUpdate(BlockPos(5, 0, 5), Blocks.STONE.defaultBlockState())

      overworld.setBlockAndUpdate(floorPos, Blocks.STONE.defaultBlockState())
      overworld.setBlockAndUpdate(parentPos, Blocks.AIR.defaultBlockState())
      overworld.setBlockAndUpdate(headPos, Blocks.AIR.defaultBlockState())

      val profile = GameProfile(
        UUID.fromString("c26ac6cf-25a2-41e2-998c-11709e346026"),
        "HexicOneironautProbe"
      )
      probePlayer = ServerPlayer(server, overworld, profile, ClientInformation.createDefault())
      probePlayer.moveTo(parentPos.getX + 0.5, parentPos.getY, parentPos.getZ + 0.5, 0.0f, 0.0f)
      val connection = Connection(PacketFlow.SERVERBOUND)
      channel = EmbeddedChannel()
      val channelField = classOf[Connection].getDeclaredField("channel")
      channelField.setAccessible(true)
      channelField.set(connection, channel)
      NetworkRegistry.configureMockConnection(connection)
      val listener = ServerGamePacketListenerImpl(
        server,
        connection,
        probePlayer,
        CommonListenerCookie.createInitial(profile, false)
      )
      overworld.addNewPlayer(probePlayer)

      val action = OpDimTeleport()
      val overworldEnv = StaffCastEnv(probePlayer, InteractionHand.MAIN_HAND)
      def execute(destination: DimIota, env: StaffCastEnv) =
        action.execute(
          java.util.List.of[Iota](EntityIota(probePlayer), destination),
          env
        )

      val ordinaryResult = execute(DimIota(Level.OVERWORLD), overworldEnv)
      val entryResult = execute(DimIota(first.asKey), overworldEnv)
      val entryAccess = entryResult.getEffect.asInstanceOf[OpDimTeleportSpellAccess]
      val displayText = DimIota(first.asKey).display().getString
      val expectedDisplay = Extern.getPocketName(first.asKey.location().toString).getString
      val displayNamed = displayText == expectedDisplay && displayText.startsWith("Demiplane ")
      val entryCostOk = entryResult.getCost == 5L * MediaConstants.SHARD_UNIT
      val ordinaryCostUnchanged = ordinaryResult.getCost == 0L
      entryResult.getEffect.cast(overworldEnv)
      val entryCoordsOk = closeVec(entryAccess.hexic$getCoords(), Vec3(5.5, 1.0, 5.5))
      val enteredTarget = entryAccess.hexic$getTarget()
      val enteredPlane =
        (enteredTarget.level() eq first.asWorld) &&
          close(enteredTarget.getX, 5.5) &&
          close(enteredTarget.getY, 1.0) &&
          close(enteredTarget.getZ, 5.5)
      val (excursionWorld, excursionPos) = JavaPlaneAccess.findExcursion(probePlayer)
      val excursionLogged =
        (excursionWorld eq overworld) &&
          closeVec(excursionPos, Vec3.atBottomCenterOf(parentPos))

      val planeEnv = StaffCastEnv(probePlayer, InteractionHand.MAIN_HAND)
      val betweenResult = execute(DimIota(second.asKey), planeEnv)
      val betweenCostOk = betweenResult.getCost == 0L
      val exitResult = execute(DimIota(Level.OVERWORLD), planeEnv)
      val exitAccess = exitResult.getEffect.asInstanceOf[OpDimTeleportSpellAccess]
      val exitCostOk = exitResult.getCost == 5L * MediaConstants.SHARD_UNIT
      exitResult.getEffect.cast(planeEnv)
      val expectedExit = Vec3.atBottomCenterOf(parentPos)
      val exitOriginRewritten = exitAccess.hexic$getOrigin() eq overworld
      val exitCoordsOk = closeVec(exitAccess.hexic$getCoords(), expectedExit)
      val returnedToExcursion =
        (probePlayer.serverLevel() eq overworld) &&
          closeVec(probePlayer.position(), expectedExit)
      val connectionReady = probePlayer.connection eq listener
      val ok =
        connectionReady &&
          displayNamed &&
          ordinaryCostUnchanged &&
          entryCostOk &&
          entryCoordsOk &&
          enteredPlane &&
          excursionLogged &&
          betweenCostOk &&
          exitCostOk &&
          exitOriginRewritten &&
          exitCoordsOk &&
          returnedToExcursion
      if ok then
        log.info(
          "[HEXIC-PROBE] oneironaut_interop=PASS connection={} display={} ordinary_cost={} entry_cost={} entry_coords={} entered_plane={} excursion_logged={} between_cost={} exit_cost={} exit_origin_rewritten={} exit_coords={} returned={}",
          connectionReady,
          displayText,
          ordinaryResult.getCost,
          entryResult.getCost,
          entryAccess.hexic$getCoords(),
          enteredPlane,
          excursionLogged,
          betweenResult.getCost,
          exitResult.getCost,
          exitOriginRewritten,
          exitAccess.hexic$getCoords(),
          returnedToExcursion
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] oneironaut_interop=FAIL connection={} display={} expected_display={} ordinary_cost={} entry_cost={} entry_coords={} entered_plane={} excursion_world={} excursion_pos={} between_cost={} exit_cost={} exit_origin={} exit_coords={} returned={} player_world={} player_pos={}",
          connectionReady,
          displayText,
          expectedDisplay,
          ordinaryResult.getCost,
          entryResult.getCost,
          entryAccess.hexic$getCoords(),
          enteredPlane,
          excursionWorld.dimension().location(),
          excursionPos,
          betweenResult.getCost,
          exitResult.getCost,
          exitAccess.hexic$getOrigin().dimension().location(),
          exitAccess.hexic$getCoords(),
          returnedToExcursion,
          probePlayer.serverLevel().dimension().location(),
          probePlayer.position()
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] oneironaut_interop=FAIL exception", t)
        1
    finally
      if probePlayer != null && !probePlayer.isRemoved then probePlayer.discard()
      cleanupHandles.foreach: handle =>
        try handle.delete()
        catch case _: Throwable => ()
      try manager.drainPendingForProbe()
      catch case _: Throwable => ()
      try overworld.setBlockAndUpdate(floorPos, oldFloor)
      catch case _: Throwable => ()
      try overworld.setBlockAndUpdate(parentPos, oldBody)
      catch case _: Throwable => ()
      try overworld.setBlockAndUpdate(headPos, oldHead)
      catch case _: Throwable => ()
      if channel != null then
        channel.close()
        ()

  private def checkVariantFluidCodec(server: net.minecraft.server.MinecraftServer): Int =
    try
      val original = VariantIota.ofFluid(Fluids.WATER)
      val encoded = IotaType.TYPED_CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow
      val decoded = IotaType.TYPED_CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow
      decoded match
        case variant: VariantIota if variant.isFluid && variant.fluid == Fluids.WATER && variant.toleratesOther(original) =>
          log.info("[HEXIC-PROBE] variant_fluid_codec=PASS encoded_type={} fluid={}", encoded.getClass.getName, BuiltInRegistries.FLUID.getKey(variant.fluid))
          0
        case other =>
          log.error("[HEXIC-PROBE] variant_fluid_codec=FAIL decoded_type={} decoded={}", Option(other).fold("null")(_.getClass.getName), other)
          1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] variant_fluid_codec=FAIL exception", t)
        1

  private def checkVariantItemComponents(server: net.minecraft.server.MinecraftServer): Int =
    try
      val plainStack = ItemStack(Items.DIAMOND)
      val namedStack = ItemStack(Items.DIAMOND)
      namedStack.set(DataComponents.CUSTOM_NAME, ChatComponent.literal("hexic-component-probe"))
      val plain = VariantIota.fromStack(plainStack)
      val named = VariantIota.fromStack(namedStack)
      val variantsAreDistinct = !plain.toleratesOther(named) && !named.toleratesOther(plain)
      val rebuilt = named.toStack(1)
      val rebuiltPreservesComponents = ItemStack.isSameItemSameComponents(namedStack, rebuilt)
      val ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess())
      val encoded = IotaType.TYPED_CODEC.encodeStart(ops, named).getOrThrow
      val decoded = IotaType.TYPED_CODEC.parse(ops, encoded).getOrThrow
      val codecPreservesComponents = decoded match
        case variant: VariantIota => variant.toleratesOther(named) && ItemStack.isSameItemSameComponents(namedStack, variant.toStack(1))
        case _ => false
      if variantsAreDistinct && rebuiltPreservesComponents && codecPreservesComponents then
        log.info(
          "[HEXIC-PROBE] variant_item_components=PASS distinct={} rebuilt_components={} codec_components={} encoded_type={}",
          variantsAreDistinct,
          rebuiltPreservesComponents,
          codecPreservesComponents,
          encoded.getClass.getName
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] variant_item_components=FAIL distinct={} rebuilt_components={} codec_components={} plain={} named={} rebuilt={}",
          variantsAreDistinct,
          rebuiltPreservesComponents,
          codecPreservesComponents,
          plain.display(),
          named.display(),
          rebuilt.getHoverName
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] variant_item_components=FAIL exception", t)
        1

  private def checkLegacyViewAndVariantCodecs(server: net.minecraft.server.MinecraftServer): Int =
    try
      val ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess())
      val block = BoxedView.BlockView(Level.OVERWORLD.location(), BlockPos(7, 81, -4))
      val entityId = UUID.fromString("1c29f8a3-6adc-4771-b36e-198ecf5bcf97")
      val entity = BoxedView.EntityView(entityId)
      val nested = BoxedView.Instance(
        BoxedView.SumView(Seq(block, BoxedView.SumView(Seq(entity, block))))
      )
      val currentEncoded = BoxedView.TYPE.codec().codec().encodeStart(ops, nested).getOrThrow
      val currentDecoded = BoxedView.TYPE.codec().codec().parse(ops, currentEncoded).getOrThrow
      val currentRoundTrip = currentDecoded.toleratesOther(nested)

      val legacyBlock = CompoundTag()
      legacyBlock.putString("id", "hexic:block")
      legacyBlock.putLong("p", block.pos.asLong)

      val legacyEntity = CompoundTag()
      legacyEntity.putString("id", "hexic:entity")
      legacyEntity.putLong("m", entityId.getMostSignificantBits)
      legacyEntity.putLong("l", entityId.getLeastSignificantBits)

      // Hexic 2.1.0's OfSum serializer accidentally omitted its own id. The
      // compatibility reader deliberately accepts this exact emitted shape.
      val legacyChildren = ListTag()
      legacyChildren.add(legacyBlock.copy())
      legacyChildren.add(legacyEntity.copy())
      val legacySum = CompoundTag()
      legacySum.put("c", legacyChildren)

      val decodedBlock = BoxedView.TYPE.codec().codec().parse(ops, legacyBlock).getOrThrow
      val decodedEntity = BoxedView.TYPE.codec().codec().parse(ops, legacyEntity).getOrThrow
      val decodedSum = BoxedView.TYPE.codec().codec().parse(ops, legacySum).getOrThrow
      val legacyViews =
        decodedBlock.view == block &&
          decodedEntity.view == entity &&
          (decodedSum.view match
            case BoxedView.SumView(views) => views == Seq(block, entity)
            case _ => false)

      val malformedRejected =
        BoxedView.parseValidated("block|not a dimension|x|y|z").isLeft &&
          BoxedView.parseValidated("entity|not-a-uuid").isLeft &&
          BoxedView.parseValidated("sum|block\\;broken").isLeft

      val legacyItemTag = CompoundTag()
      legacyItemTag.putString("type", "minecraft:item")
      legacyItemTag.putString("item", "minecraft:diamond")
      val legacyItemNbt = CompoundTag()
      val legacyDisplay = CompoundTag()
      legacyDisplay.putString("Name", """{"text":"hexic legacy item"}""")
      legacyItemNbt.put("display", legacyDisplay)
      legacyItemTag.put("tag", legacyItemNbt)
      val decodedItem = VariantIota.TYPE.codec().codec().parse(ops, legacyItemTag).getOrThrow
      val legacyItem =
        decodedItem.isItem &&
          decodedItem.item == Items.DIAMOND &&
          Option(decodedItem.toStack(1).get(DataComponents.CUSTOM_NAME))
            .exists(_.getString == "hexic legacy item")

      val legacyFluidTag = CompoundTag()
      legacyFluidTag.putString("type", "minecraft:fluid")
      legacyFluidTag.putString("fluid", "minecraft:water")
      val legacyFluidNbt = CompoundTag()
      legacyFluidNbt.putString("hexic_probe", "preserved")
      legacyFluidTag.put("tag", legacyFluidNbt)
      val decodedFluid = VariantIota.TYPE.codec().codec().parse(ops, legacyFluidTag).getOrThrow
      val legacyFluid =
        decodedFluid.isFluid &&
          decodedFluid.fluid == Fluids.WATER &&
          Option(decodedFluid.fluidStackPrototype.get(DataComponents.CUSTOM_DATA))
            .exists(_.copyTag().getString("hexic_probe") == "preserved")

      val legacyMediaTag = CompoundTag()
      legacyMediaTag.putString("type", "hexic:media")
      val legacyHeatTag = CompoundTag()
      legacyHeatTag.putString("type", "hexic:heat")
      val legacySingletons =
        VariantIota.TYPE.codec().codec().parse(ops, legacyMediaTag).getOrThrow.isMedia &&
          VariantIota.TYPE.codec().codec().parse(ops, legacyHeatTag).getOrThrow.isHeat

      if currentRoundTrip && legacyViews && malformedRejected && legacyItem && legacyFluid && legacySingletons then
        log.info(
          "[HEXIC-PROBE] legacy_view_variant_codecs=PASS current_nested={} legacy_views={} malformed_rejected={} legacy_item={} legacy_fluid={} legacy_singletons={}",
          currentRoundTrip,
          legacyViews,
          malformedRejected,
          legacyItem,
          legacyFluid,
          legacySingletons
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] legacy_view_variant_codecs=FAIL current_nested={} legacy_views={} malformed_rejected={} legacy_item={} legacy_item_name={} legacy_fluid={} legacy_singletons={} current_encoded={}",
          currentRoundTrip,
          legacyViews,
          malformedRejected,
          legacyItem,
          Option(decodedItem.toStack(1).get(DataComponents.CUSTOM_NAME)).map(_.getString).orNull,
          legacyFluid,
          legacySingletons,
          currentEncoded
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] legacy_view_variant_codecs=FAIL exception", t)
        1

  private def checkConfiguredStackCounts(server: net.minecraft.server.MinecraftServer): Int =
    try
      val ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess())
      val large = ItemStack(Items.DIAMOND, 128)
      val encodedLarge = ItemStack.CODEC.encodeStart(ops, large).getOrThrow
      val decodedLarge = ItemStack.CODEC.parse(ops, encodedLarge).getOrThrow
      val persistentLarge = decodedLarge.getCount == StackCountCompat.clamp(128)

      val largeBuffer = RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess())
      ItemStack.OPTIONAL_STREAM_CODEC.encode(largeBuffer, large)
      largeBuffer.readerIndex(0)
      val streamedLarge = ItemStack.OPTIONAL_STREAM_CODEC.decode(largeBuffer)
      val packetLarge = streamedLarge.getCount == StackCountCompat.clamp(128)

      val negative = ItemStack(Items.EMERALD, -4)
      val negativeInMemory = !negative.isEmpty && negative.getCount == -4
      val expectedNegative = StackCountCompat.clamp(-4)
      val encodedNegative = ItemStack.CODEC.encodeStart(ops, negative).getOrThrow
      val decodedNegative = ItemStack.CODEC.parse(ops, encodedNegative).getOrThrow
      val persistentNegative = decodedNegative.getCount == expectedNegative

      val negativeBuffer = RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess())
      ItemStack.OPTIONAL_STREAM_CODEC.encode(negativeBuffer, negative)
      negativeBuffer.readerIndex(0)
      val streamedNegative = ItemStack.OPTIONAL_STREAM_CODEC.decode(negativeBuffer)
      val packetNegative = streamedNegative.getCount == expectedNegative

      if persistentLarge && packetLarge && negativeInMemory && persistentNegative && packetNegative then
        log.info(
          "[HEXIC-PROBE] configured_stack_counts=PASS range={}..{} persistent_large={} packet_large={} negative_memory={} persistent_negative={} packet_negative={}",
          StackCountCompat.min(),
          StackCountCompat.max(),
          decodedLarge.getCount,
          streamedLarge.getCount,
          negative.getCount,
          decodedNegative.getCount,
          streamedNegative.getCount
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] configured_stack_counts=FAIL range={}..{} persistent_large={} packet_large={} negative_memory={} persistent_negative={} packet_negative={} expected_negative={} encoded_large={} encoded_negative={}",
          StackCountCompat.min(),
          StackCountCompat.max(),
          decodedLarge.getCount,
          streamedLarge.getCount,
          negativeInMemory,
          decodedNegative.getCount,
          streamedNegative.getCount,
          expectedNegative,
          encodedLarge,
          encodedNegative
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] configured_stack_counts=FAIL exception", t)
        1

  private def checkThinkAboutInterop(server: net.minecraft.server.MinecraftServer): Int =
    val level = server.overworld()
    var storageId: UUID | Null = null
    try
      val registry = server.registryAccess().registryOrThrow(HexRegistries.ACTION)
      val entry = registry.get(id("thinkaboutit"))
      val action =
        if entry == null then throw IllegalStateException("Missing hexic:thinkaboutit action")
        else entry.action()
      val env = StaffCastEnv(FakePlayerFactory.getMinecraft(level), InteractionHand.MAIN_HAND)
      def run(input: Iota): Seq[Iota] =
        val image = CastingImage(
          TreeList.from(Seq[Iota](input).asJava),
          0,
          TreeList.empty(),
          false,
          false,
          0,
          CompoundTag()
        )
        action
          .operate(env, image, SpellContinuation.Done.INSTANCE)
          .getNewImage
          .getStack
          .asScala
          .toSeq

      val namedStack = ItemStack(Items.DIAMOND, 4)
      namedStack.set(DataComponents.CUSTOM_NAME, ChatComponent.literal("hexic-thought-component-probe"))
      val stackResult = run(ItemStackIota.createFiltered(namedStack))
      val stackOk =
        stackResult.size == 1 &&
          (stackResult.head match
            case variant: VariantIota => variant.matchesItem(namedStack) && variant.toStack(1).getCount == 1
            case _ => false)

      val typeResult = run(ItemTypeIota(Items.EMERALD))
      val typeOk =
        typeResult.size == 1 &&
          (typeResult.head match
            case variant: VariantIota => variant.isItem && variant.item == Items.EMERALD
            case _ => false)

      level.setBlockAndUpdate(moteProbePos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(moteProbePos, HexalBlocks.MEDIAFIED_STORAGE.defaultBlockState())
      val storage = level.getBlockEntity(moteProbePos) match
        case value: BlockEntityMediafiedStorage => value
        case other => throw IllegalStateException(s"Expected mediafied storage, got ${Option(other).map(_.getClass.getName).getOrElse("null")}")
      storageId = storage.getUuid
      MediafiedItemManager.addStorage(storage.getUuid, storage)
      val mote = MoteIota.makeIfStorageLoaded(namedStack, storage.getUuid)
      val moteResult =
        if mote == null then Seq.empty[Iota]
        else run(mote)
      val moteOk =
        mote != null &&
          moteResult.size == 1 &&
          (moteResult.head match
            case variant: VariantIota => variant.matchesItem(namedStack) && ItemStack.isSameItemSameComponents(namedStack, variant.toStack(1))
            case _ => false)

      if stackOk && typeOk && moteOk then
        log.info(
          "[HEXIC-PROBE] thinkaboutit_interop=PASS item_stack={} item_type={} hexal_mote={} storage={}",
          stackOk,
          typeOk,
          moteOk,
          storage.getUuid
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] thinkaboutit_interop=FAIL item_stack={} item_type={} hexal_mote={} stack_result={} type_result={} mote_result={}",
          stackOk,
          typeOk,
          moteOk,
          stackResult,
          typeResult,
          moteResult
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] thinkaboutit_interop=FAIL exception", t)
        1
    finally
      if storageId != null then MediafiedItemManager.removeStorage(storageId.nn)
      level.setBlockAndUpdate(moteProbePos, Blocks.AIR.defaultBlockState())

  private def checkPackagedHexPigmentCompat(): Int =
    try
      val packaged = HexItems.CYPHER.get().asInstanceOf[ItemPackagedHex]
      val stack = ItemStack(packaged)
      val holder: PigmentHolderItem = packaged
      val pigment = FrozenPigment(ItemStack(HexItems.UUID_PIGMENT.get()), UUID.fromString("a16b0f67-2fa2-4868-8af5-6ab9de45d07f"))
      holder.setPigment(stack)(pigment)
      val restored = holder.getPigment(stack)
      val componentPresent = stack.has(HexDataComponents.PIGMENT.get())
      val sameOwner = restored.owner == pigment.owner
      val sameItem = restored.item.getItem == pigment.item.getItem
      if componentPresent && sameOwner && sameItem then
        log.info(
          "[HEXIC-PROBE] packaged_hex_pigment=PASS component_present={} owner={} item={}",
          componentPresent,
          restored.owner,
          BuiltInRegistries.ITEM.getKey(restored.item.getItem)
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] packaged_hex_pigment=FAIL component_present={} same_owner={} same_item={} item={}",
          componentPresent,
          sameOwner,
          sameItem,
          BuiltInRegistries.ITEM.getKey(restored.item.getItem)
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] packaged_hex_pigment=FAIL exception", t)
        1

  private def checkDyeOffpawAction(server: net.minecraft.server.MinecraftServer): Int =
    if !ModList.get().isLoaded("hexical") then
      log.info("[HEXIC-PROBE] dye_offpaw=SKIP hexical_not_loaded")
      0
    else
      try
        val level = server.overworld()
        val registry = server.registryAccess().registryOrThrow(HexRegistries.ACTION)
        val entry = registry.get(id("dye_offpaw"))
        if entry == null then
          log.error("[HEXIC-PROBE] dye_offpaw=FAIL action_missing")
          return 1

        val player = FakePlayerFactory.get(
          level,
          GameProfile(
            UUID.fromString("487ed090-9712-4658-bf39-fb9c26660344"),
            "HexicDyeOffpawProbe"
          )
        )
        val env = StaffCastEnv(player, InteractionHand.MAIN_HAND)
        val pigment = FrozenPigment(
          ItemStack(HexItems.UUID_PIGMENT.get()),
          UUID.fromString("c2cb756b-aedb-4e99-beee-873328c434ea")
        )
        val pigmentIota = Class
          .forName("miyucomics.hexical.features.pigments.PigmentIota")
          .getConstructor(classOf[FrozenPigment])
          .newInstance(pigment)
          .asInstanceOf[Iota]

        def castInto(held: ItemStack): (Boolean, Boolean) =
          player.setItemInHand(InteractionHand.OFF_HAND, held)
          val image = CastingImage(
            TreeList.from(Seq[Iota](pigmentIota).asJava),
            0,
            TreeList.empty(),
            false,
            false,
            0,
            CompoundTag()
          )
          val result =
            entry.action().operate(env, image, SpellContinuation.Done.INSTANCE)
          val spell = result.getSideEffects.asScala.collectFirst:
            case attempt: OperatorSideEffect.AttemptSpell => attempt.getSpell
          spell.foreach(_.cast(env))
          result.getNewImage.getStack.isEmpty -> spell.isDefined

        val packaged = ItemStack(HexItems.CYPHER.get())
        val (packagedStackConsumed, packagedSpellPresent) = castInto(packaged)
        val packagedPigment = Option(packaged.get(HexDataComponents.PIGMENT.get()))
        val packagedOk =
          packagedStackConsumed &&
            packagedSpellPresent &&
            packagedPigment.exists(restored =>
              restored.owner == pigment.owner &&
                ItemStack.isSameItemSameComponents(restored.item, pigment.item)
            )

        val stringworm = ItemStack(stringworms("pure"))
        val (wormStackConsumed, wormSpellPresent) = castInto(stringworm)
        val wormPigment = Option(stringworm.getSubNbt("pigment")).map(frozenPigmentFromNbt)
        val wormOk =
          wormStackConsumed &&
            wormSpellPresent &&
            stringworm.getItem == dyedStringworm &&
            wormPigment.exists(restored =>
              restored.owner == pigment.owner &&
                ItemStack.isSameItemSameComponents(restored.item, pigment.item)
            )

        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
        if packagedOk && wormOk then
          log.info(
            "[HEXIC-PROBE] dye_offpaw=PASS registry=true packaged=true stringworm=true"
          )
          0
        else
          log.error(
            "[HEXIC-PROBE] dye_offpaw=FAIL packaged={} stringworm={} packaged_stack_consumed={} packaged_spell={} worm_stack_consumed={} worm_spell={} worm_item={} worm_nbt={}",
            packagedOk,
            wormOk,
            packagedStackConsumed,
            packagedSpellPresent,
            wormStackConsumed,
            wormSpellPresent,
            BuiltInRegistries.ITEM.getKey(stringworm.getItem),
            wormPigment.isDefined
          )
          1
      catch
        case t: Throwable =>
          log.error("[HEXIC-PROBE] dye_offpaw=FAIL exception", t)
          1

  private def checkPlayerActionSemantics(server: net.minecraft.server.MinecraftServer): Int =
    val level = server.overworld()
    var channels = Vector.empty[EmbeddedChannel]
    var players = Vector.empty[ServerPlayer]
    try
      level.getChunkSource.getChunk(4, 0, ChunkStatus.FULL, true)

      def connectedPlayer(uuid: String, name: String, x: Double): ServerPlayer =
        val profile = GameProfile(UUID.fromString(uuid), name)
        val player = ServerPlayer(server, level, profile, ClientInformation.createDefault())
        player.moveTo(x, 80.0, 0.5, 0.0f, 0.0f)
        val connection = Connection(PacketFlow.SERVERBOUND)
        val channel = EmbeddedChannel()
        val channelField = classOf[Connection].getDeclaredField("channel")
        channelField.setAccessible(true)
        channelField.set(connection, channel)
        NetworkRegistry.configureMockConnection(connection)
        val listener = ServerGamePacketListenerImpl(
          server,
          connection,
          player,
          CommonListenerCookie.createInitial(profile, false)
        )
        if !(player.connection eq listener) then
          throw IllegalStateException(s"Could not install probe connection for $name")
        player.setGameMode(GameType.CREATIVE)
        level.addNewPlayer(player)
        channels :+= channel
        players :+= player
        player

      val caster = connectedPlayer(
        "139d0212-78d5-4665-b01f-760d2c3180bb",
        "HexicPlayerActionCaster",
        72.5
      )
      val other = connectedPlayer(
        "cd030846-d8c1-43e4-a18c-f5705ae9a66e",
        "HexicPlayerActionOther",
        73.5
      )
      val registry = server.registryAccess().registryOrThrow(HexRegistries.ACTION)
      val env = StaffCastEnv(caster, InteractionHand.MAIN_HAND)

      def image(stack: Seq[Iota]): CastingImage =
        CastingImage(
          TreeList.from(stack.asJava),
          0,
          TreeList.empty(),
          false,
          false,
          0,
          CompoundTag()
        )

      def operate(path: String, stack: Seq[Iota]) =
        val entry = registry.get(id(path))
        if entry == null then throw IllegalStateException(s"Missing hexic:$path action")
        entry.action().operate(env, image(stack), SpellContinuation.Done.INSTANCE)

      def castAttempts(result: at.petrak.hexcasting.api.casting.eval.OperationResult): Int =
        result.getSideEffects.asScala.count:
          case attempt: OperatorSideEffect.AttemptSpell =>
            attempt.getSpell.cast(env)
            true
          case _ => false

      val revealList = ListIota(Seq[Iota](DoubleIota(2.0), BooleanIota(true)).asJava)
      val revealResult = operate("reveal", Seq(revealList))
      val revealLines = caster.component[RevealComponent].lines
      val revealSet =
        revealResult.getNewImage.getStack.isEmpty &&
          revealLines.size == 2 &&
          revealLines.forall(_.getString.nonEmpty)
      val revealClearResult = operate("reveal", Seq(NullIota()))
      val revealOk =
        revealSet &&
          revealClearResult.getNewImage.getStack.isEmpty &&
          caster.component[RevealComponent].lines.isEmpty

      caster.component[MurmurCache].value = Some("hexic-murmur-probe")
      val murmurPresent = operate("murmur", Seq.empty).getNewImage.getStack.asScala.toSeq
      caster.component[MurmurCache].value = None
      val murmurMissing = operate("murmur", Seq.empty).getNewImage.getStack.asScala.toSeq
      val murmurOk =
        murmurPresent match
          case Seq(value: StringIota) if value.getString == "hexic-murmur-probe" =>
            murmurMissing.size == 1 && murmurMissing.head.isInstanceOf[NullIota]
          case _ => false

      val playerInfo: PlayerInfoComponent = other
      playerInfo.foxType = None
      val expectedFoxType = net.minecraft.world.entity.animal.Fox.Type.byBiome(
        other.level().getBiome(other.blockPosition())
      )
      val foxResult = operate("fox", Seq(EntityIota(other)))
      val foxCastCount = castAttempts(foxResult)
      val foxApplied = playerInfo.foxType.contains(expectedFoxType)
      val unfoxResult = operate("unfox", Seq(EntityIota(other)))
      val unfoxCastCount = castAttempts(unfoxResult)
      val foxOk =
        foxResult.getNewImage.getStack.isEmpty &&
          unfoxResult.getNewImage.getStack.isEmpty &&
          foxCastCount == 1 &&
          unfoxCastCount == 1 &&
          foxApplied &&
          playerInfo.foxType.isEmpty

      val weave = ItemStack(Mediaweave.colors(DyeColor.PURPLE), 1)
      val weaveInserted = CuriosCompat.insertIntoFirstEmptySlot(caster, weave)
      val context = top.theillusivec4.curios.api.SlotContext(
        "hexic_mediaweave",
        caster,
        0,
        false,
        true
      )
      val curio = CuriosApi.getCurio(weave).orElse(null)
      val initiallyUnlocked =
        curio != null &&
          curio.canUnequip(context) &&
          curio.getDropRule(context, level.damageSources().generic(), false) ==
            top.theillusivec4.curios.api.`type`.capability.ICurio.DropRule.DEFAULT
      val collarResult = operate("collar", Seq.empty)
      val collarCastCount = castAttempts(collarResult)
      val locked =
        Option(weave.getNbt).exists(_.contains("lock")) &&
          curio != null &&
          !curio.canUnequip(context) &&
          curio.getDropRule(context, level.damageSources().generic(), false) ==
            top.theillusivec4.curios.api.`type`.capability.ICurio.DropRule.ALWAYS_KEEP
      val decollarResult = operate("decollar", Seq.empty)
      val decollarCastCount = castAttempts(decollarResult)
      val unlockedAgain =
        Option(weave.getNbt).forall(!_.contains("lock")) &&
          curio != null &&
          curio.canUnequip(context) &&
          curio.getDropRule(context, level.damageSources().generic(), false) ==
            top.theillusivec4.curios.api.`type`.capability.ICurio.DropRule.DEFAULT
      val collarOk =
        weaveInserted &&
          initiallyUnlocked &&
          collarCastCount == 1 &&
          locked &&
          decollarCastCount == 1 &&
          unlockedAgain

      val otherCasterResult =
        operate("get_other_caster", Seq.empty).getNewImage.getStack.asScala.toSeq
      val otherCasterOk =
        otherCasterResult match
          case Seq(entity: EntityIota) =>
            entity.getEntity(level) eq other
          case _ => false

      val ok = revealOk && murmurOk && foxOk && collarOk && otherCasterOk
      if ok then
        log.info(
          "[HEXIC-PROBE] player_actions=PASS reveal=true murmur=true fox=true unfox=true collar=true decollar=true curio_lock=true get_other_caster=true"
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] player_actions=FAIL reveal={} murmur={} fox={} collar={} other_caster={} reveal_lines={} fox_casts={}/{} weave_inserted={} collar_casts={}/{} lock={} other_result={}",
          revealOk,
          murmurOk,
          foxOk,
          collarOk,
          otherCasterOk,
          revealLines.map(_.getString).mkString("|"),
          foxCastCount,
          unfoxCastCount,
          weaveInserted,
          collarCastCount,
          decollarCastCount,
          Option(weave.getNbt).map(_.toString).getOrElse("null"),
          otherCasterResult.map(_.getClass.getSimpleName).mkString(",")
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] player_actions=FAIL exception", t)
        1
    finally
      players.foreach: player =>
        ComponentStore.clear(player)
        if !player.isRemoved then player.discard()
      channels.foreach(_.close())

  private def checkMacroAndEntitySpellActions(server: net.minecraft.server.MinecraftServer): Int =
    val level = server.overworld()
    val player = FakePlayerFactory.get(
      level,
      GameProfile(
        UUID.fromString("3bf72255-d445-471e-bb86-24be1dc647c6"),
        "HexicMacroSpellProbe"
      )
    )
    var entities = Vector.empty[Entity]
    try
      level.getChunkSource.getChunk(4, 0, ChunkStatus.FULL, true)
      player.moveTo(74.5, 80.0, 0.5, 0.0f, 0.0f)
      player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(HexItems.STAFF_OAK.get()))
      player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
      val registry = server.registryAccess().registryOrThrow(HexRegistries.ACTION)
      val env = StaffCastEnv(player, InteractionHand.MAIN_HAND)

      def image(stack: Seq[Iota]): CastingImage =
        CastingImage(
          TreeList.from(stack.asJava),
          0,
          TreeList.empty(),
          false,
          false,
          0,
          CompoundTag()
        )

      def operate(path: String, stack: Seq[Iota]) =
        val entry = registry.get(id(path))
        if entry == null then throw IllegalStateException(s"Missing hexic:$path action")
        entry.action().operate(env, image(stack), SpellContinuation.Done.INSTANCE)

      def castAttempts(result: at.petrak.hexcasting.api.casting.eval.OperationResult): Int =
        result.getSideEffects.asScala.count:
          case attempt: OperatorSideEffect.AttemptSpell =>
            attempt.getSpell.cast(env)
            true
          case _ => false

      val macroPattern = HexPattern.fromAngles("qqqqq", HexDir.NORTH_EAST)
      // A macro stores an executable Hex, not a quoted value. A bare DoubleIota
      // correctly mishaps as an unescaped value, so execute a real zero-argument
      // action whose output is deterministic instead.
      val macroBodyPattern = registry.get(id("omni_close")).prototype()
      val macroValue =
        ListIota(java.util.List.of[Iota](PatternIota(macroBodyPattern)))
      val macroCarrier = ItemEntity(level, 75.5, 80.0, 0.5, ItemStack(Items.STICK))
      if !level.addFreshEntity(macroCarrier) then
        throw IllegalStateException("Could not add macro carrier item entity")
      entities :+= macroCarrier
      val createResult = operate(
        "mkmacro",
        Seq(
          PatternIota(macroPattern),
          StringIota.make("Hexic probe macro"),
          EntityIota(macroCarrier),
          macroValue
        )
      )
      val createCasts = castAttempts(createResult)
      given ServerLevel = level
      val storedMacro = macroCarrier.getItem.getMacro(macroPattern)
      val storedMacroOk = storedMacro.exists: definition =>
        val restored = definition.iota
        definition.name.contains("Hexic probe macro") &&
          (restored match
            case list: ListIota =>
              list.getList.asScala.toSeq match
                case Seq(pattern: PatternIota) =>
                  pattern.getPattern == macroBodyPattern
                case _ => false
            case _ => false)

      val macroStack = macroCarrier.getItem
      macroCarrier.discard()
      player.setItemInHand(InteractionHand.OFF_HAND, macroStack)
      val macroVm = CastingVM(CastingImage(), env)
      macroVm.queueExecuteAndWrapIota(PatternIota(macroPattern), level)
      val macroExecuted =
        macroVm.getImage.getStack.asScala.toSeq match
          case Seq(list: ListIota, number: DoubleIota) =>
            list.getList.asScala.isEmpty && number.getDouble == 0.0
          case _ => false

      player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
      val removeCarrier = ItemEntity(level, 75.5, 80.0, 0.5, macroStack)
      if !level.addFreshEntity(removeCarrier) then
        throw IllegalStateException("Could not add macro removal item entity")
      entities :+= removeCarrier
      val removeResult = operate(
        "mkmacro",
        Seq(PatternIota(macroPattern), EntityIota(removeCarrier), NullIota())
      )
      val removeCasts = castAttempts(removeResult)
      val macroRemoved = removeCarrier.getItem.getMacro(macroPattern).isEmpty
      val macroOk =
        createResult.getNewImage.getStack.isEmpty &&
          createCasts == 1 &&
          storedMacroOk &&
          macroExecuted &&
          removeResult.getNewImage.getStack.isEmpty &&
          removeCasts == 1 &&
          macroRemoved

      val blindTarget = EntityType.COW.create(level)
      if blindTarget == null then
        throw IllegalStateException("Could not construct blindness target")
      blindTarget.moveTo(76.5, 80.0, 0.5, 0.0f, 0.0f)
      if !level.addFreshEntity(blindTarget) then
        throw IllegalStateException("Could not add blindness target")
      entities :+= blindTarget
      player.setItemInHand(
        InteractionHand.OFF_HAND,
        ItemStack(HexItems.CHARGED_AMETHYST.get(), 1)
      )
      val blindResult =
        operate("blind", Seq(EntityIota(blindTarget), DoubleIota(1.0)))
      val blindCasts = castAttempts(blindResult)
      val blindOk =
        blindResult.getNewImage.getStack.isEmpty &&
          blindCasts == 1 &&
          blindTarget.hasEffect(MobEffects.BLINDNESS)

      val packaged = ItemStack(HexItems.CYPHER.get())
      val originalHolder = IXplatAbstractions.INSTANCE.findHexHolder(packaged)
      if originalHolder == null then
        throw IllegalStateException("Cypher did not expose an ADHexHolder")
      originalHolder.writeHex(
        java.util.List.of[Iota](PatternIota(HexPattern.fromAngles("aqae", HexDir.EAST))),
        null,
        MediaConstants.DUST_UNIT
      )
      val hadHex = originalHolder.hasHex
      val eraseTarget = ItemEntity(level, 77.5, 80.0, 0.5, packaged)
      if !level.addFreshEntity(eraseTarget) then
        throw IllegalStateException("Could not add erase target")
      entities :+= eraseTarget
      val eraseResult = operate("erase", Seq(EntityIota(eraseTarget)))
      val eraseCasts = castAttempts(eraseResult)
      val erasedHolder = IXplatAbstractions.INSTANCE.findHexHolder(eraseTarget.getItem)
      val eraseOk =
        hadHex &&
          eraseResult.getNewImage.getStack.isEmpty &&
          eraseCasts == 1 &&
          erasedHolder != null &&
          !erasedHolder.hasHex

      val ok = macroOk && blindOk && eraseOk
      if ok then
        log.info(
          "[HEXIC-PROBE] macro_entity_spells=PASS mkmacro_store=true mkmacro_execute=true mkmacro_remove=true blind=true erase=true"
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] macro_entity_spells=FAIL macro={} stored={} executed={} removed={} create_casts={} remove_casts={} blind={} blind_casts={} erase={} erase_casts={} had_hex={} has_hex_after={}",
          macroOk,
          storedMacroOk,
          macroExecuted,
          macroRemoved,
          createCasts,
          removeCasts,
          blindOk,
          blindCasts,
          eraseOk,
          eraseCasts,
          hadHex,
          Option(erasedHolder).exists(_.hasHex)
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] macro_entity_spells=FAIL exception", t)
        1
    finally
      player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
      player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
      entities.foreach(entity => if !entity.isRemoved then entity.discard())

  private def checkStaffcastAndConcurrentActions(server: net.minecraft.server.MinecraftServer): Int =
    var channel: EmbeddedChannel = null
    try
      val level = server.overworld()
      val profile = GameProfile(
        UUID.fromString("45791196-ab9d-437c-8a3b-781290ab39fc"),
        "HexicStaffcastConcurrentProbe"
      )
      val player = ServerPlayer(server, level, profile, ClientInformation.createDefault())
      val connection = Connection(PacketFlow.SERVERBOUND)
      channel = EmbeddedChannel()
      val channelField = classOf[Connection].getDeclaredField("channel")
      channelField.setAccessible(true)
      channelField.set(connection, channel)
      NetworkRegistry.configureMockConnection(connection)
      val listener = ServerGamePacketListenerImpl(
        server,
        connection,
        player,
        CommonListenerCookie.createInitial(profile, false)
      )
      if !(player.connection eq listener) then
        throw IllegalStateException("Could not install staffcast probe connection")
      player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(HexItems.STAFF_OAK.get()))
      IXplatAbstractions.INSTANCE.clearCastingData(player)

      val registry = server.registryAccess().registryOrThrow(HexRegistries.ACTION)
      val env = StaffCastEnv(player, InteractionHand.MAIN_HAND)
      def image(stack: Seq[Iota]): CastingImage =
        CastingImage(
          TreeList.from(stack.asJava),
          0,
          TreeList.empty(),
          false,
          false,
          0,
          CompoundTag()
        )
      def operate(path: String, stack: Seq[Iota]) =
        val entry = registry.get(id(path))
        if entry == null then throw IllegalStateException(s"Missing hexic:$path action")
        entry.action().operate(env, image(stack), SpellContinuation.Done.INSTANCE)
      def doubles(stack: java.util.List[Iota]): Seq[Double] =
        stack.asScala.toSeq.collect:
          case number: DoubleIota => number.getDouble

      val omniClosePattern = registry.get(id("omni_close")).prototype()
      val directExecutable =
        ListIota(java.util.List.of[Iota](PatternIota(omniClosePattern)))
      val directResult = operate("staffcast_factory", Seq(directExecutable))
      val directStack = directResult.getNewImage.getStack.asScala.toSeq
      val directOk =
        directStack match
          case Seq(list: ListIota, number: DoubleIota) =>
            list.getList.asScala.isEmpty && number.getDouble == 0.0
          case _ => false

      IXplatAbstractions.INSTANCE.setStaffcastImage(player, image(Seq(DoubleIota(3.0))))
      val lazyExecutable =
        ListIota(java.util.List.of[Iota](PatternIota(omniClosePattern)))
      val lazyResult = operate("staffcast_factory/lazy", Seq(lazyExecutable))
      val lazyOuterEmpty = lazyResult.getNewImage.getStack.isEmpty
      val lazyStaffStack =
        IXplatAbstractions.INSTANCE
          .getStaffcastVM(player, InteractionHand.MAIN_HAND)
          .getImage
          .getStack
          .asScala
          .toSeq
      val lazyOk =
        lazyOuterEmpty &&
          (lazyStaffStack match
            case Seq(seed: DoubleIota, list: ListIota, result: DoubleIota) =>
              seed.getDouble == 3.0 &&
                list.getList.asScala.isEmpty &&
                result.getDouble == 0.0
            case _ => false)

      val emptyFunction = ListIota(java.util.List.of[Iota]())
      val arguments =
        ListIota(java.util.List.of[Iota](DoubleIota(1.0), DoubleIota(2.0)))
      val concurrentResult = operate("make_cme", Seq(emptyFunction, arguments))
      val concurrentValues =
        concurrentResult.getNewImage.getStack.asScala.toSeq match
          case Seq(result: ListIota) =>
            result.getList.asScala.toSeq.collect:
              case number: DoubleIota => number.getDouble
          case _ => Seq.empty
      val concurrentOk = concurrentValues == Seq(1.0, 2.0)

      val emptyArguments = ListIota(java.util.List.of[Iota]())
      val emptyConcurrentResult = operate("make_cme", Seq(emptyFunction, emptyArguments))
      val emptyConcurrentOk =
        emptyConcurrentResult.getNewImage.getStack.asScala.toSeq match
          case Seq(result: ListIota) => result.getList.asScala.isEmpty
          case _ => false

      val ok = directOk && lazyOk && concurrentOk && emptyConcurrentOk
      if ok then
        log.info(
          "[HEXIC-PROBE] staffcast_concurrent=PASS direct={} lazy_staff={} concurrent={} empty_arguments=true",
          directStack.map(_.getClass.getSimpleName).mkString(","),
          lazyStaffStack.map(_.getClass.getSimpleName).mkString(","),
          concurrentValues.mkString(",")
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] staffcast_concurrent=FAIL direct={} lazy_outer_empty={} lazy_staff={} concurrent={} empty_arguments={}",
          directStack.map(_.getClass.getSimpleName).mkString(","),
          lazyOuterEmpty,
          lazyStaffStack.map(_.getClass.getSimpleName).mkString(","),
          concurrentValues.mkString(","),
          emptyConcurrentOk
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] staffcast_concurrent=FAIL exception", t)
        1
    finally
      if channel != null then channel.close()

  private def checkCoreActionSemantics(server: net.minecraft.server.MinecraftServer): Int =
    try
      val level = server.overworld()
      val registry = server.registryAccess().registryOrThrow(HexRegistries.ACTION)
      val player = FakePlayerFactory.get(
        level,
        GameProfile(
          UUID.fromString("8b7f0eed-b442-45e6-9fc8-0198bd0bbcfa"),
          "HexicCoreActionProbe"
        )
      )
      val env = StaffCastEnv(player, InteractionHand.MAIN_HAND)

      def image(stack: Seq[Iota]): CastingImage =
        CastingImage(
          TreeList.from(stack.asJava),
          0,
          TreeList.empty(),
          false,
          false,
          0,
          CompoundTag()
        )

      def operate(path: String, stack: Seq[Iota]) =
        val entry = registry.get(id(path))
        if entry == null then throw IllegalStateException(s"Missing hexic:$path action")
        entry.action().operate(env, image(stack), SpellContinuation.Done.INSTANCE)

      def output(path: String, stack: Seq[Iota]): Seq[Iota] =
        operate(path, stack).getNewImage.getStack.asScala.toSeq

      def numbers(values: Double*): ListIota =
        ListIota(values.map(DoubleIota(_): Iota).asJava)

      def numberValues(iota: Iota): Seq[Double] =
        iota match
          case list: ListIota =>
            list.getList.asScala.toSeq.map:
              case number: DoubleIota => number.getDouble
              case other => throw IllegalStateException(s"Expected number in list, got $other")
          case other => throw IllegalStateException(s"Expected list, got $other")

      val where = output(
        "where",
        Seq(ListIota(Seq[Iota](BooleanIota(true), BooleanIota(false), DoubleIota(2)).asJava))
      )
      val whereOk = where.size == 1 && numberValues(where.head) == Seq(0.0, 2.0, 2.0)

      val rotatePositive = output("rotate", Seq(numbers(1, 2, 3), DoubleIota(1)))
      val rotateNegative = output("rotate", Seq(numbers(1, 2, 3), DoubleIota(-1)))
      val rotateOk =
        rotatePositive.size == 1 &&
          numberValues(rotatePositive.head) == Seq(2.0, 3.0, 1.0) &&
          rotateNegative.size == 1 &&
          numberValues(rotateNegative.head) == Seq(3.0, 1.0, 2.0)

      val takeCount = output("take", Seq(numbers(1, 2, 3), DoubleIota(2)))
      val takeIndices = output("take", Seq(numbers(1, 2, 3), numbers(0, 2)))
      val takeOk =
        takeCount.size == 1 &&
          numberValues(takeCount.head) == Seq(1.0, 2.0) &&
          takeIndices.size == 1 &&
          numberValues(takeIndices.head) == Seq(1.0, 3.0)

      val dropCount = output("drop", Seq(numbers(1, 2, 3), DoubleIota(1)))
      val dropIndices = output("drop", Seq(numbers(1, 2, 3), numbers(1)))
      val dropOk =
        dropCount.size == 1 &&
          numberValues(dropCount.head) == Seq(2.0, 3.0) &&
          dropIndices.size == 1 &&
          numberValues(dropIndices.head) == Seq(1.0, 3.0)

      val extract = output("extract", Seq(numbers(1, 2, 3), DoubleIota(1)))
      val extractOk =
        extract.size == 3 &&
          numberValues(extract(0)) == Seq(1.0) &&
          numberValues(extract(1)) == Seq(3.0) &&
          extract(2).isInstanceOf[DoubleIota] &&
          extract(2).asInstanceOf[DoubleIota].getDouble == 2.0

      val grep = output(
        "grep",
        Seq(
          ListIota(Seq[Iota](BooleanIota(true), BooleanIota(false)).asJava),
          ListIota(Collections.emptyList[Iota]())
        )
      )
      val grepValues =
        grep.headOption.collect { case list: ListIota => list.getList.asScala.toSeq }
          .getOrElse(Seq.empty)
      val grepOk =
        grep.size == 1 &&
          grepValues.size == 1 &&
          grepValues.headOption.exists {
            case value: BooleanIota => value.getBool
            case _                  => false
          }

      val connect = output(
        "connect",
        Seq(numbers(7), ListIota(Collections.emptyList[Iota]()))
      )
      val connectOk = connect.size == 1 && numberValues(connect.head) == Seq(7.0)

      val omniClose = output("omni_close", Seq.empty)
      val omniCloseOk =
        omniClose.size == 2 &&
          omniClose.head.isInstanceOf[ListIota] &&
          omniClose.head.asInstanceOf[ListIota].getList.asScala.isEmpty &&
          omniClose(1).isInstanceOf[DoubleIota] &&
          omniClose(1).asInstanceOf[DoubleIota].getDouble == 0.0
      val omniOpen = operate("omni_open", Seq(DoubleIota(11), DoubleIota(22), DoubleIota(1))).getNewImage
      val omniOpenOk =
        omniOpen.getParenCount == 1 &&
          omniOpen.getStack.asScala.toSeq.collect { case value: DoubleIota => value.getDouble } == Seq(11.0, 22.0)

      def propertyAccess(path: String, readonly: Boolean = false): Option[PropertyAccessIota] =
        output(path, Seq(PropertyIota("hexic_probe_property", readonly))).collectFirst:
          case access: PropertyAccessIota => access
      val propertyParts = Seq(
        "prop_fi" -> ("add", "head"),
        "prop_fo" -> ("remove", "head"),
        "prop_li" -> ("add", "tail"),
        "prop_lo" -> ("remove", "tail")
      )
      val propertyOk = propertyParts.forall: (path, expected) =>
        propertyAccess(path).exists: access =>
          val parts = PropertyAccessIota.Type.split(access.serializeTag())
          parts._2 == expected._1 && parts._3 == expected._2
      val readonlyRejected =
        try
          propertyAccess("prop_fi", readonly = true)
          false
        catch
          case _: MishapInvalidIota => true
      val streamProperty = "hexic_probe_stream_semantics"
      StateStorage.Companion.setProperty(level, streamProperty, numbers(1, 2))
      val headStream = PropertyAccessIota.Stream(streamProperty, "head")(using level)
      val headTaken = headStream.take()
      val afterHead = StateStorage.Companion.getProperty(level, streamProperty)
      val tailStream = PropertyAccessIota.Stream(streamProperty, "tail")(using level)
      val tailTaken = tailStream.take()
      val afterTail = StateStorage.Companion.getProperty(level, streamProperty)
      val emptyTaken = tailStream.take()
      val propertyStreamOk =
        headTaken.isInstanceOf[DoubleIota] &&
          headTaken.asInstanceOf[DoubleIota].getDouble == 1.0 &&
          numberValues(afterHead) == Seq(2.0) &&
          tailTaken.isInstanceOf[DoubleIota] &&
          tailTaken.asInstanceOf[DoubleIota].getDouble == 2.0 &&
          numberValues(afterTail).isEmpty &&
          emptyTaken.isInstanceOf[NullIota]
      val writerProperty = "hexic_probe_writer_semantics"
      StateStorage.Companion.setProperty(level, writerProperty, NullIota())
      PropertyAccessIota.Writer(writerProperty, "head")(using level) << DoubleIota(4)
      PropertyAccessIota.Writer(writerProperty, "tail")(using level) << DoubleIota(5)
      val propertyWriterOk =
        numberValues(StateStorage.Companion.getProperty(level, writerProperty)) == Seq(4.0, 5.0)

      val ok =
        whereOk &&
          rotateOk &&
          takeOk &&
          dropOk &&
          extractOk &&
          grepOk &&
          connectOk &&
          omniCloseOk &&
          omniOpenOk &&
          propertyOk &&
          readonlyRejected &&
          propertyStreamOk &&
          propertyWriterOk
      if ok then
        log.info(
          "[HEXIC-PROBE] core_actions=PASS where=true rotate=true take=true drop=true extract=true grep=true connect=true omni=true property_access=true readonly_rejected=true property_stream=true property_writer=true"
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] core_actions=FAIL where={} rotate={} take={} drop={} extract={} grep={} connect={} omni_close={} omni_open={} property_access={} readonly_rejected={} property_stream={} property_writer={}",
          whereOk,
          rotateOk,
          takeOk,
          dropOk,
          extractOk,
          grepOk,
          connectOk,
          omniCloseOk,
          omniOpenOk,
          propertyOk,
          readonlyRejected,
          propertyStreamOk,
          propertyWriterOk
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] core_actions=FAIL exception", t)
        1

  private def checkEngineActionSemantics(server: net.minecraft.server.MinecraftServer): Int =
    try
      val level = server.overworld()
      val registry = server.registryAccess().registryOrThrow(HexRegistries.ACTION)
      val player = FakePlayerFactory.get(
        level,
        GameProfile(
          UUID.fromString("717dfd66-4da7-44aa-8cad-bf19d5aebed7"),
          "HexicEngineActionProbe"
        )
      )
      val env = StaffCastEnv(player, InteractionHand.MAIN_HAND)
      val expectedPos = BlockPos(9, 81, -7)
      var suspendedStack = Seq.empty[Iota]
      var sleptStack = Seq.empty[Iota]
      var sleptTicks = -1

      def image(stack: Seq[Iota]): CastingImage =
        CastingImage(
          TreeList.from(stack.asJava),
          0,
          TreeList.empty(),
          false,
          false,
          0,
          CompoundTag()
        )

      def operate(path: String, stack: Seq[Iota]) =
        val entry = registry.get(id(path))
        if entry == null then throw IllegalStateException(s"Missing hexic:$path action")
        entry.action().operate(env, image(stack), SpellContinuation.Done.INSTANCE)

      val access = new CastingEngine.Access:
        override def pos: BlockPos = expectedPos
        override def world: Level = level
        override def terminate(): Nothing = throw EngineProbeSignal("terminate")
        override def suspend(img: CastingImage, cont: SpellContinuation): Nothing =
          suspendedStack = img.getStack.asScala.toSeq
          throw EngineProbeSignal("suspend")
        override def sleep(img: CastingImage, cont: SpellContinuation, ticks: Int): Nothing =
          sleptStack = img.getStack.asScala.toSeq
          sleptTicks = ticks
          throw EngineProbeSignal("sleep")

      var posOk = false
      var terminateOk = false
      var suspendOk = false
      var sleepOk = false
      CastingEngine.Access.scope.enter(access):
        val position = operate("engine/pos", Seq.empty).getNewImage.getStack.asScala.toSeq
        posOk =
          position.collectFirst { case vector: Vec3Iota => vector.getVec3 }
            .contains(Vec3.atCenterOf(expectedPos))
        try operate("engine/terminate", Seq.empty)
        catch case signal: EngineProbeSignal if signal.kind == "terminate" => terminateOk = true
        try operate("engine/suspend", Seq(DoubleIota(31)))
        catch case signal: EngineProbeSignal if signal.kind == "suspend" =>
          suspendOk =
            suspendedStack.collect { case number: DoubleIota => number.getDouble } == Seq(31.0)
        try operate("engine/sleep", Seq(DoubleIota(47), DoubleIota(3)))
        catch case signal: EngineProbeSignal if signal.kind == "sleep" =>
          sleepOk =
            sleptTicks == 3 &&
              sleptStack.collect { case number: DoubleIota => number.getDouble } == Seq(47.0)

      if posOk && terminateOk && suspendOk && sleepOk then
        log.info(
          "[HEXIC-PROBE] engine_actions=PASS pos=true terminate=true suspend=true sleep=true ticks={}",
          sleptTicks
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] engine_actions=FAIL pos={} terminate={} suspend={} sleep={} ticks={} suspended_stack={} slept_stack={}",
          posOk,
          terminateOk,
          suspendOk,
          sleepOk,
          sleptTicks,
          suspendedStack.mkString(","),
          sleptStack.mkString(",")
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] engine_actions=FAIL exception", t)
        1

  private def checkPropertyAccessIotaCodec(level: net.minecraft.server.level.ServerLevel): Int =
    try
      given net.minecraft.server.level.ServerLevel = level
      val original = PropertyAccessIota.Writer("hexic_probe", "head")
      val encoded = IotaType.TYPED_CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow
      val decoded = IotaType.TYPED_CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow
      decoded match
        case access: PropertyAccessIota =>
          val valid = PropertyAccessIota.Type.validate(access, level)
          val originalParts = PropertyAccessIota.Type.split(original.serializeTag())
          val decodedParts = PropertyAccessIota.Type.split(access.serializeTag())
          if valid && decodedParts == originalParts then
            log.info("[HEXIC-PROBE] property_access_iota_codec=PASS encoded_type={} decoded_parts={}", encoded.getClass.getName, decodedParts.toString)
            0
          else
            log.error("[HEXIC-PROBE] property_access_iota_codec=FAIL valid={} decoded_parts={} expected={}", valid, decodedParts.toString, originalParts.toString)
            1
        case other =>
          log.error("[HEXIC-PROBE] property_access_iota_codec=FAIL decoded_type={} decoded={}", Option(other).fold("null")(_.getClass.getName), other)
          1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] property_access_iota_codec=FAIL exception", t)
        1

  private def checkPipelineFrameCodecs(): Int =
    try
      pipelineFrameCodecProbe() match
        case Right(details) =>
          log.info("[HEXIC-PROBE] pipeline_frame_codecs=PASS {}", details)
          0
        case Left(message) =>
          log.error("[HEXIC-PROBE] pipeline_frame_codecs=FAIL {}", message)
          1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] pipeline_frame_codecs=FAIL exception", t)
        1

  private def checkNetworkCompat(): Int =
    try
      NetworkCompat.probe() match
        case Right(details) =>
          log.info("[HEXIC-PROBE] network_payloads=PASS {}", details)
          0
        case Left(message) =>
          log.error("[HEXIC-PROBE] network_payloads=FAIL {}", message)
          1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] network_payloads=FAIL exception", t)
        1

  private def checkNetworkClientboundSend(server: net.minecraft.server.MinecraftServer): Int =
    var channel: EmbeddedChannel = null
    try
      val profile = GameProfile(UUID.fromString("9cf8cd3a-25ad-4f2f-9ed1-0df4640924f7"), "HexicNetConnected")
      val player = ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault())
      player.setGameMode(GameType.SURVIVAL)
      player.moveTo(33.5, 80.0, 0.5, 0.0f, 0.0f)
      val connection = Connection(PacketFlow.SERVERBOUND)
      channel = EmbeddedChannel()
      val channelField = classOf[Connection].getDeclaredField("channel")
      channelField.setAccessible(true)
      channelField.set(connection, channel)
      NetworkRegistry.configureMockConnection(connection)
      val listener = ServerGamePacketListenerImpl(
        server,
        connection,
        player,
        CommonListenerCookie.createInitial(profile, false)
      )
      val hasConnection = player.connection eq listener
      val hasChannel = NetworkRegistry.hasChannel(connection, net.minecraft.network.ConnectionProtocol.PLAY, NetworkCompat.LegacyPayload.TYPE.id)
      val sendResult =
        if hasConnection && hasChannel then
          NetworkCompat.probeClientboundSend(player)
        else if !hasChannel then
          Left("mock connection does not accept hexic legacy payload channel")
        else
          Left("listener was not attached to ServerPlayer.connection")
      sendResult match
        case Right(details) =>
          log.info("[HEXIC-PROBE] network_clientbound_send=PASS connection={} channel={} {}", hasConnection, hasChannel, details)
          0
        case Left(message) =>
          log.error("[HEXIC-PROBE] network_clientbound_send=FAIL connection={} channel={} {}", hasConnection, hasChannel, message)
          1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] network_clientbound_send=FAIL exception", t)
        1
    finally
      if channel != null then
        channel.close()
        ()

  private def checkComponentStore(server: net.minecraft.server.MinecraftServer): Int =
    try
      val player = FakePlayerFactory.getMinecraft(server.overworld())
      val key = summon[ComponentKey[MurmurCache]]
      player.component[MurmurCache].value = Some("hexic probe murmur")
      player.syncComponent(key)
      if !ComponentStore.hasPersistentComponent(player, key) then
        log.error("[HEXIC-PROBE] component_store=FAIL missing_persistent_component={}", key.id)
        1
      else
        ComponentStore.clear(player)
        val restored = player.component[MurmurCache].value
        val revealKey = summon[ComponentKey[RevealComponent]]
        val revealTag = net.minecraft.nbt.CompoundTag()
        revealTag.putInt("lineCount", 1)
        revealTag.putString("line0", "hexic probe reveal")
        val revealSynced = ComponentStore.applySync(player, revealKey.id, revealTag)
        val revealRestored = player.component[RevealComponent].lines.map(_.getString)

        val structuredRevealLine =
          ChatComponent
            .translatable(
              "chat.type.text",
              ChatComponent.literal("Hexic"),
              ChatComponent.literal("Reveal")
            )
            .withStyle(net.minecraft.ChatFormatting.AQUA)
            .append(ChatComponent.literal("!").withStyle(net.minecraft.ChatFormatting.BOLD))
        val structuredReveal = RevealComponent(Seq(structuredRevealLine))
        val structuredRevealTag = CompoundTag()
        structuredReveal.writeToNbt(structuredRevealTag)
        val structuredRevealRestored = RevealComponent(Seq.empty)
        structuredRevealRestored.readFromNbt(structuredRevealTag)
        val structuredRevealReencodedTag = CompoundTag()
        structuredRevealRestored.writeToNbt(structuredRevealReencodedTag)
        val structuredRevealRoundTrip =
          structuredRevealRestored.lines.map(_.getString) == Seq(structuredRevealLine.getString) &&
            structuredRevealReencodedTag == structuredRevealTag

        val leftWeave = ItemStack(Mediaweave.colors(DyeColor.BLUE))
        leftWeave.set(DataComponents.CUSTOM_NAME, ChatComponent.literal("hexic probe left weave"))
        Mediaweave.colors(DyeColor.BLUE).writeDatum(
          leftWeave,
          ListIota(java.util.List.of[Iota](DoubleIota(12.0)))
        )
        val rightWeave = ItemStack(Mediaweave.colors(DyeColor.RED))
        rightWeave.set(DataComponents.CUSTOM_NAME, ChatComponent.literal("hexic probe right weave"))
        val playerInfoSource = PlayerInfoComponent(
          player,
          leftWeave,
          rightWeave,
          Some(net.minecraft.world.entity.animal.Fox.Type.SNOW)
        )
        val playerInfoTag = CompoundTag()
        playerInfoSource.writeToNbt(playerInfoTag)
        val playerInfoRestored = PlayerInfoComponent(player)
        playerInfoRestored.readFromNbt(playerInfoTag)
        val playerInfoRoundTrip =
          ItemStack.isSameItemSameComponents(leftWeave, playerInfoRestored.leftWeave) &&
            ItemStack.isSameItemSameComponents(rightWeave, playerInfoRestored.rightWeave) &&
            playerInfoRestored.foxType.contains(net.minecraft.world.entity.animal.Fox.Type.SNOW)

        if restored.contains("hexic probe murmur") &&
            revealSynced &&
            revealRestored == Seq("hexic probe reveal") &&
            structuredRevealRoundTrip &&
            playerInfoRoundTrip
        then
          log.info(
            "[HEXIC-PROBE] component_store=PASS key={} restored={} sync_key={} sync_lines={} structured_reveal=true player_info_items=true",
            key.id,
            restored.get,
            revealKey.id,
            revealRestored.mkString("|")
          )
          0
        else
          log.error(
            "[HEXIC-PROBE] component_store=FAIL restored={} reveal_synced={} reveal_restored={} structured_reveal={} player_info_items={}",
            restored.orNull,
            revealSynced,
            revealRestored.mkString("|"),
            structuredRevealRoundTrip,
            playerInfoRoundTrip
          )
          1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] component_store=FAIL exception", t)
        1

  private def checkCuriosData(server: net.minecraft.server.MinecraftServer): Int =
    checkCuriosSlotData() + checkCuriosEquipment(server) + checkMediaweaveDispenser(server)

  private def checkNetworkReceivers(server: net.minecraft.server.MinecraftServer): Int =
    try
      val player = FakePlayerFactory.get(
        server.overworld(),
        GameProfile(UUID.fromString("64b99bf7-9f44-4e60-bd70-a6de1a92e127"), "HexicProbeNetwork")
      )
      val murmurText = "hexic probe network murmur"
      val murmurBuf = NetworkCompat.buffer()
      murmurBuf.writeBoolean(true)
      murmurBuf.writeUtf(murmurText)
      val murmurDispatched = NetworkCompat.dispatchServerReceiver("murmur", player, murmurBuf)
      val murmurRestored = player.component[MurmurCache].value

      val mediaweave = Mediaweave.colors(DyeColor.WHITE)
      val mediaweaveStack = ItemStack(mediaweave, 1)
      mediaweave.writeDatum(mediaweaveStack, ListIota(Collections.emptyList[Iota]()))
      val inserted = CuriosCompat.insertOneIntoFirstEmptySlot(player, mediaweaveStack)
      val directExecuted = player.executeMediaweave("hexic probe direct message", Seq())

      val messageBuf = NetworkCompat.buffer()
      messageBuf.writeByte(0)
      messageBuf.writeUtf("hexic probe network message")
      val messageDispatched = NetworkCompat.dispatchServerReceiver("message", player, messageBuf)

      val ok =
        murmurDispatched.isRight &&
          murmurRestored.contains(murmurText) &&
          inserted &&
          directExecuted &&
          messageDispatched.isRight
      if ok then
        log.info(
          "[HEXIC-PROBE] network_receivers=PASS murmur={} mediaweave_inserted={} direct_execute={} message_receiver=PASS",
          murmurRestored.get,
          inserted,
          directExecuted
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] network_receivers=FAIL murmur_result={} murmur_value={} inserted={} direct_execute={} message_result={}",
          murmurDispatched.left.toOption.orNull,
          murmurRestored.orNull,
          inserted,
          directExecuted,
          messageDispatched.left.toOption.orNull
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] network_receivers=FAIL exception", t)
        1

  private def checkMessageFrameEvaluation(server: net.minecraft.server.MinecraftServer): Int =
    try
      val player = FakePlayerFactory.get(
        server.overworld(),
        GameProfile(UUID.fromString("4c92c583-32a8-4f0a-a3e3-b6d05d480596"), "HexicProbeFrame")
      )
      val text = "hexic probe outgoing msg"
      val image = CastingImage(
        TreeList.from(java.util.List.of(StringIota.make(text))),
        0,
        TreeList.empty(),
        false,
        false,
        0,
        net.minecraft.nbt.CompoundTag()
      )
      val vm = CastingVM(image, StaffCastEnv(player, InteractionHand.MAIN_HAND))
      val frame = MessageFrame(player.getUUID, ChatComponent.literal("hexic probe sender"), player)
      val result = frame.evaluate(SpellContinuation.Done.INSTANCE, server.overworld(), vm)
      val attemptSpell = result.getSideEffects.asScala.collectFirst:
        case effect: OperatorSideEffect.AttemptSpell => effect
      val stackClear = result.getNewData != null && result.getNewData.getStack.isEmpty
      val ok =
        result.getResolutionType == ResolvedPatternType.EVALUATED &&
          stackClear &&
          attemptSpell.isDefined &&
          !attemptSpell.get.getHasCastingSound &&
          !attemptSpell.get.getAwardStat
      if ok then
        log.info(
          "[HEXIC-PROBE] message_frame_eval=PASS resolution={} stack_clear={} side_effects={}",
          result.getResolutionType,
          stackClear,
          result.getSideEffects.size
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] message_frame_eval=FAIL resolution={} stack_clear={} attempt_spell={} side_effects={}",
          result.getResolutionType,
          stackClear,
          attemptSpell.isDefined,
          result.getSideEffects.size
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] message_frame_eval=FAIL exception", t)
        1

  private def checkInventoryViews(server: net.minecraft.server.MinecraftServer): Int =
    val level = server.overworld()
    try
      level.setBlockAndUpdate(inventoryProbePos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(inventorySumProbeFirstPos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(inventorySumProbeSecondPos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(inventoryProbePos, Blocks.CHEST.defaultBlockState())
      level.setBlockAndUpdate(inventorySumProbeFirstPos, Blocks.CHEST.defaultBlockState())
      level.setBlockAndUpdate(inventorySumProbeSecondPos, Blocks.CHEST.defaultBlockState())
      val view = BoxedView.BlockView(level.dimension().location(), inventoryProbePos)
      val sumFirst = BoxedView.BlockView(level.dimension().location(), inventorySumProbeFirstPos)
      val sumSecond = BoxedView.BlockView(level.dimension().location(), inventorySumProbeSecondPos)
      val sumView = BoxedView.SumView(Seq(sumFirst, sumSecond))
      val diamond = VariantIota.ofItem(Items.DIAMOND)
      val initial = view.available(server, diamond)
      val inserted = view.insert(server, diamond, 3, simulate = false)
      val availableAfterInsert = view.available(server, diamond)
      val remainingAfterInsert = view.remaining(server, diamond)
      val queryPlayer = FakePlayerFactory.get(
        level,
        GameProfile(
          UUID.fromString("269e97fc-602b-42c1-8355-583e02213258"),
          "HexicViewQueryActionProbe"
        )
      )
      val queryEnv = StaffCastEnv(queryPlayer, InteractionHand.MAIN_HAND)
      def queryAction(path: String): Double =
        val entry =
          server.registryAccess().registryOrThrow(HexRegistries.ACTION).get(id(path))
        if entry == null then throw IllegalStateException(s"Missing hexic:$path action")
        val queryImage = CastingImage(
          TreeList.from(java.util.List.of[Iota](BoxedView.Instance(view), diamond)),
          0,
          TreeList.empty(),
          false,
          false,
          0,
          CompoundTag()
        )
        entry
          .action()
          .operate(queryEnv, queryImage, SpellContinuation.Done.INSTANCE)
          .getNewImage
          .getStack
          .asScala
          .toSeq match
            case Seq(number: DoubleIota) => number.getDouble
            case result =>
              throw IllegalStateException(
                s"hexic:$path returned ${result.map(_.getClass.getSimpleName).mkString(",")}"
              )
      val actionAvailable = queryAction("conceptavailable")
      val actionRemaining = queryAction("conceptremaining")
      val actionQueriesOk =
        actionAvailable == diamond.toConcepts(availableAfterInsert) &&
          actionRemaining == diamond.toConcepts(remainingAfterInsert)
      val contentsHasDiamond = view.contents(server).exists(_.item == Items.DIAMOND)
      val extracted = view.extract(server, diamond, 2, simulate = false)
      val availableAfterExtract = view.available(server, diamond)
      val namedDiamondStack = ItemStack(Items.DIAMOND)
      namedDiamondStack.set(DataComponents.CUSTOM_NAME, ChatComponent.literal("hexic-inventory-component-probe"))
      val namedDiamond = VariantIota.fromStack(namedDiamondStack)
      val namedInitially = view.available(server, namedDiamond)
      val namedInserted = view.insert(server, namedDiamond, 2, simulate = false)
      val plainAfterNamedInsert = view.available(server, diamond)
      val namedAfterInsert = view.available(server, namedDiamond)
      val componentVariantCount = view.contents(server).count(_.item == Items.DIAMOND)
      val finalPlainExtracted = view.extract(server, diamond, 1, simulate = false)
      val namedAfterPlainExtract = view.available(server, namedDiamond)
      val sumInserted = sumView.insert(server, diamond, 1800, simulate = false)
      val sumFirstAvailable = sumFirst.available(server, diamond)
      val sumSecondAvailable = sumSecond.available(server, diamond)
      val sumExtracted = sumView.extract(server, diamond, 1799, simulate = false)
      val sumFinalAvailable = sumView.available(server, diamond)
      val duplicateView = BoxedView.SumView(Seq(view, view))
      val singleRemaining = view.remaining(server, diamond)
      val duplicateRemaining = duplicateView.remaining(server, diamond)
      val singleNamedAvailable = view.available(server, namedDiamond)
      val duplicateNamedAvailable = duplicateView.available(server, namedDiamond)
      val nestedSum = BoxedView.SumView(Seq(sumFirst, BoxedView.SumView(Seq(sumSecond, sumFirst))))
      val flatSum = BoxedView.SumView(Seq(sumFirst, sumSecond, sumFirst))
      val nestedSumCanonical = nestedSum.serialize == flatSum.serialize
      val emptySumIsFalse = !BoxedView.Instance(BoxedView.SumView(Seq.empty)).isTruthy
      val nonEmptySumIsTrue = BoxedView.Instance(nestedSum).isTruthy
      val lockedStack = ItemStack(Items.DIAMOND, 4)
      val lockedHandler = new IItemHandler:
        override def getSlots: Int = 1
        override def getStackInSlot(slot: Int): ItemStack = lockedStack.copy()
        override def insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack = stack
        override def extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack = ItemStack.EMPTY
        override def getSlotLimit(slot: Int): Int = 64
        override def isItemValid(slot: Int, stack: ItemStack): Boolean = false
      val lockedVisible = lockedHandler.getStackInSlot(0).getCount
      val lockedExtractable =
        BoxedView.extractItems(lockedHandler, diamond, Long.MaxValue, simulate = true)
      val lockedInsertable =
        BoxedView.insertItems(lockedHandler, diamond, Long.MaxValue, simulate = true)
      val saturatedCapacity =
        BoxedView.saturatingAdd(Long.MaxValue - 5L, 10L)
      val ok =
        initial == 0 &&
          inserted == 3 &&
          availableAfterInsert == 3 &&
          remainingAfterInsert > 0 &&
          actionQueriesOk &&
          contentsHasDiamond &&
          extracted == 2 &&
          availableAfterExtract == 1 &&
          namedInitially == 0 &&
          namedInserted == 2 &&
          plainAfterNamedInsert == 1 &&
          namedAfterInsert == 2 &&
          componentVariantCount == 2 &&
          finalPlainExtracted == 1 &&
          namedAfterPlainExtract == 2 &&
          sumInserted == 1800 &&
          sumFirstAvailable == 1728 &&
          sumSecondAvailable == 72 &&
          sumExtracted == 1799 &&
          sumFinalAvailable == 1 &&
          singleRemaining > 0 &&
          duplicateRemaining == BoxedView.saturatingAdd(singleRemaining, singleRemaining) &&
          singleNamedAvailable == 2 &&
          duplicateNamedAvailable == BoxedView.saturatingAdd(singleNamedAvailable, singleNamedAvailable) &&
          nestedSumCanonical &&
          emptySumIsFalse &&
          nonEmptySumIsTrue &&
          lockedVisible == 4 &&
          lockedExtractable == 0 &&
          lockedInsertable == 0 &&
          saturatedCapacity == Long.MaxValue
      if ok then
        log.info(
          "[HEXIC-PROBE] inventory_views=PASS inserted={} available={} remaining={} action_available={} action_remaining={} contents_has_diamond={} extracted={} final_available={} named_initial={} named_inserted={} plain_after_named={} named_after_insert={} component_variants={} final_plain_extracted={} named_after_plain_extract={} sum_inserted={} sum_first={} sum_second={} sum_extracted={} sum_final={} duplicate_remaining={} duplicate_named_available={} nested_sum_canonical={} empty_sum_false={} nonempty_sum_true={} locked_visible={} locked_extractable={} locked_insertable={} saturated_capacity={}",
          inserted,
          availableAfterInsert,
          remainingAfterInsert,
          actionAvailable,
          actionRemaining,
          contentsHasDiamond,
          extracted,
          availableAfterExtract,
          namedInitially,
          namedInserted,
          plainAfterNamedInsert,
          namedAfterInsert,
          componentVariantCount,
          finalPlainExtracted,
          namedAfterPlainExtract,
          sumInserted,
          sumFirstAvailable,
          sumSecondAvailable,
          sumExtracted,
          sumFinalAvailable,
          duplicateRemaining,
          duplicateNamedAvailable,
          nestedSumCanonical,
          emptySumIsFalse,
          nonEmptySumIsTrue,
          lockedVisible,
          lockedExtractable,
          lockedInsertable,
          saturatedCapacity
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] inventory_views=FAIL initial={} inserted={} available={} remaining={} action_available={} action_remaining={} action_queries={} contents_has_diamond={} extracted={} final_available={} named_initial={} named_inserted={} plain_after_named={} named_after_insert={} component_variants={} final_plain_extracted={} named_after_plain_extract={} sum_inserted={} sum_first={} sum_second={} sum_extracted={} sum_final={} single_remaining={} duplicate_remaining={} single_named_available={} duplicate_named_available={} nested_sum_canonical={} empty_sum_false={} nonempty_sum_true={} locked_visible={} locked_extractable={} locked_insertable={} saturated_capacity={}",
          initial,
          inserted,
          availableAfterInsert,
          remainingAfterInsert,
          actionAvailable,
          actionRemaining,
          actionQueriesOk,
          contentsHasDiamond,
          extracted,
          availableAfterExtract,
          namedInitially,
          namedInserted,
          plainAfterNamedInsert,
          namedAfterInsert,
          componentVariantCount,
          finalPlainExtracted,
          namedAfterPlainExtract,
          sumInserted,
          sumFirstAvailable,
          sumSecondAvailable,
          sumExtracted,
          sumFinalAvailable,
          singleRemaining,
          duplicateRemaining,
          singleNamedAvailable,
          duplicateNamedAvailable,
          nestedSumCanonical,
          emptySumIsFalse,
          nonEmptySumIsTrue,
          lockedVisible,
          lockedExtractable,
          lockedInsertable,
          saturatedCapacity
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] inventory_views=FAIL exception", t)
        1
    finally
      level.setBlockAndUpdate(inventoryProbePos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(inventorySumProbeFirstPos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(inventorySumProbeSecondPos, Blocks.AIR.defaultBlockState())

  private def checkViewExtensions(server: net.minecraft.server.MinecraftServer): Int =
    val level = server.overworld()
    val baselineBlocks = HexicViewApi.blockProviderCount()
    val baselineEntities = HexicViewApi.entityProviderCount()
    val baselineResolvers = HexicViewApi.iotaResolverCount()
    var blockRegistration: HexicViewApi.Registration | Null = null
    var entityRegistration: HexicViewApi.Registration | Null = null
    var resolverRegistration: HexicViewApi.Registration | Null = null
    try
      level.setBlockAndUpdate(viewExtensionProbePos, Blocks.AIR.defaultBlockState())
      val diamond = VariantIota.ofItem(Items.DIAMOND)
      var stored = 7L
      val handler = new HexicViewApi.Handler:
        override def contents(
          ignored: net.minecraft.server.MinecraftServer
        ): java.util.List[Iota] =
          if stored > 0L then java.util.List.of[Iota](diamond) else java.util.List.of()

        override def extract(
          ignored: net.minecraft.server.MinecraftServer,
          variant: Iota,
          amount: Long,
          simulate: Boolean
        ): Long =
          if !variant.equals(diamond) then 0L
          else
            val extracted = Math.min(Math.max(0L, amount), stored)
            if !simulate then stored -= extracted
            extracted

        override def insert(
          ignored: net.minecraft.server.MinecraftServer,
          variant: Iota,
          amount: Long,
          simulate: Boolean
        ): Long =
          if !variant.equals(diamond) then 0L
          else
            val inserted = Math.min(Math.max(0L, amount), 16L - stored)
            if !simulate then stored += inserted
            inserted

      blockRegistration = HexicViewApi.registerBlockProvider(
        (providerLevel, pos, state) =>
          if providerLevel == level && pos == viewExtensionProbePos then
            java.util.List.of(handler)
          else
            java.util.List.of()
      )

      val entity = ArmorStand(
        EntityType.ARMOR_STAND,
        level
      )
      entityRegistration = HexicViewApi.registerEntityProvider(
        (providerLevel, candidate) =>
          if providerLevel == level && candidate == entity then
            java.util.List.of(handler)
          else
            java.util.List.of()
      )

      val resolvedView = BoxedView.SumView(Seq.empty)
      var resolverCalled = false
      resolverRegistration = HexicViewApi.registerIotaResolver(
        (environment, iota) =>
          resolverCalled = true
          if iota.isInstanceOf[NullIota] then BoxedView.Instance(resolvedView) else null
      )

      val view = BoxedView.BlockView(level.dimension().location(), viewExtensionProbePos)
      val initialContents = view.contents(server).exists(_.equals(diamond))
      val initialAvailable = view.available(server, diamond)
      val initialRemaining = view.remaining(server, diamond)
      val extracted = view.extract(server, diamond, 3L, simulate = false)
      val inserted = view.insert(server, diamond, 2L, simulate = false)
      val finalAvailable = view.available(server, diamond)
      val entityHandlers = HexicViewApi.entityHandlers(level, entity).size()

      val player = FakePlayerFactory.get(
        level,
        GameProfile(UUID.fromString("b974ca55-20d4-44ad-8702-614008fd02df"), "HexicViewApiProbe")
      )
      val env = StaffCastEnv(player, InteractionHand.MAIN_HAND)
      val image = CastingImage(
        TreeList.from(java.util.List.of[Iota](NullIota())),
        0,
        TreeList.empty(),
        false,
        false,
        0,
        CompoundTag()
      )
      val findViewAction =
        server.registryAccess().registryOrThrow(HexRegistries.ACTION).get(id("findview")).action()
      val result =
        findViewAction.operate(env, image, SpellContinuation.Done.INSTANCE).getNewImage.getStack
      val resolverActionWorked = result.asScala.exists:
        case BoxedView.Instance(found) => found.serialize == resolvedView.serialize
        case _ => false

      val duringCounts =
        HexicViewApi.blockProviderCount() == baselineBlocks + 1 &&
          HexicViewApi.entityProviderCount() == baselineEntities + 1 &&
          HexicViewApi.iotaResolverCount() == baselineResolvers + 1

      blockRegistration.close()
      blockRegistration = null
      entityRegistration.close()
      entityRegistration = null
      resolverRegistration.close()
      resolverRegistration = null
      val restoredCounts =
        HexicViewApi.blockProviderCount() == baselineBlocks &&
          HexicViewApi.entityProviderCount() == baselineEntities &&
          HexicViewApi.iotaResolverCount() == baselineResolvers

      val ok =
        initialContents &&
          initialAvailable == 7L &&
          initialRemaining == 9L &&
          extracted == 3L &&
          inserted == 2L &&
          finalAvailable == 6L &&
          entityHandlers == 1 &&
          resolverCalled &&
          resolverActionWorked &&
          duringCounts &&
          restoredCounts
      if ok then
        log.info(
          "[HEXIC-PROBE] view_extensions=PASS contents={} available={} remaining={} extracted={} inserted={} final={} entity_handlers={} resolver_called={} resolver_action={} registration_cleanup={}",
          initialContents,
          initialAvailable,
          initialRemaining,
          extracted,
          inserted,
          finalAvailable,
          entityHandlers,
          resolverCalled,
          resolverActionWorked,
          restoredCounts
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] view_extensions=FAIL contents={} available={} remaining={} extracted={} inserted={} final={} entity_handlers={} resolver_called={} resolver_action={} during_counts={} restored_counts={}",
          initialContents,
          initialAvailable,
          initialRemaining,
          extracted,
          inserted,
          finalAvailable,
          entityHandlers,
          resolverCalled,
          resolverActionWorked,
          duringCounts,
          restoredCounts
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] view_extensions=FAIL exception", t)
        1
    finally
      Option(blockRegistration).foreach(_.close())
      Option(entityRegistration).foreach(_.close())
      Option(resolverRegistration).foreach(_.close())
      level.setBlockAndUpdate(viewExtensionProbePos, Blocks.AIR.defaultBlockState())

  private def checkConceptTransfer(server: net.minecraft.server.MinecraftServer): Int =
    val level = server.overworld()
    try
      level.setBlockAndUpdate(transferProbeSourcePos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(transferProbeTargetPos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(transferProbeSourcePos, Blocks.CHEST.defaultBlockState())
      level.setBlockAndUpdate(transferProbeTargetPos, Blocks.CHEST.defaultBlockState())

      val diamond = VariantIota.ofItem(Items.DIAMOND)
      val source = BoxedView.BlockView(level.dimension().location(), transferProbeSourcePos)
      val target = BoxedView.BlockView(level.dimension().location(), transferProbeTargetPos)
      val seeded = source.insert(server, diamond, 10L, simulate = false)

      val player = FakePlayerFactory.get(
        level,
        GameProfile(UUID.fromString("af690bf1-7bea-4851-9dd3-b60a624f56ac"), "HexicTransferProbe")
      )
      val env = StaffCastEnv(player, InteractionHand.MAIN_HAND)
      val action =
        server.registryAccess().registryOrThrow(HexRegistries.ACTION).get(id("moveconcept")).action()

      def runTransfer(
        from: BoxedView.View,
        into: BoxedView.View,
        amount: Double
      ): Option[Double] =
        val image = CastingImage(
          TreeList.from(java.util.List.of[Iota](
            BoxedView.Instance(from),
            BoxedView.Instance(into),
            diamond,
            DoubleIota(amount)
          )),
          0,
          TreeList.empty(),
          false,
          false,
          0,
          CompoundTag()
        )
        action
          .operate(env, image, SpellContinuation.Done.INSTANCE)
          .getNewImage
          .getStack
          .asScala
          .collectFirst:
            case value: DoubleIota => value.getDouble

      val realMoved = runTransfer(source, target, 6.0)
      val realSource = source.available(server, diamond)
      val realTarget = target.available(server, diamond)

      var rollbackSourceAmount = 10L
      var rollbackTargetAmount = 0L
      val rollbackSource = new BoxedView.View:
        override def serialize: String = "probe|rollback_source"
        override def extract(
          ignored: net.minecraft.server.MinecraftServer,
          variant: VariantIota,
          amount: Long,
          simulate: Boolean
        ): Long =
          val extracted = Math.min(Math.max(0L, amount), rollbackSourceAmount)
          if !simulate then rollbackSourceAmount -= extracted
          extracted
        override def insert(
          ignored: net.minecraft.server.MinecraftServer,
          variant: VariantIota,
          amount: Long,
          simulate: Boolean
        ): Long =
          val inserted = Math.min(Math.max(0L, amount), 10L - rollbackSourceAmount)
          if !simulate then rollbackSourceAmount += inserted
          inserted

      val lateRejectingTarget = new BoxedView.View:
        override def serialize: String = "probe|late_rejecting_target"
        override def insert(
          ignored: net.minecraft.server.MinecraftServer,
          variant: VariantIota,
          amount: Long,
          simulate: Boolean
        ): Long =
          val inserted =
            if simulate then Math.max(0L, amount)
            else Math.min(Math.max(0L, amount), 2L)
          if !simulate then rollbackTargetAmount += inserted
          inserted

      val rollbackMoved = runTransfer(rollbackSource, lateRejectingTarget, 6.0)
      val rollbackConserved = rollbackSourceAmount + rollbackTargetAmount == 10L
      val ok =
        seeded == 10L &&
          realMoved.contains(6.0) &&
          realSource == 4L &&
          realTarget == 6L &&
          rollbackMoved.contains(2.0) &&
          rollbackSourceAmount == 8L &&
          rollbackTargetAmount == 2L &&
          rollbackConserved

      if ok then
        log.info(
          "[HEXIC-PROBE] concept_transfer=PASS seeded={} moved={} source={} target={} mismatch_moved={} rollback_source={} rollback_target={} conserved={}",
          seeded,
          realMoved.get,
          realSource,
          realTarget,
          rollbackMoved.get,
          rollbackSourceAmount,
          rollbackTargetAmount,
          rollbackConserved
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] concept_transfer=FAIL seeded={} moved={} source={} target={} mismatch_moved={} rollback_source={} rollback_target={} conserved={}",
          seeded,
          realMoved,
          realSource,
          realTarget,
          rollbackMoved,
          rollbackSourceAmount,
          rollbackTargetAmount,
          rollbackConserved
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] concept_transfer=FAIL exception", t)
        1
    finally
      level.setBlockAndUpdate(transferProbeSourcePos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(transferProbeTargetPos, Blocks.AIR.defaultBlockState())

  private def checkEntityViews(server: net.minecraft.server.MinecraftServer): Int =
    val level = server.overworld()
    var spawned: Seq[Entity] = Seq.empty
    var probeChunkForced = false
    var connectedPlayerId: UUID = null
    var connectedPlayersByUuid: java.util.Map[UUID, ServerPlayer] = null
    var previousConnectedPlayer: ServerPlayer = null
    try
      def flushEntityManager(): Unit =
        val field = classOf[net.minecraft.server.level.ServerLevel].getDeclaredField("entityManager")
        field.setAccessible(true)
        val manager = field.get(level)
        manager.getClass.getMethod("tick").invoke(manager)
      def setEntityChunkVisibility(chunkX: Int, chunkZ: Int, visibility: Visibility): Unit =
        val field = classOf[net.minecraft.server.level.ServerLevel].getDeclaredField("entityManager")
        field.setAccessible(true)
        val manager = field.get(level)
        manager
          .getClass
          .getMethod("updateChunkStatus", classOf[ChunkPos], classOf[Visibility])
          .invoke(manager, ChunkPos(chunkX, chunkZ), visibility)
      val probeChunkX = entityViewSourcePos.getX >> 4
      val probeChunkZ = entityViewSourcePos.getZ >> 4
      level.setChunkForced(probeChunkX, probeChunkZ, true)
      probeChunkForced = true
      level.getChunkSource.getChunk(probeChunkX, probeChunkZ, ChunkStatus.FULL, true)
      setEntityChunkVisibility(probeChunkX, probeChunkZ, Visibility.TICKING)
      level.setBlockAndUpdate(entityViewSourcePos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(entityViewTargetPos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(entityViewEntityTargetPos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(entityViewLivingTargetPos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(entityViewMobTargetPos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(entityViewPlayerTargetPos, Blocks.AIR.defaultBlockState())
      val entity = ItemEntity(
        level,
        entityViewSourcePos.getX + 0.5,
        entityViewSourcePos.getY + 0.25,
        entityViewSourcePos.getZ + 0.5,
        ItemStack(Items.STICK)
      )
      entity.setNoPickUpDelay()
      val added = level.addFreshEntity(entity)
      spawned :+= entity
      flushEntityManager()

      val fromView = BoxedView.BlockView(level.dimension().location(), entityViewSourcePos)
      val targetView = BoxedView.BlockView(level.dimension().location(), entityViewTargetPos)
      val entityTargetView = BoxedView.BlockView(level.dimension().location(), entityViewEntityTargetPos)
      val livingTargetView = BoxedView.BlockView(level.dimension().location(), entityViewLivingTargetPos)
      val mobTargetView = BoxedView.BlockView(level.dimension().location(), entityViewMobTargetPos)
      val playerTargetView = BoxedView.BlockView(level.dimension().location(), entityViewPlayerTargetPos)
      val action = server.registryAccess().registryOrThrow(HexRegistries.ACTION).get(id("moveentity")).action()
      val findAction = server.registryAccess().registryOrThrow(HexRegistries.ACTION).get(id("findview")).action()
      val player = FakePlayerFactory.getMinecraft(level)
      player.moveTo(entityViewSourcePos.getX + 0.5, entityViewSourcePos.getY + 0.25, entityViewSourcePos.getZ + 0.5, 0.0f, 0.0f)
      val env = StaffCastEnv(player, InteractionHand.MAIN_HAND)
      def runFindViewWith(castEnv: StaffCastEnv, iota: Iota): Option[BoxedView.View] =
        val image = CastingImage(
          TreeList.from(Seq(iota).asJava),
          0,
          TreeList.empty(),
          false,
          false,
          0,
          CompoundTag()
        )
        findAction.operate(castEnv, image, SpellContinuation.Done.INSTANCE).getNewImage.getStack.asScala.collectFirst:
          case BoxedView.Instance(view) => view
      def runFindView(iota: Iota): Option[BoxedView.View] =
        runFindViewWith(env, iota)
      def runMoveWith(castEnv: StaffCastEnv, from: BoxedView.View, into: BoxedView.View): Option[Double] =
        val image = CastingImage(
          TreeList.from(Seq[Iota](
            BoxedView.Instance(from),
            BoxedView.Instance(into),
            DoubleIota(1.0)
          ).asJava),
          0,
          TreeList.empty(),
          false,
          false,
          0,
          CompoundTag()
        )
        action.operate(castEnv, image, SpellContinuation.Done.INSTANCE).getNewImage.getStack.asScala.collectFirst:
          case d: DoubleIota => d.getDouble
      def runMove(from: BoxedView.View, into: BoxedView.View): Option[Double] =
        runMoveWith(env, from, into)
      val blockFindView = runFindView(Vec3Iota(Vec3.atCenterOf(entityViewSourcePos)))
      val blockFindViewOk = blockFindView.exists(_.serialize == fromView.serialize)
      val moved = blockFindView.flatMap(runMove(_, targetView))
      val sourceEmpty = fromView.entities(server).isEmpty
      val destHasEntity = targetView.entities(server).exists(_.getUUID == entity.getUUID)
      val destDistanceOk = entity.distanceToSqr(Vec3.atCenterOf(entityViewTargetPos)) < 1.0

      val directEntity = ItemEntity(
        level,
        entityViewSourcePos.getX + 0.5,
        entityViewSourcePos.getY + 0.25,
        entityViewSourcePos.getZ + 1.5,
        ItemStack(Items.STRING)
      )
      directEntity.setNoPickUpDelay()
      val addedDirectEntity = level.addFreshEntity(directEntity)
      spawned :+= directEntity
      flushEntityManager()
      val directEntityView = BoxedView.EntityView(directEntity.getUUID)
      val entityMoved = runMove(directEntityView, entityTargetView)
      val entityViewStillFinds = directEntityView.entities(server).exists(_.getUUID == directEntity.getUUID)
      val entityTargetHasEntity = entityTargetView.entities(server).exists(_.getUUID == directEntity.getUUID)
      val entityTargetDistanceOk = directEntity.distanceToSqr(Vec3.atCenterOf(entityViewEntityTargetPos)) < 1.0

      val livingEntity = Option(EntityType.ARMOR_STAND.create(level)).getOrElse(throw IllegalStateException("Could not create armor stand probe entity"))
      livingEntity.moveTo(entityViewSourcePos.getX + 0.5, entityViewSourcePos.getY + 0.25, entityViewSourcePos.getZ + 2.5, 0.0f, 0.0f)
      val addedLivingEntity = level.addFreshEntity(livingEntity)
      spawned :+= livingEntity
      flushEntityManager()
      val livingEntityView = BoxedView.EntityView(livingEntity.getUUID)
      val livingFindView = runFindView(EntityIota(livingEntity))
      val livingFindViewOk = livingFindView.exists(_.serialize == livingEntityView.serialize)
      val livingMoved = livingFindView.flatMap(runMove(_, livingTargetView))
      val livingViewStillFinds = livingEntityView.entities(server).exists(_.getUUID == livingEntity.getUUID)
      val livingTargetHasEntity = livingTargetView.entities(server).exists(_.getUUID == livingEntity.getUUID)
      val livingTargetDistanceOk = livingEntity.distanceToSqr(Vec3.atCenterOf(entityViewLivingTargetPos)) < 1.0

      val mobEntity = Option(EntityType.PIG.create(level)).getOrElse(throw IllegalStateException("Could not create pig probe entity"))
      mobEntity.setNoAi(true)
      mobEntity.setPersistenceRequired()
      mobEntity.moveTo(entityViewSourcePos.getX + 0.5, entityViewSourcePos.getY + 0.25, entityViewSourcePos.getZ + 3.5, 0.0f, 0.0f)
      val addedMobEntity = level.addFreshEntity(mobEntity)
      spawned :+= mobEntity
      flushEntityManager()
      val mobEntityView = BoxedView.EntityView(mobEntity.getUUID)
      val mobFindView = runFindView(EntityIota(mobEntity))
      val mobFindViewOk = mobFindView.exists(_.serialize == mobEntityView.serialize)
      val mobMoved = mobFindView.flatMap(runMove(_, mobTargetView))
      val mobViewStillFinds = mobEntityView.entities(server).exists(_.getUUID == mobEntity.getUUID)
      val mobTargetHasEntity = mobTargetView.entities(server).exists(_.getUUID == mobEntity.getUUID)
      val mobTargetDistanceOk = mobEntity.distanceToSqr(Vec3.atCenterOf(entityViewMobTargetPos)) < 1.0

      val connectedProfile = GameProfile(UUID.fromString("b0a5e7a4-b0c3-4e85-930a-3183256b03da"), "HexicViewConnected")
      val connectedPlayer = ServerPlayer(
        server,
        level,
        connectedProfile,
        ClientInformation.createDefault()
      )
      connectedPlayerId = connectedProfile.getId
      connectedPlayer.setGameMode(GameType.SURVIVAL)
      connectedPlayer.moveTo(entityViewSourcePos.getX + 0.5, entityViewSourcePos.getY + 0.25, entityViewSourcePos.getZ + 4.5, 0.0f, 0.0f)
      val connectedConnection = new Connection(PacketFlow.SERVERBOUND)
      val connectedListener = ServerGamePacketListenerImpl(
        server,
        connectedConnection,
        connectedPlayer,
        CommonListenerCookie.createInitial(connectedProfile, false)
      )
      val playerListField = classOf[net.minecraft.server.players.PlayerList].getDeclaredField("playersByUUID")
      playerListField.setAccessible(true)
      connectedPlayersByUuid = playerListField.get(server.getPlayerList).asInstanceOf[java.util.Map[UUID, ServerPlayer]]
      previousConnectedPlayer = connectedPlayersByUuid.put(connectedProfile.getId, connectedPlayer)
      val connectedPlayerLookupOk = server.getPlayerList.getPlayer(connectedProfile.getId) eq connectedPlayer
      val connectedPlayerConnectionOk = connectedPlayer.connection eq connectedListener
      val connectedPlayerView = BoxedView.EntityView(connectedProfile.getId)
      val connectedPlayerViewFinds = connectedPlayerView.entities(server).exists(_.getUUID == connectedProfile.getId)
      val connectedEnv = StaffCastEnv(connectedPlayer, InteractionHand.MAIN_HAND)
      val connectedPlayerFindView = runFindViewWith(
        connectedEnv,
        EntityIota(connectedProfile.getId, ChatComponent.literal("HexicViewConnected"), true)
      )
      val connectedPlayerFindViewOk = connectedPlayerFindView.exists(_.serialize == connectedPlayerView.serialize)
      val connectedPlayerMoved = connectedPlayerFindView.flatMap(runMoveWith(connectedEnv, _, playerTargetView))
      val connectedPlayerStillFinds = connectedPlayerView.entities(server).exists(_.getUUID == connectedProfile.getId)
      val connectedPlayerTargetDistanceOk = connectedPlayer.distanceToSqr(Vec3.atCenterOf(entityViewPlayerTargetPos)) < 1.0
      val ok =
        added &&
          blockFindViewOk &&
          moved.contains(1.0) &&
          sourceEmpty &&
          destHasEntity &&
          destDistanceOk &&
          !entity.isRemoved &&
          addedDirectEntity &&
          entityMoved.contains(1.0) &&
          entityViewStillFinds &&
          entityTargetHasEntity &&
          entityTargetDistanceOk &&
          !directEntity.isRemoved &&
          addedLivingEntity &&
          livingFindViewOk &&
          livingMoved.contains(1.0) &&
          livingViewStillFinds &&
          livingTargetHasEntity &&
          livingTargetDistanceOk &&
          !livingEntity.isRemoved &&
          addedMobEntity &&
          mobFindViewOk &&
          mobMoved.contains(1.0) &&
          mobViewStillFinds &&
          mobTargetHasEntity &&
          mobTargetDistanceOk &&
          !mobEntity.isRemoved &&
          connectedPlayerLookupOk &&
          connectedPlayerConnectionOk &&
          connectedPlayerViewFinds &&
          connectedPlayerFindViewOk &&
          connectedPlayerMoved.contains(1.0) &&
          connectedPlayerStillFinds &&
          connectedPlayerTargetDistanceOk &&
          !connectedPlayer.isRemoved
      if ok then
        log.info(
          "[HEXIC-PROBE] entity_views=PASS block_added={} block_findview={} block_moved={} source_empty={} dest_has_entity={} dest_distance_ok={} entity_added={} entity_moved={} entity_view_finds={} entity_target_has_entity={} entity_target_distance_ok={} living_added={} living_findview={} living_moved={} living_view_finds={} living_target_has_entity={} living_target_distance_ok={} mob_added={} mob_findview={} mob_moved={} mob_view_finds={} mob_target_has_entity={} mob_target_distance_ok={} connected_player_lookup={} connected_player_connection={} connected_player_view_finds={} connected_player_findview={} connected_player_moved={} connected_player_still_finds={} connected_player_target_distance_ok={}",
          added,
          blockFindViewOk,
          moved.get,
          sourceEmpty,
          destHasEntity,
          destDistanceOk,
          addedDirectEntity,
          entityMoved.get,
          entityViewStillFinds,
          entityTargetHasEntity,
          entityTargetDistanceOk,
          addedLivingEntity,
          livingFindViewOk,
          livingMoved.get,
          livingViewStillFinds,
          livingTargetHasEntity,
          livingTargetDistanceOk,
          addedMobEntity,
          mobFindViewOk,
          mobMoved.get,
          mobViewStillFinds,
          mobTargetHasEntity,
          mobTargetDistanceOk,
          connectedPlayerLookupOk,
          connectedPlayerConnectionOk,
          connectedPlayerViewFinds,
          connectedPlayerFindViewOk,
          connectedPlayerMoved.get,
          connectedPlayerStillFinds,
          connectedPlayerTargetDistanceOk
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] entity_views=FAIL block_added={} block_findview={} block_moved={} source_empty={} dest_has_entity={} dest_distance_ok={} block_removed={} entity_added={} entity_moved={} entity_view_finds={} entity_target_has_entity={} entity_target_distance_ok={} entity_removed={} living_added={} living_findview={} living_moved={} living_view_finds={} living_target_has_entity={} living_target_distance_ok={} living_removed={} mob_added={} mob_findview={} mob_moved={} mob_view_finds={} mob_target_has_entity={} mob_target_distance_ok={} mob_removed={} connected_player_lookup={} connected_player_connection={} connected_player_view_finds={} connected_player_findview={} connected_player_moved={} connected_player_still_finds={} connected_player_target_distance_ok={} connected_player_removed={}",
          added,
          blockFindViewOk,
          moved.map(_.toString).getOrElse("missing"),
          sourceEmpty,
          destHasEntity,
          destDistanceOk,
          entity.isRemoved,
          addedDirectEntity,
          entityMoved.map(_.toString).getOrElse("missing"),
          entityViewStillFinds,
          entityTargetHasEntity,
          entityTargetDistanceOk,
          directEntity.isRemoved,
          addedLivingEntity,
          livingFindViewOk,
          livingMoved.map(_.toString).getOrElse("missing"),
          livingViewStillFinds,
          livingTargetHasEntity,
          livingTargetDistanceOk,
          livingEntity.isRemoved,
          addedMobEntity,
          mobFindViewOk,
          mobMoved.map(_.toString).getOrElse("missing"),
          mobViewStillFinds,
          mobTargetHasEntity,
          mobTargetDistanceOk,
          mobEntity.isRemoved,
          connectedPlayerLookupOk,
          connectedPlayerConnectionOk,
          connectedPlayerViewFinds,
          connectedPlayerFindViewOk,
          connectedPlayerMoved.map(_.toString).getOrElse("missing"),
          connectedPlayerStillFinds,
          connectedPlayerTargetDistanceOk,
          connectedPlayer.isRemoved
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] entity_views=FAIL exception", t)
        1
    finally
      if connectedPlayersByUuid != null && connectedPlayerId != null then
        if previousConnectedPlayer != null then
          connectedPlayersByUuid.put(connectedPlayerId, previousConnectedPlayer)
        else
          connectedPlayersByUuid.remove(connectedPlayerId)
      spawned.foreach(_.discard())
      level.setBlockAndUpdate(entityViewSourcePos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(entityViewTargetPos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(entityViewEntityTargetPos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(entityViewLivingTargetPos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(entityViewMobTargetPos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(entityViewPlayerTargetPos, Blocks.AIR.defaultBlockState())
      if probeChunkForced then
        try level.setChunkForced(entityViewSourcePos.getX >> 4, entityViewSourcePos.getZ >> 4, false)
        catch case _: Throwable => ()

  private def checkEntityVoidAndDemiplaneBounds(server: net.minecraft.server.MinecraftServer): Int =
    val overworld = server.overworld()
    val voidPos = BlockPos(37, 82, 37)
    val oldVoidState = overworld.getBlockState(voidPos)
    val manager = Fantasy.get(server)
    val location = ResourceLocation.fromNamespaceAndPath(
      "hexic",
      s"fresh-${UUID.randomUUID().toString.replace("-", "")}"
    )
    var handle: RuntimeWorldHandle | Null = null
    var inside: ItemEntity | Null = null
    var outside: ItemEntity | Null = null
    var voidEntity: ItemEntity | Null = null
    try
      handle = manager.getOrOpenPersistentWorld(location, RuntimeWorldConfig())
      val plane = handle.asWorld

      inside = ItemEntity(plane, 5.5, 5.0, 5.5, ItemStack(Items.STICK))
      val insideAdded = plane.addFreshEntity(inside)
      inside.checkBelowWorld()
      val insideSurvived = insideAdded && !inside.isRemoved

      outside = ItemEntity(plane, 12.25, 5.0, 5.5, ItemStack(Items.STRING))
      val outsideAdded = plane.addFreshEntity(outside)
      outside.checkBelowWorld()
      val outsideRemoved = outsideAdded && outside.isRemoved

      overworld.setBlockAndUpdate(voidPos, Interop.VOID_AIR.defaultBlockState())
      voidEntity = ItemEntity(
        overworld,
        voidPos.getX + 0.5,
        voidPos.getY + 0.1,
        voidPos.getZ + 0.5,
        ItemStack(Items.PAPER)
      )
      val voidAdded = overworld.addFreshEntity(voidEntity)
      voidEntity.checkBelowWorld()
      val voidRemoved = voidAdded && voidEntity.isRemoved

      if insideSurvived && outsideRemoved && voidRemoved then
        log.info(
          "[HEXIC-PROBE] entity_void_bounds=PASS inside_survived={} outside_removed={} void_air_removed={}",
          insideSurvived,
          outsideRemoved,
          voidRemoved
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] entity_void_bounds=FAIL inside_added={} inside_removed={} outside_added={} outside_removed={} void_added={} void_removed={} dimension={}",
          insideAdded,
          inside.isRemoved,
          outsideAdded,
          outside.isRemoved,
          voidAdded,
          voidEntity.isRemoved,
          plane.dimension().location()
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] entity_void_bounds=FAIL exception", t)
        1
    finally
      Option(inside).filterNot(_.isRemoved).foreach(_.discard())
      Option(outside).filterNot(_.isRemoved).foreach(_.discard())
      Option(voidEntity).filterNot(_.isRemoved).foreach(_.discard())
      overworld.setBlockAndUpdate(voidPos, oldVoidState)
      Option(handle).foreach: current =>
        try current.delete()
        catch case _: Throwable => ()
      try manager.drainPendingForProbe()
      catch case _: Throwable => ()

  private def checkFluidViews(server: net.minecraft.server.MinecraftServer): Int =
    val level = server.overworld()
    try
      level.setBlockAndUpdate(fluidProbePos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(fluidStateProbePos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(fluidSumProbeFirstPos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(fluidSumProbeSecondPos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(fluidProbePos, Blocks.CAULDRON.defaultBlockState())
      level.setBlockAndUpdate(fluidStateProbePos, Blocks.WATER.defaultBlockState())
      level.setBlockAndUpdate(fluidSumProbeFirstPos, Blocks.CAULDRON.defaultBlockState())
      level.setBlockAndUpdate(fluidSumProbeSecondPos, Blocks.CAULDRON.defaultBlockState())

      val water = VariantIota.ofFluid(Fluids.WATER)
      val conceptScaleOk =
        water.fromConcepts(1.0) == FluidType.BUCKET_VOLUME.toLong &&
          water.toConcepts(FluidType.BUCKET_VOLUME.toLong) == 1.0
      val view = BoxedView.BlockView(level.dimension().location(), fluidProbePos)
      val sumFirst = BoxedView.BlockView(level.dimension().location(), fluidSumProbeFirstPos)
      val sumSecond = BoxedView.BlockView(level.dimension().location(), fluidSumProbeSecondPos)
      val sumView = BoxedView.SumView(Seq(sumFirst, sumSecond))
      val initial = view.available(server, water)
      val simulatedInsert = view.insert(server, water, FluidType.BUCKET_VOLUME, simulate = true)
      val remainingInitial = view.remaining(server, water)
      val inserted = view.insert(server, water, FluidType.BUCKET_VOLUME, simulate = false)
      val availableAfterInsert = view.available(server, water)
      val remainingAfterInsert = view.remaining(server, water)
      val contentsHasWater = view.contents(server).exists(variant => variant.isFluid && variant.fluid == Fluids.WATER)
      val thoughtFluid =
        VariantIota.fromBlockAt(level, fluidStateProbePos) match
          case variant: VariantIota => variant.isFluid && variant.fluid == Fluids.WATER
          case _ => false
      val extracted = view.extract(server, water, FluidType.BUCKET_VOLUME, simulate = false)
      val availableAfterExtract = view.available(server, water)
      val drainedToEmpty = level.getBlockState(fluidProbePos).getBlock == Blocks.CAULDRON
      val sumInitial = sumView.available(server, water)
      val sumRemainingInitial = sumView.remaining(server, water)
      val sumInserted = sumView.insert(server, water, FluidType.BUCKET_VOLUME * 2, simulate = false)
      val sumFirstAvailable = sumFirst.available(server, water)
      val sumSecondAvailable = sumSecond.available(server, water)
      val sumAvailableAfterInsert = sumView.available(server, water)
      val sumRemainingAfterInsert = sumView.remaining(server, water)
      val sumExtracted = sumView.extract(server, water, FluidType.BUCKET_VOLUME * 2, simulate = false)
      val sumFinalAvailable = sumView.available(server, water)
      val sumFirstDrained = level.getBlockState(fluidSumProbeFirstPos).getBlock == Blocks.CAULDRON
      val sumSecondDrained = level.getBlockState(fluidSumProbeSecondPos).getBlock == Blocks.CAULDRON
      val lockedFluidStack = FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME)
      val lockedFluidHandler = new IFluidHandler:
        override def getTanks: Int = 1
        override def getFluidInTank(tank: Int): FluidStack = lockedFluidStack.copy()
        override def getTankCapacity(tank: Int): Int = FluidType.BUCKET_VOLUME
        override def isFluidValid(tank: Int, stack: FluidStack): Boolean = false
        override def fill(resource: FluidStack, action: IFluidHandler.FluidAction): Int = 0
        override def drain(
          resource: FluidStack,
          action: IFluidHandler.FluidAction
        ): FluidStack = FluidStack.EMPTY
        override def drain(
          maxDrain: Int,
          action: IFluidHandler.FluidAction
        ): FluidStack = FluidStack.EMPTY
      val lockedVisibleFluid = lockedFluidHandler.getFluidInTank(0).getAmount
      val lockedExtractableFluid =
        BoxedView.extractFluid(lockedFluidHandler, water, Long.MaxValue, simulate = true)
      val lockedInsertableFluid =
        BoxedView.insertFluid(lockedFluidHandler, water, Long.MaxValue, simulate = true)
      val ok =
        conceptScaleOk &&
          initial == 0 &&
          simulatedInsert == FluidType.BUCKET_VOLUME &&
          remainingInitial == FluidType.BUCKET_VOLUME &&
          inserted == FluidType.BUCKET_VOLUME &&
          availableAfterInsert == FluidType.BUCKET_VOLUME &&
          remainingAfterInsert == 0 &&
          contentsHasWater &&
          thoughtFluid &&
          extracted == FluidType.BUCKET_VOLUME &&
          availableAfterExtract == 0 &&
          drainedToEmpty &&
          sumInitial == 0 &&
          sumRemainingInitial == FluidType.BUCKET_VOLUME * 2 &&
          sumInserted == FluidType.BUCKET_VOLUME * 2 &&
          sumFirstAvailable == FluidType.BUCKET_VOLUME &&
          sumSecondAvailable == FluidType.BUCKET_VOLUME &&
          sumAvailableAfterInsert == FluidType.BUCKET_VOLUME * 2 &&
          sumRemainingAfterInsert == 0 &&
          sumExtracted == FluidType.BUCKET_VOLUME * 2 &&
          sumFinalAvailable == 0 &&
          sumFirstDrained &&
          sumSecondDrained &&
          lockedVisibleFluid == FluidType.BUCKET_VOLUME &&
          lockedExtractableFluid == 0 &&
          lockedInsertableFluid == 0
      if ok then
        log.info(
          "[HEXIC-PROBE] fluid_views=PASS concept_scale={} simulated_insert={} inserted={} available={} remaining_initial={} remaining_after={} contents_has_water={} thought_fluid={} extracted={} final_available={} drained_to_empty={} sum_initial={} sum_remaining_initial={} sum_inserted={} sum_first_available={} sum_second_available={} sum_available={} sum_remaining_after={} sum_extracted={} sum_final_available={} sum_first_drained={} sum_second_drained={} locked_visible={} locked_extractable={} locked_insertable={}",
          conceptScaleOk,
          simulatedInsert,
          inserted,
          availableAfterInsert,
          remainingInitial,
          remainingAfterInsert,
          contentsHasWater,
          thoughtFluid,
          extracted,
          availableAfterExtract,
          drainedToEmpty,
          sumInitial,
          sumRemainingInitial,
          sumInserted,
          sumFirstAvailable,
          sumSecondAvailable,
          sumAvailableAfterInsert,
          sumRemainingAfterInsert,
          sumExtracted,
          sumFinalAvailable,
          sumFirstDrained,
          sumSecondDrained,
          lockedVisibleFluid,
          lockedExtractableFluid,
          lockedInsertableFluid
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] fluid_views=FAIL concept_scale={} initial={} simulated_insert={} inserted={} available={} remaining_initial={} remaining_after={} contents_has_water={} thought_fluid={} extracted={} final_available={} drained_to_empty={} sum_initial={} sum_remaining_initial={} sum_inserted={} sum_first_available={} sum_second_available={} sum_available={} sum_remaining_after={} sum_extracted={} sum_final_available={} sum_first_drained={} sum_second_drained={} locked_visible={} locked_extractable={} locked_insertable={}",
          conceptScaleOk,
          initial,
          simulatedInsert,
          inserted,
          availableAfterInsert,
          remainingInitial,
          remainingAfterInsert,
          contentsHasWater,
          thoughtFluid,
          extracted,
          availableAfterExtract,
          drainedToEmpty,
          sumInitial,
          sumRemainingInitial,
          sumInserted,
          sumFirstAvailable,
          sumSecondAvailable,
          sumAvailableAfterInsert,
          sumRemainingAfterInsert,
          sumExtracted,
          sumFinalAvailable,
          sumFirstDrained,
          sumSecondDrained,
          lockedVisibleFluid,
          lockedExtractableFluid,
          lockedInsertableFluid
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] fluid_views=FAIL exception", t)
        1
    finally
      level.setBlockAndUpdate(fluidProbePos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(fluidStateProbePos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(fluidSumProbeFirstPos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(fluidSumProbeSecondPos, Blocks.AIR.defaultBlockState())

  private def checkBlockPickFallback(server: net.minecraft.server.MinecraftServer): Int =
    val level = server.overworld()
    try
      level.setBlockAndUpdate(blockPickFallbackProbePos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(blockPickFallbackProbePos, Blocks.PISTON_HEAD.defaultBlockState())
      val directItem = level.getBlockState(blockPickFallbackProbePos).getBlock.asItem
      val fallback =
        VariantIota.fromBlockAt(level, blockPickFallbackProbePos) match
          case variant: VariantIota => variant
          case _ => VariantIota.ofItem(Items.AIR)
      val fallbackItem = fallback.item
      val ok = directItem == Items.AIR && fallback.isItem && fallbackItem == Items.PISTON
      if ok then
        log.info(
          "[HEXIC-PROBE] block_pick_fallback=PASS block={} direct_item={} fallback_item={}",
          BuiltInRegistries.BLOCK.getKey(Blocks.PISTON_HEAD),
          BuiltInRegistries.ITEM.getKey(directItem),
          BuiltInRegistries.ITEM.getKey(fallbackItem)
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] block_pick_fallback=FAIL direct_item={} fallback_kind={} fallback_item={}",
          BuiltInRegistries.ITEM.getKey(directItem),
          fallback.kind,
          BuiltInRegistries.ITEM.getKey(fallbackItem)
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] block_pick_fallback=FAIL exception", t)
        1
    finally
      level.setBlockAndUpdate(blockPickFallbackProbePos, Blocks.AIR.defaultBlockState())

  private def checkHeatViews(server: net.minecraft.server.MinecraftServer): Int =
    val level = server.overworld()
    try
      level.setBlockAndUpdate(heatProbePos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(heatProbePos, Blocks.FURNACE.defaultBlockState())
      val view = BoxedView.BlockView(level.dimension().location(), heatProbePos)
      val heat = VariantIota.ofHeat
      val conceptScaleOk = heat.fromConcepts(1.0) == 20L && heat.toConcepts(20L) == 1.0
      val initial = view.available(server, heat)
      val remainingInitial = view.remaining(server, heat)
      val contentsHasHeat = view.contents(server).exists(_.isHeat)
      val simulatedInsert = view.insert(server, heat, 120, simulate = true)
      val inserted = view.insert(server, heat, 120, simulate = false)
      val availableAfterInsert = view.available(server, heat)
      val remainingAfterInsert = view.remaining(server, heat)
      val litAfterInsert = level.getBlockState(heatProbePos).getValue(AbstractFurnaceBlock.LIT)
      val extracted = view.extract(server, heat, 45, simulate = false)
      val availableAfterExtract = view.available(server, heat)
      val finalExtracted = view.extract(server, heat, 75, simulate = false)
      val finalAvailable = view.available(server, heat)
      val litAfterDrain = level.getBlockState(heatProbePos).getValue(AbstractFurnaceBlock.LIT)
      val ok =
        conceptScaleOk &&
          initial == 0 &&
          remainingInitial == BoxedView.MaxHeat &&
          contentsHasHeat &&
          simulatedInsert == 120 &&
          inserted == 120 &&
          availableAfterInsert == 120 &&
          remainingAfterInsert == BoxedView.MaxHeat - 120 &&
          litAfterInsert &&
          extracted == 45 &&
          availableAfterExtract == 75 &&
          finalExtracted == 75 &&
          finalAvailable == 0 &&
          !litAfterDrain
      if ok then
        log.info(
          "[HEXIC-PROBE] heat_views=PASS concept_scale={} initial={} remaining_initial={} contents_has_heat={} simulated_insert={} inserted={} available={} remaining_after={} lit_after_insert={} extracted={} after_extract={} final_extracted={} final_available={} lit_after_drain={}",
          conceptScaleOk,
          initial,
          remainingInitial,
          contentsHasHeat,
          simulatedInsert,
          inserted,
          availableAfterInsert,
          remainingAfterInsert,
          litAfterInsert,
          extracted,
          availableAfterExtract,
          finalExtracted,
          finalAvailable,
          litAfterDrain
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] heat_views=FAIL concept_scale={} initial={} remaining_initial={} contents_has_heat={} simulated_insert={} inserted={} available={} remaining_after={} lit_after_insert={} extracted={} after_extract={} final_extracted={} final_available={} lit_after_drain={}",
          conceptScaleOk,
          initial,
          remainingInitial,
          contentsHasHeat,
          simulatedInsert,
          inserted,
          availableAfterInsert,
          remainingAfterInsert,
          litAfterInsert,
          extracted,
          availableAfterExtract,
          finalExtracted,
          finalAvailable,
          litAfterDrain
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] heat_views=FAIL exception", t)
        1
    finally
      level.setBlockAndUpdate(heatProbePos, Blocks.AIR.defaultBlockState())

  private def checkMediaViews(server: net.minecraft.server.MinecraftServer): Int =
    val level = server.overworld()
    try
      level.setBlockAndUpdate(mediaProbePos, Blocks.AIR.defaultBlockState())
      level.setBlockAndUpdate(mediaProbePos, HexBlocks.IMPETUS_RIGHTCLICK.get().defaultBlockState())
      val view = BoxedView.BlockView(level.dimension().location(), mediaProbePos)
      val media = VariantIota.ofMedia
      val impetus = level.getBlockEntity(mediaProbePos) match
        case value: at.petrak.hexcasting.api.casting.circles.BlockEntityAbstractImpetus => value
        case other => throw IllegalStateException(s"Expected impetus block entity, got ${Option(other).map(_.getClass.getName).getOrElse("null")}")
      impetus.setMedia(30000L)
      impetus.setChanged()
      val conceptScaleOk = media.fromConcepts(2.5) == 25000L && media.toConcepts(25000L) == 2.5
      val contentsHasMedia = view.contents(server).exists(_.isMedia)
      val initial = view.available(server, media)
      val simulatedExtract = view.extract(server, media, 10000L, simulate = true)
      val unchangedAfterSimulation = impetus.getMedia == 30000L
      val extracted = view.extract(server, media, 10000L, simulate = false)
      val afterExtract = view.available(server, media)
      val inserted = view.insert(server, media, 5000L, simulate = false)
      val afterInsert = view.available(server, media)
      val remaining = view.remaining(server, media)
      val ok =
        conceptScaleOk &&
          contentsHasMedia &&
          initial == 30000L &&
          simulatedExtract == 10000L &&
          unchangedAfterSimulation &&
          extracted == 10000L &&
          afterExtract == 20000L &&
          inserted == 5000L &&
          afterInsert == 25000L &&
          remaining == BoxedView.MaxMedia - 25000L
      if ok then
        log.info(
          "[HEXIC-PROBE] media_views=PASS concept_scale={} contents={} initial={} simulated_extract={} unchanged={} extracted={} after_extract={} inserted={} after_insert={} remaining={}",
          conceptScaleOk,
          contentsHasMedia,
          initial,
          simulatedExtract,
          unchangedAfterSimulation,
          extracted,
          afterExtract,
          inserted,
          afterInsert,
          remaining
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] media_views=FAIL concept_scale={} contents={} initial={} simulated_extract={} unchanged={} extracted={} after_extract={} inserted={} after_insert={} remaining={}",
          conceptScaleOk,
          contentsHasMedia,
          initial,
          simulatedExtract,
          unchangedAfterSimulation,
          extracted,
          afterExtract,
          inserted,
          afterInsert,
          remaining
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] media_views=FAIL exception", t)
        1
    finally
      level.setBlockAndUpdate(mediaProbePos, Blocks.AIR.defaultBlockState())

  private def checkMediaBundleBehavior(server: net.minecraft.server.MinecraftServer): Int =
    try
      val player = FakePlayerFactory.getMinecraft(server.overworld())
      val bundleItem = MediaBundle(DyeColor.WHITE, 6)
      val bundle = ItemStack(bundleItem)
      val shard = ItemStack(Items.AMETHYST_SHARD)
      val shardHolder = Option(IXplatAbstractions.INSTANCE.findMediaHolder(shard))
        .getOrElse(throw IllegalStateException("Vanilla amethyst shard has no Hex media holder"))
      val expectedDefaultMedia = shardHolder.getMedia
      val expectedDefaultMax = shardHolder.getMaxMedia
      val defaultMedia = bundleItem.getMedia(bundle)
      val defaultMax = bundleItem.getMaxMedia(bundle)

      val batteryMedia = 20000L
      val batteryMax = 50000L
      val battery = ItemMediaHolder.withMedia(ItemStack(HexItems.BATTERY.get()), batteryMedia, batteryMax)
      // Hex Casting pre-2's withMedia helper accidentally mirrors `media` into
      // MEDIA_MAX. Set the intended capacity explicitly so this probe exercises
      // component-preserving persistence rather than that upstream helper bug.
      battery.set(HexDataComponents.MEDIA_MAX.get(), batteryMax)
      val insertedByClick = bundleItem.overrideOtherStackedOnMe(
        bundle,
        battery,
        null.asInstanceOf[net.minecraft.world.inventory.Slot],
        ClickAction.SECONDARY,
        player,
        SlotAccess.NULL
      )
      val batteryConsumed = battery.isEmpty
      val mediaAfterClick = bundleItem.getMedia(bundle)
      val maxAfterClick = bundleItem.getMaxMedia(bundle)
      val contents = bundle.get(DataComponents.BUNDLE_CONTENTS)
      val componentItemCount = if contents == null then 0 else contents.size

      val simulatedWithdraw = bundleItem.withdrawMedia(bundle, 5000L, simulate = true)
      val unchangedAfterSimulatedWithdraw = bundleItem.getMedia(bundle) == mediaAfterClick
      val withdrawn = bundleItem.withdrawMedia(bundle, 5000L, simulate = false)
      val mediaAfterWithdraw = bundleItem.getMedia(bundle)
      val simulatedInsert = bundleItem.insertMedia(bundle, 3000L, simulate = true)
      val unchangedAfterSimulatedInsert = bundleItem.getMedia(bundle) == mediaAfterWithdraw
      val inserted = bundleItem.insertMedia(bundle, 3000L, simulate = false)
      val mediaAfterInsert = bundleItem.getMedia(bundle)

      val saved = bundle.saveOptional(server.registryAccess()) match
        case compound: CompoundTag => compound
        case other => throw IllegalStateException(s"Expected media bundle CompoundTag, got ${other.getClass.getName}")
      val restored = ItemStack.parseOptional(server.registryAccess(), saved)
      val restoredContents = restored.get(DataComponents.BUNDLE_CONTENTS)
      val restoredItemCount = if restoredContents == null then 0 else restoredContents.size
      val restoredMedia = bundleItem.getMedia(restored)
      val restoredMax = bundleItem.getMaxMedia(restored)

      val legacyBundle = ItemStack(bundleItem)
      val legacyShard = CompoundTag()
      legacyShard.putString("id", "minecraft:amethyst_shard")
      legacyShard.putByte("Count", 1.toByte)
      val legacyContents = ListTag()
      legacyContents.add(legacyShard)
      legacyBundle.getOrCreateNbt().put("Contents", legacyContents)
      val legacyReadMedia = bundleItem.getMedia(legacyBundle)
      val legacyBattery = ItemMediaHolder.withMedia(ItemStack(HexItems.BATTERY.get()), batteryMedia, batteryMax)
      legacyBattery.set(HexDataComponents.MEDIA_MAX.get(), batteryMax)
      val legacyInserted = bundleItem.overrideOtherStackedOnMe(
        legacyBundle,
        legacyBattery,
        null.asInstanceOf[net.minecraft.world.inventory.Slot],
        ClickAction.SECONDARY,
        player,
        SlotAccess.NULL
      )
      val migratedLegacyContents = legacyBundle.get(DataComponents.BUNDLE_CONTENTS)
      val migratedLegacyCount = if migratedLegacyContents == null then 0 else migratedLegacyContents.size
      val legacyCustomRemoved = !Option(legacyBundle.getNbt).exists(_.contains("Contents"))
      val migratedLegacyMedia = bundleItem.getMedia(legacyBundle)

      val honeycomb = ItemStack(Items.HONEYCOMB)
      val waxedByClick = bundleItem.overrideOtherStackedOnMe(
        restored,
        honeycomb,
        null.asInstanceOf[net.minecraft.world.inventory.Slot],
        ClickAction.SECONDARY,
        player,
        SlotAccess.NULL
      )
      val waxed = Option(restored.getNbt).exists(_.contains("ro"))
      val blockedWithdraw = bundleItem.withdrawMedia(restored, 1000L, simulate = false)
      val unchangedWhileWaxed = bundleItem.getMedia(restored) == restoredMedia
      val wetSponge = ItemStack(Items.WET_SPONGE)
      val unwaxedByClick = bundleItem.overrideOtherStackedOnMe(
        restored,
        wetSponge,
        null.asInstanceOf[net.minecraft.world.inventory.Slot],
        ClickAction.SECONDARY,
        player,
        SlotAccess.NULL
      )
      val unwaxed = !Option(restored.getNbt).exists(_.contains("ro"))
      val resumedWithdraw = bundleItem.withdrawMedia(restored, 1000L, simulate = false)
      val tooltipPresent = bundleItem.getTooltipImage(restored).isPresent

      val expectedAfterClick = expectedDefaultMedia + batteryMedia
      val expectedMaxAfterClick = expectedDefaultMax + batteryMax
      val ok =
        defaultMedia == expectedDefaultMedia &&
          defaultMax == expectedDefaultMax &&
          insertedByClick &&
          batteryConsumed &&
          componentItemCount == 2 &&
          mediaAfterClick == expectedAfterClick &&
          maxAfterClick == expectedMaxAfterClick &&
          bundleItem.canProvideMedia(bundle) &&
          bundleItem.canRecharge(bundle) &&
          simulatedWithdraw == 5000L &&
          unchangedAfterSimulatedWithdraw &&
          withdrawn == 5000L &&
          mediaAfterWithdraw == expectedAfterClick - 5000L &&
          simulatedInsert == 3000L &&
          unchangedAfterSimulatedInsert &&
          inserted == 3000L &&
          mediaAfterInsert == expectedAfterClick - 2000L &&
          (restored.getItem eq bundleItem) &&
          restoredItemCount == 2 &&
          restoredMedia == mediaAfterInsert &&
          restoredMax == expectedMaxAfterClick &&
          legacyReadMedia == expectedDefaultMedia &&
          legacyInserted &&
          legacyBattery.isEmpty &&
          migratedLegacyCount == 2 &&
          legacyCustomRemoved &&
          migratedLegacyMedia == expectedAfterClick &&
          waxedByClick &&
          honeycomb.isEmpty &&
          waxed &&
          blockedWithdraw == 0L &&
          unchangedWhileWaxed &&
          unwaxedByClick &&
          unwaxed &&
          resumedWithdraw == 1000L &&
          tooltipPresent

      if ok then
        log.info(
          "[HEXIC-PROBE] media_bundle=PASS default={}/{} contents={} after_click={}/{} simulated_withdraw={} withdrawn={} simulated_insert={} inserted={} restored={}/{} legacy_read={} legacy_migrated_items={} legacy_migrated_media={} wax_blocked={} resumed={} tooltip={}",
          defaultMedia,
          defaultMax,
          componentItemCount,
          mediaAfterClick,
          maxAfterClick,
          simulatedWithdraw,
          withdrawn,
          simulatedInsert,
          inserted,
          restoredMedia,
          restoredMax,
          legacyReadMedia,
          migratedLegacyCount,
          migratedLegacyMedia,
          blockedWithdraw,
          resumedWithdraw,
          tooltipPresent
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] media_bundle=FAIL default={}/{} expected_default={}/{} inserted_click={} battery_consumed={} contents={} after_click={}/{} expected_after_click={}/{} provide={} recharge={} simulated_withdraw={} unchanged_sim_withdraw={} withdrawn={} after_withdraw={} simulated_insert={} unchanged_sim_insert={} inserted={} after_insert={} restored_item={} restored_contents={} restored={}/{} legacy_read={} legacy_inserted={} legacy_battery_count={} legacy_migrated_items={} legacy_custom_removed={} legacy_migrated_media={} waxed_click={} honeycomb_count={} waxed={} blocked={} unchanged_waxed={} unwaxed_click={} unwaxed={} resumed={} tooltip={}",
          defaultMedia,
          defaultMax,
          expectedDefaultMedia,
          expectedDefaultMax,
          insertedByClick,
          batteryConsumed,
          componentItemCount,
          mediaAfterClick,
          maxAfterClick,
          expectedAfterClick,
          expectedMaxAfterClick,
          bundleItem.canProvideMedia(bundle),
          bundleItem.canRecharge(bundle),
          simulatedWithdraw,
          unchangedAfterSimulatedWithdraw,
          withdrawn,
          mediaAfterWithdraw,
          simulatedInsert,
          unchangedAfterSimulatedInsert,
          inserted,
          mediaAfterInsert,
          BuiltInRegistries.ITEM.getKey(restored.getItem),
          restoredItemCount,
          restoredMedia,
          restoredMax,
          legacyReadMedia,
          legacyInserted,
          legacyBattery.getCount,
          migratedLegacyCount,
          legacyCustomRemoved,
          migratedLegacyMedia,
          waxedByClick,
          honeycomb.getCount,
          waxed,
          blockedWithdraw,
          unchangedWhileWaxed,
          unwaxedByClick,
          unwaxed,
          resumedWithdraw,
          tooltipPresent
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] media_bundle=FAIL exception", t)
        1

  private def checkPlayerDeathDrops(server: net.minecraft.server.MinecraftServer): Int =
    try
      val level = server.overworld()
      val player = FakePlayerFactory.get(level, GameProfile(UUID.fromString("dd2d8214-d5c3-4d3e-a4e1-e65ef6378a75"), "HexicDeathProbe"))
      player.setPos(deathDropProbePos.getX + 0.5, deathDropProbePos.getY, deathDropProbePos.getZ + 0.5)
      val component: PlayerInfoComponent = player
      component.leftWeave = ItemStack(Mediaweave.colors(DyeColor.WHITE))
      component.rightWeave = ItemStack(Mediaweave.colors(DyeColor.BLACK))
      val drops = java.util.ArrayList[ItemEntity]()
      val event = LivingDropsEvent(player, level.damageSources().generic(), drops, false)
      HexicDeathDrops.onLivingDrops(event)
      val droppedStacks = drops.asScala.map(_.getItem).toSeq
      val leftDropped = droppedStacks.exists(stack => stack.getItem == Mediaweave.colors(DyeColor.WHITE))
      val rightDropped = droppedStacks.exists(stack => stack.getItem == Mediaweave.colors(DyeColor.BLACK))
      val componentsCleared = component.leftWeave.isEmpty && component.rightWeave.isEmpty
      if drops.size == 2 && leftDropped && rightDropped && componentsCleared then
        log.info(
          "[HEXIC-PROBE] player_death_drops=PASS drops={} left_dropped={} right_dropped={} components_cleared={}",
          drops.size,
          leftDropped,
          rightDropped,
          componentsCleared
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] player_death_drops=FAIL drops={} left_dropped={} right_dropped={} components_cleared={}",
          drops.size,
          leftDropped,
          rightDropped,
          componentsCleared
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] player_death_drops=FAIL exception", t)
        1

  private def checkCuriosSlotData(): Int =
    try
      val slotId = "hexic_mediaweave"
      val playerSlots = CuriosApi.getPlayerSlots(false)
      val slot = CuriosApi.getSlot(slotId, false)
      val ok = playerSlots.containsKey(slotId) && slot.isPresent
      if ok then
        log.info("[HEXIC-PROBE] curios_slots=PASS id={} player_slots={} slot_present={}", slotId, playerSlots.size, slot.isPresent)
        0
      else
        log.error("[HEXIC-PROBE] curios_slots=FAIL id={} player_slot_present={} slot_present={} player_slots={}", slotId, playerSlots.containsKey(slotId), slot.isPresent, playerSlots.keySet)
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] curios_slots=FAIL exception", t)
        1

  private def checkCuriosEquipment(server: net.minecraft.server.MinecraftServer): Int =
    try
      val player = FakePlayerFactory.getMinecraft(server.overworld())
      val item = Mediaweave.colors(DyeColor.WHITE)
      val source = ItemStack(item, 1)
      val inserted = CuriosCompat.insertOneIntoFirstEmptySlot(player, source)
      val equipped = CuriosCompat.equippedStacks(player)
      val equippedMediaweave = equipped.exists(stack => stack.getItem eq item)
      val consumedSource = source.isEmpty || source.getCount == 0
      if inserted && consumedSource && equippedMediaweave then
        log.info("[HEXIC-PROBE] curios_equipment=PASS inserted={} source_count={} equipped_count={}", inserted, source.getCount, equipped.size)
        0
      else
        log.error("[HEXIC-PROBE] curios_equipment=FAIL inserted={} source_count={} equipped_count={} equipped_mediaweave={}", inserted, source.getCount, equipped.size, equippedMediaweave)
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] curios_equipment=FAIL exception", t)
        1

  private def checkMediaweaveDispenser(server: net.minecraft.server.MinecraftServer): Int =
    val level = server.overworld()
    val dispenserPos = BlockPos(81, 80, 0)
    val frontPos = dispenserPos.relative(Direction.EAST)
    var player: ServerPlayer = null
    var channel: EmbeddedChannel = null
    try
      level.getChunkSource.getChunk(
        dispenserPos.getX >> 4,
        dispenserPos.getZ >> 4,
        ChunkStatus.FULL,
        true
      )
      val dispenserState =
        Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST)
      level.setBlockAndUpdate(dispenserPos, dispenserState)
      val dispenserEntity = level.getBlockEntity(dispenserPos) match
        case value: DispenserBlockEntity => value
        case other =>
          throw IllegalStateException(s"Missing dispenser block entity at $dispenserPos: $other")

      val profile = GameProfile(
        UUID.fromString("7ee945ed-d227-4cd1-93ce-a68160178ba5"),
        "HexicMediaweaveDispenser"
      )
      player = ServerPlayer(server, level, profile, ClientInformation.createDefault())
      player.moveTo(
        frontPos.getX + 0.5,
        frontPos.getY,
        frontPos.getZ + 0.5,
        0.0f,
        0.0f
      )
      val connection = Connection(PacketFlow.SERVERBOUND)
      channel = EmbeddedChannel()
      val channelField = classOf[Connection].getDeclaredField("channel")
      channelField.setAccessible(true)
      channelField.set(connection, channel)
      NetworkRegistry.configureMockConnection(connection)
      val listener = ServerGamePacketListenerImpl(
        server,
        connection,
        player,
        CommonListenerCookie.createInitial(profile, false)
      )
      if !(player.connection eq listener) then
        throw IllegalStateException("Could not install mediaweave dispenser probe connection")
      level.addNewPlayer(player)

      val mediaweave = Mediaweave.colors(DyeColor.CYAN)
      val behavior = DispenserBlock.DISPENSER_REGISTRY.get(mediaweave)
      val source = BlockSource(level, dispenserPos, dispenserState, dispenserEntity)
      val equipInput = ItemStack(mediaweave, 2)
      val equipResult = behavior.dispense(source, equipInput)
      val equipped = CuriosCompat.equippedStacks(player)
      val equippedCount =
        equipped.filter(_.getItem eq mediaweave).map(_.getCount).sum
      val equippedOk =
        (equipResult eq equipInput) &&
          equipResult.getCount == 1 &&
          equippedCount == 1

      player.moveTo(
        frontPos.getX + 32.5,
        frontPos.getY,
        frontPos.getZ + 0.5,
        0.0f,
        0.0f
      )
      val dropBounds = AABB.ofSize(Vec3.atCenterOf(dispenserPos), 8.0, 8.0, 8.0)
      val beforeDrops =
        level.getEntitiesOfClass(classOf[ItemEntity], dropBounds).asScala.map(_.getUUID).toSet
      val fallbackInput = ItemStack(mediaweave, 2)
      val fallbackResult = behavior.dispense(source, fallbackInput)
      val spawnedDrops =
        level
          .getEntitiesOfClass(classOf[ItemEntity], dropBounds)
          .asScala
          .filterNot(entity => beforeDrops.contains(entity.getUUID))
      val fallbackOk =
        (fallbackResult eq fallbackInput) &&
          fallbackResult.getCount == 1 &&
          spawnedDrops.exists(entity =>
            (entity.getItem.getItem eq mediaweave) && entity.getItem.getCount == 1
          )

      if equippedOk && fallbackOk then
        log.info(
          "[HEXIC-PROBE] mediaweave_dispenser=PASS behavior={} equipped={} source_after={} fallback_source_after={} fallback_drops={}",
          behavior.getClass.getName,
          equippedCount,
          equipResult.getCount,
          fallbackResult.getCount,
          spawnedDrops.size
        )
        0
      else
        log.error(
          "[HEXIC-PROBE] mediaweave_dispenser=FAIL behavior={} equipped={} source_after={} fallback_source_after={} fallback_drops={} equipped_ok={} fallback_ok={}",
          behavior.getClass.getName,
          equippedCount,
          equipResult.getCount,
          fallbackResult.getCount,
          spawnedDrops.map(_.getItem.toString).mkString(","),
          equippedOk,
          fallbackOk
        )
        1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] mediaweave_dispenser=FAIL exception", t)
        1
    finally
      if player != null && !player.isRemoved then player.discard()
      if channel != null then channel.close()
      level
        .getEntitiesOfClass(
          classOf[ItemEntity],
          AABB.ofSize(Vec3.atCenterOf(dispenserPos), 8.0, 8.0, 8.0)
        )
        .asScala
        .filter(entity => entity.getItem.getItem.isInstanceOf[Mediaweave])
        .foreach(_.discard())
      level.setBlockAndUpdate(dispenserPos, Blocks.AIR.defaultBlockState())

  private def checkChiselTable(event: ServerStartedEvent): Int =
    val level = event.getServer.overworld()
    try
      val block = BuiltInRegistries.BLOCK.get(chiselTableId)
      val blockKey = BuiltInRegistries.BLOCK.getKey(block)
      if blockKey != chiselTableId then
        log.error(s"[HEXIC-PROBE] chisel_table_entity=FAIL missing registered block $chiselTableId, got $blockKey")
        1
      else
        level.setBlockAndUpdate(probePos, Blocks.AIR.defaultBlockState())
        level.setBlockAndUpdate(probePos, block.defaultBlockState())
        val blockEntity = level.getBlockEntity(probePos)
        if blockEntity == null then
          log.error(s"[HEXIC-PROBE] chisel_table_entity=FAIL missing block entity at $probePos")
          1
        else
          val entityTypeKey = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType)
          if entityTypeKey == chiselTableId then
            log.info(s"[HEXIC-PROBE] chisel_table_entity=PASS block_entity_type=$entityTypeKey pos=$probePos")
            checkChiselTableInteractions(level)
          else
            log.error(s"[HEXIC-PROBE] chisel_table_entity=FAIL block_entity_type=$entityTypeKey pos=$probePos entity=$blockEntity")
            1
    catch
      case t: Throwable =>
        log.error("[HEXIC-PROBE] chisel_table_entity=FAIL exception", t)
        1
    finally
      level.setBlockAndUpdate(probePos, Blocks.AIR.defaultBlockState())

  private def checkChiselTableInteractions(level: net.minecraft.server.level.ServerLevel): Int =
    val fakePlayer = FakePlayerFactory.getMinecraft(level)
    fakePlayer.setShiftKeyDown(false)
    val fillFailures = fillChiselTable(level, fakePlayer)
    val extractFailures =
      if fillFailures == 0 then checkChiselTableExtract(level, fakePlayer)
      else 1
    val refillFailures =
      if extractFailures == 0 then fillChiselTable(level, fakePlayer)
      else 1
    val cutFailures =
      if refillFailures == 0 then checkChiselTableCut(level, fakePlayer)
      else 1
    fillFailures + extractFailures + refillFailures + cutFailures

  private def fillChiselTable(level: net.minecraft.server.level.ServerLevel, fakePlayer: net.minecraft.world.entity.player.Player): Int =
    val stack = ItemStack(HexItems.CHARGED_AMETHYST.get(), 1)
    fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, stack)
    val hit = BlockHitResult(Vec3.atCenterOf(probePos), Direction.UP, probePos, false)
    val result = level.getBlockState(probePos).useItemOn(stack, level, fakePlayer, InteractionHand.MAIN_HAND, hit)
    val heldStack = fakePlayer.getItemInHand(InteractionHand.MAIN_HAND)
    val blockEntity = level.getBlockEntity(probePos)
    val tag = blockEntity.saveCustomOnly(level.registryAccess())
    val filledBits = tag.getLongArray("b").map(java.lang.Long.bitCount).sum
    val ok = result == ItemInteractionResult.SUCCESS && heldStack.getCount == 0 && filledBits == 196
    if ok then
      log.info("[HEXIC-PROBE] chisel_table_fill=PASS result={} stack_count={} bits={}", result, heldStack.getCount, filledBits)
      0
    else
      log.error("[HEXIC-PROBE] chisel_table_fill=FAIL result={} stack_count={} bits={}", result, heldStack.getCount, filledBits)
      1

  private def checkChiselTableExtract(level: net.minecraft.server.level.ServerLevel, fakePlayer: net.minecraft.world.entity.player.Player): Int =
    fakePlayer.setShiftKeyDown(true)
    val emptyStack = ItemStack(Items.AIR, 0)
    fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, emptyStack)
    val hit = BlockHitResult(Vec3.atCenterOf(probePos), Direction.UP, probePos, false)
    val result = level.getBlockState(probePos).useItemOn(emptyStack, level, fakePlayer, InteractionHand.MAIN_HAND, hit)
    fakePlayer.setShiftKeyDown(false)

    val heldStack = fakePlayer.getItemInHand(InteractionHand.MAIN_HAND)
    val heldId = BuiltInRegistries.ITEM.getKey(heldStack.getItem)
    val heldBits =
      Option(heldStack.getNbt)
        .map(_.getLongArray("b").map(java.lang.Long.bitCount).sum)
        .getOrElse(0)
    val tableBits = tableBitCount(level)
    val ok = result == ItemInteractionResult.SUCCESS && heldId == id("cut") && heldStack.getCount == 1 && heldBits == 196 && tableBits == 0
    if ok then
      log.info("[HEXIC-PROBE] chisel_table_extract=PASS result={} item={} count={} cut_bits={} table_bits={}", result, heldId, heldStack.getCount, heldBits, tableBits)
      0
    else
      log.error("[HEXIC-PROBE] chisel_table_extract=FAIL result={} item={} count={} cut_bits={} table_bits={}", result, heldId, heldStack.getCount, heldBits, tableBits)
      1

  private def checkChiselTableCut(level: net.minecraft.server.level.ServerLevel, fakePlayer: net.minecraft.world.entity.player.Player): Int =
    val chiselItem = BuiltInRegistries.ITEM.get(id("chisel"))
    val stack = ItemStack(chiselItem, 1)
    fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, stack)
    val hit = BlockHitResult(Vec3.atCenterOf(probePos), Direction.UP, probePos, false)
    val result = level.getBlockState(probePos).useItemOn(stack, level, fakePlayer, InteractionHand.MAIN_HAND, hit)
    val tableBits = tableBitCount(level)
    val ok = result == ItemInteractionResult.SUCCESS && tableBits == 195
    if ok then
      log.info("[HEXIC-PROBE] chisel_table_cut=PASS result={} table_bits={}", result, tableBits)
      0
    else
      log.error("[HEXIC-PROBE] chisel_table_cut=FAIL result={} table_bits={}", result, tableBits)
      1

  private def tableBitCount(level: net.minecraft.server.level.ServerLevel): Int =
    val blockEntity = level.getBlockEntity(probePos)
    if blockEntity == null then 0
    else blockEntity.saveCustomOnly(level.registryAccess()).getLongArray("b").map(java.lang.Long.bitCount).sum

