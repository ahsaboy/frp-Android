package io.github.acedroidx.frp

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.acedroidx.frp.ui.theme.FrpTheme
import kotlinx.coroutines.flow.MutableStateFlow

class AboutActivity : ComponentActivity() {
    private val frpVersion = MutableStateFlow("Loading...")
    private val themeMode = MutableStateFlow("跟随系统")
    private lateinit var preferences: SharedPreferences

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = getSharedPreferences("data", MODE_PRIVATE)
        frpVersion.value = preferences.getString(PreferencesKey.FRP_VERSION, "Loading...") ?: "Loading..."
        themeMode.value = preferences.getString(PreferencesKey.THEME_MODE, "跟随系统") ?: "跟随系统"

        enableEdgeToEdge()
        setContent {
            val currentTheme by themeMode.collectAsStateWithLifecycle("跟随系统")
            FrpTheme(themeMode = currentTheme) {
                val frpVersion by frpVersion.collectAsStateWithLifecycle("Loading...")
                Scaffold(topBar = {
                    TopAppBar(
                        title = {
                            Text("frp for Android - ${BuildConfig.VERSION_NAME}/$frpVersion")
                        },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_arrow_back_24dp),
                                    contentDescription = "返回"
                                )
                            }
                        }
                    )
                }) { contentPadding ->
                    // Screen content
                    Box(
                        modifier = Modifier
                            .padding(contentPadding)
                            .verticalScroll(rememberScrollState())
                            .scrollable(orientation = Orientation.Vertical,
                                state = rememberScrollableState { delta -> 0f })
                    ) {
                        MainContent()
                    }
                }
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun MainContent() {
        val uriHandler = LocalUriHandler.current
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            // 当前仓库（二次开发）
            Text("本仓库（二次开发）：", style = MaterialTheme.typography.titleMedium)
            Text(buildAnnotatedString {
                val link = LinkAnnotation.Url(
                    "https://github.com/ahsaboy/frp-Android",
                    TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary))
                ) {
                    val url = (it as LinkAnnotation.Url).url
                    uriHandler.openUri(url)
                }
                withLink(link) { append("https://github.com/ahsaboy/frp-Android") }
            })

            // 原 App 作者
            Text("原 App 作者 (AceDroidX)：", style = MaterialTheme.typography.titleMedium)
            Text(buildAnnotatedString {
                val link = LinkAnnotation.Url(
                    "https://github.com/AceDroidX/frp-Android",
                    TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary))
                ) {
                    val url = (it as LinkAnnotation.Url).url
                    uriHandler.openUri(url)
                }
                withLink(link) { append("https://github.com/AceDroidX/frp-Android") }
            })

            // frp 原作者
            Text("frp 原作者 (fatedier)：", style = MaterialTheme.typography.titleMedium)
            Text(buildAnnotatedString {
                val link = LinkAnnotation.Url(
                    "https://github.com/fatedier/frp",
                    TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary))
                ) {
                    val url = (it as LinkAnnotation.Url).url
                    uriHandler.openUri(url)
                }
                withLink(link) { append("https://github.com/fatedier/frp") }
            })
        }
    }
}
