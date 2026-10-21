package com.djbooya.forcestatusbaron

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.widget.Toast

/** The single saved setting, plus applying it by starting or stopping the service. */
object ForceSetting {
    private const val PREFS = "force_status_bar"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    /** Saves [enabled], starts or stops the service to match, and toasts the result. */
    fun save(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        apply(context)

        val message = when {
            !enabled -> R.string.toast_off
            AndroidSettings.canDrawOverlays(context) -> R.string.toast_on
            else -> R.string.toast_need_overlay
        }
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
    }

    /** Starts the service if the saved setting is Yes, stops it otherwise. */
    fun apply(context: Context) {
        val intent = Intent(context, StatusBarService::class.java)
        if (isEnabled(context)) {
            context.startForegroundService(intent)
        } else {
            context.stopService(intent)
        }
    }

    fun overlayPermissionIntent(context: Context): Intent =
        Intent(
            AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
