package io.github.acedroidx.frp.config

import org.junit.Test
import org.junit.Assert.*
import java.io.File

class TomlParserUtilTest {

    private fun readFile(relativePath: String): String {
        val projectRoot = File("D:/my_first_web/frp-Android")
        return File(projectRoot, relativePath).readText()
    }

    // ==================== 1. Basic parsing (default frpc.toml) ====================

    @Test
    fun parseToMap_defaultConfig_topLevelFields() {
        val toml = readFile("app/src/main/assets/frpc.toml")
        val result = TomlParserUtil.parseToMap(toml)

        assertEquals("", result["serverAddr"])
        assertEquals(7000L, result["serverPort"])
        assertEquals("114.114.114.114", result["dnsServer"])
    }

    @Test
    fun parseToMap_defaultConfig_nestedLogTable() {
        val toml = readFile("app/src/main/assets/frpc.toml")
        val result = TomlParserUtil.parseToMap(toml)

        val log = result["log"] as Map<*, *>
        assertEquals("debug", log["level"])
        assertEquals(true, log["disablePrintColor"])
    }

    @Test
    fun parseToMap_defaultConfig_visitorsArray() {
        val toml = readFile("app/src/main/assets/frpc.toml")
        val result = TomlParserUtil.parseToMap(toml)

        val visitors = result["visitors"] as List<*>
        assertEquals(1, visitors.size)

        val visitor = visitors[0] as Map<*, *>
        assertEquals("p2p_visitor", visitor["name"])
        assertEquals("xtcp", visitor["type"])
        assertEquals("", visitor["serverName"])
        assertEquals("", visitor["secretKey"])
        assertEquals("127.0.0.1", visitor["bindAddr"])
        assertEquals(6000L, visitor["bindPort"])
    }

    // ==================== 2. Full example parsing ====================

    @Test
    fun parseToMap_fullExample_topLevelFields() {
        val toml = readFile("frpc_full_example.toml")
        val result = TomlParserUtil.parseToMap(toml)

        assertEquals("your_client_id", result["clientID"])
        assertEquals("your_name", result["user"])
        assertEquals("0.0.0.0", result["serverAddr"])
        assertEquals(7000L, result["serverPort"])
        assertEquals(true, result["loginFailExit"])
        assertEquals(1500L, result["udpPacketSize"])
    }

    @Test
    fun parseToMap_fullExample_nestedTables() {
        val toml = readFile("frpc_full_example.toml")
        val result = TomlParserUtil.parseToMap(toml)

        // log table (dotted keys: log.to, log.level, etc.)
        val log = result["log"] as Map<*, *>
        assertEquals("./frpc.log", log["to"])
        assertEquals("info", log["level"])
        assertEquals(3L, log["maxDays"])
        assertEquals(false, log["disablePrintColor"])

        // auth table (dotted keys: auth.method, auth.token)
        val auth = result["auth"] as Map<*, *>
        assertEquals("token", auth["method"])
        assertEquals("12345678", auth["token"])

        // transport table with nested tls
        val transport = result["transport"] as Map<*, *>
        assertEquals(5L, transport["poolCount"])
        assertEquals("tcp", transport["protocol"])
        assertEquals("0.0.0.0", transport["connectServerLocalIP"])

        val tls = transport["tls"] as Map<*, *>
        assertEquals(true, tls["enable"])

        // webServer table
        val webServer = result["webServer"] as Map<*, *>
        assertEquals("127.0.0.1", webServer["addr"])
        assertEquals(7400L, webServer["port"])
        assertEquals("admin", webServer["user"])
        assertEquals("admin", webServer["password"])
        assertEquals(false, webServer["pprofEnable"])

        // metadatas map (dotted keys)
        val metadatas = result["metadatas"] as Map<*, *>
        assertEquals("abc", metadatas["var1"])
        assertEquals("123", metadatas["var2"])
    }

    @Test
    fun parseToMap_fullExample_proxiesArraySizeAndFirstEntry() {
        val toml = readFile("frpc_full_example.toml")
        val result = TomlParserUtil.parseToMap(toml)

        val proxies = result["proxies"] as List<*>
        // The full example has: ssh, ssh_random, dns, web01, web02, tcpmuxhttpconnect,
        // plugin_unix_domain_socket, plugin_http_proxy, plugin_socks5, plugin_static_file,
        // plugin_https2http, plugin_https2https, plugin_http2https, plugin_http2http,
        // plugin_tls2raw, secret_tcp, p2p_tcp, vnet-server = 18 proxies
        assertEquals(18, proxies.size)

        val ssh = proxies[0] as Map<*, *>
        assertEquals("ssh", ssh["name"])
        assertEquals("tcp", ssh["type"])
        assertEquals("127.0.0.1", ssh["localIP"])
        assertEquals(22L, ssh["localPort"])
        assertEquals(6001L, ssh["remotePort"])
    }

    @Test
    fun parseToMap_fullExample_proxySubTables() {
        val toml = readFile("frpc_full_example.toml")
        val result = TomlParserUtil.parseToMap(toml)

        val proxies = result["proxies"] as List<*>
        val ssh = proxies[0] as Map<*, *>

        // Note: ktoml 0.5.2 promotes dotted keys from inside [[proxies]] to top level.
        // transport, loadBalancer, healthCheck, metadatas become top-level keys.

        // annotations sub-table (explicit [proxies.annotations] stays inside proxy)
        val annotations = ssh["annotations"] as Map<*, *>
        assertEquals("value1", annotations["key1"])
        assertEquals("value2", annotations["'prefix/key2'"])

        // Dotted keys promoted to top level
        val transport = result["transport"] as Map<*, *>
        assertEquals("1MB", transport["bandwidthLimit"])
        assertEquals("client", transport["bandwidthLimitMode"])
        assertEquals(false, transport["useEncryption"])
        assertEquals(false, transport["useCompression"])
    }

    @Test
    fun parseToMap_fullExample_differentProxyTypes() {
        val toml = readFile("frpc_full_example.toml")
        val result = TomlParserUtil.parseToMap(toml)

        val proxies = result["proxies"] as List<*>
        val proxyMap = proxies.associateBy { (it as Map<*, *>)["name"] as String }

        assertEquals("tcp", (proxyMap["ssh"] as Map<*, *>)["type"])
        assertEquals("tcp", (proxyMap["ssh_random"] as Map<*, *>)["type"])
        assertEquals("udp", (proxyMap["dns"] as Map<*, *>)["type"])
        assertEquals("http", (proxyMap["web01"] as Map<*, *>)["type"])
        assertEquals("https", (proxyMap["web02"] as Map<*, *>)["type"])
        assertEquals("tcpmux", (proxyMap["tcpmuxhttpconnect"] as Map<*, *>)["type"])
        assertEquals("stcp", (proxyMap["secret_tcp"] as Map<*, *>)["type"])
        assertEquals("xtcp", (proxyMap["p2p_tcp"] as Map<*, *>)["type"])
        assertEquals("stcp", (proxyMap["vnet-server"] as Map<*, *>)["type"])
    }

    @Test
    fun parseToMap_fullExample_pluginConfigs() {
        val toml = readFile("frpc_full_example.toml")
        val result = TomlParserUtil.parseToMap(toml)

        val proxies = result["proxies"] as List<*>
        val proxyMap = proxies.associateBy { (it as Map<*, *>)["name"] as String }

        // unix_domain_socket plugin
        val udsProxy = proxyMap["plugin_unix_domain_socket"] as Map<*, *>
        val udsPlugin = udsProxy["plugin"] as Map<*, *>
        assertEquals("unix_domain_socket", udsPlugin["type"])
        assertEquals("/var/run/docker.sock", udsPlugin["unixPath"])

        // http_proxy plugin
        val hpProxy = proxyMap["plugin_http_proxy"] as Map<*, *>
        val hpPlugin = hpProxy["plugin"] as Map<*, *>
        assertEquals("http_proxy", hpPlugin["type"])
        assertEquals("abc", hpPlugin["httpUser"])
        assertEquals("abc", hpPlugin["httpPassword"])

        // socks5 plugin
        val s5Proxy = proxyMap["plugin_socks5"] as Map<*, *>
        val s5Plugin = s5Proxy["plugin"] as Map<*, *>
        assertEquals("socks5", s5Plugin["type"])
        assertEquals("abc", s5Plugin["username"])
        assertEquals("abc", s5Plugin["password"])

        // static_file plugin
        val sfProxy = proxyMap["plugin_static_file"] as Map<*, *>
        val sfPlugin = sfProxy["plugin"] as Map<*, *>
        assertEquals("static_file", sfPlugin["type"])
        assertEquals("/var/www/blog", sfPlugin["localPath"])
        assertEquals("static", sfPlugin["stripPrefix"])
    }

    @Test
    fun parseToMap_fullExample_httpProxyDetails() {
        val toml = readFile("frpc_full_example.toml")
        val result = TomlParserUtil.parseToMap(toml)

        val proxies = result["proxies"] as List<*>
        val proxyMap = proxies.associateBy { (it as Map<*, *>)["name"] as String }

        val web01 = proxyMap["web01"] as Map<*, *>
        assertEquals("http", web01["type"])
        assertEquals("admin", web01["httpUser"])
        assertEquals("admin", web01["httpPassword"])
        assertEquals("web01", web01["subdomain"])
        assertEquals("example.com", web01["hostHeaderRewrite"])

        val customDomains = web01["customDomains"] as List<*>
        assertEquals(1, customDomains.size)
        assertEquals("web01.yourdomain.com", customDomains[0])

        val locations = web01["locations"] as List<*>
        assertEquals(2, locations.size)
        assertEquals("/", locations[0])
        assertEquals("/pic", locations[1])
    }

    @Test
    fun parseToMap_fullExample_stcpXtcpProxyFields() {
        val toml = readFile("frpc_full_example.toml")
        val result = TomlParserUtil.parseToMap(toml)

        val proxies = result["proxies"] as List<*>
        val proxyMap = proxies.associateBy { (it as Map<*, *>)["name"] as String }

        // stcp: secret_tcp
        val secretTcp = proxyMap["secret_tcp"] as Map<*, *>
        assertEquals("stcp", secretTcp["type"])
        assertEquals("abcdefg", secretTcp["secretKey"])
        val allowUsers = secretTcp["allowUsers"] as List<*>
        assertEquals(listOf("*"), allowUsers)

        // xtcp: p2p_tcp
        val p2pTcp = proxyMap["p2p_tcp"] as Map<*, *>
        assertEquals("xtcp", p2pTcp["type"])
        assertEquals("abcdefg", p2pTcp["secretKey"])
        val p2pAllowUsers = p2pTcp["allowUsers"] as List<*>
        assertEquals(listOf("user1", "user2"), p2pAllowUsers)

        // natTraversal sub-table within proxy
        val natTraversal = p2pTcp["natTraversal"] as Map<*, *>
        assertEquals(false, natTraversal["disableAssistedAddrs"])
    }

    @Test
    fun parseToMap_fullExample_visitors() {
        val toml = readFile("frpc_full_example.toml")
        val result = TomlParserUtil.parseToMap(toml)

        val visitors = result["visitors"] as List<*>
        assertEquals(3, visitors.size)

        // secret_tcp_visitor
        val stcpVisitor = visitors[0] as Map<*, *>
        assertEquals("secret_tcp_visitor", stcpVisitor["name"])
        assertEquals("stcp", stcpVisitor["type"])
        assertEquals("secret_tcp", stcpVisitor["serverName"])
        assertEquals("abcdefg", stcpVisitor["secretKey"])
        assertEquals("127.0.0.1", stcpVisitor["bindAddr"])
        assertEquals(9000L, stcpVisitor["bindPort"])

        // p2p_tcp_visitor
        val xtcpVisitor = visitors[1] as Map<*, *>
        assertEquals("p2p_tcp_visitor", xtcpVisitor["name"])
        assertEquals("xtcp", xtcpVisitor["type"])
        assertEquals("user1", xtcpVisitor["serverUser"])
        assertEquals("p2p_tcp", xtcpVisitor["serverName"])
        assertEquals("abcdefg", xtcpVisitor["secretKey"])
        assertEquals("127.0.0.1", xtcpVisitor["bindAddr"])
        assertEquals(9001L, xtcpVisitor["bindPort"])
        assertEquals(false, xtcpVisitor["keepTunnelOpen"])
        assertEquals(8L, xtcpVisitor["maxRetriesAnHour"])
        assertEquals(90L, xtcpVisitor["minRetryInterval"])

        // vnet-visitor with plugin
        val vnetVisitor = visitors[2] as Map<*, *>
        assertEquals("vnet-visitor", vnetVisitor["name"])
        assertEquals("stcp", vnetVisitor["type"])
        assertEquals("vnet-server", vnetVisitor["serverName"])
        assertEquals("your-secret-key", vnetVisitor["secretKey"])
        assertEquals(-1L, vnetVisitor["bindPort"])

        val vnetPlugin = vnetVisitor["plugin"] as Map<*, *>
        assertEquals("virtual_net", vnetPlugin["type"])
        assertEquals("100.86.0.1", vnetPlugin["destinationIP"])
    }

    // ==================== 3. Round-trip tests ====================

    @Test
    fun roundTrip_simpleConfig_preservesAllFields() {
        val toml = """
            serverAddr = "0.0.0.0"
            serverPort = 7000
            loginFailExit = true
            udpPacketSize = 1500

            [log]
            level = "info"
            maxDays = 3
            disablePrintColor = false

            [auth]
            method = "token"
            token = "12345678"

            [transport]
            poolCount = 5
            protocol = "tcp"

            [transport.tls]
            enable = true
        """.trimIndent()
        val original = TomlParserUtil.parseToMap(toml)
        val serialized = TomlParserUtil.mapToToml(original)
        val reparsed = TomlParserUtil.parseToMap(serialized)

        // Top-level
        assertEquals(original["serverAddr"], reparsed["serverAddr"])
        assertEquals(original["serverPort"], reparsed["serverPort"])
        assertEquals(original["loginFailExit"], reparsed["loginFailExit"])
        assertEquals(original["udpPacketSize"], reparsed["udpPacketSize"])

        // Nested tables
        val origLog = original["log"] as Map<*, *>
        val reparsedLog = reparsed["log"] as Map<*, *>
        assertEquals(origLog["level"], reparsedLog["level"])
        assertEquals(origLog["maxDays"], reparsedLog["maxDays"])
        assertEquals(origLog["disablePrintColor"], reparsedLog["disablePrintColor"])

        val origAuth = original["auth"] as Map<*, *>
        val reparsedAuth = reparsed["auth"] as Map<*, *>
        assertEquals(origAuth["method"], reparsedAuth["method"])
        assertEquals(origAuth["token"], reparsedAuth["token"])

        val origTransport = original["transport"] as Map<*, *>
        val reparsedTransport = reparsed["transport"] as Map<*, *>
        assertEquals(origTransport["poolCount"], reparsedTransport["poolCount"])
        assertEquals(origTransport["protocol"], reparsedTransport["protocol"])

        val origTls = origTransport["tls"] as Map<*, *>
        val reparsedTls = reparsedTransport["tls"] as Map<*, *>
        assertEquals(origTls["enable"], reparsedTls["enable"])
    }

    @Test
    fun roundTrip_withArrayOfTables_preservesEntries() {
        val toml = """
            name = "test"

            [[proxies]]
            name = "ssh"
            type = "tcp"
            localIP = "127.0.0.1"
            localPort = 22
            remotePort = 6001

            [[proxies]]
            name = "web"
            type = "http"
            localIP = "127.0.0.1"
            localPort = 80

            [[visitors]]
            name = "v1"
            type = "stcp"
            bindPort = 9000
        """.trimIndent()
        val original = TomlParserUtil.parseToMap(toml)
        val serialized = TomlParserUtil.mapToToml(original)
        val reparsed = TomlParserUtil.parseToMap(serialized)

        assertEquals(original["name"], reparsed["name"])

        val origProxies = original["proxies"] as List<*>
        val reparsedProxies = reparsed["proxies"] as List<*>
        assertEquals(origProxies.size, reparsedProxies.size)

        for (i in origProxies.indices) {
            val orig = origProxies[i] as Map<*, *>
            val rep = reparsedProxies[i] as Map<*, *>
            assertEquals(orig["name"], rep["name"])
            assertEquals(orig["type"], rep["type"])
            assertEquals(orig["localIP"], rep["localIP"])
            assertEquals(orig["localPort"], rep["localPort"])
        }

        val origVisitors = original["visitors"] as List<*>
        val reparsedVisitors = reparsed["visitors"] as List<*>
        assertEquals(origVisitors.size, reparsedVisitors.size)
        assertEquals(
            (origVisitors[0] as Map<*, *>)["name"],
            (reparsedVisitors[0] as Map<*, *>)["name"]
        )
    }

    @Test
    fun roundTrip_fullExample_topLevelAndCounts() {
        val toml = readFile("frpc_full_example.toml")
        val original = TomlParserUtil.parseToMap(toml)
        val serialized = TomlParserUtil.mapToToml(original)
        val reparsed = TomlParserUtil.parseToMap(serialized)

        // Top-level fields preserved
        assertEquals(original["clientID"], reparsed["clientID"])
        assertEquals(original["user"], reparsed["user"])
        assertEquals(original["serverAddr"], reparsed["serverAddr"])
        assertEquals(original["serverPort"], reparsed["serverPort"])
        assertEquals(original["loginFailExit"], reparsed["loginFailExit"])
        assertEquals(original["udpPacketSize"], reparsed["udpPacketSize"])

        // Nested tables preserved
        val origLog = original["log"] as Map<*, *>
        val reparsedLog = reparsed["log"] as Map<*, *>
        assertEquals(origLog["to"], reparsedLog["to"])
        assertEquals(origLog["level"], reparsedLog["level"])
        assertEquals(origLog["maxDays"], reparsedLog["maxDays"])

        val origAuth = original["auth"] as Map<*, *>
        val reparsedAuth = reparsed["auth"] as Map<*, *>
        assertEquals(origAuth["method"], reparsedAuth["method"])
        assertEquals(origAuth["token"], reparsedAuth["token"])

        // Proxy and visitor counts preserved
        val origProxies = original["proxies"] as List<*>
        val reparsedProxies = reparsed["proxies"] as List<*>
        assertEquals(origProxies.size, reparsedProxies.size)

        val origVisitors = original["visitors"] as List<*>
        val reparsedVisitors = reparsed["visitors"] as List<*>
        assertEquals(origVisitors.size, reparsedVisitors.size)
    }

    @Test
    fun roundTrip_fullExample_simpleProxiesPreserved() {
        val toml = readFile("frpc_full_example.toml")
        val original = TomlParserUtil.parseToMap(toml)
        val serialized = TomlParserUtil.mapToToml(original)
        val reparsed = TomlParserUtil.parseToMap(serialized)

        val origProxies = original["proxies"] as List<*>
        val reparsedProxies = reparsed["proxies"] as List<*>

        // Find proxies by name and compare simple fields
        val origMap = origProxies.associateBy { (it as Map<*, *>)["name"] as String }
        val repMap = reparsedProxies.associateBy { (it as Map<*, *>)["name"] as String }

        // These proxies have no sub-tables, so round-trip should be clean
        for (name in listOf("ssh_random", "dns")) {
            val orig = origMap[name] as Map<*, *>
            val rep = repMap[name] as Map<*, *>
            assertEquals("Proxy $name type mismatch", orig["type"], rep["type"])
            assertEquals("Proxy $name localIP mismatch", orig["localIP"], rep["localIP"])
            assertEquals("Proxy $name localPort mismatch", orig["localPort"], rep["localPort"])
            assertEquals("Proxy $name remotePort mismatch", orig["remotePort"], rep["remotePort"])
        }
    }

    // ==================== 4. Edge cases ====================

    @Test
    fun parseToMap_emptyString_returnsEmptyMap() {
        val result = TomlParserUtil.parseToMap("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun parseToMap_minimalConfig_singleField() {
        val toml = """serverAddr = "127.0.0.1"""".trimIndent()
        val result = TomlParserUtil.parseToMap(toml)
        assertEquals(1, result.size)
        assertEquals("127.0.0.1", result["serverAddr"])
    }

    @Test
    fun parseToMap_booleanValues_trueAndFalse() {
        val toml = """
            flagTrue = true
            flagFalse = false
        """.trimIndent()
        val result = TomlParserUtil.parseToMap(toml)
        assertEquals(true, result["flagTrue"])
        assertEquals(false, result["flagFalse"])
        assertTrue(result["flagTrue"] is Boolean)
        assertTrue(result["flagFalse"] is Boolean)
    }

    @Test
    fun parseToMap_integerValues_positiveNegativeZero() {
        val toml = """
            positive = 42
            negative = -1
            zero = 0
        """.trimIndent()
        val result = TomlParserUtil.parseToMap(toml)
        assertEquals(42L, result["positive"])
        assertEquals(-1L, result["negative"])
        assertEquals(0L, result["zero"])
        assertTrue(result["positive"] is Long)
    }

    @Test
    fun parseToMap_doubleValues() {
        val toml = """
            pi = 3.14
            negative = -2.5
        """.trimIndent()
        val result = TomlParserUtil.parseToMap(toml)
        assertEquals(3.14, result["pi"] as Double, 0.001)
        assertEquals(-2.5, result["negative"] as Double, 0.001)
        assertTrue(result["pi"] is Double)
    }

    @Test
    fun parseToMap_stringValues_quotedAndUnquoted() {
        val toml = """
            simple = "hello"
            withSpaces = "hello world"
            empty = ""
        """.trimIndent()
        val result = TomlParserUtil.parseToMap(toml)
        assertEquals("hello", result["simple"])
        assertEquals("hello world", result["withSpaces"])
        assertEquals("", result["empty"])
    }

    @Test
    fun parseToMap_stringList_multipleItems() {
        val toml = """items = ["alpha", "beta", "gamma"]""".trimIndent()
        val result = TomlParserUtil.parseToMap(toml)
        val items = result["items"] as List<*>
        assertEquals(3, items.size)
        assertEquals("alpha", items[0])
        assertEquals("beta", items[1])
        assertEquals("gamma", items[2])
    }

    @Test
    fun parseToMap_emptyList_returnsEmptyList() {
        val toml = """items = []""".trimIndent()
        val result = TomlParserUtil.parseToMap(toml)
        val items = result["items"] as List<*>
        assertTrue(items.isEmpty())
    }

    @Test
    fun parseToMap_nestedTable_withBrackets() {
        val toml = """
            [database]
            host = "localhost"
            port = 5432
            enabled = true
        """.trimIndent()
        val result = TomlParserUtil.parseToMap(toml)
        val db = result["database"] as Map<*, *>
        assertEquals("localhost", db["host"])
        assertEquals(5432L, db["port"])
        assertEquals(true, db["enabled"])
    }

    @Test
    fun parseToMap_deeplyNestedTable() {
        val toml = """
            [outer.inner]
            value = "deep"
        """.trimIndent()
        val result = TomlParserUtil.parseToMap(toml)
        val outer = result["outer"] as Map<*, *>
        val inner = outer["inner"] as Map<*, *>
        assertEquals("deep", inner["value"])
    }

    @Test
    fun parseToMap_arrayOfTables_multipleEntries() {
        val toml = """
            [[items]]
            name = "first"
            value = 1

            [[items]]
            name = "second"
            value = 2

            [[items]]
            name = "third"
            value = 3
        """.trimIndent()
        val result = TomlParserUtil.parseToMap(toml)
        val items = result["items"] as List<*>
        assertEquals(3, items.size)

        assertEquals("first", (items[0] as Map<*, *>)["name"])
        assertEquals(1L, (items[0] as Map<*, *>)["value"])
        assertEquals("second", (items[1] as Map<*, *>)["name"])
        assertEquals(2L, (items[1] as Map<*, *>)["value"])
        assertEquals("third", (items[2] as Map<*, *>)["name"])
        assertEquals(3L, (items[2] as Map<*, *>)["value"])
    }

    @Test
    fun parseToMap_arrayOfTablesWithSubTable() {
        val toml = """
            [[proxies]]
            name = "test"
            type = "tcp"

            [proxies.plugin]
            type = "unix_domain_socket"
            unixPath = "/var/run/docker.sock"
        """.trimIndent()
        val result = TomlParserUtil.parseToMap(toml)
        val proxies = result["proxies"] as List<*>
        assertEquals(1, proxies.size)

        val proxy = proxies[0] as Map<*, *>
        assertEquals("test", proxy["name"])
        assertEquals("tcp", proxy["type"])

        val plugin = proxy["plugin"] as Map<*, *>
        assertEquals("unix_domain_socket", plugin["type"])
        assertEquals("/var/run/docker.sock", plugin["unixPath"])
    }

    // ==================== 5. mapToToml serialization tests ====================

    @Test
    fun mapToToml_simpleKeyValues() {
        val data = mapOf<String, Any?>(
            "serverAddr" to "0.0.0.0",
            "serverPort" to 7000L,
            "enabled" to true
        )
        val toml = TomlParserUtil.mapToToml(data)
        assertTrue(toml.contains("serverAddr = \"0.0.0.0\""))
        assertTrue(toml.contains("serverPort = 7000"))
        assertTrue(toml.contains("enabled = true"))
    }

    @Test
    fun mapToToml_nestedTable() {
        val data = mapOf<String, Any?>(
            "log" to mapOf(
                "level" to "debug",
                "maxDays" to 3L
            )
        )
        val toml = TomlParserUtil.mapToToml(data)
        assertTrue(toml.contains("[log]"))
        assertTrue(toml.contains("level = \"debug\""))
        assertTrue(toml.contains("maxDays = 3"))
    }

    @Test
    fun mapToToml_arrayOfTables() {
        val data = mapOf<String, Any?>(
            "proxies" to listOf(
                mapOf<String, Any?>("name" to "ssh", "type" to "tcp"),
                mapOf<String, Any?>("name" to "web", "type" to "http")
            )
        )
        val toml = TomlParserUtil.mapToToml(data)
        assertTrue(toml.contains("[[proxies]]"))
        assertTrue(toml.contains("name = \"ssh\""))
        assertTrue(toml.contains("name = \"web\""))
    }

    @Test
    fun mapToToml_stringList() {
        val data = mapOf<String, Any?>(
            "domains" to listOf("a.com", "b.com")
        )
        val toml = TomlParserUtil.mapToToml(data)
        assertTrue(toml.contains("domains = [\"a.com\", \"b.com\"]"))
    }

    @Test
    fun mapToToml_roundTrip_simpleKeyValues() {
        val original = mapOf<String, Any?>(
            "name" to "test",
            "port" to 8080L,
            "enabled" to true,
            "ratio" to 1.5
        )
        val toml = TomlParserUtil.mapToToml(original)
        val reparsed = TomlParserUtil.parseToMap(toml)
        assertEquals(original["name"], reparsed["name"])
        assertEquals(original["port"], reparsed["port"])
        assertEquals(original["enabled"], reparsed["enabled"])
        assertEquals(original["ratio"], reparsed["ratio"])
    }
}
