package com.displayconnect.plus.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import org.json.JSONObject
import java.util.*

@SuppressLint("MissingPermission")
object BleManager {
    private const val TAG = "BleManager"

    // Nordic UART Service (NUS)
    val SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    val RX_CHAR_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // Write to ESP32
    val TX_CHAR_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // Notify from ESP32
    private val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    private var isScanning = false
    var isConnected = false
        private set

    var onStatusChanged: ((Boolean, String) -> Unit)? = null
    var onCommandReceived: ((String) -> Unit)? = null
    var onLogMessage: ((String) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
    }

    fun startScan(context: Context) {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            log("Bluetooth scanner tidak tersedia!")
            return
        }

        if (isScanning) return
        isScanning = true
        log("Mencari ESP32 CYD (DisplayConnect)...")
        onStatusChanged?.invoke(false, "Mencari perangkat...")

        val filter = ScanFilter.Builder()
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Stop scan otomatis setelah 10 detik
        mainHandler.postDelayed({
            if (isScanning) {
                stopScan()
                if (!isConnected) {
                    log("Pencarian selesai, perangkat tidak ditemukan.")
                    onStatusChanged?.invoke(false, "Tidak Ditemukan")
                }
            }
        }, 10000)

        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    fun stopScan() {
        if (!isScanning) return
        isScanning = false
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val name = result.scanRecord?.deviceName ?: device.name ?: ""

            if (name.contains("DisplayConnect", ignoreCase = true) ||
                result.scanRecord?.serviceUuids?.any { it.uuid == SERVICE_UUID } == true
            ) {
                log("Ditemukan CYD: $name [${device.address}]")
                stopScan()
                connectToDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            log("Scan gagal: error $errorCode")
            onStatusChanged?.invoke(false, "Scan Gagal ($errorCode)")
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        log("Menghubungkan ke ${device.address}...")
        onStatusChanged?.invoke(false, "Menghubungkan...")
        bluetoothGatt = device.connectGatt(null, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        rxCharacteristic = null
        isConnected = false
        log("Terputus dari ESP32.")
        onStatusChanged?.invoke(false, "Terputus")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("Terhubung! Menemukan service...")
                mainHandler.post { onStatusChanged?.invoke(true, "Terhubung (Menyiapkan...)") }
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false
                rxCharacteristic = null
                log("Koneksi terputus.")
                mainHandler.post { onStatusChanged?.invoke(false, "Terputus") }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    rxCharacteristic = service.getCharacteristic(RX_CHAR_UUID)
                    val txCharacteristic = service.getCharacteristic(TX_CHAR_UUID)

                    // Aktifkan notifikasi untuk Characteristic TX (menerima data dari ESP32)
                    if (txCharacteristic != null) {
                        gatt.setCharacteristicNotification(txCharacteristic, true)
                        val descriptor = txCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                        if (descriptor != null) {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        }
                    }

                    isConnected = true
                    log("Service NUS Siap! Siap kirim data.")
                    mainHandler.post { onStatusChanged?.invoke(true, "Terhubung ke ESP32 CYD") }
                } else {
                    log("Service Nordic UART tidak ditemukan di ESP32!")
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingBytes(value)
        }

        @Deprecated("Deprecated for SDK < 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            characteristic?.value?.let { handleIncomingBytes(it) }
        }
    }

    private fun handleIncomingBytes(bytes: ByteArray) {
        val msg = String(bytes, Charsets.UTF_8).trim()
        log("RX dari ESP32: $msg")
        mainHandler.post {
            try {
                val json = JSONObject(msg)
                val act = json.optString("act")
                if (act.isNotEmpty()) {
                    onCommandReceived?.invoke(act)
                }
            } catch (e: Exception) {
                onCommandReceived?.invoke(msg)
            }
        }
    }

    fun sendJson(jsonString: String): Boolean {
        val gatt = bluetoothGatt
        val char = rxCharacteristic
        if (!isConnected || gatt == null || char == null) {
            return false
        }

        try {
            val bytes = jsonString.toByteArray(Charsets.UTF_8)
            char.value = bytes
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            val success = gatt.writeCharacteristic(char)
            if (success) {
                log("TX ke ESP32: $jsonString")
            } else {
                log("TX Gagal terkirim!")
            }
            return success
        } catch (e: Exception) {
            log("TX Exception: ${e.message}")
            return false
        }
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        mainHandler.post { onLogMessage?.invoke(message) }
    }
}
