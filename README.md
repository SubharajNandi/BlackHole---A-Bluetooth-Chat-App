# BlackHole - A Bluetooth chat App 📡💬

**Messaging that works with no Internet — only Bluetooth.** Two phones within Bluetooth range can talk to each other, completely offline.

Built with **Kotlin** and **Bluetooth Classic (RFCOMM)** — a true peer-to-peer connection with no server, no SIM, and no Wi-Fi required.

---

## ✨ Features

- **100% offline** peer-to-peer messaging over Bluetooth Classic RFCOMM
- **Automatic server mode** — the app listens for incoming connections on launch
- **Device discovery** — scan for and pick nearby Bluetooth devices to connect to
- **Real-time chat** between two devices in range (~10 m)
- Works on Android 6.0 (API 23) through the latest Android versions
- Modern Android toolchain: Gradle 9.5, AGP 9.3.2, Kotlin, View Binding

---

## 🧠 How it works

Bluetooth messaging is a **server/client model**:

| Role | What it does |
|------|--------------|
| **Server** | Runs automatically when the app opens — it listens on a known service UUID for incoming connections |
| **Client** | Taps **Scan**, picks a device from the list, and dials in via RFCOMM |

Both devices must use the **same service UUID** (`BluetoothChatService.kt`), which acts like a "phone number" for the chat service. Once the socket is established, messages stream back and forth as UTF-8 bytes over `InputStream` / `OutputStream`.

```
┌─────────────┐   Bluetooth Classic RFCOMM   ┌─────────────┐
│   Phone A   │  ◄──────────────────────────► │   Phone B   │
│  (listens)  │          (same UUID)          │  (connects) │
└─────────────┘                               └─────────────┘
```

### Three key threads (`BluetoothChatService.kt`)
- **`AcceptThread`** — the server: waits for an incoming connection
- **`ConnectThread`** — the client: opens the RFCOMM socket to the remote device
- **`ConnectedThread`** — manages the open socket: reads incoming bytes, writes outgoing bytes

---

## 🚀 Getting started

### Prerequisites
- [Android Studio](https://developer.android.com/studio) (bundles the JDK + Gradle)
- Two Android devices (or an emulator with Bluetooth support) — **one device is enough for lower-bound testing; two are needed for real chat**

### Build
```bash
# Windows
gradlew.bat :app:assembleDebug

# macOS / Linux
./gradlew :app:assembleDebug
```
The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

### Install & chat
1. Install the APK on **both** phones.
2. Open the app on each — grant the Bluetooth permission prompt.
3. On **phone A**: tap **Scan for devices**, then pick **phone B** (pair once if prompted).
4. Start typing — messages appear instantly on both screens. 📴 No internet needed.

> **Note:** On Android 11 and below, device discovery also needs Location permission. On Android 12+, the modern Bluetooth permission model is used.

---

## 📁 Project structure

```
BluetoothChat/
├── gradle/                              # Gradle 9.5.0 wrapper
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml          # Permissions + activities
│       ├── java/com/example/bluetoothchat/
│       │   ├── BluetoothChatService.kt  # RFCOMM server/client/connected threads
│       │   ├── DeviceListActivity.kt    # Scan + pick nearby devices
│       │   └── MainActivity.kt          # Chat UI + message handler
│       └── res/                         # Layouts, theme, strings, launcher icons
```

Full tree: see [`PROJECT_STRUCTURE.txt`](PROJECT_STRUCTURE.txt)

---

## 🛠 Tech stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| Build | Gradle 9.5.0 · Android Gradle Plugin 9.3.2 |
| UI | XML layouts + View Binding |
| Bluetooth | Bluetooth Classic RFCOMM (`BluetoothAdapter`, `BluetoothServerSocket`, `BluetoothSocket`) |
| Min / Target SDK | 23 / 37 |

---

## 🔐 Permissions used

```xml
<!-- Android 12+ -->
BLUETOOTH_SCAN · BLUETOOTH_CONNECT · BLUETOOTH_ADVERTISE

<!-- Android 11 and below -->
BLUETOOTH · BLUETOOTH_ADMIN · ACCESS_FINE_LOCATION
```

---

## 🎓 Why this project is interesting

- Demonstrates **real device-to-device networking without infrastructure** — a core concept with many real-world uses (emergency comms, IoT, field work, offline local chat).
- Exercises **concurrent programming**: three threads managing listening, connecting, and streaming.
- Shows **modern Android permission handling** across two permission models.
- Pure **Kotlin + Android SDK** — no proprietary back-end or subscription costs.

---

## 📄 License
MIT

## OwnerShip and Author
Subharaj Nandi | 
AIML Student at Sanskriti University batch of '28

