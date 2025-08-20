import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import de.undercouch.gradle.tasks.download.Download
import net.fabricmc.loom.task.prod.ClientProductionRunTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import kotlin.text.replace

plugins {
    id("fabric-loom") version "1.10-SNAPSHOT"
    id("scala")
    kotlin("jvm") version "2.2.0"
    id("maven-publish")
    id("idea")
    id("de.undercouch.download") version "5.6.0"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String
val minecraft_version: String by project.properties
val cca_version by lazy { project.property("cca_version") as String }

val serialVersion = minecraft_version.let {
    val (maj, min, pat) = it.toString().split('.')
    min.toInt() * 100 + pat.toInt()
}

project.buildDir = file("build$serialVersion")

fun <T> List<T>.uncons() = first() to drop(1)
run {
    val (headers, versions) = file("versions.csv").readText().lines().map { it.split(',').uncons() }.uncons()
    val row = versions.toMap()[project.properties["minecraft_version"].toString()]
    val map = headers.second.zip(row!!).filter { it.second != "" }.toMap()
    map.forEach(project.ext::set)
}

val assetsArchive by tasks.register<Download>("assetsArchive") {
    src("https://github.com/InventivetalentDev/minecraft-assets/archive/refs/heads/$minecraft_version.tar.gz")
    dest("$buildDir/assets.tar.gz")
    overwrite(true)
    onlyIf { !dest.exists() }
}
val soundsDir by tasks.register<Sync>("assets") {
    dependsOn(assetsArchive)
    inputs.file(assetsArchive.dest)
    val interest = "minecraft-assets-$minecraft_version/assets/minecraft/sounds"
    from(tarTree(assetsArchive.dest)) {
        exclude("**/_list.json")
    }
    val outDir = file("$buildDir/mc-assets")
    into(outDir)
    outputs.dir(outDir)
    // FIXME: hack
    doLast {
        outDir.listFiles().forEach {
            it.listFiles().forEach {
                it.renameTo(outDir.resolve(it.name))
            }
        }
    }
}
val synthSounds by tasks.register("synthSounds") {
    dependsOn(soundsDir)
    val inDir = "$buildDir/mcSounds"
    val outDir = "$buildDir/generatedSounds"
    inputs.dir(inDir)
    outputs.dir(outDir)
    doLast {
        file(outDir).mkdirs()
        exec {
            // ffmpeg -i shears_trim.ogg -i book_page_turn.ogg \
            //  -filter_complex "[0:a]volume=1.0[a0]; \
            //                   [1:a]volume=0.6,asetrate=48000*1.2,aresample=48000[a1]; \
            //                   [a0][a1]amix=inputs=2:duration=first" \
            //  mica_peel.ogg
            for (n in 1..4) {
                commandLine("ffmpeg", "-i", "$inDir/block/amethyst/place$n.ogg")
            }
        }
    }
}

//tasks.register<Download>("fetchGo") {
//    src("https://go.dev/dl/go1.22.6.${
//        org.gradle.internal.os.OperatingSystem.current().let {
//            when {
//                it.isWindows -> "windows"
//                it.isMacOsX -> "darwin"
//                it.isLinux -> "linux"
//            }
//        }
//    }")
//}
//
//val fetchM4 = tasks.register<Download>("fetchM4") {
//    src("https://downloads.sourceforge.net/gnuwin32/m4-1.4.14-1-bin.zip")
//    dest("$buildDir/m4.zip")
//    overwrite(true)
//}
//tasks.register<Sync>("unpackM4") {
//    from(zipTree(fetchM4.get().outputFiles))
//    into("$buildDir/m4/")
//}

base {
    archivesName.set(project.property("archives_base_name") as String)
}

sourceSets {
    main {
        scala {
            srcDirs += file("src/main/java")
        }
        java {
            srcDirs.clear()
        }
    }
}

val targetJavaVersion = 21
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()
}

scala {
    scalaVersion = "3.7.2"
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
    maven { url = uri("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/") }
    maven { url = uri("https://dl.cloudsmith.io/public/libp2p/jvm-libp2p/maven/") }
    maven { url = uri("https://artifacts.consensys.net/public/maven/maven/") }
    maven { url = uri("https://jitpack.io/") }
    //maven { url = uri("https://maven-pool-net-eu-org.ipns.dweb.link/") }
    maven { url = uri("https://maven.quiltmc.org/repository/release/") }
    maven { url = uri("https://maven.terraformersmc.com/") }
    maven { url = uri("https://maven.shedaniel.me/") }
    maven { url = uri("https://maven.blamejared.com/") }
    maven { url = uri("https://masa.dy.fi/maven/") }
    //maven { url = uri("https://maven.jamieswhiteshirt.com/libs-release/") }
    maven { url = uri("https://maven.ladysnake.org/releases/") }
    maven { url = uri("https://maven.gegy.dev/releases") }
    maven { url = uri("https://maven.skye.vg/") }
    maven { url = uri("https://maven.krysztal.dev/releases") }
    maven { url = uri("https://repo.sleeping.town/") }
    exclusiveContent {
        forRepository {
            maven { url = uri("https://api.modrinth.com/maven/") }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
}

val mergedDeps by configurations.creating

tasks.register<ClientProductionRunTask>("prodClient")

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:$minecraft_version")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")

    include(modImplementation("dev.krysztal:krysztal-language-scala:3.3.0+scala.3.7.1")!!)
    include(modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}+$minecraft_version")!!)
    include(modApi("dev.onyxstudios.cardinal-components-api:cardinal-components-base:$cca_version")!!)
    include(modApi("dev.onyxstudios.cardinal-components-api:cardinal-components-world:$cca_version")!!)
    include("maven.modrinth:cardinal-components-api:$cca_version")
    modImplementation("folk.sisby:crunchy-crunchy-advancements:1.7.1+1.21")
    include(modImplementation("com.github.Chocohead:Fabric-ASM:v2.3")!!)
    modImplementation("net.modfest:fireblanket:0.8.3+toybox-1.21.7") { exclude("com.github.bawnorton.mixinsquared") }
    include(modImplementation("com.github.Bawnorton.MixinSquared:mixinsquared-common:0.3.3")!!)
    include(modImplementation("com.github.Bawnorton.MixinSquared:mixinsquared-fabric:0.3.3")!!)
    modImplementation("com.github.afamiliarquiet:be-a-doll:1.0.0")
//    include(modImplementation("maven.modrinth:familiar-magic:1.1.4")!!)
    //include(modImplementation("io.github.0x3c50.renderer:renderer-fabric:2.1.2")!!)
    //mergedDeps("org.scala-lang:scala-library:2.12.21-M2")
    //include("org.scala-lang:scala-library:2.12.21-M2")
    //mergedDeps(provider { "org.scala-lang:scala3-library_3:${scala.scalaVersion.get()}" })
    //include(provider { "org.scala-lang:scala3-library_3:${scala.scalaVersion.get()}" })
    //include(modApi("dev.onyxstudios.cardinal-components-api:cardinal-components-item:$cca_version")!!)
}

object Statics {
    val File.wslPath
        get() = path.replace('\\', '/').replace(Regex("^([A-Z]):")) { g -> "/mnt/${g.groupValues[1].lowercase()}" }
}

@DslMarker
annotation class JsonDsl {
    sealed interface Element
    @JsonDsl
    class Object(val element: JsonObject = JsonObject()): Element {
        fun put(key: String, value: String) {
            element.addProperty(key, value)
        }
        fun put(key: String, value: Number) {
            element.addProperty(key, value)
        }
        fun put(key: String, value: Boolean) {
            element.addProperty(key, value)
        }
        fun array(key: String, action: JsonDsl.Array.() -> Unit) {
            element.add(key, JsonArray().also { Array(it).action() })
        }
        fun map(key: String, action: Object.() -> Unit) {
            element.add(key, JsonObject().also { Object(it).action() })
        }

        override fun toString(): String = Gson().toJson(element)
    }
    @JsonDsl
    class Array(val element: JsonArray = JsonArray()): Element {
        fun put(value: String) {
            element.add(value)
        }
        fun put(value: Number) {
            element.add(value)
        }
        fun put(value: Boolean) {
            element.add(value)
        }
        fun array(action: JsonDsl.Array.() -> Unit) {
            element.add(JsonArray().also { Array(it).action() })
        }
        fun map(action: Object.() -> Unit) {
            element.add(JsonObject().also { Object(it).action() })
        }

        override fun toString(): String = Gson().toJson(element)
    }
}

loom {
    runs["client"].apply {
        vmArg("-XX:+FlightRecorder")
        programArgs("--username", "PoolloverNathan")
    }
}

tasks.named<Jar>("jar") {
    from(mergedDeps.resolve())
}

tasks.withType<ProcessResources> {
    val props = mapOf(
        "version" to project.version,
        "minecraft_version" to minecraft_version,
        "loader_version" to project.properties["loader_version"],
        "scala_loader_version" to project.properties["scala_loader_version"],
    )
    inputs.property("keys", props)
    filteringCharset = "UTF-8"
}

tasks.processResources {
    from(resources.text.fromString(
        JsonDsl.Object().apply {
            put("schemaVersion", 1)
            put("id", "mica")
            put("version", version.toString())
            put("name", "Mica")
            put("description",
                file("README.md").readText().split("<!-- summary -->")[1].lineSequence().first().replace("\\", "\\\\")
                    .replace("\"", "\\\"").trim()
            )
            array("authors") {
                map {
                    put("name", "PoolloverNathan")
                    map("contact") {
                        put("discord", "https://discord.com/users/402104961812660226")
                        put("github", "https://github.com/PoolloverNathan")
                        put("codeberg", "https://github.com/PoolloverNathan")
                    }
                }
            }
            array("contributors") {
                map {
                    put("name", "dinosore_rs")
                    put("role", "textures")
                    map("contact") {
                        put("discord", "https://discord.com/users/219925949309779970")
                        put("github", "https://github.com/dinosore-rs")
                    }
                }
                map {
                    put("name", "afamiliarquiet")
                    put("role", "stole your fops code") // she stole it first
                    map("contact") {
                        put("discord", "https://discord.com/users/813502272355958844")
                        put("github", "https://github.com/afamiliarquiet")
                    }
                }
            }
            map("contact") {
                put("issues", "https://codeberg.org/poollovernathan/mica/issues")
                put("homepage", "https://codeberg.org/poollovernathan/mica")
                put("sources", "git+https://codeberg.org/poollovernathan/mica")
            }
            put("license", "GPL-3.0")
            put("icon", "assets.mica/icon.png")
            put("environment", "*")
            map("entrypoints") {
                array("fabric-datagen") {
                    put("org.net.eu.pool.mica.client.MicaClient\$package::datagen")
                }
                array("client") {
                    put("org.net.eu.pool.mica.client.MicaClient\$package::init")
                }
                array("main") {
                    put("org.net.eu.pool.mica.Mica\$package::init")
                }
                array("cardinal-components") {
                    put("org.net.eu.pool.mica.ComponentInitializer")
                }
            }
            array("mixins") {
                put("mica.mixins.json")
                map {
                    put("config", "mica.client.mixins.json")
                    put("environment", "client")
                }
            }
            map("depends") {
                put("fabricloader", ">=${project.properties["loader_version"]}")
                put("krysztal-language-scala", "3.3.0+scala.3.7.1")
                put("fabric", "*")
                put("minecraft", minecraft_version)
//                put("cardinal-components-item", ">=$cca_version")
                put("cardinal-components-world", ">=$cca_version")
            }
            map("custom") {
                array("cardinal-components") {
                    for (i in 0..767) {
                        put("mica:runes$i")
                    }
                }
            }
        }.toString()
    )) {
        rename { "fabric.mod.json" }
    }

    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    exclude("**/.cache/**")
}

tasks.register<ScalaDoc>("scaladocAll") {
    dependsOn("classes")
    dependsOn("clientClasses")
    source(files("$buildDir/generated*Scala/**"))
    destinationDir = file("$buildDir/docs/scaladoc")
}

tasks.named("ideaSyncTask") {
    dependsOn("runDatagen")
}

abstract class FrozenFile: DefaultTask() {
    @get:InputFiles val macroFiles: ListProperty<RegularFile> = project.objects.listProperty()
    @get:OutputFile val frozenFile: Property<RegularFile> = project.objects.fileProperty()
    @get:Input val globals: MapProperty<String, String> = project.objects.mapProperty()
    init {
        inputs.files(macroFiles)
        inputs.property("globals", globals)
        outputs.file(frozenFile)
    }
    @TaskAction
    fun run() {
        project.exec {
            Statics.run {
                val args = mutableListOf("m4", "-F", frozenFile.get().asFile.wslPath)
                globals.get().forEach { k, v ->
                    args.add("-D$k=$v")
                }
                macroFiles.get().forEach {
                    args.add(it.asFile.wslPath)
                }
                commandLine = args
            }
        }
    }
}

private val makeFrozen = {
    open class Task @Inject constructor() : FrozenFile() {
        @Input
        @Option(description = "Run the build in seed mode. This compiles only the portion of source needed to bootstrap actual compilation (e.g. macros).")
        var seed: Boolean = false
    }
    tasks.register<Task>("freezeMacros", Task::class.java) {
        macroFiles.add(project.layout.projectDirectory.file("macros.m4"))
        frozenFile = project.layout.buildDirectory.file("macros.m4f")
        globals.put("minecraft_version", serialVersion.toString())
        globals.put("SEED", provider { if (seed) "1" else "0" })
    }
}()

sourceSets.all {
    fun processTask(lang: String, body: Pair<String, Task>.(Provider<Directory>) -> Unit) {
        val inDir = project.layout.projectDirectory.dir("src/${this@all.name}/$lang")
        val outDir = project.layout.buildDirectory.dir(getTaskName("generated", lang))
        val frozen = project.layout.buildDirectory.file("macros.m4f")
        val task by tasks.register(getTaskName("process", lang)) {
            inputs.dir(inDir).optional()
            inputs.file(frozen)
            dependsOn(makeFrozen)
            outputs.dir(outDir)
			onlyIf { file(inDir).exists() }
            doLast {
                fileTree(inDir).files.forEach {
                    file("${outDir.get()}/${it.relativeTo(inDir.asFile)}").parentFile.mkdirs()
                    Statics.run {
                        exec {
                            commandLine("bash", "-c", "m4 -R ${frozen.get().asFile.wslPath} ${it.wslPath}")
                            standardOutput = file("${outDir.get()}/${it.relativeTo(inDir.asFile)}").outputStream()
                        }
                    }
                }
            }
        }
        idea {
            module {
                generatedSourceDirs.add(outDir.get().asFile)
            }
        }
        (getCompileTaskName(lang) to task).body(outDir)
    }
    processTask("java") {
        tasks.named<JavaCompile>(first) {
            inputs.property("minecraft_version", serialVersion)
            inputs.property("seed", project.properties["seed"])
            dependsOn(second)
            doFirst {
                setSource(it)
            }
        }
    }
    processTask("kotlin") {
        tasks.named<KotlinCompile>(first) {
            inputs.property("minecraft_version", serialVersion)
            inputs.property("seed", project.properties["seed"])
            dependsOn(second)
            doFirst {
                setSource(it)
            }
        }
    }
    processTask("scala") {
        tasks.named<ScalaCompile>(first) {
            inputs.property("minecraft_version", serialVersion)
            inputs.property("seed", project.properties["seed"])
            dependsOn(second)
            doFirst {
                setSource(it)
            }
        }
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

tasks.withType<ScalaCompile> {
    scalaCompileOptions.additionalParameters.addAll(listOf("-experimental", "-explain-cyclic", "-Xprint-suspension", "-Ydebug", "-Xprint:typer"))
    scalaCompileOptions.forkOptions.jvmArgs!!.add("-Xmx12G")
}

tasks.withType<Zip> {
    entryCompression = ZipEntryCompression.STORED
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName}" }
    }
    duplicatesStrategy = DuplicatesStrategy.FAIL
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
