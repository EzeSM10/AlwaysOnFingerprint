# Always-On Fingerprint

Un módulo de LSPosed que restaura el desbloqueo por huella digital con la pantalla apagada para dispositivos Google Pixel con sensor óptico.

[🇺🇸 Read in English](README.md)

---

## 📖 Descripción

A partir de la versión **Android 16 QPR3**, Google eliminó por completo la capacidad de usar el sensor óptico de huellas dactilares bajo pantalla (UDFPS) mientras la pantalla está apagada. Incluso la conocida solución alternativa mediante ADB:
```sh
adb shell settings put secure screen_off_udfps_enabled 1
```
dejó de funcionar por sí sola debido a nuevas restricciones en el framework y a banderas del lado del servidor gestionadas por `DeviceConfig` (Phenotype).

**Always-On Fingerprint** elude estas limitaciones artificiales, devolviendo la experiencia fluida de desbloqueo con "pantalla apagada" a tus dispositivos Pixel sin necesidad de scripts de arranque en segundo plano ni comandos manuales por ADB.

---

## ⚙️ Cómo Funciona (La Solución Técnica)

En Android 16 QPR3, Google introdujo múltiples capas de bloqueo contra el desbloqueo con pantalla apagada en sensores ópticos. Este módulo neutraliza todas ellas directamente en memoria a través de LSPosed:

1. **Hook del controlador de preferencias en Ajustes:**
   * Intercepta `FingerprintSettingsFragment.isScreenOffUnlockSupported()` y `FingerprintSettingsScreenOffUnlockUdfpsPreferenceController.getAvailabilityStatus()` para restaurar el interruptor nativo **"Desbloqueo con huella y pantalla apagada"** dentro de la app de Ajustes del sistema.

2. **Interceptación de `DeviceConfig` en memoria (Sin necesidad de scripts de inicio):**
   * Intercepta las llamadas a `android.provider.DeviceConfig` y `DeviceConfig.Properties` en `com.android.systemui` y `com.android.settings`:
     * `biometrics / screen_off_udfps_enabled` $\rightarrow$ devuelve `true`
     * `latency_tracker / refresh_rate_switching_policy` $\rightarrow$ devuelve `1`
   * Esto anula los restablecimientos automáticos que hace Google Play Services y elimina la necesidad de scripts shell en `/data/adb/service.d/` o comandos periódicos de `killall com.android.systemui`.

3. **Sesión de huella activa durante la suspensión:**
   * Intercepta `KeyguardUpdateMonitor.isFingerprintDetectionRunning()` para mantener activa la escucha del hardware biométrico (HAL) incluso cuando el teléfono entra en reposo profundo (`!mDeviceInteractive`).

4. **Detección táctil inmediata con pantalla apagada y tasa de refresco:**
   * Intercepta `UdfpsController.onFingerDown()` para activar `mIsAodInterruptActive` y evitar retrasos en el cambio de tasa de refresco de la pantalla (`mIgnoreRefreshRate`), permitiendo la iluminación óptica instantánea al apoyar el dedo.

5. **Auto-inicialización y persistencia de preferencias del usuario:**
   * Inicializa automáticamente `screen_off_udfps_enabled` en `Settings.Secure` en el arranque, respetando al mismo tiempo la decisión del usuario si decide apagar el interruptor desde los Ajustes.

6. **Detalles de interfaz y experiencia de usuario:**
   * Intercepta `SystemUIDeviceEntryFaceAuthInteractor` para evitar que la cámara de desbloqueo facial parpadee al tocar el lector en la oscuridad.
   * Restaura la animación de ondas (ripple) mediante `AuthRippleController`.

---

## 🚀 Instalación

1. Instala el APK (`app-debug.apk` o la versión más reciente).
2. Activa el módulo **Always-On Fingerprint** en **LSPosed Manager**.
3. Asegúrate de que **System UI** (`com.android.systemui`) y **Ajustes** (`com.android.settings`) estén seleccionados en el alcance (*scope*) del módulo.
4. Reinicia tu dispositivo.
5. (Opcional) Dirígete a *Ajustes $\rightarrow$ Seguridad y privacidad $\rightarrow$ Bloqueo del dispositivo $\rightarrow$ Huella digital* para ver o gestionar el interruptor **Desbloqueo con huella y pantalla apagada**.

---

## 📱 Compatibilidad

* **Dispositivos:** Pixel 6, 6 Pro, 6a, 7, 7 Pro, 7a, Fold, 8, 8 Pro, 8a (todos los dispositivos Pixel con sensor óptico UDFPS)
* **Sistema operativo:** Android 16 QPR3+ (Firmware oficial Stock de Google Pixel)

---

## 🤝 Créditos y Agradecimientos

* **Proyecto Original:** [AlwaysOnFingerprint](https://github.com/klab7/AlwaysOnFingerprint) por [klab7](https://github.com/klab7)
* **Correcciones, interceptación de `DeviceConfig` y optimizaciones:** Desarrollado por **Antigravity** (Google DeepMind) en pair-programming con **[@derxan](https://github.com/derxan)**.
