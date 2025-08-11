import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("fabric-loom") version "1.10-SNAPSHOT"
    id("scala")
    kotlin("jvm") version "2.2.0"
    id("maven-publish")
    id("idea")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

val serialVersion = project.properties["minecraft_version"].let {
    val (maj, min, pat) = it.toString().split('.')
    min.toInt() * 100 + pat.toInt()
}

project.buildDir = file("build$serialVersion")

project.properties.forEach { k, v ->
    if (k.endsWith("_$serialVersion")) {
        project.ext[k.replace("_$serialVersion", "")] = v
    }
}

base {
    archivesName.set(project.property("archives_base_name") as String)
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
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    include(modApi("dev.onyxstudios.cardinal-components-api:cardinal-components-base:5.2.3")!!)
    include(modApi("dev.onyxstudios.cardinal-components-api:cardinal-components-world:5.2.3")!!)
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
            val args = mutableListOf("m4", "-F", frozenFile.get().asFile.path)
            globals.get().forEach { k, v ->
                args.add("-D$k=$v")
            }
            macroFiles.get().forEach {
                args.add(it.asFile.path)
            }
            commandLine = args
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
                    exec {
                        commandLine("sh", "-c", "m4 -R ${frozen.get()} ${it.absolutePath}")
                        standardOutput = file("${outDir.get()}/${it.relativeTo(inDir.asFile)}").outputStream()
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
    scalaCompileOptions.additionalParameters.add("-experimental")
    scalaCompileOptions.additionalParameters.add("-explain-cyclic")
//    scalaCompileOptions.additionalParameters.add("-Ydebug-cyclic")
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
