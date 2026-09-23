package io.github.acedroidx.frp.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConfigFormData {
    private val _values = MutableStateFlow(mutableMapOf<String, Any?>())
    val values = _values.asStateFlow()

    private val _proxies = MutableStateFlow(mutableListOf<MutableMap<String, Any?>>())
    val proxies = _proxies.asStateFlow()

    private val _visitors = MutableStateFlow(mutableListOf<MutableMap<String, Any?>>())
    val visitors = _visitors.asStateFlow()

    fun loadFromMap(data: Map<String, Any?>) {
        val map = pruneUnsetValues(data)
        val proxiesList = (map.remove("proxies") as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>()
            ?.map { it.toMutableMap() }
            ?.toMutableList()
            ?: mutableListOf()
        val visitorsList = (map.remove("visitors") as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>()
            ?.map { it.toMutableMap() }
            ?.toMutableList()
            ?: mutableListOf()
        _values.value = map
        _proxies.value = proxiesList
        _visitors.value = visitorsList
    }

    fun toMap(): Map<String, Any?> {
        val result = _values.value.toMutableMap()
        if (_proxies.value.isNotEmpty()) {
            result["proxies"] = _proxies.value.map { it.toMap() }
        }
        if (_visitors.value.isNotEmpty()) {
            result["visitors"] = _visitors.value.map { it.toMap() }
        }
        return result
    }

    fun loadFromToml(tomlString: String) {
        val data = TomlParserUtil.parseToMap(tomlString)
        loadFromMap(data)
    }

    /**
     * 载入时把空值统一归一化为“未设置”：null、空白字符串、空表、
     * 空集合以及全空条目一律移除，让旧版本写入的 `xxx = ""` 在下次保存后自然消失。
     */
    private fun pruneUnsetValues(data: Map<*, *>): MutableMap<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        for ((key, value) in data) {
            val name = key as? String ?: continue
            when {
                value == null -> Unit
                value is String && value.isBlank() -> Unit
                value is Map<*, *> -> {
                    val nested = pruneUnsetValues(value)
                    if (nested.isNotEmpty()) result[name] = nested
                }
                value is List<*> -> {
                    val items = value.mapNotNull { item ->
                        when {
                            item == null -> null
                            item is String && item.isBlank() -> null
                            item is Map<*, *> -> pruneUnsetValues(item).takeIf { it.isNotEmpty() }
                            else -> item
                        }
                    }
                    if (items.isNotEmpty()) result[name] = items
                }
                else -> result[name] = value
            }
        }
        return result
    }

    fun toToml(): String {
        return TomlParserUtil.mapToToml(toMap())
    }

    fun getValue(path: String): Any? = SchemaHelpers.getValueByPath(_values.value, path)

    fun setValue(path: String, value: Any?) {
        val newValues = _values.value.toMutableMap()
        SchemaHelpers.setValueByPath(newValues, path, value)
        _values.value = newValues
    }

    fun addProxy(proxy: Map<String, Any?>) {
        val newList = _proxies.value.toMutableList()
        newList.add(proxy.toMutableMap())
        _proxies.value = newList
    }

    fun removeProxy(index: Int) {
        val newList = _proxies.value.toMutableList()
        newList.removeAt(index)
        _proxies.value = newList
    }

    fun updateProxy(index: Int, proxy: Map<String, Any?>) {
        val newList = _proxies.value.toMutableList()
        newList[index] = proxy.toMutableMap()
        _proxies.value = newList
    }

    fun addVisitor(visitor: Map<String, Any?>) {
        val newList = _visitors.value.toMutableList()
        newList.add(visitor.toMutableMap())
        _visitors.value = newList
    }

    fun removeVisitor(index: Int) {
        val newList = _visitors.value.toMutableList()
        newList.removeAt(index)
        _visitors.value = newList
    }

    fun updateVisitor(index: Int, visitor: Map<String, Any?>) {
        val newList = _visitors.value.toMutableList()
        newList[index] = visitor.toMutableMap()
        _visitors.value = newList
    }
}
