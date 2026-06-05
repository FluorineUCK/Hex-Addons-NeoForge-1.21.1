pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        mavenLocal() {
            content {
                includeGroupAndSubgroups("org.eu.net.pool")
            }
        }
        maven("https://maven.pool.net.eu.org/") {
            content {
                includeGroupAndSubgroups("org.eu.net.pool")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "hex-addons"

fun scanProjects(rootDir: File, prefix: String) {
    val listFile = rootDir.resolve("project.list");
    val declLines = if (listFile.exists()) listFile.useLines { it.toList() } else listOf("${rootDir.name}=.")
    for (line in declLines) {
        if (line.isBlank()) continue;
        val (name, path) = line.split("=")
        if (path == ".") {
            include(prefix)
            project(":$prefix").projectDir = rootDir
        } else {
            scanProjects(rootDir.resolve(path), if (name.isEmpty()) prefix else if (prefix.isEmpty()) name else "$prefix:$name")
        }
    }
}

includeBuild("plugin")
scanProjects(rootDir, "")
