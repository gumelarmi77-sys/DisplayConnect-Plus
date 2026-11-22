package com.displayconnect.plus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.displayconnect.plus.ble.BleManager
import com.displayconnect.plus.databinding.ActivityMainBinding
import com.displayconnect.plus.service.DisplayNotificationService
import com.displayconnect.plus.ui.NavigationActivity

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    // Permission launcher
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (!granted) toast("Izin lokasi diperlukan")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        BleManager.init(this)
        setupListeners()
        requestAppPermissions()
    }

    override fun onResume() {
        super.onResume()
        checkNotificationAccess()
    }

    private fun setupListeners() {
        // Scan & Connect
        binding.btnScanConnect.setOnClickListener { BleManager.startScan(this) }
        // Disconnect
        binding.btnDisconnect.setOnClickListener { BleManager.disconnect() }
        // Grant Notification Access
        binding.btnGrantNotifAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        // Test WA & Call
        binding.btnTestWA.setOnClickListener { testMessage("wa", "Uji WA", "Halo!") }
        binding.btnTestCall.setOnClickListener { testMessage("call", "incoming", "Budi Test") }

        // **Navigasi** (baru)
        binding.btnOpenNavigation.setOnClickListener {
            startActivity(Intent(this, NavigationActivity::class.java))
        }

        // BLE status callback
        BleManager.onStatusChanged = { connected, statusText ->
            binding.tvBleStatus.text = "Status: $statusText"
            binding.tvBleStatus.setTextColor(
                if (connected) Color.parseColor("#4CAF50")
                else Color.parseColor("#F44336")
            )
        }

        // Log callback
        BleManager.onLogMessage = { msg -> appendLog(msg) }

        // Media helper callback (update UI)
        DisplayNotificationService.instance?.getMediaHelper()?.onMediaChanged = { title, artist, playing ->
            binding.tvMediaTitle.text = title
            binding.tvMediaArtist.text = "$artist - [${if (playing) "Playing" else "Paused"}]"
        }
    }

    private fun testMessage(app: String, type: String, payload: String) {
        val json = org.json.JSONObject().apply {
            put("t", if (app == "wa") "notif" else "call")
            put("app", app)
            put("from", payload)
            put("msg", "Test message")
            put("status", type)
            put("name", payload)
        }
        if (!BleManager.sendJson(json.toString())) {
            toast("BLE belum terhubung")
        }
    }

    private fun checkNotificationAccess() {
        val cn = ComponentName(this, DisplayNotificationService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val enabled = flat?.contains(cn.flattenToString()) == true

        if (enabled) {
            binding.tvNotifPermissionStatus.text = "Akses Notifikasi: Aktif"
            binding.tvNotifPermissionStatus.setTextColor(Color.parseColor("#4CAF50"))
            binding.btnGrantNotifAccess.isEnabled = false
            binding.btnGrantNotifAccess.text = "Akses Notifikasi Sudah Aktif"
        } else {
            binding.tvNotifPermissionStatus.text =
                "Akses Belum Diberikan (Klik tombol di bawah)"
            binding.tvNotifPermissionStatus.setTextColor(Color.parseColor("#F44336"))
            binding.btnGrantNotifAccess.isEnabled = true
        }
    }

    private fun requestAppPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        if (perms.isNotEmpty()) permissionLauncher.launch(perms.toTypedArray())
    }

    private fun appendLog(text: String) {
        binding.tvLog.append("> $text\n")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
