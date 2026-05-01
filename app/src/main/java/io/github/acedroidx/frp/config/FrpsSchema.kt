package io.github.acedroidx.frp.config

import io.github.acedroidx.frp.FrpType

object FrpsSchema {

    fun get(): ConfigSchema = ConfigSchema(
        type = FrpType.FRPS,
        sections = listOf(
            basicSection(),
            portSection(),
            authSection(),
            transportSection(),
            tlsSection(),
            quicSection(),
            sshSection(),
            webServerSection(),
            logSection(),
            advancedSection(),
        ),
    )

    private fun basicSection() = ConfigSection(
        id = "basic",
        title = "基本设置",
        fields = listOf(
            FieldSchema("bindAddr", FieldType.STRING, "监听地址", defaultValue = "0.0.0.0"),
            FieldSchema("bindPort", FieldType.INT, "监听端口", defaultValue = 7000, required = true),
            FieldSchema("kcpBindPort", FieldType.INT, "KCP 端口", hint = "0 = 禁用"),
            FieldSchema("quicBindPort", FieldType.INT, "QUIC 端口", hint = "0 = 禁用"),
            FieldSchema("proxyBindAddr", FieldType.STRING, "代理监听地址", hint = "默认与监听地址相同"),
        ),
    )

    private fun portSection() = ConfigSection(
        id = "ports",
        title = "端口设置",
        fields = listOf(
            FieldSchema("vhostHTTPPort", FieldType.INT, "HTTP 虚拟主机端口", hint = "0 = 禁用"),
            FieldSchema("vhostHTTPSPort", FieldType.INT, "HTTPS 虚拟主机端口", hint = "0 = 禁用"),
            FieldSchema("vhostHTTPTimeout", FieldType.INT, "HTTP 超时(秒)", defaultValue = 60),
            FieldSchema("tcpmuxHTTPConnectPort", FieldType.INT, "TCPMUX 端口", hint = "0 = 禁用"),
            FieldSchema("tcpmuxPassthrough", FieldType.BOOL, "TCPMUX 透传", defaultValue = false),
            FieldSchema("subDomainHost", FieldType.STRING, "子域名根域名", hint = "如 frps.com"),
            FieldSchema("custom404Page", FieldType.STRING, "自定义 404 页面路径"),
        ),
    )

    private fun authSection() = ConfigSection(
        id = "auth",
        title = "认证",
        fields = listOf(
            FieldSchema("auth.method", FieldType.ENUM, "认证方式", defaultValue = "token", enumOptions = listOf("token", "oidc")),
            FieldSchema("auth.token", FieldType.STRING, "Token", visibleWhen = { SchemaHelpers.getValueByPath(it, "auth.method") == "token" }),
            FieldSchema("auth.tokenSource.type", FieldType.ENUM, "Token 来源", enumOptions = listOf("file", "exec"), visibleWhen = { SchemaHelpers.getValueByPath(it, "auth.method") == "token" }),
            FieldSchema("auth.tokenSource.file.path", FieldType.STRING, "Token 文件路径", visibleWhen = { SchemaHelpers.getValueByPath(it, "auth.tokenSource.type") == "file" }),
            FieldSchema("auth.tokenSource.exec.command", FieldType.STRING, "执行命令", visibleWhen = { SchemaHelpers.getValueByPath(it, "auth.tokenSource.type") == "exec" }),
            FieldSchema("auth.tokenSource.exec.args", FieldType.STRING_LIST, "命令参数", visibleWhen = { SchemaHelpers.getValueByPath(it, "auth.tokenSource.type") == "exec" }),
            FieldSchema("auth.tokenSource.exec.env", FieldType.MAP_STRING, "环境变量", visibleWhen = { SchemaHelpers.getValueByPath(it, "auth.tokenSource.type") == "exec" }),
            FieldSchema("auth.oidc.issuer", FieldType.STRING, "OIDC Issuer", visibleWhen = { SchemaHelpers.getValueByPath(it, "auth.method") == "oidc" }),
            FieldSchema("auth.oidc.audience", FieldType.STRING, "OIDC Audience", visibleWhen = { SchemaHelpers.getValueByPath(it, "auth.method") == "oidc" }),
            FieldSchema("auth.oidc.skipExpiryCheck", FieldType.BOOL, "跳过过期检查", defaultValue = false, visibleWhen = { SchemaHelpers.getValueByPath(it, "auth.method") == "oidc" }),
            FieldSchema("auth.oidc.skipIssuerCheck", FieldType.BOOL, "跳过 Issuer 检查", defaultValue = false, visibleWhen = { SchemaHelpers.getValueByPath(it, "auth.method") == "oidc" }),
        ),
    )

    private fun transportSection() = ConfigSection(
        id = "transport",
        title = "传输",
        fields = listOf(
            FieldSchema("transport.tcpMux", FieldType.BOOL, "TCP 多路复用", defaultValue = true),
            FieldSchema("transport.tcpMuxKeepaliveInterval", FieldType.INT, "多路复用保活间隔(秒)", defaultValue = 30),
            FieldSchema("transport.tcpKeepalive", FieldType.INT, "TCP 保活(秒)", defaultValue = 7200),
            FieldSchema("transport.maxPoolCount", FieldType.INT, "最大连接池", defaultValue = 5),
            FieldSchema("transport.heartbeatTimeout", FieldType.INT, "心跳超时(秒)", defaultValue = -1, hint = "-1 = 根据 tcpMux 自动"),
        ),
    )

    private fun tlsSection() = ConfigSection(
        id = "tls",
        title = "TLS",
        fields = listOf(
            FieldSchema("transport.tls.force", FieldType.BOOL, "强制 TLS", defaultValue = false),
            FieldSchema("transport.tls.certFile", FieldType.STRING, "证书文件"),
            FieldSchema("transport.tls.keyFile", FieldType.STRING, "私钥文件"),
            FieldSchema("transport.tls.trustedCaFile", FieldType.STRING, "CA 证书"),
        ),
    )

    private fun quicSection() = ConfigSection(
        id = "quic",
        title = "QUIC",
        fields = listOf(
            FieldSchema("transport.quic.keepalivePeriod", FieldType.INT, "保活周期(秒)", defaultValue = 10),
            FieldSchema("transport.quic.maxIdleTimeout", FieldType.INT, "最大空闲超时(秒)", defaultValue = 30),
            FieldSchema("transport.quic.maxIncomingStreams", FieldType.INT, "最大并发流", defaultValue = 100000),
        ),
    )

    private fun sshSection() = ConfigSection(
        id = "ssh",
        title = "SSH 隧道网关",
        fields = listOf(
            FieldSchema("sshTunnelGateway.bindPort", FieldType.INT, "SSH 端口", hint = "0 = 禁用"),
            FieldSchema("sshTunnelGateway.privateKeyFile", FieldType.STRING, "私钥文件"),
            FieldSchema("sshTunnelGateway.autoGenPrivateKeyPath", FieldType.STRING, "自动生成密钥路径", defaultValue = "./.autogen_ssh_key"),
            FieldSchema("sshTunnelGateway.authorizedKeysFile", FieldType.STRING, "授权密钥文件"),
        ),
    )

    private fun webServerSection() = ConfigSection(
        id = "webserver",
        title = "Web 管理",
        fields = listOf(
            FieldSchema("webServer.addr", FieldType.STRING, "监听地址", defaultValue = "127.0.0.1"),
            FieldSchema("webServer.port", FieldType.INT, "监听端口", hint = "0 = 禁用"),
            FieldSchema("webServer.user", FieldType.STRING, "用户名"),
            FieldSchema("webServer.password", FieldType.STRING, "密码"),
            FieldSchema("webServer.pprofEnable", FieldType.BOOL, "启用 pprof", defaultValue = false),
            FieldSchema("webServer.assetsDir", FieldType.STRING, "静态资源目录", hint = "默认使用内置资源"),
            FieldSchema("webServer.tls.certFile", FieldType.STRING, "TLS 证书"),
            FieldSchema("webServer.tls.keyFile", FieldType.STRING, "TLS 私钥"),
            FieldSchema("webServer.tls.trustedCaFile", FieldType.STRING, "TLS CA 证书"),
        ),
    )

    private fun logSection() = ConfigSection(
        id = "log",
        title = "日志",
        fields = listOf(
            FieldSchema("log.to", FieldType.STRING, "日志输出", defaultValue = "console"),
            FieldSchema("log.level", FieldType.ENUM, "日志级别", defaultValue = "info", enumOptions = listOf("trace", "debug", "info", "warn", "error")),
            FieldSchema("log.maxDays", FieldType.INT, "最大保留天数", defaultValue = 3),
            FieldSchema("log.disablePrintColor", FieldType.BOOL, "禁用日志颜色", defaultValue = false),
        ),
    )

    private fun advancedSection() = ConfigSection(
        id = "advanced",
        title = "高级",
        fields = listOf(
            FieldSchema("detailedErrorsToClient", FieldType.BOOL, "向客户端发送详细错误", defaultValue = true),
            FieldSchema("maxPortsPerClient", FieldType.INT, "每客户端最大端口数", hint = "0 = 不限制"),
            FieldSchema("userConnTimeout", FieldType.INT, "用户连接超时(秒)", defaultValue = 10),
            FieldSchema("udpPacketSize", FieldType.INT, "UDP 包大小(字节)", defaultValue = 1500),
            FieldSchema("natholeAnalysisDataReserveHours", FieldType.INT, "NAT 穿透数据保留(小时)", defaultValue = 168),
            FieldSchema("enablePrometheus", FieldType.BOOL, "启用 Prometheus", defaultValue = false),
        ),
    )
}
