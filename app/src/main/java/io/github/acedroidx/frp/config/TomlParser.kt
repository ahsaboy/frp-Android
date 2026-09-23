package io.github.acedroidx.frp.config

import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.parsers.TomlParser
import com.akuleshov7.ktoml.tree.nodes.TableType
import com.akuleshov7.ktoml.tree.nodes.TomlArrayOfTablesElement
import com.akuleshov7.ktoml.tree.nodes.TomlInlineTable
import com.akuleshov7.ktoml.tree.nodes.TomlKeyValueArray
import com.akuleshov7.ktoml.tree.nodes.TomlKeyValuePrimitive
import com.akuleshov7.ktoml.tree.nodes.TomlNode
import com.akuleshov7.ktoml.tree.nodes.TomlTable
import com.akuleshov7.ktoml.tree.nodes.pairs.values.TomlArray
import com.akuleshov7.ktoml.tree.nodes.pairs.values.TomlBasicString
import com.akuleshov7.ktoml.tree.nodes.pairs.values.TomlBoolean
import com.akuleshov7.ktoml.tree.nodes.pairs.values.TomlDouble
import com.akuleshov7.ktoml.tree.nodes.pairs.values.TomlLong
import com.akuleshov7.ktoml.tree.nodes.pairs.values.TomlNull
import com.akuleshov7.ktoml.tree.nodes.pairs.values.TomlValue

object TomlParserUtil {

    private val arrayOfTablesKeys = setOf("proxies", "visitors", "httpPlugins")

    private val tomlInputConfig = TomlInputConfig(
        ignoreUnknownNames = true,
        allowEmptyValues = true,
        allowNullValues = true,
        allowEmptyToml = true,
    )

    fun parseToMap(tomlString: String): MutableMap<String, Any?> {
        val parser = TomlParser(tomlInputConfig)
        val tomlFile = parser.parseString(tomlString.trimIndent())
        val result = mutableMapOf<String, Any?>()
        processChildren(tomlFile, result)
        return result
    }

    private fun processChildren(node: TomlNode, result: MutableMap<String, Any?>) {
        for (child in node.children) {
            when (child) {
                is TomlKeyValuePrimitive -> {
                    val key = child.key.last()
                    result[key] = extractTomlValue(child.value)
                }
                is TomlKeyValueArray -> {
                    val key = child.key.last()
                    val arrayValue = child.value
                    if (arrayValue is TomlArray) {
                        result[key] = arrayValue.parse(tomlInputConfig).map { extractTomlValue(it) }
                    }
                }
                is TomlArrayOfTablesElement -> {
                    val arrayName = child.name
                    @Suppress("UNCHECKED_CAST")
                    val existing = result.getOrPut(arrayName) {
                        mutableListOf<MutableMap<String, Any?>>()
                    } as MutableList<MutableMap<String, Any?>>
                    val item = mutableMapOf<String, Any?>()
                    processChildren(child, item)
                    existing.add(item)
                }
                is TomlTable -> {
                    if (child.type == TableType.ARRAY) {
                        // [[array-of-tables]] parent node — children are TomlArrayOfTablesElement
                        val arrayName = child.fullTableKey.last()
                        @Suppress("UNCHECKED_CAST")
                        val existing = result.getOrPut(arrayName) {
                            mutableListOf<MutableMap<String, Any?>>()
                        } as MutableList<MutableMap<String, Any?>>
                        for (arrayChild in child.children) {
                            if (arrayChild is TomlArrayOfTablesElement) {
                                val item = mutableMapOf<String, Any?>()
                                processChildren(arrayChild, item)
                                existing.add(item)
                            }
                        }
                    } else {
                        val tableName = child.fullTableKey.last()
                        val nested = mutableMapOf<String, Any?>()
                        processChildren(child, nested)
                        mergeInto(result, tableName, nested)
                    }
                }
                else -> {}
            }
        }
    }

    private fun extractTomlValue(value: Any?): Any? {
        return when (value) {
            is TomlNull -> null
            is TomlBasicString -> value.content as? String ?: value.content?.toString()
            is TomlBoolean -> value.content as? Boolean
            is TomlLong -> (value.content as? Number)?.toLong()
            is TomlDouble -> (value.content as? Number)?.toDouble()
            is TomlInlineTable -> mutableMapOf<String, Any?>().also { processChildren(value, it) }
            is List<*> -> value.map { extractTomlValue(it) }
            is Map<*, *> -> value.entries.associate { it.key.toString() to extractTomlValue(it.value) }
            is TomlValue -> extractTomlValue(value.content)
            else -> value
        }
    }

    private fun mergeInto(result: MutableMap<String, Any?>, key: String, nested: Map<String, Any?>) {
        val existing = result[key]
        if (existing is MutableMap<*, *>) {
            @Suppress("UNCHECKED_CAST")
            (existing as MutableMap<String, Any?>).putAll(nested)
        } else {
            result[key] = nested
        }
    }

    fun mapToToml(data: Map<String, Any?>): String {
        val sb = StringBuilder()
        val tables = mutableListOf<Pair<String, Map<String, Any?>>>()
        val arraysOfTables = mutableListOf<Pair<String, List<Map<String, Any?>>>>()

        for ((key, value) in data) {
            when (value) {
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    tables.add(key to (value as Map<String, Any?>))
                }
                is List<*> -> {
                    if (key in arrayOfTablesKeys && value.isNotEmpty() && value.first() is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        arraysOfTables.add(key to (value as List<Map<String, Any?>>))
                    } else {
                        writeKeyValue(sb, key, value)
                    }
                }
                else -> writeKeyValue(sb, key, value)
            }
        }

        for ((key, table) in tables) {
            writeTable(sb, key, table)
        }

        for ((key, array) in arraysOfTables) {
            writeArrayOfTables(sb, key, array)
        }

        return sb.toString()
    }

    private fun writeKeyValue(sb: StringBuilder, key: String, value: Any?) {
        when (value) {
            null -> return
            //空白字符串视为“未设置”，不写入
            is String -> if (value.isNotBlank()) {
                sb.appendLine("$key = \"${escapeString(value)}\"")
            }
            is Boolean -> sb.appendLine("$key = $value")
            is Number -> sb.appendLine("$key = $value")
            is List<*> -> {
                //过滤 null / 空白标量 / 空内联表；全空的列表不写入
                val items = value
                    .filter { item -> if (item is Map<*, *>) hasContent(item) else isSerializableScalar(item) }
                    .map { item -> if (item is Map<*, *>) writeInlineTable(item) else writeScalar(item) }
                if (items.isNotEmpty()) {
                    sb.appendLine("$key = [${items.joinToString(", ")}]")
                }
            }
            is Map<*, *> -> {}
        }
    }

    /** 标量是否应写入 TOML：null 与空白字符串表示“未设置”。 */
    private fun isSerializableScalar(value: Any?): Boolean = when (value) {
        null -> false
        is String -> value.isNotBlank()
        else -> true
    }

    /** 值（含嵌套集合）是否包含可写入的内容；空表/全空集合不输出。 */
    private fun hasContent(value: Any?): Boolean = when (value) {
        null -> false
        is String -> value.isNotBlank()
        is Map<*, *> -> value.values.any { hasContent(it) }
        is List<*> -> value.any { hasContent(it) }
        else -> true
    }

    private fun writeTable(sb: StringBuilder, key: String, table: Map<String, Any?>) {
        if (!hasContent(table)) return
        sb.appendLine()
        sb.appendLine("[$key]")
        writeTableContent(sb, key, table)
    }

    private fun writeTableContent(sb: StringBuilder, parentPath: String, table: Map<String, Any?>) {
        val simpleKVs = mutableListOf<Pair<String, Any?>>()
        val nestedTables = mutableListOf<Pair<String, Map<String, Any?>>>()
        val nestedArrays = mutableListOf<Pair<String, List<Map<String, Any?>>>>()

        for ((key, value) in table) {
            when (value) {
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    nestedTables.add(key to (value as Map<String, Any?>))
                }
                is List<*> -> {
                    if (key in arrayOfTablesKeys && value.isNotEmpty() && value.first() is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        nestedArrays.add(key to (value as List<Map<String, Any?>>))
                    } else {
                        simpleKVs.add(key to value)
                    }
                }
                else -> simpleKVs.add(key to value)
            }
        }

        for ((key, value) in simpleKVs) {
            writeKeyValue(sb, key, value)
        }

        for ((key, nested) in nestedTables) {
            //空的子表不输出表头
            if (!hasContent(nested)) continue
            val fullPath = "$parentPath.$key"
            sb.appendLine()
            sb.appendLine("[$fullPath]")
            writeTableContent(sb, fullPath, nested)
        }

        for ((key, array) in nestedArrays) {
            writeArrayOfTables(sb, "$parentPath.$key", array)
        }
    }

    private fun writeArrayOfTables(sb: StringBuilder, key: String, array: List<Map<String, Any?>>) {
        //没有任何可写入内容的条目（如清空后的表单项）直接跳过
        val items = array.filter { hasContent(it) }
        if (items.isEmpty()) return
        for (item in items) {
            sb.appendLine()
            sb.appendLine("[[$key]]")
            writeTableContent(sb, key, item)
        }
    }

    private fun writeScalar(value: Any?): String {
        return when (value) {
            null -> ""
            is String -> "\"${escapeString(value)}\""
            is Boolean, is Number -> value.toString()
            else -> "\"${escapeString(value.toString())}\""
        }
    }

    private fun writeInlineTable(table: Map<*, *>): String {
        val entries = table.entries.filter { hasContent(it.value) }
        if (entries.isEmpty()) return "{ }"
        return entries.joinToString(", ", prefix = "{ ", postfix = " }") { (key, value) ->
            "${key} = ${if (value is Map<*, *>) writeInlineTable(value) else writeScalar(value)}"
        }
    }

    private fun escapeString(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
