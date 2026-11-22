package com.displayconnect.plus

import android.Manifest
import android.content.ComponentName
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
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            appendLog("Izin Bluetooth & Lokasi disetujui.")
        } else {
            appendLog("Peringatan: Sebagian izin Bluetooth tidak disetujui!")
        }
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
        // Tombol Scan & Hubungkan ke CYD
        binding.btnScanConnect.setOnClickListener {
            BleManager.startScan(this)
        }

        // Tombol Putuskan
        binding.btnDisconnect.setOnClickListener {
            BleManager.disconnect()
        }

        // Tombol Izin Notifikasi
        binding.btnGrantNotifAccess.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }

        // Tombol Uji Coba WhatsApp
        binding.btnTestWA.setOnClickListener {
            val testJson = JSONObject().apply {
                put("t", "notif")
                put("app", "wa")
                put("from", "Uji Coba WhatsApp")
                put("msg", "Halo! Pesan WhatsApp berhasil tampil di layar ESP32 CYD.")
            }
            val ok = BleManager.sendJson(testJson.toString())
            if (!ok) appendLog("Gagal: BLE belum terhubung ke ESP32.")
        }

        // Tombol Uji Coba Panggilan
        binding.btnTestCall.setOnClickListener {
            val testJson = JSONObject().apply {
                put("t", "call")
                put("status", "incoming")
                put("name", "Budi Santoso (Test)")
            }
            val ok = BleManager.sendJson(testJson.toString())
            if (!ok) appendLog("Gagal: BLE belum terhubung ke ESP32.")
        }

        // Callback Status BLE
        BleManager.onStatusChanged = { connected, statusText ->
            binding.tvBleStatus.text = "Status: $statusText"
            if (connected) {
                binding.tvBleStatus.setTextColor(Color.parseColor("#4CAF50"))
            } else {
                binding.tvBleStatus.setTextColor(Color.parseColor("#F44336"))
            }
        }

        // Callback Log
        BleManager.onLogMessage = { msg ->
            appendLog(msg)
        }

        // Hook MediaHelper untuk update tampilan saat lagu berubah
        DisplayNotificationService.instance?.getMediaHelper()?.onMediaChanged = { title, artist, isPlaying ->
            binding.tvMediaTitle.text = title
            binding.tvMediaArtist.text = "$artist - [${if (isPlaying) "Playing" else "Paused"}]"
        }
    }

    private fun checkNotificationAccess() {
        val cn = ComponentName(this, DisplayNotificationService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val isEnabled = flat != null && flat.contains(cn.flattenToString())

        if (isEnabled) {
            binding.tvNotifPermissionStatus.text = "Akses Notifikasi: Aktif & Siap"
            binding.tvNotifPermissionStatus.setTextColor(Color.parseColor("#4CAF50"))
            binding.btnGrantNotifAccess.isEnabled = false
            binding.btnGrantNotifAccess.text = "Akses Notifikasi Sudah Aktif"
        } else {
            binding.tvNotifPermissionStatus.text = "Akses Belum Diberikan (Klik tombol di bawah)"
            binding.tvNotifPermissionStatus.setTextColor(Color.parseColor("#F44336"))
            binding.btnGrantNotifAccess.isEnabled = true
        }
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun appendLog(text: String) {
        binding.tvLog.append("> $text\n")
    }
}
