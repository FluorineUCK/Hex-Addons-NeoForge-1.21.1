package org.eu.net.pool.hexic

import com.chocohead.mm.api.ClassTinkerers

def warCrimes(): Unit =
  ClassTinkerers
    .enumBuilder("at.petrak.hexcasting.api.casting.eval.ResolvedPatternType", classOf[Int], classOf[Int], classOf[Boolean])
    .addEnum("HEXIC$ECHO_SHARD_ABSORBED", 0x0a5060, 0x29dfeb, true)
    .build()