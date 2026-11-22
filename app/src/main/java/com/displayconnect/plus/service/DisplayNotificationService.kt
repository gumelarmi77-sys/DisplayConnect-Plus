package com.displayconnect.plus.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.displayconnect.plus.ble.BleManager
import com.displayconnect.plus.media.MediaHelper
import org.json.JSONObject

class DisplayNotificationService : NotificationListenerService() {
    private val TAG = "DisplayNotifService"

    private lateinit var mediaHelper: MediaHelper
    private lateinit var mediaSessionManager: MediaSessionManager
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        var instance: DisplayNotificationService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        mediaHelper = MediaHelper(this)
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

        // Setup listener perintah dari BLE (sentuhan layar ESP32 CYD)
        BleManager.onCommandReceived = { cmd ->
            mediaHelper.handleCommand(cmd)
        }

        // Listen perubahan active media sessions (Spotify, YT Music, dll)
        try {
            val component = ComponentName(this, DisplayNotificationService::class.java)
            mediaSessionManager.addOnActiveSessionsChangedListener({ controllers ->
                val primaryController = controllers?.firstOrNull()
                mediaHelper.setActiveController(primaryController)
            }, component)

            // Inisialisasi awal jika sudah ada controller aktif
            val sessions = mediaSessionManager.getActiveSessions(component)
            mediaHelper.setActiveController(sessions.firstOrNull())
        } catch (e: Exception) {
            Log.e(TAG, "Gagal listen active sessions: ${e.message}")
        }

        Log.d(TAG, "DisplayNotificationService berjalan.")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // 1. Tangkap Notifikasi WhatsApp & WhatsApp Business
        if (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b") {
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

            // Lewati notifikasi sistem internal WA seperti "Mencari pesan baru..."
            if (title.isNotEmpty() && text.isNotEmpty() && !text.contains("messages", ignoreCase = true)) {
                try {
                    val json = JSONObject().apply {
                        put("t", "notif")
                        put("app", "wa")
                        put("from", title)
                        put("msg", text)
                    }
                    BleManager.sendJson(json.toString())
                    Log.d(TAG, "Kirim Notif WA: $title - $text")
                } catch (e: Exception) {
                    Log.e(TAG, "Error WA JSON: ${e.message}")
                }
            }
        }

        // 2. Tangkap Panggilan Telepon Masuk (Dialer biasa / WA Call)
        val category = notification.category
        if (category == Notification.CATEGORY_CALL || pkg.contains("dialer", ignoreCase = true) || pkg.contains("incall", ignoreCase = true)) {
            val caller = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: "Panggilan Masuk"

            try {
                val json = JSONObject().apply {
                    put("t", "call")
                    put("status", "incoming")
                    put("name", caller)
                }
                BleManager.sendJson(json.toString())
                Log.d(TAG, "Kirim Panggilan Masuk: $caller")
            } catch (e: Exception) {
                Log.e(TAG, "Error Call JSON: ${e.message}")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val category = sbn?.notification?.category
        val pkg = sbn?.packageName ?: ""

        // Saat panggilan telepon berakhir / diangkat
        if (category == Notification.CATEGORY_CALL || pkg.contains("dialer", ignoreCase = true) || pkg.contains("incall", ignoreCase = true)) {
            try {
                val json = JSONObject().apply {
                    put("t", "call")
                    put("status", "ended")
                }
                BleManager.sendJson(json.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error Call End JSON: ${e.message}")
            }
        }
    }

    fun getMediaHelper(): MediaHelper = mediaHelper
}
