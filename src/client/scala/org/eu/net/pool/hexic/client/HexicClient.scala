package org.eu.net.pool.hexic.client

import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator

def init(): Unit =
  println("Hello, client!")

def datagen(using FabricDataGenerator): Unit =
  println("Hello, datagen!")