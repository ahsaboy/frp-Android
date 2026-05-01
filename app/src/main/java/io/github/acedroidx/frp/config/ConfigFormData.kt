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
        val map = data.toMutableMap()
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
