package io.github.acedroidx.frp

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.acedroidx.frp.ui.theme.AppThemeMode
import io.github.acedroidx.frp.ui.theme.FrpTheme
import io.github.acedroidx.frp.ui.theme.readAppThemeMode
import io.github.acedroidx.frp.ui.theme.readUseMonet
import kotlinx.coroutines.flow.MutableStateFlow
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar

class AboutActivity : BaseActivity() {
    private val frpVersion = MutableStateFlow("Loading...")
    private val themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    private val useMonet = MutableStateFlow(false)
    private lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = getSharedPreferences("data", MODE_PRIVATE)
        frpVersion.value = preferences.getString(PreferencesKey.FRP_VERSION, "Loading...") ?: "Loading..."
        themeMode.value = preferences.readAppThemeMode()
        useMonet.value = preferences.readUseMonet()

        applyEdgeToEdge()
        setContent {
        val navEventOwner = rememberNavigationEventDispatcherOwner(enabled = true, parent = null)
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides navEventOwner) {
            val currentTheme by themeMode.collectAsStateWithLifecycle(AppThemeMode.SYSTEM)
            val currentUseMonet by useMonet.collectAsStateWithLifecycle(false)
            FrpTheme(themeMode = currentTheme, useMonet = currentUseMonet) {
                val frpVersion by frpVersion.collectAsStateWithLifecycle("Loading...")
                Scaffold(topBar = {
                    SmallTopAppBar(
                        title = "${stringResource(R.string.frp_for_android)} - ${BuildConfig.VERSION_NAME}/$frpVersion",
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_arrow_back_24dp),
                                    contentDescription = stringResource(R.string.back)
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
                    ) {
                        MainContent()
                    }
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = stringResource(R.string.about_repository_fork),
                    summary = "https://github.com/ahsaboy/frp-Android",
                    onClick = {
                        uriHandler.openUri("https://github.com/ahsaboy/frp-Android")
                    }
                )
                BasicComponent(
                    title = stringResource(R.string.about_repository_original),
                    summary = "https://github.com/AceDroidX/frp-Android",
                    onClick = {
                        uriHandler.openUri("https://github.com/AceDroidX/frp-Android")
                    }
                )
                BasicComponent(
                    title = stringResource(R.string.about_repository_frp),
                    summary = "https://github.com/fatedier/frp",
                    onClick = {
                        uriHandler.openUri("https://github.com/fatedier/frp")
                    }
                )
            }
        }
    }
}
