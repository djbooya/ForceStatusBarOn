package com.djbooya.forcestatusbaron

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restores the saved setting after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (ForceSetting.isEnabled(context)) ForceSetting.apply(context)
    }
}
