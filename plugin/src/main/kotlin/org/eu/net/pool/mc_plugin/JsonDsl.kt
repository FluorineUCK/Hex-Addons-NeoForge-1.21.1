package org.eu.net.pool.mc_plugin

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal val gson = Gson()

@DslMarker
annotation class JsonDsl {
    sealed interface Element {
        val element: JsonElement
    }
    @JsonDsl
    class Object(override val element: JsonObject = JsonObject()): Element {
        internal fun check(key: String) {
            if (element.has(key))
                throw IllegalArgumentException("Duplicate key $key")
        }
        fun put(key: String, value: String) {
            check(key)
            element.addProperty(key, value)
        }
        fun put(key: String, value: Number) {
            check(key)
            element.addProperty(key, value)
        }
        fun put(key: String, value: Boolean) {
            check(key)
            element.addProperty(key, value)
        }
        fun put(key: String, value: JsonElement) {
            check(key)
            element.add(key, value)
        }
        fun put(key: String, value: Element) {
            check(key)
            element.add(key, value.element)
        }
        inline fun array(key: String, action: Array.() -> Unit) {
            element.add(key, (element.get(key)?.asJsonArray ?: JsonArray()).also { Array(it).action() })
        }
        inline fun map(key: String, action: Object.() -> Unit) {
            element.add(key, (element.get(key)?.asJsonObject ?: JsonObject()).also { Object(it).action() })
        }

        override fun toString(): String = gson.toJson(element)
    }
    @JsonDsl
    class Array(override val element: JsonArray = JsonArray()): Element {
        fun put(value: String) {
            element.add(value)
        }
        fun put(value: Number) {
            element.add(value)
        }
        fun put(value: Boolean) {
            element.add(value)
        }
        fun put(value: JsonElement) {
            element.add(value)
        }
        fun put(value: Element) {
            element.add(value.element)
        }
        inline fun array(action: Array.() -> Unit) {
            element.add(JsonArray().also { Array(it).action() })
        }
        inline fun map(action: Object.() -> Unit) {
            element.add(JsonObject().also { Object(it).action() })
        }

        override fun toString(): String = gson.toJson(element)
    }
}