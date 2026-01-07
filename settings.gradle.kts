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

include("util")
includeBuild("plugin")
for (mod in listOf("hexic", "iotaworks", "hexxytounge", "hexxychests")) {
    include(mod)
    project(":$mod").projectDir = file("project/$mod")
}