plugins {
    id("fabric-loom")
    id("scala")
    kotlin("jvm") version "2.2.0"
    id("maven-publish")
    id("de.undercouch.download") version "5.6.0"
    id("org.eu.net.pool.mc-plugin") version "0.1.1"
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    api("org.scala-lang:scala3-library_3:3.7.1")
    api("org.scala-lang:scala-library:2.13.6")
    modApi("poollovernathan.fabric:mod-tools:1.1.5+1.20.1")
    modApi("vazkii.patchouli:Patchouli:$minecraft_version-84-FABRIC")
    modImplementation("at.petra-k.hexcasting:hexcasting-fabric-$minecraft_version:0.11.3")
    modImplementation("at.petra-k.paucal:paucal-fabric-$minecraft_version:0.6.0-pre-118")
    modImplementation("com.samsthenerd.inline:inline-fabric:$minecraft_version-1.0.1")
    modApi("io.github.tropheusj:serialization-hooks:0.4.99999")
    val cardinal_version = "5.2.3"
    modImplementation("dev.onyxstudios.cardinal-components-api:cardinal-components-base:$cardinal_version")
    modImplementation("dev.onyxstudios.cardinal-components-api:cardinal-components-block:$cardinal_version")
    modImplementation("dev.onyxstudios.cardinal-components-api:cardinal-components-entity:$cardinal_version")
    modImplementation("dev.onyxstudios.cardinal-components-api:cardinal-components-item:$cardinal_version")
    modImplementation("dev.onyxstudios.cardinal-components-api:cardinal-components-level:$cardinal_version")
    modImplementation("dev.onyxstudios.cardinal-components-api:cardinal-components-world:$cardinal_version")
    modRuntimeOnly("dev.onyxstudios.cardinal-components-api:cardinal-components-api:$cardinal_version")
}

tasks.processResources {
    preprocessor {
        fabricMod("phlib", version as String) {
            name = "PoolHexLib"
            description = "Internal library for my Hex Casting addons."
            license = "LGPL-3.0"
            icon = "assets/phlib/icon.png"

            author("pool") {
                put("discord", "https://discord.com/users/758407438251720795")
            }

            depends("mod-tools", "^1.1.5+1.20.1")
            depends("hexcasting", ">=0.11.2")
            breaks("hexic", "<2.0.0")

            entrypoint("org.eu.net.pool.phlib.main\$package::init")
            mixins("phlib.mixins.json")
        }
    }
}
