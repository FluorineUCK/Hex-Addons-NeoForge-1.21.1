import de.undercouch.gradle.tasks.download.Download
import groovy.json.JsonSlurper
import org.eu.net.pool.mc_plugin.Environment
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import kotlin.io.path.exists
import kotlin.io.path.readText
import groovy.json.JsonOutput
import `java.nio`.file.Files;
import kotlin.io.path.deleteIfExists
import util.P

plugins {
    id("fabric-loom")
    id("scala")
    kotlin("jvm") version "2.2.0"
    id("maven-publish")
    id("de.undercouch.download") version "5.6.0"
    id("org.eu.net.pool.mc-plugin")
}

val p: P by ext
val release: Boolean by ext
val py_version: String by project.properties
val wheelPath = file("dist/hexdoc_hexic-$version.$py_version-py3-none-any.whl")

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

loom.runs {
    register("mixinDebugClient") {
        inherit(loom.runs["client"])
        vmArgs("-Dmixin.debug.export=true", "-Dmixin.debug.export.decompile=true")
    }
}

val modDepends: Configuration by configurations.creating {
    isTransitive = false
    isCanBeResolved = true
}
val modSuggests: Configuration by configurations.creating {
    isTransitive = false
    isCanBeResolved = true
}
val modCompatibility: Configuration by configurations.creating

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:1.13.4+kotlin.2.2.0")

    fun compat(with: String) {
        modSuggests(with)
        modCompileOnly(with)
        modLocalRuntime(with)
    }

    val minecraft_version = "1.20.1"
    implementation(annotationProcessor("io.github.llamalad7:mixinextras-fabric:0.5.0")!!)
    implementation(project(":util", "namedElements"))
    modImplementation("io.github.tropheusj:serialization-hooks:0.4.99999")
    modImplementation("at.petra-k.hexcasting:hexcasting-fabric-$minecraft_version:0.11.3")
    modImplementation("com.samsthenerd.inline:inline-fabric:$minecraft_version-1.0.1")
    include(implementation("com.github.Chocohead:Fabric-ASM:v2.3")!!)
    modCompileOnly("dev.kineticcat.hexportation:hexportation-fabric-1.20.1-fabric-fabric:0.0.3")
    modCompileOnly("carpet:fabric-carpet:1.20-1.+")
    modLocalRuntime("maven.modrinth:lithium:mc1.20.1-0.11.4-fabric")
//    modRuntimeOnly("carpet:fabric-carpet:1.20-1.+")
    compat("gay.object.ioticblocks:ioticblocks-fabric:1.0.2+1.20.1")
    modImplementation("net.beholderface.oneironaut:oneironaut-fabric-1.20.1-fabric-fabric:1.20.1-SNAPSHOT")
    compat("maven.modrinth:hexcassettes:1.1.4")
    modLocalRuntime("maven.modrinth:trinkets:3.7.2")
//    modImplementation("maven.modrinth:slate-works:1.0.5")
    compat("miyucomics.hexical:hexical:2.0.0+a3c47ad9")
    compat("miyucomics.overevaluate:overevaluate:main-SNAPSHOT")
    modImplementation("ram.talia.moreiotas:moreiotas-fabric-$minecraft_version:0.1.1") { exclude(module = "serialization-hooks") }
    modImplementation("ram.talia.hexal:hexal-fabric-1.20.1:0.3.0") { exclude(module = "serialization-hooks") }
    modImplementation("miyucomics.hexcellular:hexcellular:1.1.0")
    modImplementation("maven.modrinth:jsonpatcher:1.0.0-beta.4+mc.1.20.1")
    implementation("com.github.mattidragon:JsonPatcherLang:v1.0.0-beta.3") // trans maven.modrinth:jsonpatcher
    modImplementation("com.github.mattidragon:ConfigToolkit:v1.0.0") // trans maven.modrinth:jsonpatcher
    modImplementation("miyucomics.hexpose:hexpose:1.0.0")
    include(modApi("xyz.nucleoid:fantasy:0.4.11+1.20-rc1")!!)
    modImplementation("dev.emi:trinkets:3.7.2")
//    modImplementation("miyucomics:hexpose:1.0.0")
//    modImplementation(files("hexical-2.0.0.jar"))
    include(implementation("net.bytebuddy:byte-buddy:1.17.7")!!)
    include(implementation("net.bytebuddy:byte-buddy-agent:1.17.7")!!)
    modImplementation("dev.onyxstudios.cardinal-components-api:cardinal-components-item:5.2.3")
    modImplementation("dev.onyxstudios.cardinal-components-api:cardinal-components-entity:5.2.3")
    modImplementation("dev.onyxstudios.cardinal-components-api:cardinal-components-level:5.2.3")
    modRuntimeOnly("dev.onyxstudios.cardinal-components-api:cardinal-components-api:5.2.3")
    modRuntimeOnly("com.unascribed:lib39-core:[2.0.0,)!!2.0.27+1.20.1")
    modRuntimeOnly("com.unascribed:lib39-avant:[2.0.0,3.0.0)!!2.0.27+1.20.1")
    modRuntimeOnly("com.unascribed:lib39-phantom:[2.0.0,3.0.0)!!2.0.27+1.20.1")
    modLocalRuntime("gay.object.hexdebug:hexdebug-fabric:0.5.0+1.20.1-SNAPSHOT")
    modLocalRuntime("maven.modrinth:hexcessible:0.2.0")
    modLocalRuntime("maven.modrinth:complex-hex:0.1.3-beta")
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

    preprocessor {
        fabricMod("hexic", version as String) {
            name = "Hexic"
            description = "Miscellaneous neat features and QoL patterns for Hex Casting."
            license = "LGPL-3.0"
            icon = "assets/hexic/icon.png"

            author("PoolloverNathan") {
                put("discord", "https://discord.com/users/402104961812660226")
            }

            depends("mixinextras", "*")
            depends("mm", "^2.3")
            depends("moreiotas", ">=0.1.1")
            depends("hexal", ">=0.3.0")
            depends("hexcellular", "^1.1.0")
            depends("jsonpatcher", "^1.0.0-beta.4+mc.1.20.1")
            depends("hexpose", ">=1.0.0 <3.0.0")
            depends("trinkets", "^3.7.2")
            depends("phlib", ">=0.1.2 <0.2.0")
            conflicts("valkyrienskies", "*") // need to figure out how to create dimensions without causing a crash

            entrypoint("org.eu.net.pool.hexic.main\$package::init")
            entrypoint("org.eu.net.pool.hexic.client\$package::init", Environment.Client)
            entrypoint("fabric-datagen", "org.eu.net.pool.hexic.client\$package::datagen")
            entrypoint("mm:early_risers", "org.eu.net.pool.hexic.early_riser\$package::warCrimes")
            entrypoint("cardinal-components", "org.eu.net.pool.hexic.ComponentInit")
            mixins("hexic.mixins.json")
            mixins("hexic.client.mixins.json", Environment.Client)
            cardinalComponents("player_wisp", "server_info", "excursion", "murmur", "reveal", "cat")
        }
    }

    dependsOn(cloth)
    dependsOn(*downloadedBags.values.toTypedArray())
    val itemsRoot = destinationDir.resolve("assets/hexic/textures/item")
    val jxlOpts = arrayOf("-quality", "100", "-define", "jxl:effort=11", "-define", "jxl:lossless=true", "-define", "jxl:modular=true")
    doLast {
        for ((name, color) in colors) {
            exec {
                commandLine("env", "magick", cloth.dest, "-channel", "red,green,blue", "-fx", "u*#${color.toString(16)}", *jxlOpts, "jxl:${itemsRoot.resolve("${name}_mediaweave.png")}")
            }
            val bag = downloadedBags[name]!!.dest
            exec {
                commandLine("env", "magick", bag, *jxlOpts, "-write", "jxl:${itemsRoot.resolve("large_${name}_bundle.png")}", "-sample", "14x14", "-background", "transparent", "-extent", "16x16-1-2", "jxl:${itemsRoot.resolve("small_${name}_bundle.png")}")
            }
        }
        exec {
            commandLine("env", "magick", "wizard:", *jxlOpts, "jxl:${itemsRoot.resolve("wizard.png")}")
        }
        exec {
            commandLine("env", "magick", "null:", *jxlOpts, "jxl:${itemsRoot.resolve("no.jxl")}")
        }
        exec {
            commandLine("env", "magick",
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
                "-fx", "u*2",
                itemsRoot.resolve("stringworm.miff")
            )
        }
        for ((name, expr) in mapOf(
            "media" to "u*#74b3f2",
            "hex" to "u*#b38ef3",
            "action" to "u*#fc77be",
            "thing" to "u*#8d6acc",
            "pure" to "u",
        )) {
            exec {
                commandLine("env", "magick", itemsRoot.resolve("stringworm.miff"), "-channel", "rgb", "-fx", expr, *jxlOpts, "jxl:$itemsRoot/stringworm_$name.png")
            }
        }
        // people will hate this
        for (i in 0..31) {
            exec {
                commandLine("env", "magick", itemsRoot.resolve("stringworm.miff"), "-fx", "i+j == $i ? u : Transparent", *jxlOpts, "jxl:${itemsRoot.resolve("stringworm_tinted_$i.png")}")
            }
        }
        file("$itemsRoot/../block").mkdir()
        exec {
            commandLine("env", "magick", "xc:#ffffff[16x16]", *jxlOpts, "jxl:${itemsRoot.resolveSibling("block/border.png")}")
        }
        file("$itemsRoot/stringworm.miff").delete()
        exec {
            commandLine("env", "magick", "https://www.masterbuilt.com/cdn/shop/articles/162_20-_20Voodoo_20Baked_20Beans.jpg", "-sample", "256x256", *jxlOpts, "jxl:${itemsRoot.resolve("beans.png")}")
        }
    }

    eachFile {
        if (name.endsWith(".ase")) {
            exec {
                if (name.endsWith("_*.ase")) {
                    commandLine("env", "aseprite", "-b", "--split-layers", file, "--save-as", "$destinationDir/${path.replace("_*.ase", "")}_{layer}.png")
                } else {
                    commandLine("env", "aseprite", "-b", file, "--save-as", "$destinationDir/${path.replace(".ase", "")}.png")
                }
            }
            exclude()
        }
        if (name.endsWith(".ase.split-layers")) exclude()
    }
}

allprojects {
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
        options.release.set(17)
    }

    tasks.withType<ScalaCompile>().configureEach {
        scalaCompileOptions.additionalParameters.addAll(listOf("-explain-cyclic", "-Ydebug-cyclic", "-experimental", "-feature", "-Ycc-debug"))
    }
}

val wheelFiles by lazy {
    fileTree("_site/dst/docs/v") { include("**/*.whl") }.files
}

val contentRoot = P.contentRoot

val wheelFileHashes by lazy {
    wheelFiles.map { it.name to p.getStdout { commandLine("git", "hash-object", "-w", it) } }
}
val wheelTree by lazy {
    p.getStdout {
        commandLine("git", "mktree")
        standardInput = `java.io`.ByteArrayInputStream(wheelFileHashes.joinToString("") { (k, v) -> "100644 blob $v\t$k\n" }.toByteArray())
    }
}
val wheelCommit by lazy {
    p.getStdout {
        commandLine("git", "commit-tree", wheelTree)
        standardInput = `java.io`.ByteArrayInputStream(byteArrayOf())
    }
}
tasks.register<Exec>("pushWheels") {
    commandLine("git", "push", "origin", "+$wheelCommit:refs/heads/wheels")
}

tasks.named("clean") {
    doLast {
        file("src/main/generated").deleteRecursively()
        file("dist").deleteRecursively()
    }
}

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

    @Internal
    var docsPrefix = project.file(".")
}

tasks.register<GradleBuild>("processWithDatagen") {
    dependsOn("runDatagen")
    tasks = listOf("processResources")
}

tasks.named("runDatagen") {
    doLast {

    }
}

val Hexdoc.docsRoot get() = file("$docsPrefix/v/${if (release) "$version/$py_version" else "latest/${p.change_id}"}")
fun Hexdoc.cleanPrefix() {
    doFirst {
        docsPrefix.deleteRecursively()
    }
}
fun Hexdoc.processOutput() {
    doLast {
        for (f in docsRoot.walk()) {
            if (f.isFile && !f.name.endsWith(".png")) {
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

val syncPip by tasks.register<Exec>("syncPip") {
    doFirst {
        file("doc/src/hexdoc_hexic/__version__.py").writeText("""
            PY_VERSION = "$py_version"
        """.trimIndent())
    }
    commandLine("env", "pip", "install", "-e", ".")
}
val hexdoc by tasks.register<Hexdoc>("hexdoc") {
    dependsOn(syncPip, "processWithDatagen")
    docsPrefix = file("_site/src/docs")
    cleanPrefix()
    hexdocArgs = listOf("build", "--branch", p.change_id)
    if (release) hexdocArgs += "--release"
    processOutput()
}
val mergeHexdoc by tasks.register<Hexdoc>("mergeHexdoc") {
    dependsOn(hexdoc)
    docsPrefix = file("_site/dst/docs")
    hexdocArgs = listOf("merge")
    if (release) hexdocArgs += "--release"
    processOutput()
}
val wheel by tasks.register<Exec>("wheel") {
    dependsOn(hexdoc)
    doFirst { file("dist").deleteRecursively() }
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
    if (release) {
        archiveFileName = wheelPath.name
    } else {
        archiveFileName = "hexdoc_hexic-${(version as String).split('+')[0]}.$py_version-${p.commit_id}-py3-none-any.whl"
    }
    doLast {
        println(archivePath)
    }
}

tasks.withType<Jar> {
    dependsOn("runDatagen")
}

tasks.register("docs") {
    dependsOn(mergeHexdoc, processWheel)
    doLast {
        println("https://hexic.pool.net.eu.org/${mergeHexdoc.docsRoot.relativeTo(mergeHexdoc.docsPrefix)}/en_us")
    }
}

// configure the maven publication
allprojects {
    plugins.withType<MavenPublishPlugin> {
        publishing {
            publications {
                create<MavenPublication>("mavenJava") {
                    artifactId = project.property("modid") as String
                    from(components["java"])
                }
            }

            // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
            repositories {
                maven("https://pool.net.eu.org/") {
                    name = "poolMaven"
                    credentials(PasswordCredentials::class.java)
                }
            }
        }
    }
}
