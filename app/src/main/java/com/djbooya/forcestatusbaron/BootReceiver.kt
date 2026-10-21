package com.djbooya.forcestatusbaron

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restores the saved setting after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DebugLog.init(context)
        DebugLog.i("BootReceiver", "${intent.action}, setting is ${if (ForceSetting.isEnabled(context)) "Yes" else "No"}")
        if (ForceSetting.isEnabled(context)) ForceSetting.apply(context)
    }
}
