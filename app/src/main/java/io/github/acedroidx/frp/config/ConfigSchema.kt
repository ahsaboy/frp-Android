package io.github.acedroidx.frp.config

import io.github.acedroidx.frp.FrpType

enum class FieldType {
    STRING, INT, LONG, BOOL, ENUM, STRING_LIST, MAP_STRING, MAP_BOOL, OBJECT, OBJECT_LIST
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

    /**
     * 写入字段值。统一语义：null 与空字符串表示“未设置”，
     * 会移除该键并剪枝因此变空的父表，保存时自然不会输出到 TOML。
     */
    fun setValueByPath(data: MutableMap<String, Any?>, path: String, value: Any?) {
        if (value == null || (value is String && value.isEmpty())) {
            removeValueByPath(data, path)
            return
        }
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

    /** 移除字段值；父表因此变空时一并移除。返回是否确实移除了键。 */
    fun removeValueByPath(data: MutableMap<String, Any?>, path: String): Boolean =
        removeValueAt(data, path.split("."), 0)

    private fun removeValueAt(
        current: MutableMap<String, Any?>,
        parts: List<String>,
        index: Int,
    ): Boolean {
        val key = parts[index]
        if (index == parts.lastIndex) {
            if (!current.containsKey(key)) return false
            current.remove(key)
            return true
        }
        val child = current[key]
        if (child !is Map<*, *>) return false
        val copy = child.toMutableMap()
        current[key] = copy
        val removed = removeValueAt(copy, parts, index + 1)
        if (copy.isEmpty()) current.remove(key)
        return removed
    }

    /**
     * 把 schema 默认值叠加到“未设置”的键上，得到用于 UI 显示与
     * visibleWhen 判定的有效值。只读：不写回配置数据，保存时未设置的键仍被省略。
     */
    fun withDefaults(data: Map<String, Any?>, fields: List<FieldSchema>): Map<String, Any?> {
        if (fields.none { it.defaultValue != null }) return data
        val result = data.toMutableMap()
        for (field in fields) {
            if (field.defaultValue != null && getValueByPath(result, field.key) == null) {
                setValueByPath(result, field.key, field.defaultValue)
            }
        }
        return result
    }
}
