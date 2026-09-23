package io.github.acedroidx.frp.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigFormDataTest {

    @Test
    fun loadFromToml_blankStringsAndEmptyTablesBecomeUnset() {
        val form = ConfigFormData()
        form.loadFromToml(
            """
            serverAddr = ""
            user = "alice"

            [auth]
            method = ""
            token = "secret"

            [emptyTable]
            """
        )
        assertNull(form.getValue("serverAddr"))
        assertEquals("alice", form.getValue("user"))
        assertNull(form.getValue("auth.method"))
        assertEquals("secret", form.getValue("auth.token"))
        assertNull(form.getValue("emptyTable"))
    }

    @Test
    fun setValue_emptyString_removesKeyFromOutput() {
        val form = ConfigFormData()
        form.loadFromToml("user = \"alice\"")
        form.setValue("user", "")
        assertFalse(form.toToml().contains("user"))
    }

    @Test
    fun loadFromToml_blankListItemsAreFiltered() {
        val form = ConfigFormData()
        form.loadFromToml("start = [\"a\", \"\", \"b\"]")
        assertEquals(listOf("a", "b"), form.getValue("start"))
    }

    @Test
    fun loadFromToml_nonBlankValuesArePreserved() {
        val form = ConfigFormData()
        form.loadFromToml(
            """
            serverPort = 7000

            [log]
            level = "info"
            disablePrintColor = false
            """
        )
        assertEquals(7000L, form.getValue("serverPort"))
        assertEquals("info", form.getValue("log.level"))
        assertEquals(false, form.getValue("log.disablePrintColor"))
    }
}
