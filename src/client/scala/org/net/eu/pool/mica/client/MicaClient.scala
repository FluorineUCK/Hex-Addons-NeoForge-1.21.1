package org.net.eu.pool.mica.client

import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator

def init(): Unit =
	println("Hello, client!")

def datagen(using FabricDataGenerator): Unit =
	println("Hello, datagen!")