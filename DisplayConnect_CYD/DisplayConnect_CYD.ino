/*
  =============================================================================
  DisplayConnect+ (Plus) - ESP32 CYD (Cheap Yellow Display ESP32-2432S028)
  Firmware: Arduino IDE
  
  FITUR:
  1. Koneksi BLE Nordic UART Service (NUS) ke HP Android (Auto Reconnect).
  2. Notifikasi WhatsApp (Popup Card pengirim & pesan dengan auto-dismiss 7 detik).
  3. Notifikasi Panggilan Telepon Masuk (Banner merah & nama penelepon).
  4. Media Controller (Menampilkan Judul Lagu & Artis dari Spotify/YT Music).
  5. 5 Tombol Touch Screen XPT2046: [⏮ Prev] [⏯ Play/Pause] [⏭ Next] [Vol -] [Vol +].

  DEPENDENCIES (Install via Tools -> Manage Libraries di Arduino IDE):
  1. LovyanGFX (oleh lovyan03) -> v1.1.x atau terbaru
  2. ArduinoJson (oleh Benoit Blanchon) -> v6.x atau v7.x
  * Pustaka BLE sudah bawaan (built-in) ESP32 Arduino Core!
  =============================================================================
*/

#define LGFX_USE_V1
#include <LovyanGFX.hpp>
#include <ArduinoJson.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// =============================================================================
// KONFIGURASI HARDWARE CYD (ESP32-2432S028: ILI9341 + XPT2046)
// =============================================================================
class LGFX : public lgfx::LGFX_Device {
  lgfx::Panel_ILI9341     _panel_instance;
  lgfx::Bus_SPI           _bus_instance;
  lgfx::Light_PWM         _light_instance;
  lgfx::Touch_XPT2046     _touch_instance;

public:
  LGFX(void) {
    // 1. Konfigurasi SPI Bus LCD
    {
      auto cfg = _bus_instance.config();
      cfg.spi_host   = VSPI_HOST;
      cfg.spi_mode   = 0;
      cfg.freq_write = 40000000;
      cfg.freq_read  = 16000000;
      cfg.spi_3wire  = true;
      cfg.use_lock   = true;
      cfg.dma_channel = SPI_DMA_CH_AUTO;
      cfg.pin_sclk   = 14;
      cfg.pin_mosi   = 13;
      cfg.pin_miso   = 12;
      cfg.pin_dc     = 2;
      _bus_instance.config(cfg);
      _panel_instance.setBus(&_bus_instance);
    }

    // 2. Konfigurasi Panel LCD
    {
      auto cfg = _panel_instance.config();
      cfg.pin_cs          = 15;
      cfg.pin_rst         = -1;
      cfg.pin_busy        = -1;
      cfg.panel_width     = 240;
      cfg.panel_height    = 320;
      cfg.offset_x        = 0;
      cfg.offset_y        = 0;
      cfg.offset_rotation = 0;
      cfg.dummy_read_pixel= 8;
      cfg.dummy_read_bits = 1;
      cfg.readable        = true;
      cfg.invert          = false;
      cfg.rgb_order       = false;
      cfg.dlen_16bit      = false;
      cfg.bus_shared      = true;
      _panel_instance.config(cfg);
    }

    // 3. Konfigurasi Backlight LCD (GPIO 21, beberapa varian CYD pin 27)
    {
      auto cfg = _light_instance.config();
      cfg.pin_bl      = 21; 
      cfg.invert      = false;
      cfg.freq        = 44100;
      cfg.pwm_channel = 7;
      _light_instance.config(cfg);
      _panel_instance.setLight(&_light_instance);
    }

    // 4. Konfigurasi Touchscreen XPT2046 (Resistive)
    {
      auto cfg = _touch_instance.config();
      cfg.pin_cs   = 33;
      cfg.pin_mosi = 32;
      cfg.pin_miso = 39;
      cfg.pin_sclk = 25;
      cfg.pin_int  = 36;
      cfg.bus_host = VSPI_HOST;
      cfg.freq     = 2500000;
      // Kalibrasi standar CYD 2.8"
      cfg.x_min    = 300;
      cfg.x_max    = 3800;
      cfg.y_min    = 300;
      cfg.y_max    = 3800;
      cfg.panel_width  = 240;
      cfg.panel_height = 320;
      _touch_instance.config(cfg);
      _panel_instance.setTouch(&_touch_instance);
    }

    setPanel(&_panel_instance);
  }
};

LGFX lcd;

// =============================================================================
// BLE NORDIC UART SERVICE (NUS) DEFINITIONS
// =============================================================================
#define SERVICE_UUID           "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_RX "6E400002-B5A3-F393-E0A9-E50E24DCCA9E" // Menerima dari HP
#define CHARACTERISTIC_UUID_TX "6E400003-B5A3-F393-E0A9-E50E24DCCA9E" // Mengirim ke HP

BLEServer*         pServer = nullptr;
BLECharacteristic* pTxCharacteristic = nullptr;
bool deviceConnected = false;
bool oldDeviceConnected = false;

// Buffer pesan RX dari BLE
String rxBuffer = "";
bool hasNewData = false;

// =============================================================================
// STATE SISTEM & UI
// =============================================================================
struct MediaInfo {
  String title = "Tidak Ada Media";
  String artist = "Buka Spotify / YT Music";
  bool isPlaying = false;
} currentMedia;

struct NotifInfo {
  bool active = false;
  String app = "";
  String from = "";
  String msg = "";
  unsigned long timestamp = 0;
} currentNotif;

struct CallInfo {
  bool active = false;
  String caller = "";
} currentCall;

bool needRedraw = true;
unsigned long lastTouchTime = 0;

// =============================================================================
// BLE CALLBACKS
// =============================================================================
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      needRedraw = true;
      Serial.println("[BLE] Client Connected!");
    };

    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      needRedraw = true;
      Serial.println("[BLE] Client Disconnected!");
    }
};

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      std::string rxValue = pCharacteristic->getValue();
      if (rxValue.length() > 0) {
        rxBuffer = String(rxValue.c_str());
        hasNewData = true;
      }
    }
};

void sendBleCommand(const String& cmd) {
  if (deviceConnected && pTxCharacteristic != nullptr) {
    pTxCharacteristic->setValue(cmd.c_str());
    pTxCharacteristic->notify();
    Serial.printf("[BLE TX] %s\n", cmd.c_str());
  }
}

// =============================================================================
// PARSER JSON DARI HP ANDROID
// =============================================================================
void processIncomingData(const String& jsonStr) {
  #if ARDUINOJSON_VERSION_MAJOR >= 7
    JsonDocument doc;
  #else
    StaticJsonDocument<512> doc;
  #endif

  DeserializationError error = deserializeJson(doc, jsonStr);
  if (error) {
    Serial.print(F("[JSON Error] "));
    Serial.println(error.f_str());
    return;
  }

  const char* type = doc["t"];
  if (!type) return;

  if (strcmp(type, "notif") == 0) {
    currentNotif.active = true;
    currentNotif.app = doc["app"] | "WA";
    currentNotif.from = doc["from"] | "Pengirim";
    currentNotif.msg = doc["msg"] | "";
    currentNotif.timestamp = millis();
    needRedraw = true;
    Serial.println("[Notif] " + currentNotif.from + ": " + currentNotif.msg);
  }
  else if (strcmp(type, "call") == 0) {
    const char* status = doc["status"] | "";
    if (strcmp(status, "incoming") == 0) {
      currentCall.active = true;
      currentCall.caller = doc["name"] | "Panggilan Masuk";
    } else {
      currentCall.active = false;
    }
    needRedraw = true;
    Serial.println("[Call] Status: " + String(status));
  }
  else if (strcmp(type, "media") == 0) {
    currentMedia.title = doc["title"] | "Tidak Ada Lagu";
    currentMedia.artist = doc["artist"] | "";
    currentMedia.isPlaying = doc["playing"] | false;
    needRedraw = true;
    Serial.println("[Media] " + currentMedia.title + " by " + currentMedia.artist);
  }
}

// =============================================================================
// UI RENDERING DENGAN LOVYANGFX
// =============================================================================
// Dimensi Layar Landscape: 320 x 240
#define COLOR_BG        0x0821  // Dark navy background
#define COLOR_TOPBAR    0x18C3  // Slightly lighter bar
#define COLOR_GREEN     0x07E0
#define COLOR_RED       0xF800
#define COLOR_ORANGE    0xFD20
#define COLOR_WHITE     0xFFFF
#define COLOR_GRAY      0x8410
#define COLOR_BTN_BG    0x2124
#define COLOR_BTN_PRESS 0x4A69

// Koordinat Tombol di Bawah (Y: 184 s/d 236)
struct TouchBtn {
  int x;
  int y;
  int w;
  int h;
  const char* label;
  const char* act;
};

TouchBtn buttons[5] = {
  { 6,   185, 56, 48, "|<<",  "prev"     },
  { 68,  185, 56, 48, ">/||", "toggle"   },
  { 130, 185, 56, 48, ">>|",  "next"     },
  { 194, 185, 56, 48, "V -",  "vol_down" },
  { 256, 185, 56, 48, "V +",  "vol_up"   }
};

void drawHeader() {
  lcd.fillRect(0, 0, 320, 28, COLOR_TOPBAR);
  lcd.setTextColor(COLOR_WHITE, COLOR_TOPBAR);
  lcd.setTextSize(1);
  lcd.drawString("DisplayConnect+ CYD", 10, 8);

  // Status Koneksi BLE
  if (deviceConnected) {
    lcd.setTextColor(COLOR_GREEN, COLOR_TOPBAR);
    lcd.drawString("BLE: TERHUBUNG", 210, 8);
  } else {
    lcd.setTextColor(COLOR_RED, COLOR_TOPBAR);
    lcd.drawString("BLE: TERPUTUS", 215, 8);
  }
}

void drawButtons(int pressedIndex = -1) {
  for (int i = 0; i < 5; i++) {
    uint16_t bg = (i == pressedIndex) ? COLOR_BTN_PRESS : COLOR_BTN_BG;
    lcd.fillRoundRect(buttons[i].x, buttons[i].y, buttons[i].w, buttons[i].h, 6, bg);
    lcd.drawRoundRect(buttons[i].x, buttons[i].y, buttons[i].w, buttons[i].h, 6, COLOR_WHITE);

    lcd.setTextColor(COLOR_WHITE, bg);
    lcd.setTextSize(2);
    // Center label
    int tx = buttons[i].x + (buttons[i].w / 2) - (strlen(buttons[i].label) * 6);
    int ty = buttons[i].y + 16;
    lcd.drawString(buttons[i].label, tx, ty);
  }
}

void drawUI() {
  lcd.startWrite();
  lcd.fillScreen(COLOR_BG);
  drawHeader();

  // KONDISI 1: PANGGILAN TELEPON MASUK (Prioritas Tertinggi)
  if (currentCall.active) {
    lcd.fillRoundRect(15, 36, 290, 138, 8, COLOR_RED);
    lcd.drawRoundRect(15, 36, 290, 138, 8, COLOR_WHITE);

    lcd.setTextColor(COLOR_WHITE, COLOR_RED);
    lcd.setTextSize(2);
    lcd.drawString("! PANGGILAN MASUK !", 35, 48);

    lcd.setTextSize(3);
    lcd.drawString(currentCall.caller.substring(0, 15), 35, 80);

    lcd.setTextSize(1);
    lcd.drawString("Sentuh layar jika sudah selesai / tutup", 35, 140);
  }
  // KONDISI 2: NOTIFIKASI WHATSAPP
  else if (currentNotif.active) {
    lcd.fillRoundRect(15, 36, 290, 138, 8, 0x03E0); // Hijau WhatsApp
    lcd.drawRoundRect(15, 36, 290, 138, 8, COLOR_WHITE);

    lcd.setTextColor(COLOR_WHITE, 0x03E0);
    lcd.setTextSize(2);
    lcd.drawString("[ WhatsApp ]", 28, 46);

    lcd.setTextSize(2);
    lcd.drawString(currentNotif.from.substring(0, 18), 28, 72);

    lcd.setTextSize(1);
    lcd.setTextColor(0xFFE0, 0x03E0); // Kuning terang untuk pesan
    String preview = currentNotif.msg.substring(0, 75);
    lcd.drawString(preview.substring(0, 36), 28, 102);
    if (preview.length() > 36) {
      lcd.drawString(preview.substring(36), 28, 120);
    }
  }
  // KONDISI 3: MEDIA CONTROLLER (TAMPILAN UTAMA)
  else {
    lcd.fillRoundRect(15, 36, 290, 138, 8, COLOR_BTN_BG);
    lcd.drawRoundRect(15, 36, 290, 138, 8, COLOR_GRAY);

    // Status Play / Pause Badge
    if (currentMedia.isPlaying) {
      lcd.fillRoundRect(25, 46, 75, 20, 4, COLOR_GREEN);
      lcd.setTextColor(TFT_BLACK, COLOR_GREEN);
      lcd.setTextSize(1);
      lcd.drawString("PLAYING", 38, 52);
    } else {
      lcd.fillRoundRect(25, 46, 75, 20, 4, COLOR_ORANGE);
      lcd.setTextColor(TFT_BLACK, COLOR_ORANGE);
      lcd.setTextSize(1);
      lcd.drawString("PAUSED", 42, 52);
    }

    // Judul Lagu
    lcd.setTextColor(COLOR_WHITE, COLOR_BTN_BG);
    lcd.setTextSize(2);
    String title = currentMedia.title.length() > 0 ? currentMedia.title : "Tidak Ada Lagu";
    lcd.drawString(title.substring(0, 18), 25, 78);

    // Nama Artis
    lcd.setTextColor(0xAD55, COLOR_BTN_BG); // Light gray
    lcd.setTextSize(2);
    String artist = currentMedia.artist.length() > 0 ? currentMedia.artist : "Menunggu HP...";
    lcd.drawString(artist.substring(0, 20), 25, 110);

    lcd.setTextSize(1);
    lcd.drawString("Gunakan tombol touch di bawah untuk kontrol", 25, 148);
  }

  // Gambar Tombol Navigasi Media
  drawButtons();
  lcd.endWrite();
  needRedraw = false;
}

// =============================================================================
// HANDLE TOUCH INPUT
// =============================================================================
void handleTouch() {
  uint16_t tx, ty;
  if (!lcd.getTouch(&tx, &ty)) return;

  // Debounce sentuhan (300 ms)
  if (millis() - lastTouchTime < 300) return;
  lastTouchTime = millis();

  // Jika sedang ada notifikasi atau panggilan, tap di area atas menutup notif
  if (ty < 180) {
    if (currentNotif.active) {
      currentNotif.active = false;
      needRedraw = true;
      return;
    }
    if (currentCall.active) {
      currentCall.active = false;
      needRedraw = true;
      return;
    }
  }

  // Cek apakah menyentuh salah satu dari 5 tombol media
  for (int i = 0; i < 5; i++) {
    if (tx >= buttons[i].x && tx <= (buttons[i].x + buttons[i].w) &&
        ty >= buttons[i].y && ty <= (buttons[i].y + buttons[i].h)) {
      
      // Animasi feedback tombol ditekan
      drawButtons(i);
      
      // Kirim perintah JSON via BLE ke Android
      String cmd = String("{\"cmd\":\"media\",\"act\":\"") + buttons[i].act + "\"}";
      sendBleCommand(cmd);

      delay(120);
      drawButtons(-1); // Kembalikan warna normal
      break;
    }
  }
}

// =============================================================================
// SETUP
// =============================================================================
void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n--- DisplayConnect+ CYD Memulai ---");

  // Inisialisasi Layar & Touch
  lcd.init();
  lcd.setRotation(1); // Landscape 320x240
  lcd.setBrightness(200); // Kecerahan Layar (0-255)
  lcd.fillScreen(COLOR_BG);

  // Splash Screen Awal
  lcd.setTextColor(COLOR_WHITE, COLOR_BG);
  lcd.setTextSize(2);
  lcd.drawString("DisplayConnect+", 70, 90);
  lcd.setTextSize(1);
  lcd.setTextColor(COLOR_GREEN, COLOR_BG);
  lcd.drawString("Inisialisasi BLE...", 110, 130);

  // Inisialisasi BLE Device
  BLEDevice::init("DisplayConnect_CYD");
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  // Setup Service Nordic UART Service (NUS)
  BLEService *pService = pServer->createService(SERVICE_UUID);

  // TX Characteristic (Notify ke Android)
  pTxCharacteristic = pService->createCharacteristic(
                        CHARACTERISTIC_UUID_TX,
                        BLECharacteristic::PROPERTY_NOTIFY
                      );
  pTxCharacteristic->addDescriptor(new BLE2902());

  // RX Characteristic (Menerima dari Android)
  BLECharacteristic *pRxCharacteristic = pService->createCharacteristic(
                                           CHARACTERISTIC_UUID_RX,
                                           BLECharacteristic::PROPERTY_WRITE
                                         );
  pRxCharacteristic->setCallbacks(new MyCallbacks());

  // Mulai Service & Advertising
  pService->start();
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();

  Serial.println("[BLE] Siap dipasangkan! Menunggu koneksi dari HP...");
  delay(1000);

  drawUI();
}

// =============================================================================
// MAIN LOOP
// =============================================================================
void loop() {
  // 1. Tangani Data JSON yang Masuk dari Android
  if (hasNewData) {
    hasNewData = false;
    processIncomingData(rxBuffer);
  }

  // 2. Auto Dismiss Notifikasi WhatsApp setelah 7 detik
  if (currentNotif.active && (millis() - currentNotif.timestamp > 7000)) {
    currentNotif.active = false;
    needRedraw = true;
  }

  // 3. Tangani Putus/Sambung BLE
  if (!deviceConnected && oldDeviceConnected) {
    delay(500);
    pServer->startAdvertising(); // Mulai broadcast ulang otomatis
    Serial.println("[BLE] Advertising ulang...");
    oldDeviceConnected = deviceConnected;
    needRedraw = true;
  }
  if (deviceConnected && !oldDeviceConnected) {
    oldDeviceConnected = deviceConnected;
    needRedraw = true;
  }

  // 4. Update Tampilan jika ada perubahan state
  if (needRedraw) {
    drawUI();
  }

  // 5. Cek Sentuhan Touch Screen
  handleTouch();

  delay(20); // Delay kecil agar CPU ESP32 dingin
}
