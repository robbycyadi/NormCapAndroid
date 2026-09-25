package com.normcap.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button

// Bubble mengambang ala NormCap. v1: tap bubble -> buka MainActivity auto-capture.
// ponytail: seleksi-area lintas-aplikasi penuh butuh token MediaProjection di service,
// upgrade kalau bubble-tap-to-capture terasa kurang (sekarang 1 tap extra).
class CaptureService : Service() {
    private var wm: WindowManager? = null
    private var bubble: Button? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startFg()
        if (Settings.canDrawOverlays(this)) addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } else if (bubble == null) addBubble()
        return START_STICKY
    }

    private fun startFg() {
        val ch = "capture"
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(ch, "Capture", NotificationManager.IMPORTANCE_MIN))
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).apply { action = "AUTO_CAPTURE" },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, ch)
                .setContentTitle("NormCap siap")
                .setContentText("Tap bubble untuk scan layar")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentIntent(open).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("NormCap siap")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentIntent(open).build()
        }
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notif)
        }
    }

    private fun addBubble() {
        val w = getSystemService(WINDOW_SERVICE) as WindowManager
        wm = w
        val b = Button(this).apply {
            text = "OCR"
            minimumWidth = 132 // >=44dp tap target (R-03)
            minHeight = 132
            setOnClickListener {
                startActivity(Intent(this@CaptureService, MainActivity::class.java).apply {
                    action = "AUTO_CAPTURE"; addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
        bubble = b
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }
        w.addView(b, WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL })
    }

    override fun onDestroy() {
        bubble?.let { wm?.removeView(it) }
        super.onDestroy()
    }
}
