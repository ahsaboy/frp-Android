package io.github.acedroidx.frp.config

import io.github.acedroidx.frp.FrpType

object FrpcSchema {

    fun get(): ConfigSchema = ConfigSchema(
        type = FrpType.FRPC,
        sections = listOf(
            basicSection(),
            authSection(),
            logSection(),
            webServerSection(),
            transportSection(),
            tlsSection(),
            quicSection(),
        ),
        proxyTypes = proxyTypes(),
        visitorTypes = visitorTypes(),
        pluginTypes = pluginTypes(),
    )

    // region Sections

    private fun basicSection() = ConfigSection(
        id = "basic",
        title = "基本设置",
        fields = listOf(
            FieldSchema("serverAddr", FieldType.STRING, "服务器地址", required = true, hint = "如 1.2.3.4 或 example.com"),
            FieldSchema("serverPort", FieldType.INT, "服务器端口", defaultValue = 7000, required = true),
            FieldSchema("user", FieldType.STRING, "用户名", hint = "代理名将变为 {user}.{proxy}"),
            FieldSchema("clientID", FieldType.STRING, "客户端 ID"),
            FieldSchema("loginFailExit", FieldType.BOOL, "首次登录失败则退出", defaultValue = true),
            FieldSchema("natHoleStunServer", FieldType.STRING, "STUN 服务器", defaultValue = "stun.easyvoip.com:3478"),
            FieldSchema("dnsServer", FieldType.STRING, "DNS 服务器", hint = "如 8.8.8.8"),
            FieldSchema("udpPacketSize", FieldType.INT, "UDP 包大小(字节)", defaultValue = 1500),
            FieldSchema("start", FieldType.STRING_LIST, "启动的代理列表", hint = "留空表示全部启动"),
        ),
    )

    private fun authSection() = ConfigSection(
        id = "auth",
        title = "认证",
        fields = listOf(
            FieldSchema("auth.method", FieldType.ENUM, "认证方式", defaultValue = "token", enumOptions = listOf("token", "oidc")),
            FieldSchema("auth.additionalScopes", FieldType.STRING_LIST, "附加认证范围", hint = "HeartBeats, NewWorkConns"),
            FieldSchema("auth.token", FieldType.STRING, "Token", visibleWhen = { SchemaHelpers.getValueByPath(it, "auth.method") == "token" }),
            FieldSchema("auth.tokenSource.type", FieldType.ENUM, "Token 来源", enumOptions = listOf("file", "exec"), visibleWhen = { SchemaHelpers.getValueByPath(it, "auth.method") == "token" }),
            FieldSchema("auth.tokenSource.file.path", FieldType.STRING, "Token 文件路径", visibleWhen = { SchemaHelpers.getValueByPath(it, "auth.tokenSource.type") == "file" }),
            FieldSchema("auth.oidc.clientID", FieldType.STRING, "OIDC Client ID", visibleWhen = { SchemaHelpers.getValueByPath(it, "auth.method") == "oidc" }),
            FieldSchema("auth.oidc.clientSecret", FieldType.STRING, "OIDC Client Secret", visibleWhen = { SchemaHelpers.getValueByPath(it, "auth.method") == "oidc" }),
            FieldSchema("auth.oidc.audience", FieldType.STRING, "OIDC Audience", visibleWhen = { SchemaHelpers.getValueByPath(it, "auth.method") == "oidc" }),
            FieldSchema("auth.oidc.scope", FieldType.STRING, "OIDC Scope", visibleWhen = { SchemaHelpers.getValueByPath(it, "auth.method") == "oidc" }),
            FieldSchema("auth.oidc.tokenEndpointURL", FieldType.STRING, "OIDC Token Endpoint URL", visibleWhen = { SchemaHelpers.getValueByPath(it, "auth.method") == "oidc" }),
            FieldSchema("auth.oidc.trustedCaFile", FieldType.STRING, "OIDC CA 证书文件", visibleWhen = { SchemaHelpers.getValueByPath(it, "auth.method") == "oidc" }),
            FieldSchema("auth.oidc.insecureSkipVerify", FieldType.BOOL, "跳过 TLS 验证", defaultValue = false, visibleWhen = { SchemaHelpers.getValueByPath(it, "auth.method") == "oidc" }),
            FieldSchema("auth.oidc.additionalEndpointParams", FieldType.MAP_STRING, "OIDC 附加参数", visibleWhen = { SchemaHelpers.getValueByPath(it, "auth.method") == "oidc" }),
            FieldSchema("auth.oidc.proxyURL", FieldType.STRING, "OIDC 代理 URL", visibleWhen = { SchemaHelpers.getValueByPath(it, "auth.method") == "oidc" }),
        ),
    )

    private fun logSection() = ConfigSection(
        id = "log",
        title = "日志",
        fields = listOf(
            FieldSchema("log.to", FieldType.STRING, "日志输出", defaultValue = "console", hint = "console 或文件路径如 ./frpc.log"),
            FieldSchema("log.level", FieldType.ENUM, "日志级别", defaultValue = "info", enumOptions = listOf("trace", "debug", "info", "warn", "error")),
            FieldSchema("log.maxDays", FieldType.INT, "最大保留天数", defaultValue = 3),
            FieldSchema("log.disablePrintColor", FieldType.BOOL, "禁用日志颜色", defaultValue = false),
        ),
    )

    private fun webServerSection() = ConfigSection(
        id = "webserver",
        title = "Web 管理",
        fields = listOf(
            FieldSchema("webServer.addr", FieldType.STRING, "监听地址", defaultValue = "127.0.0.1"),
            FieldSchema("webServer.port", FieldType.INT, "监听端口", defaultValue = 0, hint = "0 = 禁用"),
            FieldSchema("webServer.user", FieldType.STRING, "用户名"),
            FieldSchema("webServer.password", FieldType.STRING, "密码"),
            FieldSchema("webServer.pprofEnable", FieldType.BOOL, "启用 pprof", defaultValue = false),
            FieldSchema("webServer.assetsDir", FieldType.STRING, "静态资源目录", hint = "默认使用内置资源"),
        ),
    )

    private fun transportSection() = ConfigSection(
        id = "transport",
        title = "传输",
        fields = listOf(
            FieldSchema("transport.protocol", FieldType.ENUM, "协议", defaultValue = "tcp", enumOptions = listOf("tcp", "kcp", "quic", "websocket", "wss")),
            FieldSchema("transport.wireProtocol", FieldType.ENUM, "线路协议", defaultValue = "v1", enumOptions = listOf("v1", "v2")),
            FieldSchema("transport.dialServerTimeout", FieldType.INT, "连接超时(秒)", defaultValue = 10),
            FieldSchema("transport.dialServerKeepalive", FieldType.INT, "连接保活(秒)", defaultValue = 7200),
            FieldSchema("transport.connectServerLocalIP", FieldType.STRING, "本地绑定 IP"),
            FieldSchema("transport.proxyURL", FieldType.STRING, "代理 URL", hint = "如 http://user:pass@host:port"),
            FieldSchema("transport.poolCount", FieldType.INT, "连接池大小", defaultValue = 1),
            FieldSchema("transport.tcpMux", FieldType.BOOL, "TCP 多路复用", defaultValue = true),
            FieldSchema("transport.tcpMuxKeepaliveInterval", FieldType.INT, "多路复用保活间隔(秒)", defaultValue = 30),
            FieldSchema("transport.heartbeatInterval", FieldType.INT, "心跳间隔(秒)", defaultValue = -1, hint = "-1 = 根据 tcpMux 自动"),
            FieldSchema("transport.heartbeatTimeout", FieldType.INT, "心跳超时(秒)", defaultValue = -1, hint = "-1 = 根据 tcpMux 自动"),
        ),
    )

    private fun tlsSection() = ConfigSection(
        id = "tls",
        title = "TLS",
        fields = listOf(
            FieldSchema("transport.tls.enable", FieldType.BOOL, "启用 TLS", defaultValue = true),
            FieldSchema("transport.tls.certFile", FieldType.STRING, "客户端证书", visibleWhen = { SchemaHelpers.getValueByPath(it, "transport.tls.enable") == true }),
            FieldSchema("transport.tls.keyFile", FieldType.STRING, "客户端私钥", visibleWhen = { SchemaHelpers.getValueByPath(it, "transport.tls.enable") == true }),
            FieldSchema("transport.tls.trustedCaFile", FieldType.STRING, "CA 证书", visibleWhen = { SchemaHelpers.getValueByPath(it, "transport.tls.enable") == true }),
            FieldSchema("transport.tls.serverName", FieldType.STRING, "服务器名称", visibleWhen = { SchemaHelpers.getValueByPath(it, "transport.tls.enable") == true }),
            FieldSchema("transport.tls.disableCustomTLSFirstByte", FieldType.BOOL, "禁用自定义首字节", defaultValue = true, visibleWhen = { SchemaHelpers.getValueByPath(it, "transport.tls.enable") == true }),
        ),
    )

    private fun quicSection() = ConfigSection(
        id = "quic",
        title = "QUIC",
        fields = listOf(
            FieldSchema("transport.quic.keepalivePeriod", FieldType.INT, "保活周期(秒)", defaultValue = 10, visibleWhen = { SchemaHelpers.getValueByPath(it, "transport.protocol") == "quic" }),
            FieldSchema("transport.quic.maxIdleTimeout", FieldType.INT, "最大空闲超时(秒)", defaultValue = 30, visibleWhen = { SchemaHelpers.getValueByPath(it, "transport.protocol") == "quic" }),
            FieldSchema("transport.quic.maxIncomingStreams", FieldType.INT, "最大并发流", defaultValue = 100000, visibleWhen = { SchemaHelpers.getValueByPath(it, "transport.protocol") == "quic" }),
        ),
    )

    // endregion

    // region Proxy Types

    private fun proxyBaseFields(): List<FieldSchema> = listOf(
        FieldSchema("name", FieldType.STRING, "代理名称", required = true),
        FieldSchema("type", FieldType.ENUM, "代理类型", required = true, enumOptions = listOf("tcp", "udp", "http", "https", "stcp", "xtcp", "sudp", "tcpmux")),
        FieldSchema("enabled", FieldType.BOOL, "启用", defaultValue = true),
        FieldSchema("localIP", FieldType.STRING, "本地 IP", defaultValue = "127.0.0.1"),
        FieldSchema("localPort", FieldType.INT, "本地端口"),
        FieldSchema("annotations", FieldType.MAP_STRING, "注释"),
        FieldSchema("metadatas", FieldType.MAP_STRING, "元数据"),
    )

    private fun proxyTransportFields(): List<FieldSchema> = listOf(
        FieldSchema("transport.useEncryption", FieldType.BOOL, "加密传输", defaultValue = false),
        FieldSchema("transport.useCompression", FieldType.BOOL, "压缩传输", defaultValue = false),
        FieldSchema("transport.bandwidthLimit", FieldType.STRING, "带宽限制", hint = "如 1MB、500KB"),
        FieldSchema("transport.bandwidthLimitMode", FieldType.ENUM, "限速模式", defaultValue = "client", enumOptions = listOf("client", "server")),
        FieldSchema("transport.proxyProtocolVersion", FieldType.ENUM, "代理协议版本", enumOptions = listOf("", "v1", "v2")),
    )

    private fun proxyHealthCheckFields(): List<FieldSchema> = listOf(
        FieldSchema("healthCheck.type", FieldType.ENUM, "健康检查类型", enumOptions = listOf("", "tcp", "http")),
        FieldSchema("healthCheck.timeoutSeconds", FieldType.INT, "超时(秒)", defaultValue = 3, visibleWhen = { (SchemaHelpers.getValueByPath(it, "healthCheck.type") as? String).orEmpty().isNotEmpty() }),
        FieldSchema("healthCheck.maxFailed", FieldType.INT, "最大失败次数", defaultValue = 1, visibleWhen = { (SchemaHelpers.getValueByPath(it, "healthCheck.type") as? String).orEmpty().isNotEmpty() }),
        FieldSchema("healthCheck.intervalSeconds", FieldType.INT, "检查间隔(秒)", defaultValue = 10, visibleWhen = { (SchemaHelpers.getValueByPath(it, "healthCheck.type") as? String).orEmpty().isNotEmpty() }),
        FieldSchema("healthCheck.path", FieldType.STRING, "HTTP 检查路径", visibleWhen = { SchemaHelpers.getValueByPath(it, "healthCheck.type") == "http" }),
        FieldSchema("healthCheck.httpHeaders", FieldType.MAP_STRING, "HTTP 检查请求头", visibleWhen = { SchemaHelpers.getValueByPath(it, "healthCheck.type") == "http" }),
    )

    private fun proxyLoadBalancerFields(): List<FieldSchema> = listOf(
        FieldSchema("loadBalancer.group", FieldType.STRING, "负载均衡组"),
        FieldSchema("loadBalancer.groupKey", FieldType.STRING, "组密钥"),
    )

    private fun proxyTypes(): List<ProxyTypeSchema> {
        val base = proxyBaseFields() + proxyTransportFields() + proxyHealthCheckFields() + proxyLoadBalancerFields()

        return listOf(
            ProxyTypeSchema("tcp", "TCP", base, listOf(
                FieldSchema("remotePort", FieldType.INT, "远程端口"),
            )),
            ProxyTypeSchema("udp", "UDP", base, listOf(
                FieldSchema("remotePort", FieldType.INT, "远程端口"),
            )),
            ProxyTypeSchema("http", "HTTP", base + domainFields(), listOf(
                FieldSchema("locations", FieldType.STRING_LIST, "路径"),
                FieldSchema("httpUser", FieldType.STRING, "HTTP 用户名"),
                FieldSchema("httpPassword", FieldType.STRING, "HTTP 密码"),
                FieldSchema("hostHeaderRewrite", FieldType.STRING, "重写 Host 头"),
                FieldSchema("routeByHTTPUser", FieldType.STRING, "按用户路由"),
                FieldSchema("requestHeaders.set", FieldType.MAP_STRING, "请求头"),
                FieldSchema("responseHeaders.set", FieldType.MAP_STRING, "响应头"),
            )),
            ProxyTypeSchema("https", "HTTPS", base + domainFields(), emptyList()),
            ProxyTypeSchema("tcpmux", "TCPMUX", base + domainFields(), listOf(
                FieldSchema("httpUser", FieldType.STRING, "HTTP 用户名"),
                FieldSchema("httpPassword", FieldType.STRING, "HTTP 密码"),
                FieldSchema("routeByHTTPUser", FieldType.STRING, "按用户路由"),
                FieldSchema("multiplexer", FieldType.ENUM, "复用器", enumOptions = listOf("httpconnect")),
            )),
            ProxyTypeSchema("stcp", "STCP", base, secretFields()),
            ProxyTypeSchema("xtcp", "XTCP", base, secretFields() + listOf(
                FieldSchema("natTraversal.disableAssistedAddrs", FieldType.BOOL, "禁用辅助地址", defaultValue = false),
            )),
            ProxyTypeSchema("sudp", "SUDP", base, secretFields()),
        )
    }

    private fun domainFields(): List<FieldSchema> = listOf(
        FieldSchema("customDomains", FieldType.STRING_LIST, "自定义域名"),
        FieldSchema("subdomain", FieldType.STRING, "子域名"),
    )

    private fun secretFields(): List<FieldSchema> = listOf(
        FieldSchema("secretKey", FieldType.STRING, "密钥"),
        FieldSchema("allowUsers", FieldType.STRING_LIST, "允许的用户", hint = "* 表示所有用户"),
    )

    // endregion

    // region Visitor Types

    private fun visitorBaseFields(): List<FieldSchema> = listOf(
        FieldSchema("name", FieldType.STRING, "访客名称", required = true),
        FieldSchema("type", FieldType.ENUM, "访客类型", required = true, enumOptions = listOf("stcp", "xtcp", "sudp")),
        FieldSchema("enabled", FieldType.BOOL, "启用", defaultValue = true),
        FieldSchema("serverName", FieldType.STRING, "服务端代理名称", required = true),
        FieldSchema("serverUser", FieldType.STRING, "服务端用户"),
        FieldSchema("secretKey", FieldType.STRING, "密钥"),
        FieldSchema("bindAddr", FieldType.STRING, "绑定地址", defaultValue = "127.0.0.1"),
        FieldSchema("bindPort", FieldType.INT, "绑定端口"),
        FieldSchema("transport.useEncryption", FieldType.BOOL, "加密传输", defaultValue = false),
        FieldSchema("transport.useCompression", FieldType.BOOL, "压缩传输", defaultValue = false),
    )

    private fun visitorTypes(): List<VisitorTypeSchema> {
        val base = visitorBaseFields()
        return listOf(
            VisitorTypeSchema("stcp", "STCP", base, emptyList()),
            VisitorTypeSchema("sudp", "SUDP", base, emptyList()),
            VisitorTypeSchema("xtcp", "XTCP", base, listOf(
                FieldSchema("protocol", FieldType.ENUM, "协议", defaultValue = "quic", enumOptions = listOf("kcp", "quic")),
                FieldSchema("keepTunnelOpen", FieldType.BOOL, "保持隧道", defaultValue = false),
                FieldSchema("maxRetriesAnHour", FieldType.INT, "每小时最大重试", defaultValue = 8),
                FieldSchema("minRetryInterval", FieldType.INT, "最小重试间隔(秒)", defaultValue = 90),
                FieldSchema("fallbackTo", FieldType.STRING, "降级到"),
                FieldSchema("fallbackTimeoutMs", FieldType.INT, "降级超时(ms)", defaultValue = 1000),
                FieldSchema("natTraversal.disableAssistedAddrs", FieldType.BOOL, "禁用辅助地址", defaultValue = false),
            )),
        )
    }

    // endregion

    // region Plugin Types

    private fun pluginTypes(): List<PluginTypeSchema> = listOf(
        PluginTypeSchema("http2https", "HTTP → HTTPS", listOf(
            FieldSchema("localAddr", FieldType.STRING, "本地地址", required = true, hint = "如 127.0.0.1:443"),
            FieldSchema("hostHeaderRewrite", FieldType.STRING, "重写 Host 头"),
            FieldSchema("requestHeaders.set", FieldType.MAP_STRING, "请求头"),
        )),
        PluginTypeSchema("https2http", "HTTPS → HTTP", listOf(
            FieldSchema("localAddr", FieldType.STRING, "本地地址", required = true),
            FieldSchema("hostHeaderRewrite", FieldType.STRING, "重写 Host 头"),
            FieldSchema("requestHeaders.set", FieldType.MAP_STRING, "请求头"),
            FieldSchema("crtPath", FieldType.STRING, "证书路径"),
            FieldSchema("keyPath", FieldType.STRING, "私钥路径"),
            FieldSchema("enableHTTP2", FieldType.BOOL, "启用 HTTP/2", defaultValue = false),
        )),
        PluginTypeSchema("https2https", "HTTPS → HTTPS", listOf(
            FieldSchema("localAddr", FieldType.STRING, "本地地址", required = true),
            FieldSchema("hostHeaderRewrite", FieldType.STRING, "重写 Host 头"),
            FieldSchema("requestHeaders.set", FieldType.MAP_STRING, "请求头"),
            FieldSchema("crtPath", FieldType.STRING, "证书路径"),
            FieldSchema("keyPath", FieldType.STRING, "私钥路径"),
            FieldSchema("enableHTTP2", FieldType.BOOL, "启用 HTTP/2", defaultValue = false),
        )),
        PluginTypeSchema("http2http", "HTTP → HTTP", listOf(
            FieldSchema("localAddr", FieldType.STRING, "本地地址", required = true),
            FieldSchema("hostHeaderRewrite", FieldType.STRING, "重写 Host 头"),
            FieldSchema("requestHeaders.set", FieldType.MAP_STRING, "请求头"),
        )),
        PluginTypeSchema("http_proxy", "HTTP 代理", listOf(
            FieldSchema("httpUser", FieldType.STRING, "用户名"),
            FieldSchema("httpPassword", FieldType.STRING, "密码"),
        )),
        PluginTypeSchema("socks5", "SOCKS5", listOf(
            FieldSchema("username", FieldType.STRING, "用户名"),
            FieldSchema("password", FieldType.STRING, "密码"),
        )),
        PluginTypeSchema("static_file", "静态文件", listOf(
            FieldSchema("localPath", FieldType.STRING, "本地路径", required = true),
            FieldSchema("stripPrefix", FieldType.STRING, "去除前缀"),
            FieldSchema("httpUser", FieldType.STRING, "用户名"),
            FieldSchema("httpPassword", FieldType.STRING, "密码"),
        )),
        PluginTypeSchema("unix_domain_socket", "Unix Socket", listOf(
            FieldSchema("unixPath", FieldType.STRING, "Socket 路径", required = true),
        )),
        PluginTypeSchema("tls2raw", "TLS → Raw", listOf(
            FieldSchema("localAddr", FieldType.STRING, "本地地址", required = true),
            FieldSchema("crtPath", FieldType.STRING, "证书路径"),
            FieldSchema("keyPath", FieldType.STRING, "私钥路径"),
        )),
        PluginTypeSchema("virtual_net", "虚拟网络", emptyList()),
    )

    // endregion
}
