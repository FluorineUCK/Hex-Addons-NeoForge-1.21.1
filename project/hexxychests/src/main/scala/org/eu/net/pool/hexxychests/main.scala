package org.eu.net.pool.hexxychests
import net.minecraft.util.Identifier
import org.eu.net.pool.phlib.{*, given}
import org.slf4j.{Logger, LoggerFactory}

private[hexxychests] given Conversion[String, Identifier] = Identifier.of("hexxychests", _)
private[hexxychests] given Logger = LoggerFactory.getLogger("hexxychests")

def init() =
  println("Hello, world!")