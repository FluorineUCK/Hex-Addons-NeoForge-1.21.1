import de.undercouch.gradle.tasks.download.Download
import org.gradle.kotlin.dsl.support.uppercaseFirstChar

plugins {
    id("fabric-loom") version "1.10-SNAPSHOT"
    id("scala")
    kotlin("jvm") version "2.2.0"
    id("maven-publish")
    id("de.undercouch.download") version "5.6.0"
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
        register("hexic") {
            sourceSet("main")
            sourceSet("client")
        }
    }
}

fabricApi {
    configureDataGeneration {
        modId = "hexic"
        client = true
    }
}

//tasks.register<Download>("fetchHexxy4") {
//    src("https://github.com/chloetax/hexxy4/archive/@.tar.gz")
//    dest("$buildDir/hexxy4.tgz")
//    overwrite(true)
//}
//
//tasks.register<Sync>("hexxy4") {
//    from(tarTree("$buildDir/hexxy4.tgz"))
//    into("$buildDir/hexxy4")
//}

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://api.modrinth.com/maven") }
    maven { url = uri("https://artifacts.consensys.net/public/maven/maven/") }
    maven { url = uri("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/") }
    maven { url = uri("https://dl.cloudsmith.io/public/libp2p/jvm-libp2p/maven/") }
    maven { url = uri("https://jitpack.io/") }
    maven { url = uri("https://masa.dy.fi/maven/") }
    maven { url = uri("https://maven.blamejared.com/") }
    maven { url = uri("https://maven.gegy.dev/releases") }
    maven { url = uri("https://maven.jamieswhiteshirt.com/libs-release/") }
    maven { url = uri("https://maven.kosmx.dev/") }
    maven { url = uri("https://maven.ladysnake.org/releases/") }
    maven { url = uri("https://maven.quiltmc.org/repository/release/") }
    maven { url = uri("https://maven.shedaniel.me/") }
    maven { url = uri("https://maven.skye.vg/") }
    maven { url = uri("https://maven.terraformersmc.com/") }
    maven { url = uri("https://maven.terraformersmc.com/releases") }
    maven { url = uri("https://mvn.devos.one/snapshots/") }
    maven { url = uri("https://maven-pool-net-eu-org.ipns.dweb.link/") }
//    flatDir { this. = uri("https://raw.githubusercontent.com/chloetax/hexxy4/HEAD") }
}

data class Addon(val id: String, val name: String, val version: String, val hexicVersion: String, val description: String) {
    val camelCased = id.replace(Regex("-(\\w)")) { it.groups[1]!!.value.uppercase() }
}

val hexcasting get() = if (Math.random() < 0.5) "Hex Casting" else "Hexcasting"

val allJars by tasks.register("allJars") {
    dependsOn("build")
}

for (addon in listOf(
    Addon("infinite-hexxy", "Infinite Hexxy", "0.1.0", "0.2.0", "Exposes patterns to $hexcasting that it... probably shouldn't have."),
    Addon("hexent", "Hexent", "0.1.0", "0.2.0", "Various changes and bugfixes to other $hexcasting addons' patterns."),
    Addon("hexa", "Hexa", "0.1.0", "0.2.0", "Utilities for more intuitive $hexcasting spells."),
)) {
    val jarTask by tasks.register<Jar>("${addon.camelCased}Jar") {
        archiveBaseName = addon.id
        archiveVersion = addon.version
        from("addon/${addon.name}")
        from(resources.text.fromString("""
            {
              "schemaVersion": 1,
              "id": "${addon.id}",
              "version": "${addon.version}",
              "name": "${addon.name}",
              "description": "${addon.description}",
              "authors": [
                "PoolloverNathan"
              ],
              "contact": {},
              "license": "GPL-3.0",
              "icon": "assets/hexic/${addon.id}.png",
              "environment": "*",
              "depends": {
                "hexic": ">=${addon.hexicVersion}"
              }
            }
        """.trimIndent())) {
            rename { "fabric.mod.json" }
        }
    }
    allJars.dependsOn(jarTask)
    publishing {
        publications {
            create<MavenPublication>("maven${addon.camelCased.replaceFirstChar { it.uppercase() }}Java") {
                artifactId = addon.id
                artifact(jarTask) {
                    group = rootProject.group
                }
            }
        }
    }
}

run {
    val buildKubo by tasks.register("kubo")
    val outRoot = file("$buildDir/kubo")
	fun kuboTask(os: String, arch: String, external: Boolean = false, parent: Task = buildKubo) {
		val task = tasks.register<Exec>("kubo${os.uppercaseFirstChar()}${arch.uppercaseFirstChar()}") {
			inputs.dir("vendor/kubo")
			workingDir = file("vendor/kubo")
			doFirst {
				if (!outRoot.exists()) {
					check(outRoot.mkdirs())
				}
			}
			if (!external) environment("CGO_ENABLED", "0")
			environment("GOOS", os)
			environment("GOARCH", arch)
			val out = outRoot.resolve("ipfs.$os.$arch.exe")
			commandLine("go", "build", "-o", out.absoluteFile, "github.com/ipfs/kubo/cmd/ipfs")
			outputs.file(out)
		}
		parent.dependsOn(task)
	}
	fun kuboTasks(os: String, vararg arches: String, external: Boolean = false) {
        val archTask by tasks.register("kubo${os.uppercaseFirstChar()}")
		for (arch in arches) {
			kuboTask(os, arch, external=external, parent=archTask)
		}
        buildKubo.dependsOn(archTask)
	}
	kuboTasks("linux", "386", "amd64", "arm", "arm64", "mips", "mips64", "mips64le", "mipsle", "ppc64", "ppc64le", "riscv64", "s390x")
	kuboTasks("windows", "386", "amd64", "arm", "arm64")
    kuboTasks("darwin", "amd64")

    tasks.register("cleanKubo") {
        onlyIf { outRoot.exists() }
        doLast {
            if (outRoot.exists()) {
                check(outRoot.deleteRecursively())
            }
        }
    }

    tasks.processResources {
//        dependsOn(buildKubo)
//        inputs.dir("$buildDir/kubo")
//        from(outRoot) {
//            into("vendor/hexic/")
//        }
    }
}

sourceSets {
    main {
        java {
            srcDirs.clear();
        }
        scala {
            srcDirs += file("src/main/java");
        }
    }
}

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:1.13.4+kotlin.2.2.0")
    include(modImplementation("net.fabricmc:fabric-language-scala:${project.properties["scala_loader_version"]}")!!)

    val minecraft_version = "1.20.1"
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    modImplementation("poollovernathan.fabric:mod-tools:1.1.5+1.20.1")
    include(modApi("org.eu.net.pool:common-curses:1.1.5-SNAPSHOT")!!)
    include(api("org.scala-lang:scala3-library_3:3.7.1")!!)
    include(api("org.scala-lang:scala-library:2.13.6")!!)
    //modCompileOnly("at.petra-k.hexcasting:hexcasting-common-$minecraft_version:0.11.2+fork-SNAPSHOT")
    include(modImplementation("at.petra-k.hexcasting:hexcasting-fabric-$minecraft_version:0.11.2+fork-SNAPSHOT")!!)
    modImplementation("at.petra-k.paucal:paucal-fabric-$minecraft_version:0.6.0-pre-118")
    modImplementation("com.samsthenerd.inline:inline-fabric:$minecraft_version-1.0.1")
    include(implementation("com.github.Chocohead:Fabric-ASM:v2.3")!!)
    modCompileOnly("dev.kineticcat.hexportation:hexportation-fabric-1.20.1-fabric-fabric:0.0.3")
    modImplementation("io.github.tropheusj:serialization-hooks:0.4.99999")
    modImplementation("maven.modrinth:hexcassettes:1.1.4")
    modImplementation("maven.modrinth:spasm:0.2.2")
//    modImplementation("maven.modrinth:slate-works:1.0.5")
    modCompileOnly("miyucomics.hexical:hexical:main-SNAPSHOT")
    modImplementation("ram.talia.moreiotas:moreiotas-fabric-$minecraft_version:0.1.0-6") { exclude("moreiotas") }
    modImplementation("ram.talia.hexal:hexal-fabric-1.20.1:0.3.0-3-skyevg-unofficial") { exclude("hexal") }
    modImplementation("maven.modrinth:hexcellular:1.0.4")
    modImplementation("miyucomics.hexpose:hexpose:1.0.0")
//    modImplementation("miyucomics:hexpose:1.0.0")
//    modImplementation(files("hexical-2.0.0.jar"))
    val cardinal_version = "5.2.3"
    modApi("dev.onyxstudios.cardinal-components-api:cardinal-components-base:$cardinal_version")
    modApi("dev.onyxstudios.cardinal-components-api:cardinal-components-block:$cardinal_version")
    modApi("dev.onyxstudios.cardinal-components-api:cardinal-components-entity:$cardinal_version")
    modApi("dev.onyxstudios.cardinal-components-api:cardinal-components-item:$cardinal_version")
    modApi("dev.onyxstudios.cardinal-components-api:cardinal-components-level:$cardinal_version")
    modApi("dev.onyxstudios.cardinal-components-api:cardinal-components-world:$cardinal_version")
    modRuntimeOnly("dev.onyxstudios.cardinal-components-api:cardinal-components-api:$cardinal_version")
    include(implementation("net.bytebuddy:byte-buddy:1.17.7")!!)
    include(implementation("net.bytebuddy:byte-buddy-agent:1.17.7")!!)
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

tasks.withType<AbstractArchiveTask> {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.withType<JavaCompile>().configureEach {
    // ensure that the encoding is set to UTF-8, no matter what the system default is
    // this fixes some edge cases with special characters not displaying correctly
    // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
    // If Javadoc is generated, this must be specified in that task too.
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<ScalaCompile>().configureEach {
    scalaCompileOptions.additionalParameters.addAll(listOf("-explain-cyclic", "-Ydebug-cyclic", "-experimental", "-feature", "-Ycc-debug"))
}

loom.mixin.useLegacyMixinAp = false

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName}" }
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
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
