import de.undercouch.gradle.tasks.download.Download
import groovy.json.JsonSlurper
import org.eu.net.pool.mc_plugin.Environment
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import kotlin.io.path.exists
import kotlin.io.path.readText

plugins {
    id("fabric-loom") version "1.13-SNAPSHOT"
    id("scala")
    kotlin("jvm") version "2.2.0"
    id("maven-publish")
    id("de.undercouch.download") version "5.6.0"
    id("org.eu.net.pool.mc-plugin") version "0.1.1"
}

try {
    tasks.named("downloadRenderDoc") {
        setProperty("output", file("$buildDir/renderdoc_1.37.tar.gz"))
    }

    tasks.named("extractRenderDoc") {
        enabled = false
    }

    val erd by tasks.register<Sync>("myExtractRenderDoc") {
        dependsOn("downloadRenderDoc")
        from(tarTree(resources.gzip("$buildDir/renderdoc_1.37.tar.gz")))
        into("$buildDir/renderdoc")
    }

    tasks.named("runClientRenderDoc") {
        dependsOn(erd)
    }
} catch (ignored: UnknownTaskException) {}

loom.runs["client"].programArgs += listOf("--username", "Player", "--uuid", "bd346dd5-ac1c-427d-87e8-73bdd4bf3e13")

//tasks.withType<RenderDocR>()

val release: Boolean = !System.getenv("release").isNullOrEmpty()
val p = P(project)
project.ext.set("p", p)
version = project.property("mod_version") as String
val py_version: String by project.properties
val wheelPath = file("dist/hexdoc_hexic-$version.$py_version-py3-none-any.whl")
if (!release) version = "$version+${p.commit_id.take(7)}"
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

    mixin.useLegacyMixinAp = false
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
    fun exactRepo(url: String, vararg groups: String, recursive: Boolean = true) {
        exclusiveContent {
            forRepository {
                maven(url)
            }
            filter {
                for (group in groups) {
                    if (recursive) {
                        includeGroupAndSubgroups(group)
                    } else {
                        includeGroup(group)
                    }
                }
            }
        }
    }

    mavenCentral()
    exactRepo("https://api.modrinth.com/maven",
        "maven.modrinth")
    exactRepo("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/",
        "com.eliotlash.mclib",
        "software.bernie.geckolib")
    exactRepo("https://jitpack.io/",
        "com.github.Chocohead",
        "com.github.LlamaLad7",
        "com.github.Virtuoel",
        "com.github.mattidragon")
    exactRepo("https://maven.blamejared.com/",
        "at.petra-k",
        "com.samsthenerd.inline",
        "gay.object",
        "miyucomics.hexpose",
        "net.darkhax.openloader",
        "vazkii.patchouli")
    exactRepo("https://maven.hexxy.media/",
        "io.github.tropheusj",
        "ram.talia")
    exactRepo("https://maven.jamieswhiteshirt.com/libs-release/",
        "com.jamieswhiteshirt")
    exactRepo("https://maven.kosmx.dev/",
        "dev.kosmx")
    exactRepo("https://maven.ladysnake.org/releases/",
        "dev.onyxstudios")
    exactRepo("https://maven.pool.net.eu.org/",
        "dev.kineticcat.hexportation",
        "miyucomics.hexcellular",
        "miyucomics.hexical",
        "org.eu.net.pool",
        "poollovernathan")
    exactRepo("https://maven.shedaniel.me/",
        "dev.architectury",
        "me.shedaniel")
    exactRepo("https://maven.terraformersmc.com/",
        "com.terraformersmc",
        "dev.emi")
    exactRepo("https://repo.sleeping.town/",
        "com.unascribed")
    exactRepo("https://masa.dy.fi/maven/",
        "carpet")
    exactRepo("https://maven.nucleoid.xyz/",
        "xyz.nucleoid")
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
    modDepends(implementation(annotationProcessor("io.github.llamalad7:mixinextras-fabric:0.5.0")!!)!!)
    modDepends(modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")!!)
    modDepends(include(modImplementation("poollovernathan.fabric:mod-tools:1.1.5+1.20.1")!!)!!)
    include(api("org.scala-lang:scala3-library_3:3.7.1")!!)
    include(api("org.scala-lang:scala-library:2.13.6")!!)
    modDepends(modImplementation("at.petra-k.hexcasting:hexcasting-fabric-$minecraft_version:0.11.2-pre-751")!!)
    modImplementation("at.petra-k.paucal:paucal-fabric-$minecraft_version:0.6.0-pre-118")
    modImplementation("com.samsthenerd.inline:inline-fabric:$minecraft_version-1.0.1")
    modDepends(include(implementation("com.github.Chocohead:Fabric-ASM:v2.3")!!)!!)
    modCompileOnly("dev.kineticcat.hexportation:hexportation-fabric-1.20.1-fabric-fabric:0.0.3")
    modCompileOnly("carpet:fabric-carpet:1.20-1.+")
//    modRuntimeOnly("carpet:fabric-carpet:1.20-1.+")
    compat("gay.object.ioticblocks:ioticblocks-fabric:1.0.2+1.20.1")
    modImplementation("io.github.tropheusj:serialization-hooks:0.4.99999")
    modImplementation(files("./libs/oneironaut-fabric-1.20.1-0.5.0-476cee2.jar"))
    compat("maven.modrinth:hexcassettes:1.1.4")
    modDepends(modImplementation("maven.modrinth:spasm:0.2.2")!!)
//    modImplementation("maven.modrinth:slate-works:1.0.5")
    compat("miyucomics.hexical:hexical:main-SNAPSHOT")
    modDepends(modImplementation("ram.talia.moreiotas:moreiotas-fabric-$minecraft_version:0.1.1") { exclude(module = "serialization-hooks") })
    modDepends(modImplementation("ram.talia.hexal:hexal-fabric-1.20.1:0.3.0") { exclude(module = "serialization-hooks") })
    modDepends(modImplementation("miyucomics.hexcellular:hexcellular:1.1.0")!!)
    modDepends(modImplementation("maven.modrinth:jsonpatcher:1.0.0-beta.4+mc.1.20.1")!!)
    implementation("com.github.mattidragon:JsonPatcherLang:v1.0.0-beta.3") // trans maven.modrinth:jsonpatcher
    modImplementation("com.github.mattidragon:ConfigToolkit:v1.0.0") // trans maven.modrinth:jsonpatcher
    modDepends(modImplementation("miyucomics.hexpose:hexpose:1.0.0")!!)
    include(modApi("xyz.nucleoid:fantasy:0.4.11+1.20-rc1")!!)
//    modImplementation("miyucomics:hexpose:1.0.0")
//    modImplementation(files("hexical-2.0.0.jar"))
    val cardinal_version = "5.2.3"
    modApi("dev.onyxstudios.cardinal-components-api:cardinal-components-base:$cardinal_version")
    modDepends(modApi("dev.onyxstudios.cardinal-components-api:cardinal-components-block:$cardinal_version")!!)
    modDepends(modApi("dev.onyxstudios.cardinal-components-api:cardinal-components-entity:$cardinal_version")!!)
    modDepends(modApi("dev.onyxstudios.cardinal-components-api:cardinal-components-item:$cardinal_version")!!)
    modDepends(modApi("dev.onyxstudios.cardinal-components-api:cardinal-components-level:$cardinal_version")!!)
    modDepends(modApi("dev.onyxstudios.cardinal-components-api:cardinal-components-world:$cardinal_version")!!)
    modRuntimeOnly("dev.onyxstudios.cardinal-components-api:cardinal-components-api:$cardinal_version")
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

    preprocessor {
        fabricMod("hexic", version as String) {
            name = "Hexic"
            description = "Miscellaneous neat features and QoL patterns for Hex Casting."
            license = "LGPL-3.0"
            icon = "assets/hexic/icon.png"

            author("PoolloverNathan") {
                put("discord", "https://discord.com/users/402104961812660226")
            }

            for (p in modDepends.resolve()) {
                val root =
                    if (p.isDirectory) p.toPath()
                    else if (p.name.endsWith(".jar")) `java.nio.file`.FileSystems.newFileSystem(p.toPath()).rootDirectories.single()
                    else continue
                val fmj = root.resolve("fabric.mod.json")
                if (fmj.exists()) {
                    val json = JsonSlurper().parse(fmj) as Map<String, Any>
                    depends(json["id"].toString(), ">=${json["version"]}")
                } else {
                    println("Attempt to add dependency '$p' to modDepends, which is not a Fabric mod.")
                }
            }

            entrypoint("org.eu.net.pool.hexic.Hexic\$package::init")
            entrypoint("org.eu.net.pool.hexic.client.HexicClient\$package::init", Environment.Client)
            entrypoint("fabric-datagen", "org.eu.net.pool.hexic.client.HexicClient\$package::datagen")
            entrypoint("mm:early_risers", "org.eu.net.pool.hexic.EarlyRiser\$package::warCrimes")
            entrypoint("cardinal-components", "org.eu.net.pool.hexic.ComponentInit")
            mixins("hexic.mixins.json")
            mixins("hexic.client.mixins.json", Environment.Client)
            custom {
                array("cardinal-components") {
                    put("hexic:player_wisp")
                }
            }
        }
    }

    dependsOn(cloth)
    dependsOn(*downloadedBags.values.toTypedArray())
    val itemsRoot = "$destinationDir/assets/hexic/textures/item"
    doLast {
        for ((name, color) in colors) {
            exec {
                commandLine("env", "magick", cloth.dest, "-channel", "red,green,blue", "-fx", "u*#${color.toString(16)}", "$itemsRoot/${name}_mediaweave.png")
            }
            val bag = downloadedBags[name]!!.dest
            exec {
                commandLine("env", "magick", bag, "-write", "$itemsRoot/large_${name}_bundle.png", "-sample", "14x14", "-background", "transparent", "-extent", "16x16-1-2", "$itemsRoot/small_${name}_bundle.png")
            }
        }
        exec {
            commandLine("env", "magick", "wizard:", "$itemsRoot/wizard.png")
        }
        exec {
            commandLine("env", "magick", "null:", "$itemsRoot/no.png")
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
                "$itemsRoot/stringworm.miff"
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
                commandLine("env", "magick", "$itemsRoot/stringworm.miff", "-channel", "rgb", "-fx", expr, "$itemsRoot/stringworm_$name.png")
            }
        }
        // people will hate this
        for (i in 0..31) {
            file("$itemsRoot/stringworm_tinted_$i.png").outputStream().use {
                exec {
                    commandLine("env", "magick", "$itemsRoot/stringworm.miff", "-fx", "i+j == $i ? u : Transparent", "png:-")
                    standardOutput = it
                }
            }
        }
        file("$itemsRoot/../block").mkdir()
        exec {
            commandLine("env", "magick", "xc:#ffffff[16x16]", "$itemsRoot/../block/border.png")
        }
        //file("$itemsRoot/stringworm.miff").delete()
        exec {
            commandLine("env", "magick", "https://www.masterbuilt.com/cdn/shop/articles/162_20-_20Voodoo_20Baked_20Beans.jpg", "-sample", "256x256", "$itemsRoot/beans.png")
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

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName}" }
    }
    duplicatesStrategy = DuplicatesStrategy.WARN
}

val wheelFiles by lazy {
    fileTree("_site/dst/docs/v") { include("**/*.whl") }.files
}

class P(project: Project) {
    fun getStdout(action: ExecSpec.() -> Unit) = 
        `java.io`.ByteArrayOutputStream().also {
            project.exec {
                action()
                standardOutput = it
            }
        }.toString().trim()

    val commit_id by lazy {
        getStdout {
            commandLine("env", "jj", "log", "-r", "@", "--template", "commit_id", "--no-graph")
        }
    }

    val change_id by lazy {
        getStdout {
            commandLine("env", "jj", "log", "-r", "@", "--template", "change_id", "--no-graph")
        }
    }

    companion object {
        const val contentRoot = "__CONTENT__"
    }
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
    dependsOn(syncPip, tasks.processResources, "runDatagen")
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
