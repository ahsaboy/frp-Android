package io.github.acedroidx.frp.config

import io.github.acedroidx.frp.FrpType

enum class FieldType {
    STRING, INT, LONG, BOOL, ENUM, STRING_LIST, MAP_STRING, OBJECT
}

data class FieldSchema(
    val key: String,
    val type: FieldType,
    val label: String,
    val defaultValue: Any? = null,
    val enumOptions: List<String> = emptyList(),
    val required: Boolean = false,
    val visibleWhen: ((Map<String, Any?>) -> Boolean)? = null,
    val hint: String = "",
    val children: List<FieldSchema> = emptyList(),
)

data class ConfigSection(
    val id: String,
    val title: String,
    val fields: List<FieldSchema>,
)

data class ProxyTypeSchema(
    val type: String,
    val label: String,
    val baseFields: List<FieldSchema>,
    val typeSpecificFields: List<FieldSchema>,
)

data class VisitorTypeSchema(
    val type: String,
    val label: String,
    val baseFields: List<FieldSchema>,
    val typeSpecificFields: List<FieldSchema>,
)

data class PluginTypeSchema(
    val type: String,
    val label: String,
    val fields: List<FieldSchema>,
)

data class ConfigSchema(
    val type: FrpType,
    val sections: List<ConfigSection>,
    val proxyTypes: List<ProxyTypeSchema> = emptyList(),
    val visitorTypes: List<VisitorTypeSchema> = emptyList(),
    val pluginTypes: List<PluginTypeSchema> = emptyList(),
)

object SchemaHelpers {
    fun path(vararg parts: String): String = parts.joinToString(".")

    fun getValueByPath(data: Map<String, Any?>, path: String): Any? {
        val parts = path.split(".")
        var current: Any? = data
        for (part in parts) {
            current = when (current) {
                is Map<*, *> -> (current as Map<*, *>)[part]
                else -> return null
            }
        }
        return current
    }

    fun setValueByPath(data: MutableMap<String, Any?>, path: String, value: Any?) {
        val parts = path.split(".")
        var current = data
        for (i in 0 until parts.size - 1) {
            val part = parts[i]
            val existing = current[part]
            if (existing is Map<*, *>) {
                val copy = existing.toMutableMap()
                current[part] = copy
                @Suppress("UNCHECKED_CAST")
                current = copy as MutableMap<String, Any?>
            } else {
                val newMap = mutableMapOf<String, Any?>()
                current[part] = newMap
                current = newMap
            }
        }
        current[parts.last()] = value
    }
}
