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
    include(modImplementation("maven.modrinth:jsonpatcher:1.0.0-beta.4+mc.1.20.1")!!)
    implementation("com.github.mattidragon:JsonPatcherLang:v1.0.0-beta.3") // trans maven.modrinth:jsonpatcher
    modImplementation("com.github.mattidragon:ConfigToolkit:v1.0.0") // trans maven.modrinth:jsonpatcher
    modImplementation("at.petra-k.hexcasting:hexcasting-fabric-$minecraft_version:0.11.3")
    modImplementation("at.petra-k.paucal:paucal-fabric-$minecraft_version:0.6.0-pre-118")
    modImplementation("com.samsthenerd.inline:inline-fabric:$minecraft_version-1.0.1")
    modImplementation("dev.onyxstudios.cardinal-components-api:cardinal-components-entity:5.2.3")
    modImplementation("ram.talia.moreiotas:moreiotas-fabric-$minecraft_version:0.1.1") { exclude(module = "serialization-hooks") }
}

tasks.processResources {
    preprocessor {
        fabricMod("hexxytounge" /* typo'd but too late to change */, version as String) {
            name = "Tongued Hexxy"
            description = "Adds chat manipulation to Hex Casting because that's certainly a great idea."
            license = "LGPL-3.0"
            icon = "hexxytounge.icon.png"

            author("pool") {
                put("discord", "https://discord.com/users/758407438251720795")
            }

            depends("mod-tools", "^1.1.5+1.20.1")
            depends("phlib", "0.1.1")
            depends("hexcasting", ">=0.11.2")
            depends("moreiotas", ">=0.1.1")
            depends("cardinal-components-entity", "^5.2.3")
            depends("jsonpatcher", "^1.0.0-beta")
            recommends("hexpose", "*")
            breaks("hexic", "<2.0.0")

            entrypoint("org.eu.net.pool.hexxytounge.main\$package::init")
            entrypoint("cardinal-components", "org.eu.net.pool.hexxytounge.Components")
            mixins("hexxytongge.mixins.json")
            cardinalComponents("murmur", "reveal")
        }
    }

    from("icon.png") {
        rename { "hexxytounge.icon.png" }
    }
}
