package rj.cocacode.config

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object SettingsMerger {

    fun merge(target: JsonObject, source: JsonObject): JsonObject {
        val result = target.toMutableMap()
        
        for ((key, value) in source) {
            when {
                isNullValue(value) -> {
                    result.remove(key)
                }
                value is JsonArray && result.containsKey(key) -> {
                    val existing = result[key]
                    if (existing is JsonArray) {
                        result[key] = mergeAndDedupeArrays(existing, value)
                    } else {
                        result[key] = value
                    }
                }
                value is JsonObject && result.containsKey(key) -> {
                    val existing = result[key]
                    if (existing is JsonObject) {
                        result[key] = deepMerge(existing, value)
                    } else {
                        result[key] = value
                    }
                }
                else -> {
                    result[key] = value
                }
            }
        }
        
        return JsonObject(result)
    }

    private fun isNullValue(element: kotlinx.serialization.json.JsonElement): Boolean {
        return element is JsonPrimitive && element.content == "null"
    }

    private fun deepMerge(target: JsonObject, source: JsonObject): JsonObject {
        val result = target.toMutableMap()
        for ((key, value) in source) {
            when {
                isNullValue(value) -> {
                    result.remove(key)
                }
                value is JsonArray && result.containsKey(key) -> {
                    val existing = result[key]
                    if (existing is JsonArray) {
                        result[key] = mergeAndDedupeArrays(existing, value)
                    } else {
                        result[key] = value
                    }
                }
                value is JsonObject && result.containsKey(key) -> {
                    val existing = result[key]
                    if (existing is JsonObject) {
                        result[key] = deepMerge(existing, value)
                    } else {
                        result[key] = value
                    }
                }
                else -> {
                    result[key] = value
                }
            }
        }
        return JsonObject(result)
    }

    private fun mergeAndDedupeArrays(target: JsonArray, source: JsonArray): JsonArray {
        val uniqueItems = mutableSetOf<String>()
        val result = mutableListOf<kotlinx.serialization.json.JsonElement>()
        
        for (element in target) {
            val str = element.toString()
            if (uniqueItems.add(str)) {
                result.add(element)
            }
        }
        for (element in source) {
            val str = element.toString()
            if (uniqueItems.add(str)) {
                result.add(element)
            }
        }
        
        return JsonArray(result)
    }

    fun mergeAll(configs: List<JsonObject>): JsonObject {
        if (configs.isEmpty()) return JsonObject(emptyMap())
        if (configs.size == 1) return configs[0]
        
        return configs.reduce { acc, next -> merge(acc, next) }
    }

    fun mergeFromSources(sources: Map<SettingsSource, JsonObject>): JsonObject {
        val priorityOrder = SettingsSource.getPriorityOrder()
        val configs = priorityOrder.mapNotNull { sources[it] }
        return mergeAll(configs)
    }
}