package com.djbooya.forcestatusbaron

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.input.InputManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView

/**
 * Keeps a status bar on screen. A sideloaded app cannot force the system status bar to
 * show over a full-screen app, so this draws a replica (clock and battery) as an overlay
 * in the same spot whenever the real one is hidden, and removes it when the real one
 * comes back.
 *
 * Two overlay windows, both non-touchable so taps pass straight through:
 * - a 1px-wide tracker that only listens for the system status bar's visibility
 * - the replica bar itself, shown only while the real bar is hidden
 */
class StatusBarService : Service() {

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var tracker: View? = null
    private var bar: View? = null
    private var batteryReceiver: BroadcastReceiver? = null

    /** Polls for the overlay grant, since nothing notifies the app when it is given. */
    private val waitForPermission = object : Runnable {
        override fun run() {
            if (Settings.canDrawOverlays(this@StatusBarService)) addOverlays()
            else handler.postDelayed(this, PERMISSION_POLL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must be called even when about to stop, or startForegroundService() crashes the app.
        startInForeground()
        if (!ForceSetting.isEnabled(this)) {
            // Started externally (Automate, boot) while the setting is No.
            stopSelf()
            return START_NOT_STICKY
        }
        if (tracker == null) {
            handler.removeCallbacks(waitForPermission)
            waitForPermission.run()
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Status bar height can change with orientation; rebuild at the new size.
        if (tracker != null) {
            removeOverlays()
            addOverlays()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeOverlays()
        super.onDestroy()
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_MIN)
        )
        val openPrompt = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bar)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openPrompt)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun addOverlays() {
        val barView = LayoutInflater.from(this).inflate(R.layout.overlay_bar, null)
        barView.visibility = View.GONE
        windowManager.addView(barView, barParams())
        bar = barView

        val trackerView = View(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            trackerView.setOnApplyWindowInsetsListener { v, insets ->
                setRealBarVisible(insets.isVisible(WindowInsets.Type.statusBars()))
                v.onApplyWindowInsets(insets)
            }
        } else {
            @Suppress("DEPRECATION")
            trackerView.setOnSystemUiVisibilityChangeListener { flags ->
                setRealBarVisible(flags and View.SYSTEM_UI_FLAG_FULLSCREEN == 0)
            }
        }
        windowManager.addView(trackerView, trackerParams())
        tracker = trackerView

        startBatteryUpdates(barView.findViewById(R.id.battery))
    }

    private fun removeOverlays() {
        batteryReceiver?.let { unregisterReceiver(it) }
        batteryReceiver = null
        tracker?.let { windowManager.removeView(it) }
        bar?.let { windowManager.removeView(it) }
        tracker = null
        bar = null
    }

    private fun setRealBarVisible(visible: Boolean) {
        bar?.visibility = if (visible) View.GONE else View.VISIBLE
    }

    private fun startBatteryUpdates(label: TextView) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)) {
                    // Head units on constant power usually report no battery.
                    label.visibility = View.GONE
                    return
                }
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                if (level < 0 || scale <= 0) return
                label.text = "${if (plugged) "⚡" else ""}${level * 100 / scale}%"
                label.visibility = View.VISIBLE
            }
        }
        registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryReceiver = receiver
    }

    private fun overlayFlags() =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    private fun barParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        statusBarHeight(),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        overlayFlags() or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ blocks touches through another app's overlay above this opacity,
            // which would make the top strip of the screen untappable.
            alpha = getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun trackerParams() = WindowManager.LayoutParams(
        1,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        overlayFlags(),
        PixelFormat.TRANSPARENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    /** Height of the real status bar, whether or not it is currently showing. */
    private fun statusBarHeight(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val top = windowManager.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
            if (top > 0) return top
        }
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (id > 0) {
            val px = resources.getDimensionPixelSize(id)
            if (px > 0) return px
        }
        return (24 * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val CHANNEL_ID = "status_bar_service"
        private const val NOTIFICATION_ID = 1
        private const val PERMISSION_POLL_MS = 2000L
    }
}
