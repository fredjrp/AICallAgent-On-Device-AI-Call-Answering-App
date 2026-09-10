# AICallAgent — On-Device AI Call Answering Assistant

`AICallAgent` is an intelligent Android telephony application designed to run on Android devices powered by **Shizuku** (no root, no bootloader unlocking, no data wipe required). When a voice call arrives (such as via carrier-level call forwarding from a Safaricom line), the app:

1. **Auto-answers** the call immediately via `InCallService`.
2. **Captures caller voice audio** in real-time through an ADB-level privileged `AudioRecord` tap (`VOICE_CALL` / `VOICE_DOWNLINK`) authorized via Shizuku.
3. **Transcribes** caller speech locally on-device using Whisper (`whisper.cpp`).
4. **Queries** OpenRouter's API for intelligent conversation response.
5. **Synthesizes** voice locally on-device using Kokoro / Piper TTS.
6. **Plays** the synthesized audio through the phone's earpiece so the device mic picks it up, seamlessly responding to the caller.

---

## 📱 Device Requirements

- **Android Version**: Android 10 (API 29) through Android 15 (API 35).
- **No Root Required**: Powered by Shizuku via ADB shell permissions.
- **Permissions Required**:
  - `android.permission.CAPTURE_AUDIO_OUTPUT` (granted via Shizuku ADB shell)
  - `android.permission.RECORD_AUDIO` (granted in app setup)
  - Default Dialer role (`RoleManager.ROLE_DIALER`)

---

## 🛠️ Installation & Shizuku Setup

1. **Install Shizuku**:
   - Install **Shizuku** from Google Play Store or [GitHub Releases](https://github.com/RikkaApps/Shizuku/releases).

2. **Pair Shizuku Once**:
   - **Option A — Wireless Debugging (Android 11+)**:
     1. Open phone **Settings** → **Developer Options** → Enable **Wireless Debugging**.
     2. Tap **Pair device with pairing code**.
     3. Open **Shizuku**, tap **Pairing**, and enter the 6-digit code.
     4. In Shizuku, tap **Start**.
   - **Option B — USB Debugging (One-time PC connection)**:
     1. Connect your phone to a computer with ADB installed.
     2. Run the command:
        ```bash
        adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
        ```

3. **Install AICallAgent**:
   - Install the signed APK normally (no Magisk, no recovery flashing, no data wipe).

4. **Grant Shizuku Permission**:
   - Open **AICallAgent**.
   - Under the **Shizuku Privileged Access** card, confirm the status shows **"Shizuku: Running & Authorized"** (or tap **"Grant Permission in Shizuku"** when prompted).

5. **Set Default Dialer Role**:
   - Tap **"Set Role"** under Telecom Default Dialer so AICallAgent can intercept and auto-answer calls.

> ℹ️ **Note after device reboot**: After rebooting your phone, reopen the Shizuku app and tap **Start** before AICallAgent can capture call audio.

---

## 🔑 OpenRouter API Key Setup

1. Open `AICallAgent` and switch to the **Settings** tab.
2. Enter your OpenRouter API key (`sk-or-v1-...`).
3. Tap **Save Key**. The key is securely encrypted and stored in the Android Keystore via `EncryptedSharedPreferences`.
4. Customize your AI Persona / System Prompt instructions as desired.

---

## 🧪 Testing Milestones

### Milestone 1: Auto-Answer Validation
1. Enable **Auto-Answer Calls** in the Assistant tab.
2. Dial the target phone number from another phone.
3. Verify the call is automatically answered within 1-2 seconds.

### Milestone 2: Audio Capture Validation
1. Place a test call and speak for 10-15 seconds.
2. Verify the call appears in the **History** tab with inline conversation transcription.
3. Check recorded WAV audio in local storage:
   ```bash
   adb pull /sdcard/Android/data/com.aicall.agent/files/recordings/ ./test_recordings/
   ```
