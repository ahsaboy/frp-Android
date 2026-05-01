package io.github.acedroidx.frp.config

import androidx.lifecycle.ViewModel
import io.github.acedroidx.frp.FrpType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConfigFormViewModel(
    val configSchema: ConfigSchema,
    initialToml: String,
) : ViewModel() {
    val formData = ConfigFormData()

    private val _currentSection = MutableStateFlow(0)
    val currentSection = _currentSection.asStateFlow()

    private val _isFormMode = MutableStateFlow(true)
    val isFormMode = _isFormMode.asStateFlow()

    private val _textContent = MutableStateFlow("")
    val textContent = _textContent.asStateFlow()

    private val _expandedSections = MutableStateFlow(setOf<String>())
    val expandedSections = _expandedSections.asStateFlow()

    private val _editingProxyIndex = MutableStateFlow<Int?>(null)
    val editingProxyIndex = _editingProxyIndex.asStateFlow()

    private val _editingVisitorIndex = MutableStateFlow<Int?>(null)
    val editingVisitorIndex = _editingVisitorIndex.asStateFlow()

    private val _showAddProxyDialog = MutableStateFlow(false)
    val showAddProxyDialog = _showAddProxyDialog.asStateFlow()

    private val _showAddVisitorDialog = MutableStateFlow(false)
    val showAddVisitorDialog = _showAddVisitorDialog.asStateFlow()

    init {
        formData.loadFromToml(initialToml)
        applyDefaults()
        _textContent.value = initialToml
        _expandedSections.value = configSchema.sections.map { it.id }.toSet()
    }

    fun setSection(index: Int) {
        _currentSection.value = index
    }

    fun toggleSection(sectionId: String) {
        val current = _expandedSections.value.toMutableSet()
        if (current.contains(sectionId)) current.remove(sectionId) else current.add(sectionId)
        _expandedSections.value = current
    }

    fun isSectionExpanded(sectionId: String): Boolean =
        _expandedSections.value.contains(sectionId)

    fun switchToFormMode() {
        try {
            formData.loadFromToml(_textContent.value)
            applyDefaults()
            _isFormMode.value = true
        } catch (_: Exception) {
            // parse failed, stay in text mode
        }
    }

    fun switchToTextMode() {
        _textContent.value = formData.toToml()
        _isFormMode.value = false
    }

    fun setTextContent(text: String) {
        _textContent.value = text
    }

    fun setEditingProxy(index: Int?) {
        _editingProxyIndex.value = index
    }

    fun setEditingVisitor(index: Int?) {
        _editingVisitorIndex.value = index
    }

    fun showAddProxyDialog() {
        _showAddProxyDialog.value = true
    }

    fun hideAddProxyDialog() {
        _showAddProxyDialog.value = false
    }

    fun showAddVisitorDialog() {
        _showAddVisitorDialog.value = true
    }

    fun hideAddVisitorDialog() {
        _showAddVisitorDialog.value = false
    }

    fun addProxy(type: String) {
        val proxyType = configSchema.proxyTypes.find { it.type == type } ?: return
        val newProxy = mutableMapOf<String, Any?>("type" to type)
        for (field in proxyType.baseFields + proxyType.typeSpecificFields) {
            if (field.defaultValue != null && field.key != "type") {
                SchemaHelpers.setValueByPath(newProxy, field.key, field.defaultValue)
            }
        }
        formData.addProxy(newProxy)
        _showAddProxyDialog.value = false
        _editingProxyIndex.value = formData.proxies.value.size - 1
    }

    fun addVisitor(type: String) {
        val visitorType = configSchema.visitorTypes.find { it.type == type } ?: return
        val newVisitor = mutableMapOf<String, Any?>("type" to type)
        for (field in visitorType.baseFields + visitorType.typeSpecificFields) {
            if (field.defaultValue != null && field.key != "type") {
                SchemaHelpers.setValueByPath(newVisitor, field.key, field.defaultValue)
            }
        }
        formData.addVisitor(newVisitor)
        _showAddVisitorDialog.value = false
        _editingVisitorIndex.value = formData.visitors.value.size - 1
    }

    fun deleteProxy(index: Int) {
        formData.removeProxy(index)
        if (_editingProxyIndex.value == index) _editingProxyIndex.value = null
    }

    fun deleteVisitor(index: Int) {
        formData.removeVisitor(index)
        if (_editingVisitorIndex.value == index) _editingVisitorIndex.value = null
    }

    fun updateProxyField(index: Int, path: String, value: Any?) {
        val proxy = formData.proxies.value[index].toMutableMap()
        SchemaHelpers.setValueByPath(proxy, path, value)
        formData.updateProxy(index, proxy)
    }

    fun updateVisitorField(index: Int, path: String, value: Any?) {
        val visitor = formData.visitors.value[index].toMutableMap()
        SchemaHelpers.setValueByPath(visitor, path, value)
        formData.updateVisitor(index, visitor)
    }

    fun getProxyTypeSchema(proxyType: String): ProxyTypeSchema? {
        return configSchema.proxyTypes.find { it.type == proxyType }
    }

    fun getVisitorTypeSchema(visitorType: String): VisitorTypeSchema? {
        return configSchema.visitorTypes.find { it.type == visitorType }
    }

    fun getPluginTypeSchema(pluginType: String): PluginTypeSchema? {
        return configSchema.pluginTypes.find { it.type == pluginType }
    }

    private fun applyDefaults() {
        for (section in configSchema.sections) {
            for (field in section.fields) {
                if (field.defaultValue != null && formData.getValue(field.key) == null) {
                    formData.setValue(field.key, field.defaultValue)
                }
            }
        }
    }

    companion object {
        fun create(type: FrpType, initialToml: String): ConfigFormViewModel {
            val schema = when (type) {
                FrpType.FRPC -> FrpcSchema.get()
                FrpType.FRPS -> FrpsSchema.get()
            }
            return ConfigFormViewModel(schema, initialToml)
        }
    }
}
