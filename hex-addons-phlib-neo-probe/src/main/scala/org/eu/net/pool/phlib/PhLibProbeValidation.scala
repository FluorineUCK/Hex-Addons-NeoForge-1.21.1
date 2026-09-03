package org.eu.net.pool
package phlib

import at.petrak.hexcasting.api.casting.iota.{DoubleIota, Iota, IotaType}
import at.petrak.hexcasting.common.lib.HexRegistries
import at.petrak.hexcasting.xplat.IXplatAbstractions
import net.minecraft.core.Registry
import net.minecraft.nbt.NbtOps
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.server.ServerStartedEvent
import org.slf4j.LoggerFactory

private[phlib] object PhLibProbeValidation:
  private val property = "phlib.probe.validateRegistries"
  private val log = LoggerFactory.getLogger("phlib")

  private def id(namespace: String, path: String): ResourceLocation =
    ResourceLocation.fromNamespaceAndPath(namespace, path)

  private val actionIds = Seq(id("phlib", "empty_map"))
  private val iotaTypeIds = Seq(id("phlib", "map"))
  private val arithmeticIds = Seq(id("phlib", "maps"))

  def onServerStarted(event: ServerStartedEvent): Unit =
    if java.lang.Boolean.getBoolean(property) then
      var failures = 0
      try
        val access = event.getServer.registryAccess()
        failures += checkRegistry("actions", access.registryOrThrow(HexRegistries.ACTION), actionIds)
        failures += checkRegistry("iota_types", access.registryOrThrow(HexRegistries.IOTA_TYPE), iotaTypeIds)
        failures += checkRegistry("arithmetics", access.registryOrThrow(HexRegistries.ARITHMETIC), arithmeticIds)
        failures += checkCommands(event)
        failures += checkMapIotaCodec(event)
        failures += checkGimmeIotaStaffcast(event)
        failures += checkGimmeIotaCommandExecution(event)
        failures += checkTestHexesCommandExecution(event)

        if failures == 0 then
          log.info(
            "[PHLIB-PROBE] registries=PASS actions={} iota_types={} arithmetics={} commands=PASS map_iota_codec=PASS gimmeiota_staffcast=PASS gimmeiota_command=PASS test_hexes=PASS",
            actionIds.size,
            iotaTypeIds.size,
            arithmeticIds.size
          )
        else
          log.error("[PHLIB-PROBE] registries=FAIL failure_count={}", failures)
      catch
        case throwable: Throwable =>
          log.error("[PHLIB-PROBE] registries=FAIL exception", throwable)
      finally
        event.getServer.halt(false)

  private def checkRegistry[T](label: String, registry: Registry[T], ids: Seq[ResourceLocation]): Int =
    val missing = ids.filterNot(id => registry.containsKey(id))
    if missing.isEmpty then
      log.info("[PHLIB-PROBE] {}=PASS count={}", label, ids.size)
    else
      log.error("[PHLIB-PROBE] {}=FAIL missing={}", label, missing.mkString(","))
    missing.size

  private def checkCommands(event: ServerStartedEvent): Int =
    val root = event.getServer.getCommands.getDispatcher.getRoot
    val expectedCommands = Seq("gimmeiota") ++ (if isDev then Seq("testHexes") else Seq.empty)
    val missing = expectedCommands.filter(name => root.getChild(name) == null)
    if missing.isEmpty then
      log.info("[PHLIB-PROBE] commands=PASS count={}", expectedCommands.size)
      0
    else
      log.error("[PHLIB-PROBE] commands=FAIL missing={}", missing.mkString(","))
      missing.size

  private def checkMapIotaCodec(event: ServerStartedEvent): Int =
    given ServerWorld = event.getServer.overworld()
    val key = new DoubleIota(1.0)
    val value = new DoubleIota(2.0)
    val map = MapIota.fromMap(Map[Iota, Iota](key -> value))
    val encoded = IotaType.TYPED_CODEC.encodeStart(NbtOps.INSTANCE, map).result().orElse(null)
    val decoded =
      if encoded == null then null
      else IotaType.TYPED_CODEC.parse(NbtOps.INSTANCE, encoded).result().orElse(null)

    val lookupOk = decoded match
      case decodedMap: MapIota =>
        decodedMap(key) match
          case double: DoubleIota => math.abs(double.getDouble - value.getDouble) < DoubleIota.TOLERANCE
          case _ => false
      case _ => false

    if lookupOk then
      log.info("[PHLIB-PROBE] map_iota_codec=PASS encoded_type={} decoded_type={}", encoded.getClass.getName, decoded.getClass.getName)
      0
    else
      log.error("[PHLIB-PROBE] map_iota_codec=FAIL encoded={} decoded={}", encoded, decoded)
      1

  private def checkGimmeIotaStaffcast(event: ServerStartedEvent): Int =
    try
      val player = FakePlayerFactory.getMinecraft(event.getServer.overworld())
      val before = IXplatAbstractions.INSTANCE.getStaffcastVM(player, InteractionHand.MAIN_HAND).getImage.getStack.size
      val pushed = new DoubleIota(42.0)
      player.gimmeIota(pushed)
      val stack = IXplatAbstractions.INSTANCE.getStaffcastVM(player, InteractionHand.MAIN_HAND).getImage.getStack
      val top = if stack.isEmpty then null else stack.get(stack.size - 1)
      val ok = stack.size == before + 1 && (top match
        case double: DoubleIota => math.abs(double.getDouble - pushed.getDouble) < DoubleIota.TOLERANCE
        case _ => false
      )
      if ok then
        log.info("[PHLIB-PROBE] gimmeiota_staffcast=PASS before={} after={} top={}", before, stack.size, top.display().getString)
        0
      else
        log.error("[PHLIB-PROBE] gimmeiota_staffcast=FAIL before={} after={} top={}", before, stack.size, Option(top).fold("null")(_.getClass.getName))
        1
    catch
      case throwable: Throwable =>
        log.error("[PHLIB-PROBE] gimmeiota_staffcast=FAIL exception", throwable)
        1

  private def checkGimmeIotaCommandExecution(event: ServerStartedEvent): Int =
    try
      val player = FakePlayerFactory.getMinecraft(event.getServer.overworld())
      val source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput()
      val before = IXplatAbstractions.INSTANCE.getStaffcastVM(player, InteractionHand.MAIN_HAND).getImage.getStack.size
      event.getServer.getCommands.performPrefixedCommand(source, "gimmeiota phlib:map {entries:[]}")
      val stack = IXplatAbstractions.INSTANCE.getStaffcastVM(player, InteractionHand.MAIN_HAND).getImage.getStack
      val top = if stack.isEmpty then null else stack.get(stack.size - 1)
      val ok = stack.size == before + 1 && (top match
        case map: MapIota => map.map.isEmpty
        case _ => false
      )
      if ok then
        log.info("[PHLIB-PROBE] gimmeiota_command=PASS before={} after={} top={}", before, stack.size, top.display().getString)
        0
      else
        log.error("[PHLIB-PROBE] gimmeiota_command=FAIL before={} after={} top={}", before, stack.size, Option(top).fold("null")(_.getClass.getName))
        1
    catch
      case throwable: Throwable =>
        log.error("[PHLIB-PROBE] gimmeiota_command=FAIL exception", throwable)
        1

  private def checkTestHexesCommandExecution(event: ServerStartedEvent): Int =
    try
      val root = event.getServer.getCommands.getDispatcher.getRoot
      if root.getChild("testHexes") == null then
        log.error("[PHLIB-PROBE] test_hexes=FAIL missing_command=true")
        1
      else
        val testRoot = java.nio.file.Files.createTempDirectory("phlib-testhexes-probe")
        val testFile = testRoot.resolve("stack_roundtrip.snbt")
        java.nio.file.Files.writeString(
          testFile,
          """[
            |  {vm:"staff"},
            |  {push:1.0d},
            |  {check:1.0d},
            |  {checkStack:0}
            |]""".stripMargin,
          java.nio.charset.StandardCharsets.UTF_8
        )
        val player = FakePlayerFactory.getMinecraft(event.getServer.overworld())
        val source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput()
        val result = event.getServer.getCommands.getDispatcher.execute(s"testHexes ${testRoot.toAbsolutePath}", source)
        val reportPath = java.nio.file.Paths.get("logs", "phlib-tests.log")
        val report =
          if java.nio.file.Files.exists(reportPath) then
            java.nio.file.Files.readString(reportPath, java.nio.charset.StandardCharsets.UTF_8)
          else ""
        val ok = result == 1 && report.contains("===== PHLIB /testHexes REPORT =====") && !report.contains("=== FAILED ===")
        if ok then
          log.info("[PHLIB-PROBE] test_hexes=PASS result={} file={} report={}", result, testFile.toAbsolutePath, reportPath.toAbsolutePath)
          0
        else
          log.error("[PHLIB-PROBE] test_hexes=FAIL result={} report_exists={} report={}", result, java.nio.file.Files.exists(reportPath), report.replace('\n', '|'))
          1
    catch
      case throwable: Throwable =>
        log.error("[PHLIB-PROBE] test_hexes=FAIL exception", throwable)
        1
