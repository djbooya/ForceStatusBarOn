package com.djbooya.forcestatusbaron

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.provider.Settings

/**
 * The only screen: "Force Status Bar On" with Yes / No. Picking either saves the choice,
 * applies it, toasts the result and exits. Back or tapping outside exits without changes.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val enabled = ForceSetting.isEnabled(this)
        var message = getString(R.string.prompt_current, getString(if (enabled) R.string.yes else R.string.no))
        if (enabled && !Settings.canDrawOverlays(this)) message += getString(R.string.prompt_no_overlay)
        message += getString(R.string.prompt_log, packageName)

        AlertDialog.Builder(this)
            .setTitle(R.string.prompt_title)
            .setMessage(message)
            .setPositiveButton(R.string.yes) { _, _ -> choose(true) }
            .setNegativeButton(R.string.no) { _, _ -> choose(false) }
            .setOnCancelListener { finishAndRemoveTask() }
            .show()
    }

    private fun choose(enabled: Boolean) {
        ForceSetting.save(this, enabled)
        if (enabled && !Settings.canDrawOverlays(this)) {
            // The service waits for the grant and draws the bar as soon as it is given.
            try {
                startActivity(ForceSetting.overlayPermissionIntent(this))
            } catch (e: ActivityNotFoundException) {
                // Some head units ship without this settings page; see README for the adb grant.
            }
        }
        finishAndRemoveTask()
    }
}
