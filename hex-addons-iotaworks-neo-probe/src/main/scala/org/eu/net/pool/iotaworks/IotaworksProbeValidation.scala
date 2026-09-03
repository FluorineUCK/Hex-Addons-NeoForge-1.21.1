package org.eu.net.pool
package iotaworks

import at.petrak.hexcasting.api.casting.eval.{CastResult, CastingEnvironment, ResolvedPatternType}
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv
import at.petrak.hexcasting.api.casting.eval.vm.{CastingImage, CastingVM, SpellContinuation}
import at.petrak.hexcasting.api.casting.iota.{DoubleIota, Iota, IotaType, PatternIota, Vec3Iota}
import at.petrak.hexcasting.api.casting.math.{HexDir, HexPattern}
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota
import at.petrak.hexcasting.api.casting.castables.{ConstMediaAction, SpellAction}
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.api.utils.TreeList

import at.petrak.hexcasting.common.lib.HexRegistries
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds
import miyucomics.hexcellular.{PropertyIota, StateStorage}
import net.minecraft.core.Registry
import net.minecraft.nbt.{CompoundTag, NbtOps}
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.server.ServerStartedEvent
import org.eu.net.pool.phlib.{MapIota, ServerWorld, finishCast, isDev}
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters.given

private object IotaworksProbeValidation:
  private val property = "iotaworks.probe.validateRegistries"
  private val log = LoggerFactory.getLogger("iotaworks")

  private def id(namespace: String, path: String): ResourceLocation =
    ResourceLocation.fromNamespaceAndPath(namespace, path)

  private val actionIds =
    Seq("create_property", "readonly_property", "observe_property", "set_property").map(id("hexcellular", _)) ++
      Seq("metatable", "set_subscript", "get_subscript").map(id("iotaworks", _)) ++
      (if isDev then Seq(id("iotaworks", "metatable_abridged")) else Seq.empty)
  private val iotaTypeIds =
    Seq(id("hexcellular", "property")) ++ (0 until 216).map(i => id("iotaworks", s"meta/$i"))
  private val continuationIds = Seq(id("iotaworks", "delta"))

  def onServerStarted(event: ServerStartedEvent): Unit =
    if java.lang.Boolean.getBoolean(property) then
      var failures = 0
      try
        val access = event.getServer.registryAccess()
        failures += checkRegistry("actions", access.registryOrThrow(HexRegistries.ACTION), actionIds)
        failures += checkRegistry("iota_types", access.registryOrThrow(HexRegistries.IOTA_TYPE), iotaTypeIds)
        failures += checkRegistry("continuation_types", access.registryOrThrow(HexRegistries.CONTINUATION_TYPE), continuationIds)
        failures += checkPatternDepthMixin()
        failures += checkSubscriptActions(event)
        failures += checkHexcellularPropertyActions(event)
        failures += checkMetatableAction(event)
        failures += checkHexcellularStatePersistence(event)
        failures += checkMetatableExecution(event)
        failures += checkPatternExecuteBridge(event)
        failures += checkPatternExecuteMixin(event)

        if failures == 0 then
          log.info(
            "[IOTAWORKS-PROBE] registries=PASS actions={} iota_types={} continuation_types={} pattern_depth_mixin=PASS subscript_actions=PASS hexcellular_actions=PASS metatable_action=PASS hexcellular_state=PASS metatable_execution=PASS pattern_execute_bridge=PASS pattern_execute_mixin=PASS",
            actionIds.size,
            iotaTypeIds.size,
            continuationIds.size
          )
        else
          log.error("[IOTAWORKS-PROBE] registries=FAIL failure_count={}", failures)
      catch
        case throwable: Throwable =>
          log.error("[IOTAWORKS-PROBE] registries=FAIL exception", throwable)
      finally
        event.getServer.halt(false)

  private def checkRegistry[T](label: String, registry: Registry[T], ids: Seq[ResourceLocation]): Int =
    val missing = ids.filterNot(id => registry.containsKey(id))
    if missing.isEmpty then
      log.info("[IOTAWORKS-PROBE] {}=PASS count={}", label, ids.size)
    else
      log.error("[IOTAWORKS-PROBE] {}=FAIL missing={}", label, missing.mkString(","))
    missing.size

  private def checkSubscriptActions(event: ServerStartedEvent): Int =
    try
      val level = event.getServer.overworld()
      val env = StaffCastEnv(FakePlayerFactory.getMinecraft(level), InteractionHand.MAIN_HAND)
      val registry = event.getServer.registryAccess().registryOrThrow(HexRegistries.ACTION)
      val setAction = registry.get(id("iotaworks", "set_subscript")).action()
      val getAction = registry.get(id("iotaworks", "get_subscript")).action()
      val base = HexPattern.fromAngles("eeedewa", HexDir.WEST)

      def operate(actionArgs: Seq[Iota], action: at.petrak.hexcasting.api.casting.castables.Action): Seq[Iota] =
        val image = CastingImage(TreeList.from(actionArgs.asJava), 0, TreeList.empty(), false, false, 0, CompoundTag())
        action.operate(env, image, SpellContinuation.Done.INSTANCE).getNewImage.getStack.asScala.toSeq

      val shifted = operate(Seq[Iota](PatternIota(base), DoubleIota(2.0)), setAction)
      val shiftedPattern = shifted.headOption.collect { case p: PatternIota => p.getPattern }.orNull
      val shiftedDepth =
        if shiftedPattern == null then Int.MinValue else shiftedPattern.asInstanceOf[HexPatternAccessor].depth

      val read = operate(Seq[Iota](PatternIota(shiftedPattern)), getAction)
      val readDepth = read.headOption.collect { case d: DoubleIota => d.getDouble }.getOrElse(Double.NaN)

      val fractionalRejected =
        try
          operate(Seq[Iota](PatternIota(base), DoubleIota(1.5)), setAction)
          false
        catch
          case _: MishapInvalidIota => true

      if shiftedDepth == 2 && math.abs(readDepth - 2.0) < DoubleIota.TOLERANCE && fractionalRejected then
        log.info("[IOTAWORKS-PROBE] subscript_actions=PASS shifted_depth={} read_depth={} fractional_rejected={}", shiftedDepth, readDepth, fractionalRejected)
        0
      else
        log.error("[IOTAWORKS-PROBE] subscript_actions=FAIL shifted_depth={} read_depth={} fractional_rejected={}", shiftedDepth, readDepth, fractionalRejected)
        1
    catch
      case throwable: Throwable =>
        log.error("[IOTAWORKS-PROBE] subscript_actions=FAIL exception", throwable)
        1

  private def checkMetatableAction(event: ServerStartedEvent): Int =
    try
      val level = event.getServer.overworld()
      given ServerWorld = level
      val env = StaffCastEnv(FakePlayerFactory.getMinecraft(level), InteractionHand.MAIN_HAND)
      val registry = event.getServer.registryAccess().registryOrThrow(HexRegistries.ACTION)
      val action = registry.get(id("iotaworks", "metatable")).action()
      val property = PropertyIota("iotaworks_probe_metatable", false)
      val userdata = DoubleIota(7.0)
      val display = DoubleIota(42.0)
      val color = Vec3Iota(Vec3(0.0, 0.6, 1.0))
      val image = CastingImage(TreeList.from(Seq[Iota](userdata, display, color, property).asJava), 0, TreeList.empty(), false, false, 0, CompoundTag())
      val resultStack = action.operate(env, image, SpellContinuation.Done.INSTANCE).getNewImage.getStack.asScala.toSeq
      val metatable = resultStack.headOption.collect { case m: AbstractMetatableIota => m }.orNull

      val key = HexPattern.fromAngles("eeedewa", HexDir.WEST)
      val mapped = DoubleIota(123.0)
      if metatable != null then
        StateStorage.Companion.setProperty(level, property.getName, MapIota() + (PatternIota(key) -> mapped))
      val mroValue = Option(metatable).flatMap(_.mro(key))
      val mroOk = mroValue.exists:
        case d: DoubleIota => math.abs(d.getDouble - mapped.getDouble) < DoubleIota.TOLERANCE
        case _ => false
      val displayOk = Option(metatable).exists(_.display().getString == display.display().getString)
      val userdataOk = Option(metatable).exists(m => Iota.tolerates(m.userdata, userdata))
      val propertyOk = Option(metatable).exists(m => m.metatable == property.getName && !m.readonlyMetatable)
      val typeOk = Option(metatable).exists(_.iotaType.color() == MetatableIotaType.colors((0, 9, 15)).color())
      val roundTripOk = Option(metatable).exists: m =>
        val decoded = MetatableIotaType.colors((0, 9, 15)).deserialize(m.toTag, level)
        decoded.metatable == property.getName &&
          decoded.display().getString == display.display().getString &&
          Iota.tolerates(decoded.userdata, userdata)

      if resultStack.size == 1 && mroOk && displayOk && userdataOk && propertyOk && typeOk && roundTripOk then
        log.info(
          "[IOTAWORKS-PROBE] metatable_action=PASS stack={} display='{}' property={} mro={} round_trip={}",
          resultStack.size,
          metatable.display().getString,
          metatable.metatable,
          mroValue.map(_.display().getString).getOrElse("missing"),
          roundTripOk
        )
        0
      else
        log.error(
          "[IOTAWORKS-PROBE] metatable_action=FAIL stack={} display_ok={} userdata_ok={} property_ok={} type_ok={} mro_ok={} round_trip_ok={}",
          resultStack.size,
          displayOk,
          userdataOk,
          propertyOk,
          typeOk,
          mroOk,
          roundTripOk
        )
        1
    catch
      case throwable: Throwable =>
        log.error("[IOTAWORKS-PROBE] metatable_action=FAIL exception", throwable)
        1

  private def checkHexcellularPropertyActions(event: ServerStartedEvent): Int =
    try
      val level = event.getServer.overworld()
      val env = StaffCastEnv(FakePlayerFactory.getMinecraft(level), InteractionHand.MAIN_HAND)
      val registry = event.getServer.registryAccess().registryOrThrow(HexRegistries.ACTION)
      def action(name: String) = registry.get(id("hexcellular", name)).action()
      val createAction = action("create_property").asInstanceOf[SpellAction]
      val createResult = createAction.execute(java.util.List.of(), env)
      val createdImage = createResult.getEffect.cast(env, CastingImage())
      val created = Option(createdImage).toSeq
        .flatMap(_.getStack.asScala)
        .lastOption
        .collect { case property: PropertyIota => property }
        .orNull
      val readonlyAction = action("readonly_property").asInstanceOf[ConstMediaAction]
      val readonly =
        if created == null then null
        else
          readonlyAction.execute(Seq[Iota](created).asJava, env).asScala
            .headOption
            .collect { case property: PropertyIota => property }
            .orNull

      val value = DoubleIota(64.0)
      val setAction = action("set_property").asInstanceOf[ConstMediaAction]
      val observeAction = action("observe_property").asInstanceOf[ConstMediaAction]
      val setStack =
        if created == null then Seq(DoubleIota(-1.0))
        else setAction.execute(Seq[Iota](created, value).asJava, env).asScala.toSeq
      val stored = if created == null then null else StateStorage.Companion.getProperty(level, created.getName)
      val readStack =
        if created == null then Seq.empty
        else observeAction.execute(Seq[Iota](created).asJava, env).asScala.toSeq
      val read = readStack.headOption.orNull
      val readonlyRejected =
        try
          if readonly != null then setAction.execute(Seq[Iota](readonly, value).asJava, env)
          false
        catch
          case _: MishapInvalidIota => true

      val createdOk =
        created != null &&
          created.getName.nonEmpty &&
          !created.getName.contains(":") &&
          !created.getReadonly &&
          createResult.getCost == MediaConstants.CRYSTAL_UNIT * 5
      val readonlyOk = readonly != null && readonly.getName == created.getName && readonly.getReadonly
      val setOk = setStack.isEmpty && (stored match
        case double: DoubleIota => math.abs(double.getDouble - value.getDouble) < DoubleIota.TOLERANCE
        case _ => false
      )
      val getOk = read match
        case double: DoubleIota => math.abs(double.getDouble - value.getDouble) < DoubleIota.TOLERANCE
        case _ => false

      val setCostOk = setAction.getMediaCost == MediaConstants.DUST_UNIT / 10
      if createdOk && readonlyOk && setOk && getOk && setCostOk && readonlyRejected then
        log.info(
          "[IOTAWORKS-PROBE] hexcellular_actions=PASS property={} create_cost={} set_cost={} readonly={} set_stack={} read={}",
          created.getName,
          createResult.getCost,
          setAction.getMediaCost,
          readonly.getReadonly,
          setStack.size,
          read.display().getString
        )
        0
      else
        log.error(
          "[IOTAWORKS-PROBE] hexcellular_actions=FAIL created_ok={} readonly_ok={} set_ok={} get_ok={} set_cost_ok={} readonly_rejected={}",
          createdOk,
          readonlyOk,
          setOk,
          getOk,
          setCostOk,
          readonlyRejected
        )
        1
    catch
      case throwable: Throwable =>
        log.error("[IOTAWORKS-PROBE] hexcellular_actions=FAIL exception", throwable)
        1

  private def checkMetatableExecution(event: ServerStartedEvent): Int =
    try
      val level = event.getServer.overworld()
      given ServerWorld = level
      val env = StaffCastEnv(FakePlayerFactory.getMinecraft(level), InteractionHand.MAIN_HAND)
      val registry = event.getServer.registryAccess().registryOrThrow(HexRegistries.ACTION)
      val action = registry.get(id("iotaworks", "metatable")).action()
      val property = PropertyIota("iotaworks_probe_metatable_exec", false)
      val userdata = DoubleIota(17.0)
      val display = DoubleIota(71.0)
      val color = Vec3Iota(Vec3(0.0, 0.6, 1.0))
      val image = CastingImage(TreeList.from(Seq[Iota](userdata, display, color, property).asJava), 0, TreeList.empty(), false, false, 0, CompoundTag())
      val resultStack = action.operate(env, image, SpellContinuation.Done.INSTANCE).getNewImage.getStack.asScala.toSeq
      val metatable = resultStack.headOption.collect { case m: AbstractMetatableIota => m }.orNull

      val execKey = HexPattern.fromAngles("deaqq", HexDir.SOUTH_EAST)
      val emptyMapPattern = HexPattern.fromAngles("dqdwdqd", HexDir.EAST)
      if metatable != null then
        StateStorage.Companion.setProperty(level, property.getName, MapIota() + (PatternIota(execKey) -> PatternIota(emptyMapPattern)))

      val initialImage = CastingImage(TreeList.from(Seq[Iota](DoubleIota(5.0)).asJava), 0, TreeList.empty(), false, false, 0, CompoundTag())
      val vm = CastingVM(initialImage, env)
      val result =
        if metatable == null then null
        else metatable.execute(vm, level, SpellContinuation.Done.INSTANCE)
      val newImage =
        if result == null then null
        else
          given CastingEnvironment = env
          finishCast(result, initialImage).getNewImage
      val newStack =
        if newImage == null then Seq.empty
        else newImage.getStack.asScala.toSeq
      val userdataPushed = newStack.exists:
        case d: DoubleIota => math.abs(d.getDouble - userdata.getDouble) < DoubleIota.TOLERANCE
        case _ => false
      val emptyMapPushed = newStack.exists:
        case m: MapIota => m.map.isEmpty
        case _ => false
      val seedKept = newStack.headOption.exists:
        case d: DoubleIota => math.abs(d.getDouble - 5.0) < DoubleIota.TOLERANCE
        case _ => false
      val ok = metatable != null &&
        result.getResolutionType == ResolvedPatternType.EVALUATED &&
        seedKept &&
        userdataPushed &&
        emptyMapPushed

      if ok then
        log.info(
          "[IOTAWORKS-PROBE] metatable_execution=PASS stack={} seed_kept={} userdata_pushed={} empty_map_pushed={} sound={}",
          newStack.size,
          seedKept,
          userdataPushed,
          emptyMapPushed,
          result.getSound
        )
        0
      else
        log.error(
          "[IOTAWORKS-PROBE] metatable_execution=FAIL metatable_present={} resolution={} stack={} seed_kept={} userdata_pushed={} empty_map_pushed={}",
          metatable != null,
          if result == null then "null" else result.getResolutionType.toString,
          newStack.size,
          seedKept,
          userdataPushed,
          emptyMapPushed
        )
        1
    catch
      case throwable: Throwable =>
        log.error("[IOTAWORKS-PROBE] metatable_execution=FAIL exception", throwable)
        1

  private def checkHexcellularStatePersistence(event: ServerStartedEvent): Int =
    try
      given ServerWorld = event.getServer.overworld()
      val key = DoubleIota(3.0)
      val expected = DoubleIota(9.0)
      val name = "iotaworks_probe_saved_state"
      val stored = MapIota() + (key -> expected)
      StateStorage.Companion.setProperty(summon[ServerWorld], name, stored)
      val tag = StateStorage.Companion.saveForProbe(summon[ServerWorld])
      val reloaded = StateStorage.Companion.loadForProbe(tag, summon[ServerWorld].registryAccess())
      val encoded = reloaded.getProperties.get(name)
      val decoded =
        if encoded == null then null
        else IotaType.TYPED_CODEC.parse(NbtOps.INSTANCE, encoded).result().orElse(null)
      val valueOk = decoded match
        case map: MapIota =>
          map(key) match
            case double: DoubleIota => math.abs(double.getDouble - expected.getDouble) < DoubleIota.TOLERANCE
            case _ => false
        case _ => false
      val dirty = StateStorage.Companion.getServerState(event.getServer).isDirty
      val rootFormat = !tag.contains("properties")
      if valueOk && dirty && rootFormat then
        log.info("[IOTAWORKS-PROBE] hexcellular_state=PASS saved_keys={} restored_type={} dirty={} root_format={}", reloaded.getProperties.size, decoded.getClass.getName, dirty, rootFormat)
        0
      else
        log.error("[IOTAWORKS-PROBE] hexcellular_state=FAIL tag={} restored={} dirty={} root_format={}", tag, Option(decoded).fold("null")(_.getClass.getName), dirty, rootFormat)
        1
    catch
      case throwable: Throwable =>
        log.error("[IOTAWORKS-PROBE] hexcellular_state=FAIL exception", throwable)
        1

  private def checkPatternDepthMixin(): Int =
    val depth = 12
    val pattern = HexPattern.fromAngles("eeedewa", HexDir.WEST)
    pattern.asInstanceOf[HexPatternAccessor].depth = depth

    val display = PatternIota.display(pattern).getString
    val displayOk = display.contains("\u00b9\u00b2")
    val encoded = HexPattern.CODEC.encodeStart(NbtOps.INSTANCE, pattern).result().orElse(null)
    val decoded =
      if encoded == null then null
      else HexPattern.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElse(null)
    val decodedDepth =
      if decoded == null then Int.MinValue
      else decoded.asInstanceOf[HexPatternAccessor].depth
    val codecOk = decodedDepth == depth

    if displayOk && codecOk then
      log.info("[IOTAWORKS-PROBE] pattern_depth_mixin=PASS display='{}' codec_depth={}", display, decodedDepth)
      0
    else
      log.error("[IOTAWORKS-PROBE] pattern_depth_mixin=FAIL display='{}' display_ok={} codec_depth={}", display, displayOk, decodedDepth)
      1

  private def checkPatternExecuteBridge(event: ServerStartedEvent): Int =
    try
      val level = event.getServer.overworld()
      given ServerWorld = level
      val env = StaffCastEnv(FakePlayerFactory.getMinecraft(level), InteractionHand.MAIN_HAND)
      val image = testImage()
      val vm = CastingVM(image, env)
      val pattern = depthPattern("eeedewa", 2)
      var originalCalled = false
      var shiftedAtOriginal = false
      val result = Extern.handleExecute(
        PatternIota(pattern),
        vm,
        level,
        SpellContinuation.Done.INSTANCE,
        (originalVm, _, continuation) =>
          originalCalled = true
          val shifted = originalVm.getImage
          shiftedAtOriginal = stackMatches(shifted, Seq(1.0)) && heldStackSize(shifted) == 2 && continuation.isInstanceOf[SpellContinuation.NotDone]
          CastResult(DoubleIota(99.0), continuation, shifted, java.util.List.of(), ResolvedPatternType.EVALUATED, HexEvalSounds.NOTHING.get())
      )
      val shiftedImage = result.getNewData
      val shiftedStackSize = shiftedImage.getStack.size
      val shiftedHeldSize = heldStackSize(shiftedImage)
      val restored = DeltaFrame(-2).evaluate(SpellContinuation.Done.INSTANCE, level, CastingVM(shiftedImage, env)).getNewData
      val restoredOk = stackMatches(restored, Seq(1.0, 2.0, 3.0)) && heldStackSize(restored) == 0
      if originalCalled && shiftedAtOriginal && restoredOk then
        log.info("[IOTAWORKS-PROBE] pattern_execute_bridge=PASS shifted_stack={} held_before_restore={} restored_stack={} restored_held={}", shiftedStackSize, shiftedHeldSize, restored.getStack.size, heldStackSize(restored))
        0
      else
        log.error("[IOTAWORKS-PROBE] pattern_execute_bridge=FAIL original_called={} shifted_at_original={} restored_ok={}", originalCalled, shiftedAtOriginal, restoredOk)
        1
    catch
      case throwable: Throwable =>
        log.error("[IOTAWORKS-PROBE] pattern_execute_bridge=FAIL exception", throwable)
        1

  private def checkPatternExecuteMixin(event: ServerStartedEvent): Int =
    try
      val level = event.getServer.overworld()
      val env = StaffCastEnv(FakePlayerFactory.getMinecraft(level), InteractionHand.MAIN_HAND)
      val vm = CastingVM(testImage(), env)
      val iota = PatternIota(depthPattern("eeedewa", 2))
      val result = iota.execute(vm, level, SpellContinuation.Done.INSTANCE)
      val shifted = vm.getImage
      val shiftedOk = stackMatches(shifted, Seq(1.0)) && heldStackSize(shifted) == 2
      val continuationOk = result.getContinuation.isInstanceOf[SpellContinuation.NotDone]
      if shiftedOk && continuationOk then
        log.info("[IOTAWORKS-PROBE] pattern_execute_mixin=PASS shifted_stack={} held={} continuation={}", shifted.getStack.size, heldStackSize(shifted), result.getContinuation.getClass.getName)
        0
      else
        log.error("[IOTAWORKS-PROBE] pattern_execute_mixin=FAIL shifted_ok={} continuation_ok={} stack={} held={}", shiftedOk, continuationOk, shifted.getStack.size, heldStackSize(shifted))
        1
    catch
      case throwable: Throwable =>
        log.error("[IOTAWORKS-PROBE] pattern_execute_mixin=FAIL exception", throwable)
        1

  private def testImage(): CastingImage =
    CastingImage(
      TreeList.from(Seq[Iota](DoubleIota(1.0), DoubleIota(2.0), DoubleIota(3.0)).asJava),
      0,
      TreeList.empty(),
      false,
      false,
      0,
      CompoundTag()
    )

  private def depthPattern(angles: String, depth: Int): HexPattern =
    val pattern = HexPattern.fromAngles(angles, HexDir.WEST)
    pattern.asInstanceOf[HexPatternAccessor].depth = depth
    pattern

  private def heldStackSize(image: CastingImage): Int =
    image.getUserData.getList("iotaworks:stack", net.minecraft.nbt.Tag.TAG_COMPOUND).size

  private def stackMatches(image: CastingImage, expected: Seq[Double]): Boolean =
    val stack = image.getStack.asScala.toSeq
    stack.size == expected.size &&
      stack.zip(expected).forall:
        case (double: DoubleIota, value) => math.abs(double.getDouble - value) < DoubleIota.TOLERANCE
        case _ => false
