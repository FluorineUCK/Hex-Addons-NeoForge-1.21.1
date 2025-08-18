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
  override def onLoad(mixinPackage: String): Unit = ()
  override def getRefMapperConfig: String = null
  override def shouldApplyMixin(targetClassName: String, mixinClassName: String): Boolean = true
  override def acceptTargets(myTargets: util.Set[String], otherTargets: util.Set[String]): Unit = ()
  override def preApply(targetClassName: String, targetClass: ClassNode, mixinClassName: String, mixinInfo: IMixinInfo): Unit = ()
  override def postApply(targetClassName: String, targetClass: ClassNode, mixinClassName: String, mixinInfo: IMixinInfo): Unit = ()
  override def getMixins: util.List[String] = null