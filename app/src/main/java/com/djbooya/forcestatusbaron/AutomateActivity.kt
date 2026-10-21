package com.djbooya.forcestatusbaron

import android.app.Activity
import android.os.Bundle

/**
 * Headless entry point for Automate's "App start" / "Activity start" blocks. It has no
 * layout (Theme.NoDisplay) and finishes immediately, so nothing is shown and the caller
 * stays in front.
 *
 * - `.action.START` (or no action): apply the saved setting
 * - `.action.ENABLE` / `.action.DISABLE`: save Yes / No, then apply it (with a toast)
 */
class AutomateActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent?.action) {
            ACTION_ENABLE -> ForceSetting.save(this, true)
            ACTION_DISABLE -> ForceSetting.save(this, false)
            else -> ForceSetting.apply(this)
        }
        finish()
    }

    companion object {
        const val ACTION_ENABLE = "com.djbooya.forcestatusbaron.action.ENABLE"
        const val ACTION_DISABLE = "com.djbooya.forcestatusbaron.action.DISABLE"
    }
}
