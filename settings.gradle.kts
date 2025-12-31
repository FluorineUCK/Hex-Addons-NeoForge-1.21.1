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
