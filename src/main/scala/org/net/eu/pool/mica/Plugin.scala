package org.net.eu.pool.mica

import com.chocohead.mm.api.ClassTinkerers
import org.objectweb.asm.tree.ClassNode
import org.spongepowered.asm.mixin.extensibility.{IMixinConfigPlugin, IMixinInfo}

import java.nio.file.{Files, Path}
import java.util
import java.util.Base64
import scala.collection.mutable
import scala.collection.convert.ImplicitConversions.given

class Plugin extends IMixinConfigPlugin:
  private val mixinClasses = mutable.Buffer[String]()
  override def onLoad(mixinPackage: String): Unit =
    val config = Path.of("config/mica:extra_classes.txt")
    if Files.exists(config) then
      Files.readAllLines(config).forEach: line =>
        val ary = line.split("\t")
        ary(0) match
          case "CLASS" =>
            //<[[
            ClassTinkerers.define(ary(1), Base64.getDecoder.decode(ary(2)))
          case "MIXIN" =>
            ClassTinkerers.define(ary(1), Base64.getDecoder.decode(ary(2)));
            mixinClasses += ary(1)
            //]]>
  override def getRefMapperConfig: String = null
  override def shouldApplyMixin(targetClassName: String, mixinClassName: String): Boolean = true
  override def acceptTargets(myTargets: util.Set[String], otherTargets: util.Set[String]): Unit = ()
  override def preApply(targetClassName: String, targetClass: ClassNode, mixinClassName: String, mixinInfo: IMixinInfo): Unit = ()
  override def postApply(targetClassName: String, targetClass: ClassNode, mixinClassName: String, mixinInfo: IMixinInfo): Unit = ()
  override def getMixins: util.List[String] = mixinClasses.toVector