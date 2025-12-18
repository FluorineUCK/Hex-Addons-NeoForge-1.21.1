plugins {
    id("fabric-loom") version "1.13-SNAPSHOT"
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
    api(project(":util", "namedElements"))
    modImplementation("at.petra-k.hexcasting:hexcasting-fabric-$minecraft_version:0.11.3")
    modImplementation("at.petra-k.paucal:paucal-fabric-$minecraft_version:0.6.0-pre-118")
    modImplementation("com.samsthenerd.inline:inline-fabric:$minecraft_version-1.0.1")
}

tasks.processResources {
    preprocessor {
        fabricMod("hexxytounge" /* typo'd but too late to change */, version as String) {
            name = "Tongued Hexxy"
            description = "Adds chat manipulation to Hex Casting because that's certainly a great idea."
            license = "LGPL-3.0"
            icon = "hexxytounge.icon.png" // downloadedResource("https://media.discordapp.net/attachments/950847275549229086/1451031021356060722/HungryHexxy.png?ex=6944b172&is=69435ff2&hm=dff7455cb5739537eae623c051581ba1595cc1c575282215de8a45b20a37a6a9&=&format=webp&quality=lossless&width=355&height=290")

            author("pool") {
                put("discord", "https://discord.com/users/758407438251720795")
            }

            depends("mod-tools", "^1.1.5+1.20.1")
            depends("phlib", "0.1.1")
            depends("hexcasting", ">=0.11.2")
            recommends("hexpose", "*")
            breaks("hexic", "<2.0.0")

            entrypoint("org.eu.net.pool.hexxytounge.main\$package::init")
        }
    }
}
