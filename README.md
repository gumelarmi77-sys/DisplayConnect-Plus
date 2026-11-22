# DisplayConnect+ (Plus) 🚀

Solusi terintegrasi untuk menampilkan **Notifikasi WhatsApp**, **Panggilan Telepon Masuk**, dan **Media Player Controller (Play, Pause, Next, Prev, Volume)** langsung di layar **ESP32 CYD (Cheap Yellow Display ESP32-2432S028)** melalui koneksi **Bluetooth Low Energy (BLE)**.

Repository ini sudah dirancang **100% Plug-and-Play**:
1. **Android App:** Sudah dilengkapi **GitHub Actions CI/CD**. Anda **tidak perlu menginstal Android Studio di komputer**. Cukup push project ini ke repository GitHub Anda, dan GitHub Actions akan otomatis meng-compile dan menghasilkan file `app-debug.apk` siap download dan install ke HP!
2. **ESP32 Firmware:** Menggunakan library modern **LovyanGFX** dan **ArduinoJson**. Tidak perlu edit file konfigurasi library (`User_Setup.h` dll). Cukup buka di **Arduino IDE**, pasang 2 library via Library Manager, dan langsung klik **Upload**!

---

## 📂 Struktur Direktori

```text
DisplayConnect-Plus/
├── .github/workflows/
│   └── build-apk.yml               # Workflow otomatis pembuat APK di GitHub
├── app/                            # Source code Aplikasi Android (Kotlin)
│   ├── src/main/java/com/displayconnect/plus/
│   │   ├── ble/BleManager.kt       # BLE Nordic UART Service (NUS) Scanner & Handler
│   │   ├── media/MediaHelper.kt    # Controller Spotify/YT Music & Volume
│   │   ├── service/DisplayNotificationService.kt # Listener Notif WhatsApp & Call
│   │   └── MainActivity.kt         # Tampilan Antarmuka Aplikasi HP
│   └── src/main/AndroidManifest.xml
├── DisplayConnect_CYD/
│   └── DisplayConnect_CYD.ino      # Sketch Firmware Arduino untuk ESP32 CYD
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 📱 BAGIAN 1: Mendapatkan APK Android (Tanpa Android Studio)

Anda hanya butuh akun GitHub gratis:

1. **Buat Repository Baru di GitHub**:
   - Buka [github.com/new](https://github.com/new).
   - Beri nama repository, misalnya `DisplayConnect-Plus`, set ke **Public** (agar runner Actions gratis tanpa batas kuota).
2. **Push Project ini ke GitHub**:
   Buka terminal di folder project ini:
   ```bash
   git init
   git add .
   git commit -m "Initial commit DisplayConnect Plus"
   git branch -M main
   git remote add origin https://github.com/USERNAME_ANDA/DisplayConnect-Plus.git
   git push -u origin main
   ```
3. **Download APK yang Jadi**:
   - Buka tab **Actions** di halaman repository GitHub Anda.
   - Anda akan melihat workflow **Build Android APK** sedang berjalan (icon oranye/berputar).
   - Tunggu sekitar 2–3 menit hingga icon berubah menjadi centang hijau ✅ **Success**.
   - Klik workflow tersebut, gulir ke bagian paling bawah di **Artifacts**, klik **DisplayConnectPlus-Debug-APK** untuk mendownload zip yang berisi `app-debug.apk`.
   - Atau buka tab **Releases** di samping kanan repository Anda untuk mendownload langsung file APK-nya ke HP Android Anda.
4. **Instal di HP**:
   - Buka file `.apk` di HP Android dan pilih *Install* (Izinkan *Install from unknown sources* jika diminta).

---

## 📟 BAGIAN 2: Upload Firmware ke ESP32 CYD via Arduino IDE

### 1. Persiapan Arduino IDE
Pastikan Anda sudah menginstal **Arduino IDE** (v2.x disarankan) dan menambahkan board **ESP32**:
* Di Arduino IDE: Masuk ke **File** -> **Preferences** -> Masukkan URL Board Manager:
  `https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json`
* Masuk ke **Tools** -> **Board** -> **Boards Manager**, cari `esp32` oleh *Espressif Systems*, lalu klik **Install**.

### 2. Install Library yang Dibutuhkan
Buka **Tools** -> **Manage Libraries...** (atau tekan `Ctrl + Shift + I`), cari dan install:
1. **LovyanGFX** (oleh *lovyan03*)
2. **ArduinoJson** (oleh *Benoit Blanchon*)
*(Catatan: Library BLE sudah bawaan dari core ESP32, tidak perlu diinstal lagi).*

### 3. Pengaturan Board & Upload
1. Buka file: `DisplayConnect_CYD/DisplayConnect_CYD.ino`.
2. Pasang kabel USB dari komputer ke modul **ESP32 CYD (ESP32-2432S028)**.
3. Atur menu di **Tools**:
   - **Board**: `ESP32 Dev Module`
   - **Upload Speed**: `921600` (atau `115200` jika kabel USB Anda panjang)
   - **Flash Frequency**: `80MHz`
   - **Partition Scheme**: `Huge APP (3MB No OTA/1MB SPIFFS)`
   - **Port**: Pilih port COM ESP32 Anda.
4. Klik tombol **Upload** (Tanda panah ke kanan).
5. Tunggu hingga proses kompilasi dan flash selesai 100%. Layar CYD akan menyala menampilkan antarmuka DisplayConnect+.

> **Tips Catatan Backlight:**
> Pada 95% varian CYD standar, pin backlight layar adalah **GPIO 21**. Jika setelah upload layar terlihat hitam namun tulisan di Serial Monitor muncul normal, cukup ganti satu baris pada sketch:
> `cfg.pin_bl = 21;` menjadi `cfg.pin_bl = 27;` lalu upload kembali.

---

## 🚴 BAGIAN 3: Cara Penggunaan & Pengujian

1. **Nyalakan ESP32 CYD**: Layar akan menampilkan tulisan `BLE: TERPUTUS` di sudut kanan atas.
2. **Buka Aplikasi DisplayConnect+ di HP**:
   - Berikan izin Bluetooth & Lokasi saat diminta.
   - Klik tombol **"Buka Pengaturan Akses Notifikasi"**, lalu cari **DisplayConnect+** dan geser switch ke posisi **Aktif (ON)**. Ini wajib agar notifikasi WhatsApp dan kontrol media musik terbaca.
3. **Hubungkan BLE**:
   - Klik tombol **"Scan & Hubungkan"**.
   - Dalam 2–5 detik, status di HP dan di layar CYD akan berubah menjadi hijau: `BLE: TERHUBUNG`.
4. **Uji Coba Fitur**:
   - **WhatsApp:** Saat ada pesan WhatsApp masuk ke HP, layar CYD langsung menampilkan pop-up hijau dengan nama pengirim dan isi pesan selama 7 detik (atau tap layar untuk langsung menutup). Anda juga bisa klik tombol **"Test WA"** di aplikasi HP.
   - **Panggilan Masuk:** Saat ada telepon masuk, layar CYD langsung berganti banner merah besar berkedip dengan nama penelepon.
   - **Media Player:** Putar lagu di Spotify atau YouTube Music di HP. Judul lagu dan nama artis akan langsung muncul di layar CYD.
   - **Tombol Layar Sentuh CYD:**
     - `|<<` : Lagu Sebelumnya (Previous Track)
     - `>/||` : Play / Pause
     - `>>|` : Lagu Berikutnya (Next Track)
     - `V -` : Turunkan Volume HP
     - `V +` : Naikkan Volume HP
