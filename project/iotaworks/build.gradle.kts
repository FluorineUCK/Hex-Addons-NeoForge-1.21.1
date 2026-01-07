plugins {
    id("fabric-loom")
    id("scala")
    kotlin("jvm") version "2.2.0"
    id("maven-publish")
    id("de.undercouch.download") version "5.6.0"
    id("org.eu.net.pool.mc-plugin")
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    api("org.scala-lang:scala3-library_3:3.7.1")
    api("org.scala-lang:scala-library:2.13.6")
    api(project(":util", "namedElements"))
    modImplementation("at.petra-k.hexcasting:hexcasting-fabric-$minecraft_version:0.11.3")
    modImplementation("at.petra-k.paucal:paucal-fabric-$minecraft_version:0.6.0-pre-118")
    modImplementation("com.samsthenerd.inline:inline-fabric:$minecraft_version-1.0.1")
    modImplementation("dev.onyxstudios.cardinal-components-api:cardinal-components-block:5.2.3")
    modImplementation("dev.onyxstudios.cardinal-components-api:cardinal-components-item:5.2.3")
    modApi("miyucomics.hexcellular:hexcellular:1.1.0")
}

tasks.processResources {
    preprocessor {
        fabricMod("iotaworks", version as String) {
            name = "Iotaworks"
            description = "A Hex Casting addon for manipulating iotas."
            license = "LGPL-3.0"
            icon = "assets/iotaworks/icon.png"

            author("pool") {
                put("discord", "https://discord.com/users/758407438251720795")
            }

            depends("mod-tools", "^1.1.5+1.20.1")
            depends("phlib", "0.1.1")
            depends("hexcasting", ">=0.11.2")
            depends("hexcellular", "^1.0.4")
            recommends("hexpose", "*")
            breaks("hexic", "<2.0.0")

            entrypoint("org.eu.net.pool.iotaworks.main\$package::init")
            mixins("iotaworks.mixins.json")
        }
    }
}
