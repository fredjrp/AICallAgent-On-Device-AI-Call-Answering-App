# AICallAgent — On-Device AI Call Answering Assistant

`AICallAgent` is a privileged Android telephony application designed to run on a dedicated, rooted Qualcomm Android device. When a voice call arrives (such as via carrier-level call forwarding from a Safaricom line), the app:

1. **Auto-answers** the call immediately via `InCallService`.
2. **Captures caller voice audio** in real-time through a privileged `AudioRecord` tap (`VOICE_CALL` / `VOICE_DOWNLINK`).
3. **Transcribes** caller speech locally on-device using Whisper (`whisper.cpp`).
4. **Queries** OpenRouter's API for intelligent conversation response.
5. **Synthesizes** voice locally on-device using Kokoro / Piper TTS.
6. **Plays** the synthesized audio through the phone's earpiece so the device mic picks it up, seamlessly responding to the caller.

---

## 📱 Device Requirements

- **Chipset**: Qualcomm Snapdragon (recommended for standard audio HAL behavior).
- **Root**: Magisk v24+ installed and active.
- **Android Version**: Android 10 (API 29) through Android 15 (API 35).
- **Permissions Required**:
  - `android.permission.CAPTURE_AUDIO_OUTPUT` (granted via Magisk priv-app allowlist)
  - `android.permission.CONTROL_INCALL_EXPERIENCE` (granted via Magisk priv-app allowlist)
  - `android.permission.RECORD_AUDIO` (granted in app setup)
  - Default Dialer role (`RoleManager.ROLE_DIALER`)

---

## 🛠️ Flashing & Installation via Magisk

1. **Build the Magisk Module Zip**:
   ```bash
   ./gradlew packageMagiskModule
   ```
   The flashable zip is generated at:
   `app/build/outputs/magisk/AICallAgent-magisk.zip`

2. **Flash onto Device**:
   - Transfer `AICallAgent-magisk.zip` to the phone:
     ```bash
     adb push app/build/outputs/magisk/AICallAgent-magisk.zip /sdcard/Download/
     ```
   - Open the **Magisk App** on the device.
   - Go to **Modules** → **Install from storage**.
   - Select `AICallAgent-magisk.zip` and flash.
   - **Reboot the device**.

3. **Verify Privileged Permissions**:
   After rebooting, check that the priv-app permissions were recognized:
   ```bash
   adb shell dumpsys package com.aicall.agent | grep -E "CAPTURE_AUDIO_OUTPUT|CONTROL_INCALL_EXPERIENCE"
   ```
   Both permissions must show as granted.

4. **Set as Default Dialer**:
   - Open `AICallAgent` from the app launcher.
   - Tap **"Grant Role"** under **System & Privileges** to set AICallAgent as the Default Dialer.

---

## 🔑 OpenRouter API Key Setup

1. Open `AICallAgent`.
2. Scroll to the **Agent Configuration** bento card.
3. Enter your OpenRouter API key (`sk-or-v1-...`).
4. Tap **Save Key**. The key is encrypted and stored in the hardware-backed Android Keystore via `EncryptedSharedPreferences`.

---

## 🧪 Testing Phase 1 & 2 Milestones

### Phase 1: Auto-Answer Validation
1. Enable **Auto-Answer Calls** switch in the UI.
2. Dial the target phone number from another phone.
3. Verify the call is automatically answered within 1-2 seconds without manual swipe.
4. Tap **"End Call"** in the Hero Card to verify programmatic call termination.

### Phase 2: Privileged Audio Capture Validation
1. Tap **"Test HAL (3s)"** in the UI to perform a quick 3-second capture test.
2. Alternatively, place a real test call, speak for 5-10 seconds, and hang up.
3. Pull the recorded WAV file to inspect audio quality:
   ```bash
   adb pull /sdcard/Android/data/com.aicall.agent/files/recordings/ ./test_recordings/
   ```
4. Verify the recording contains clean caller voice audio.
