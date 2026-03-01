package io.github.acedroidx.frp

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

fun Context.startShellService(intent: Intent) {
    if (intent.action == ShellServiceAction.START) {
        ContextCompat.startForegroundService(this, intent)
    } else {
        startService(intent)
    }
}
