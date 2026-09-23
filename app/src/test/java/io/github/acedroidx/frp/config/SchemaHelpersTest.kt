package io.github.acedroidx.frp.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaHelpersTest {

    @Test
    fun setValueByPath_setsNestedValue() {
        val data = mutableMapOf<String, Any?>()
        SchemaHelpers.setValueByPath(data, "auth.method", "oidc")
        assertEquals("oidc", SchemaHelpers.getValueByPath(data, "auth.method"))
    }

    @Test
    fun setValueByPath_emptyString_removesKey() {
        val data = mutableMapOf<String, Any?>("user" to "alice")
        SchemaHelpers.setValueByPath(data, "user", "")
        assertFalse(data.containsKey("user"))
    }

    @Test
    fun setValueByPath_null_removesKey() {
        val data = mutableMapOf<String, Any?>("user" to "alice")
        SchemaHelpers.setValueByPath(data, "user", null)
        assertFalse(data.containsKey("user"))
    }

    @Test
    fun setValueByPath_emptyStringOnNestedKey_prunesEmptiedParentTable() {
        val data = mutableMapOf<String, Any?>(
            "auth" to mutableMapOf<String, Any?>("token" to "secret"),
        )
        SchemaHelpers.setValueByPath(data, "auth.token", "")
        assertFalse(data.containsKey("auth"))
    }

    @Test
    fun removeValueByPath_keepsParentTableThatStillHasKeys() {
        val data = mutableMapOf<String, Any?>(
            "auth" to mutableMapOf<String, Any?>("method" to "token", "token" to "secret"),
        )
        val removed = SchemaHelpers.removeValueByPath(data, "auth.token")
        assertTrue(removed)
        val auth = data["auth"] as Map<*, *>
        assertEquals("token", auth["method"])
        assertFalse(auth.containsKey("token"))
    }

    @Test
    fun removeValueByPath_missingKey_returnsFalseWithoutThrowing() {
        val data = mutableMapOf<String, Any?>(
            "log" to mutableMapOf<String, Any?>("level" to "info"),
        )
        assertFalse(SchemaHelpers.removeValueByPath(data, "log.nope"))
        assertFalse(SchemaHelpers.removeValueByPath(data, "missing.path"))
        assertEquals("info", SchemaHelpers.getValueByPath(data, "log.level"))
    }

    @Test
    fun withDefaults_fillsOnlyMissingDefaultsWithoutMutatingInput() {
        val data = mutableMapOf<String, Any?>(
            "log" to mutableMapOf<String, Any?>("level" to "debug"),
        )
        val fields = listOf(
            FieldSchema("log.level", FieldType.ENUM, "level", defaultValue = "info"),
            FieldSchema("log.maxDays", FieldType.INT, "maxDays", defaultValue = 3),
        )
        val effective = SchemaHelpers.withDefaults(data, fields)
        //已显式的值不被默认值覆盖
        assertEquals("debug", SchemaHelpers.getValueByPath(effective, "log.level"))
        //未设置的键在有效值中回显默认值
        assertEquals(3, SchemaHelpers.getValueByPath(effective, "log.maxDays"))
        //原始配置数据不被写入默认值
        assertFalse((data["log"] as Map<*, *>).containsKey("maxDays"))
    }
}
