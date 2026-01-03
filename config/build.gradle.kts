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
import net.fabricmc.loom.api.*

buildscript {
    repositories {
        mavenCentral()
        maven { url = uri("https://maven.fabricmc.net") }
    }
    dependencies {
        classpath("net.fabricmc:fabric-loom:1.13-SNAPSHOT")
    }
}

plugins {
    id("scala")
    kotlin("jvm") version "2.2.0"
    id("maven-publish")
    id("idea")
    id("de.undercouch.download") version "5.6.0"
    id("org.eu.net.pool.mc-plugin") version "0.1.1"
}

allprojects {
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
}

val release: Boolean = !System.getenv("release").isNullOrEmpty()
ext["release"] = release
allprojects {
    val p = P(project)
    val modid: String by project.properties
    ext.set("p", p)
    version = project.property("mod_version") as String
    if (!release) version = "${version}+${p.commit_id.take(7)}"
    group = rootProject.property("maven_group") as String
    println("configuring $modid ($project) v$version @ $group")
    plugins.withId("java") {
        base {
            archivesName.set(modid)
        }
        java {
            toolchain.languageVersion = JavaLanguageVersion.of(17)
            withSourcesJar()
        }

        tasks.named<Jar>("jar").configure {
            from("LICENSE") {
                rename { "LICENSE_$modid" }
            }
            duplicatesStrategy = DuplicatesStrategy.WARN
        }
    }

    plugins.withId("scala") {
        scala {
            scalaVersion = "3.7.1"
        }
    }

    plugins.withId("fabric-loom") {
        extensions.getByType<LoomGradleExtensionAPI>().apply {
            splitEnvironmentSourceSets()
            runs["client"].programArgs += listOf("--username", "Player", "--uuid", "9e1b34e3-8031-4623-8918-eb7914ab564b")

            mods {
                register(modid) {
                    sourceSet("main")
                    sourceSet("client")
                }
            }

            mixin.useLegacyMixinAp = false
        }

        extensions.getByType<net.fabricmc.loom.api.fabricapi.FabricApiExtension>().apply {
            configureTests {
                modId = modid
                eula = true
            }
        }

        dependencies {
            "modLocalRuntime"("maven.modrinth:ears:1.4.7+fabric-1.20")
        }

        if (project != rootProject) {
            tasks.named("runClient") {
                doFirst {
                    val rootOptions = rootProject.file("run/options.txt").toPath()
                    val options = file("run/options.txt").toPath()
                    options.deleteIfExists()
                    Files.createSymbolicLink(options, rootOptions)
                }
            }
        }

        tasks.processResources {
            val bookRoot = destinationDir.resolve("assets/hexcasting/patchouli_books/thehexbook")
            val langRoot = destinationDir.resolve("assets/$modid/lang")

            doLast {
                bookRoot.list()?.forEach { lang ->
                    val langFile = langRoot.resolve("$lang.json")
                    if (langFile.exists()) {
                        val entries = JsonSlurper().parseText(langFile.readText()) as MutableMap<String, String>
                        var n = 0
                        for (bookFile in bookRoot.resolve(lang).walkTopDown()) {
                            if (bookFile.isFile) {
                                val json = JsonSlurper().parseText(bookFile.readText())
                                if (json !is Map<*, *>) continue
                                json as MutableMap<Any, Any>
                                val name = json["name"]
                                if (name is String) {
                                    entries["text.$modid.book.${n}"] = name
                                    json["name"] = "text.$modid.book.${n}"
                                    n++
                                }
                                val pages = json["pages"]
                                if (pages !is MutableList<*>) continue
                                pages as MutableList<Any>
                                for (i in pages.indices) {
                                    val page = pages[i]
                                    if (page is String) {
                                        entries["text.$modid.book.${n}"] = page
                                        pages[i] = "text.$modid.book.${n}"
                                        n++
                                    } else if (page is MutableMap<*, *>) {
                                        page as MutableMap<Any, Any>
                                        for (key in listOf("text", "title", "header")) {
                                            val text = page[key]
                                            if (text != null && text is String) {
                                                entries["text.$modid.book.${n}"] = text
                                                page[key] = "text.$modid.book.${n}"
                                                n++
                                            }
                                        }
                                    }
                                }
                                bookFile.writeText(JsonOutput.toJson(json))
                            }
                        }
                        langFile.writeText(JsonOutput.toJson(entries))
                    }
                }
            }
        }
    }

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
        exactRepo("https://pool.net.eu.org/",
            "dev.kineticcat.hexportation",
            "miyucomics.hexcellular",
            "miyucomics.hexical",
            "miyucomics.overevaluate",
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
}