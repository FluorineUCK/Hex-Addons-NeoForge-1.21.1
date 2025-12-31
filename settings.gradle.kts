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

include("util", "iotaworks", "hexxytounge", "hexxychests")
project(":iotaworks").projectDir = file("project/iotaworks")
project(":hexxytounge").projectDir = file("project/hexxytounge")
project(":hexxychests").projectDir = file("project/hexxychests")