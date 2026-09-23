import java.net.URL

plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.parcelize") version "2.4.20" apply false
}

// 升级 frp 到官方最新版本：./gradlew updateFrp（更新 gradle.properties 的 frpVersion 与 README 徽章）
tasks.register("updateFrp") {
    group = "frp"
    description = "查询 frp 官方最新版本并更新 gradle.properties 与 README 徽章"
    val propsFile = rootProject.file("gradle.properties")
    val readmes = listOf(rootProject.file("README.md"), rootProject.file("README_en.md"))
    doLast {
        val json = URL("https://api.github.com/repos/fatedier/frp/releases/latest")
            .openConnection()
            .apply { setRequestProperty("User-Agent", "frp-Android-build") }
            .getInputStream()
            .bufferedReader()
            .use { it.readText() }
        val latest = Regex("\"tag_name\"\\s*:\\s*\"v?(\\d+\\.\\d+\\.\\d+)\"")
            .find(json)?.groupValues?.get(1)
            ?: error("无法解析 GitHub 上 frp 的最新版本")
        val current = Regex("^frpVersion=(.*)$", RegexOption.MULTILINE)
            .find(propsFile.readText())?.groupValues?.get(1)?.trim()
        if (current == latest) {
            logger.lifecycle("frp 已是最新版本: v$latest")
            return@doLast
        }
        val text = propsFile.readText()
        propsFile.writeText(
            if (current != null) {
                text.replace(Regex("^frpVersion=.*$", RegexOption.MULTILINE), "frpVersion=$latest")
            } else {
                text.trimEnd() + "\nfrpVersion=$latest\n"
            }
        )
        readmes.forEach { readme ->
            if (readme.exists()) {
                readme.writeText(
                    readme.readText().replace(Regex("frp-\\d+\\.\\d+\\.\\d+"), "frp-$latest")
                )
            }
        }
        logger.lifecycle("frp: ${current ?: "?"} -> $latest，下次构建将自动下载官方二进制")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}