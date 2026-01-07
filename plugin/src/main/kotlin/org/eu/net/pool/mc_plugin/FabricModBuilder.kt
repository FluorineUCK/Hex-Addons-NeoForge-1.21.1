package org.eu.net.pool.mc_plugin

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.gradle.api.Project
import org.gradle.api.tasks.AbstractCopyTask
import java.util.function.Consumer
import java.util.function.Supplier

class FabricMod(val id: String, val version: String): Consumer<JsonDsl.Object> {
    var name = id
    var description = ""
    var license = "GPL-3.0"
    var icon: String? = null
    var environment: Environment? = null
    @PublishedApi internal val authors = JsonDsl.Array()
    @PublishedApi internal val contributors = JsonDsl.Array()
    @PublishedApi internal val entrypoints = JsonDsl.Object()
    @PublishedApi internal val mixins = JsonDsl.Array()
    @PublishedApi internal val contact = JsonDsl.Object()
    @PublishedApi internal val custom = JsonDsl.Object()
    @PublishedApi internal val depends = JsonDsl.Object()
    @PublishedApi internal val recommends = JsonDsl.Object()
    @PublishedApi internal val suggests = JsonDsl.Object()
    @PublishedApi internal val conflicts = JsonDsl.Object()
    @PublishedApi internal val breaks = JsonDsl.Object()
    fun entrypoint(key: String, path: String) {
        entrypoints.array(key) {
            put(path)
        }
    }
    fun entrypoint(path: String) = entrypoint("main", path)
    fun entrypoint(path: String, environment: Environment) = entrypoint(environment.id, path)
    fun mixins(path: String) {
        mixins.put(path)
    }
    fun mixins(path: String, environment: Environment) {
        mixins.map {
            put("config", path)
            put("environment", environment.id)
        }
    }
    inline fun contact(action: JsonDsl.Object.() -> Unit) = contact.run(action)
    inline fun contact(name: String, url: String) = contact { put(name, url) }
    inline fun custom(action: JsonDsl.Object.() -> Unit) = custom.run(action)
    fun cardinalComponents(vararg ids: String) = custom {
        array("cardinal-components") {
            ids.forEach {
                put(if (it.contains(':')) id else "$id:$it")
            }
        }
    }
    fun author(name: String) {
        authors.put(name)
    }
    inline fun author(name: String, contact: JsonDsl.Object.() -> Unit) {
        authors.map {
            put("name", name)
            map("contact") {
                contact()
            }
        }
    }
    fun contributor(name: String) {
        contributors.put(name)
    }
    inline fun contributor(name: String, role: String? = "", contact: JsonDsl.Object.() -> Unit = {}) {
        contributors.map {
            put("name", name)
            if (role != null) put("role", role)
            map("contact") {
                contact()
            }
        }
    }
    fun depends(mod: String, version: String = "*") {
        depends.put(mod, version)
    }
    fun recommends(mod: String, version: String = "*") {
        recommends.put(mod, version)
    }
    fun suggests(mod: String, version: String = "*") {
        suggests.put(mod, version)
    }
    fun conflicts(mod: String, version: String = "*") {
        conflicts.put(mod, version)
    }
    fun breaks(mod: String, version: String = "*") {
        breaks.put(mod, version)
    }

    fun JsonDsl.Object.merge() {
        put("schemaVersion", 1)
        put("id", id)
        put("version", version)
        put("name", name)
        put("description", description)
        if (icon != null) put("icon", icon!!)
        environment?.let { put("environment", it.id) }
        put("authors", authors)
        put("contributors", contributors)
        put("entrypoints", entrypoints)
        put("mixins", mixins)
        put("custom", custom)
        put("depends", depends)
        put("recommends", recommends)
        put("suggests", suggests)
        put("conflicts", conflicts)
        put("breaks", breaks)
    }

    override fun accept(t: JsonDsl.Object) = t.merge()
    override fun toString(): String = JsonDsl.Object().run { merge(); toString() }
}

enum class Environment(val id: String) {
    Server("server"),
    Client("client"),
}