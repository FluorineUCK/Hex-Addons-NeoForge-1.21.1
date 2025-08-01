plugins {
    id("fabric-loom") version "1.10-SNAPSHOT"
    id("scala")
    kotlin("jvm") version "2.2.0"
    id("maven-publish")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 17
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()
}

scala {
    scalaVersion = "3.7.1"
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("mica") {
            sourceSet("main")
            sourceSet("client")
        }
    }
}

fabricApi {
    configureDataGeneration {
        modId = "mica"
        client = true
    }
}

repositories {
    mavenLocal()
	maven { url = uri("https://maven-pool-net-eu-org.ipns.dweb.link/") }
    maven { url = uri("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/") }
    maven { url = uri("https://dl.cloudsmith.io/public/libp2p/jvm-libp2p/maven/") }
    maven { url = uri("https://artifacts.consensys.net/public/maven/maven/") }
    maven { url = uri("https://jitpack.io/") }
    maven { url = uri("https://maven.quiltmc.org/repository/release/") }
    maven { url = uri("https://maven.terraformersmc.com/") }
    maven { url = uri("https://maven.shedaniel.me/") }
    maven { url = uri("https://maven.blamejared.com/") }
    maven { url = uri("https://masa.dy.fi/maven/") }
    maven { url = uri("https://maven.jamieswhiteshirt.com/libs-release/") }
    maven { url = uri("https://maven.ladysnake.org/releases/") }
    maven { url = uri("https://maven.gegy.dev/releases") }
    maven { url = uri("https://maven.skye.vg/") }
    exclusiveContent {
        forRepository {
            maven { url = uri("https://api.modrinth.com/maven/") }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
}

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.properties["kotlin_loader_version"]}")
    modImplementation("net.fabricmc:fabric-language-scala:${project.properties["scala_loader_version"]}")

    val minecraft_version = "1.20.1"
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    modImplementation("poollovernathan.fabric:mod-tools:1.1.5+1.20.1")
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(project.properties)
    }
}

tasks.withType<JavaCompile>().configureEach {
    // ensure that the encoding is set to UTF-8, no matter what the system default is
    // this fixes some edge cases with special characters not displaying correctly
    // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
    // If Javadoc is generated, this must be specified in that task too.
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName}" }
    }
}

// configure the maven publication
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
    repositories {
        // Add repositories to publish to here.
        // Notice: This block does NOT have the same function as the block in the top level.
        // The repositories here will be used for publishing your artifact, not for
        // retrieving dependencies.
    }
}
