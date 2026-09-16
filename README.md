# Always-On Fingerprint

An LSPosed module that restores screen-off fingerprint unlocking for Google Pixel devices equipped with optical sensors.

---

## 📖 Description

Starting with the **Android 16 QPR3** release, Google completely removed the ability to use optical fingerprint scanners (UDFPS) while the screen is turned off. Even the traditional ADB workaround:
```sh
adb shell settings put secure screen_off_udfps_enabled 1
```
stopped working on its own due to new framework-level restrictions and server-side `DeviceConfig` (Phenotype) flags.

**Always-On Fingerprint** bypasses these artificial restrictions, seamlessly restoring the "screen-off" unlock experience to your Pixel devices without requiring any background boot scripts or manual ADB intervention.

---

## ⚙️ How It Works (The Technical Solution)

In Android 16 QPR3, Google introduced multiple layers of barriers against optical screen-off unlocking. This module neutralizes all of them directly in memory via LSPosed:

1. **Settings Preference Controller Hook:**
   - Hooks `FingerprintSettingsFragment.isScreenOffUnlockSupported()` and `FingerprintSettingsScreenOffUnlockUdfpsPreferenceController.getAvailabilityStatus()` to restore the native **"Screen-off Fingerprint Unlock"** toggle in the system Settings app.

2. **In-Memory `DeviceConfig` Interception (No Boot Scripts Needed):**
   - Intercepts calls to `android.provider.DeviceConfig` and `DeviceConfig.Properties` within `com.android.systemui` and `com.android.settings`:
     - `biometrics / screen_off_udfps_enabled` $\rightarrow$ returns `true`
     - `latency_tracker / refresh_rate_switching_policy` $\rightarrow$ returns `1`
   - This bypasses Google Play Services resets and eliminates the need for `/data/adb/service.d/` shell scripts or periodic `killall com.android.systemui` commands.

3. **Active Fingerprint Session in Sleep Mode:**
   - Intercepts `KeyguardUpdateMonitor.isFingerprintDetectionRunning()` to keep the fingerprint HAL detection active even when the device enters deep sleep (`!mDeviceInteractive`).

4. **Instant Screen-Off Touch & Refresh Rate Handling:**
   - Hooks `UdfpsController.onFingerDown()` to activate `mIsAodInterruptActive` and bypass display refresh rate switching bottlenecks (`mIgnoreRefreshRate`), allowing immediate optical illumination upon finger touch.

5. **Auto-Initialization & User Preference Persistence:**
   - Automatically initializes `screen_off_udfps_enabled` in `Settings.Secure` on boot while still respecting the user's manual toggle if disabled through Settings.

6. **Polish & UX Fixes:**
   - Hooks `SystemUIDeviceEntryFaceAuthInteractor` to prevent face unlock camera flicker when unlocking via fingerprint in the dark.
   - Restores the unlock ripple animation through `AuthRippleController`.

---

## 🚀 Installation

1. Install the APK (`app-debug.apk` or latest release).
2. Enable the **Always-On Fingerprint** module in **LSPosed Manager**.
3. Ensure **System UI** (`com.android.systemui`) and **Settings** (`com.android.settings`) are selected in the module's scope.
4. Reboot your device.
5. (Optional) Go to *Settings $\rightarrow$ Security & Privacy $\rightarrow$ Device Unlock $\rightarrow$ Fingerprint* to view or manage the **Screen-off Fingerprint Unlock** toggle.

---

## 📱 Compatibility

- **Devices:** Pixel 6, 6 Pro, 6a, 7, 7 Pro, 7a, Fold, 8, 8 Pro, 8a (all Pixel devices with optical UDFPS)
- **OS:** Android 16 QPR3+ (Stock Google Pixel Firmware)

---

## 🤝 Credits & Acknowledgements

- **Original Project:** [AlwaysOnFingerprint](https://github.com/EzeSM10/AlwaysOnFingerprint) by EzeSM10 / KLab
- **Fixes, In-Memory `DeviceConfig` Interception & Enhancements:** Engineered by **Antigravity** (Google DeepMind) in pair-programming with **[@derxan](https://github.com/derxan)**.
