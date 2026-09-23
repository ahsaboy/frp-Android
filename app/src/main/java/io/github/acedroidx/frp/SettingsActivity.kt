package io.github.acedroidx.frp

import android.content.Intent
import android.os.Bundle

/**
 * Compatibility entry point for external callers such as the Quick Settings tile.
 * The in-app settings screen is hosted by MainActivity as a top-level destination.
 */
class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_SELECTED_DESTINATION, MainActivity.DESTINATION_SETTINGS)
            }
        )
        finish()
    }
}
