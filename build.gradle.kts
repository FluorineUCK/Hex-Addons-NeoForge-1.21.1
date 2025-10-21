import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal
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
    maven { url = uri("https://maven.pool.net.eu.org/") }
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

fun download(url: String, name: String = file(url).name): Download {
    val outPath = file("$buildDir/$name")
    return tasks.register<Download>("download_${file(name).nameWithoutExtension}") {
        src(url)
        dest(outPath)
        overwrite(false)
    }.get()
}

val cloth = download("https://raw.githubusercontent.com/malcolmriley/unused-textures/master/items/part_textile_cloth.png")

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
    include(modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")!!)
    include(modImplementation("net.fabricmc:fabric-language-kotlin:1.13.4+kotlin.2.2.0")!!)
    include(modImplementation("net.fabricmc:fabric-language-scala:${project.properties["scala_loader_version"]}")!!)

    val minecraft_version = "1.20.1"
    include(implementation(annotationProcessor("io.github.llamalad7:mixinextras-fabric:0.5.0")!!)!!)
    include(modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")!!)
    include(modImplementation("poollovernathan.fabric:mod-tools:1.1.5+1.20.1")!!)
    include(modApi("org.eu.net.pool:common-curses:1.1.5-SNAPSHOT")!!)
    include(api("org.scala-lang:scala3-library_3:3.7.1")!!)
    include(api("org.scala-lang:scala-library:2.13.6")!!)
    //modCompileOnly("at.petra-k.hexcasting:hexcasting-common-$minecraft_version:0.11.2+fork-SNAPSHOT")
    include(modImplementation("at.petra-k.hexcasting:hexcasting-fabric-$minecraft_version:0.11.2+fork-SNAPSHOT")!!)
    include(modImplementation("at.petra-k.paucal:paucal-fabric-$minecraft_version:0.6.0-pre-118")!!)
    include(modImplementation("com.samsthenerd.inline:inline-fabric:$minecraft_version-1.0.1")!!)
    include(implementation("com.github.Chocohead:Fabric-ASM:v2.3")!!)
    include(modCompileOnly("dev.kineticcat.hexportation:hexportation-fabric-1.20.1-fabric-fabric:0.0.3")!!)
    include(modImplementation("io.github.tropheusj:serialization-hooks:0.4.99999")!!)
    include(modImplementation("maven.modrinth:hexcassettes:1.1.4")!!)
    include(modImplementation("maven.modrinth:spasm:0.2.2")!!)
//    modImplementation("maven.modrinth:slate-works:1.0.5")
    include(modCompileOnly("miyucomics.hexical:hexical:main-SNAPSHOT")!!)
    include(modImplementation("ram.talia.moreiotas:moreiotas-fabric-$minecraft_version:0.1.0-6") { exclude("moreiotas") }!!)
    include(modImplementation("ram.talia.hexal:hexal-fabric-1.20.1:0.3.0-3-skyevg-unofficial") { exclude("hexal") }!!)
    include(modImplementation("maven.modrinth:hexcellular:1.0.4")!!)
    include(modImplementation("maven.modrinth:jsonpatcher:1.0.0-beta.4+mc.1.20.1")!!)
    include(implementation("com.github.mattidragon:JsonPatcherLang:v1.0.0-beta.3")!!) // trans maven.modrinth:jsonpatcher
    include(modImplementation("com.github.mattidragon:ConfigToolkit:v1.0.0")!!) // trans maven.modrinth:jsonpatcher
    include(modImplementation("miyucomics.hexpose:hexpose:1.0.0")!!)
//    modImplementation("miyucomics:hexpose:1.0.0")
//    modImplementation(files("hexical-2.0.0.jar"))
    val cardinal_version = "5.2.3"
    include(modApi("dev.onyxstudios.cardinal-components-api:cardinal-components-base:$cardinal_version")!!)
    include(modApi("dev.onyxstudios.cardinal-components-api:cardinal-components-block:$cardinal_version")!!)
    include(modApi("dev.onyxstudios.cardinal-components-api:cardinal-components-entity:$cardinal_version")!!)
    include(modApi("dev.onyxstudios.cardinal-components-api:cardinal-components-item:$cardinal_version")!!)
    include(modApi("dev.onyxstudios.cardinal-components-api:cardinal-components-level:$cardinal_version")!!)
    include(modApi("dev.onyxstudios.cardinal-components-api:cardinal-components-world:$cardinal_version")!!)
    include(modRuntimeOnly("dev.onyxstudios.cardinal-components-api:cardinal-components-api:$cardinal_version")!!)
    include(implementation("net.bytebuddy:byte-buddy:1.17.7")!!)
    include(implementation("net.bytebuddy:byte-buddy-agent:1.17.7")!!)

    modRuntimeOnly("gay.object.hexdebug:hexdebug-fabric:0.5.0+1.20.1-SNAPSHOT")
}

val colors = mapOf(
    "white" to 16383998,
    "orange" to 16351261,
    "magenta" to 13061821,
    "light_blue" to 3847130,
    "yellow" to 16701501,
    "lime" to 8439583,
    "pink" to 15961002,
    "gray" to 4673362,
    "light_gray" to 10329495,
    "cyan" to 1481884,
    "purple" to 8991416,
    "blue" to 3949738,
    "brown" to 8606770,
    "green" to 6192150,
    "red" to 11546150,
    "black" to 1908001,
)

val downloadedBags = colors.mapValues { download("https://raw.githubusercontent.com/malcolmriley/unused-textures/master/items/tool_pouch_${it.key}.png") }

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(project.properties)
    }

    dependsOn(cloth)
    dependsOn(*downloadedBags.values.toTypedArray())
    val itemsRoot = "$destinationDir/assets/hexic/textures/item"
    doLast {
        for ((name, color) in colors) {
            exec {
                commandLine("magick", cloth.dest, "-channel", "red,green,blue", "-fx", "u*#${color.toString(16)}", "$itemsRoot/${name}_mediaweave.png")
            }
            val bag = downloadedBags[name]!!.dest
            exec {
                commandLine("magick", bag, "-write", "$itemsRoot/large_${name}_bundle.png", "-sample", "14x14", "-background", "transparent", "-extent", "16x16-1-2", "$itemsRoot/small_${name}_bundle.png")
            }
        }
        exec {
            commandLine("magick", "wizard:", "$itemsRoot/wizard.png")
        }
        exec {
            commandLine("magick",
                "https://raw.githubusercontent.com/malcolmriley/unused-textures/master/blocks/overlay_rune_0.png",
                "(",
                    "-clone", "0",
                    "-alpha", "extract",
                    "-type", "bilevel",
                    "-define", "connected-components:mean-color=true",
                    "-define", "connected-components:area-threshold=26",
                    "-connected-components", "4",
                ")",
                "-alpha", "off",
                "-compose", "copy_opacity",
                "-composite",
                "$itemsRoot/stringworm.miff"
            )
        }
        for ((name, expr) in mapOf(
            "media" to "u*#74b3f2*2",
            "hex" to "u*#b38ef3*2",
            "action" to "u*#fc77be*2",
            "thing" to "u*#8d6acc*2",
        )) {
            exec {
                commandLine("magick", "$itemsRoot/stringworm.miff", "-channel", "rgb", "-fx", expr, "$itemsRoot/stringworm_$name.png")
            }
        }
        file("$itemsRoot/stringworm.miff").delete()
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

class P(project: Project) {
    val commit_id by lazy {
        val stdout = `java.io`.ByteArrayOutputStream()
        project.exec {
            commandLine("jj", "log", "-r", "@", "--template", "commit_id", "--no-graph")
            standardOutput = stdout;
        }
        stdout.toString()
    }

    val change_id by lazy {
        val stdout = `java.io`.ByteArrayOutputStream()
        project.exec {
            commandLine("jj", "log", "-r", "@", "--template", "change_id", "--no-graph")
            standardOutput = stdout;
        }
        stdout.toString()
    }

    companion object {
        const val contentRoot = "__CONTENT__"
    }
}
val contentRoot = P.contentRoot
val p = P(project)
project.ext.set("p", p)

val release: Boolean = !System.getenv("release").isNullOrEmpty()

open class Hexdoc: Exec() {
    init {
        environment["GITHUB_PAGES_URL"] = "https://hexic.pool.net.eu.org/"
        environment["GITHUB_REPOSITORY"] = "https://codeberg.org/poollovernathan/hexic"
        environment["DEBUG_GITHUBUSERCONTENT"] = P.contentRoot
        environment["GITHUB_SHA"] = (project.ext["p"] as P).commit_id
    }
    @Input
    var hexdocArgs = listOf<String>()
        set(value) {
            field = value
            commandLine = listOf("env", "hexdoc") + hexdocArgs
        }

    @OutputDirectory
    var docsPrefix = project.file(".")
}
val Hexdoc.docsRoot get() = file("$docsPrefix/v/${if (release) "$version/1.1" else "latest/${p.change_id}"}")
fun Hexdoc.cleanPrefix() {
    doFirst {
        docsRoot.deleteRecursively()
    }
}
fun Hexdoc.processOutput() {
    doLast {
        for (f in docsRoot.walk()) {
            if (f.isFile) {
                f.writeText(includeContent(f.readText()))
            }
        }
    }
}
fun includeContent(text: String) = 
    text.replace(Regex("$contentRoot/*([\\w/.-]+?\\.png)")) {
        val path = it.groups[1]!!.value.replace(contentRoot, "").trimStart('/')
        val b64 = `java.util`.Base64.getEncoder().encodeToString(file(path).readBytes())
        println("\t$it\t$path")
        "data:image/png;base64,$b64"
    }

val wheelPath = file("dist/hexdoc_hexic-$version.1.1-py3-none-any.whl")
tasks.register<Hexdoc>("hexdoc") {
    dependsOn("processResources")
    cleanPrefix()
    docsPrefix = file("_site/dst/docs")
    hexdocArgs = listOf("build", "--branch", p.change_id)
    if (release) hexdocArgs += "--release"
    processOutput()
}
val mergeHexdoc by tasks.register<Hexdoc>("mergeHexdoc") {
    dependsOn("hexdoc")
    docsPrefix = file("_site/dst/docs")
    hexdocArgs = listOf("merge")
    if (release) hexdocArgs += "--release"
    processOutput()
}
val wheel by tasks.register<Exec>("wheel") {
    dependsOn("hexdoc")
    commandLine("env", "uv", "build")
    outputs.file(wheelPath)
}

tasks.register<Exec>("publishToPypi") {
    dependsOn("wheel")
    commandLine("env", "uv", "publish")
}
tasks.named("publish") {
    dependsOn("publishToPypi")
}
val processWheel by tasks.register<Zip>("processWheel") {
    dependsOn(wheel)
    from(zipTree(wheelPath))
    eachFile {
        if (!name.endsWith(".png")) {
            filter(::includeContent)
            filter { it.replace(contentRoot, "https://codeberg.org/PoolloverNathan/hexic/raw/commit/${p.commit_id}") }
        }
    }
    destinationDirectory = mergeHexdoc.docsRoot
    archiveFileName = wheelPath.name
}

tasks.register("docs") {
    dependsOn(mergeHexdoc, processWheel)
    doLast {
        println("https://hexic.pool.net.eu.org/${mergeHexdoc.docsRoot.relativeTo(mergeHexdoc.docsPrefix)}/en_us")
    }
}

// configure the maven publication
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
            artifact(file("dist/hexdoc_hexic-$version.1.1-py3-none-any.whl")) {
                builtBy(wheel)
                classifier = "hexdoc-py3-none-any"
                extension = "whl"
            }
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
