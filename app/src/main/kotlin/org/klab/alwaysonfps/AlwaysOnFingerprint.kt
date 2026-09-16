package org.klab.alwaysonfps

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Modifier

class AlwaysOnFingerprint : XposedModule() {

    private companion object {
        const val TAG = "AlwaysOnFPS"
    }

    private var authRippleController: Any? = null

    @SuppressLint("PrivateApi", "BlockedPrivateApi")
    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        val packageName = param.packageName
        val classLoader = param.classLoader

        if (packageName == "com.android.settings") {
            try {
                val clazz1 = classLoader.loadClass("com.android.settings.biometrics.fingerprint.FingerprintSettingsScreenOffUnlockUdfpsPreferenceController")
                clazz1.declaredMethods.find { it.name == "getAvailabilityStatus" }?.let {
                    hook(it).intercept(ConstantHooker(0))
                }
                clazz1.declaredMethods.find { it.name == "isAvailable" }?.let {
                    hook(it).intercept(ConstantHooker(true))
                }
                log(Log.INFO, TAG, "Hooked FingerprintSettingsScreenOffUnlockUdfpsPreferenceController")
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "Failed to hook FingerprintSettingsScreenOffUnlockUdfpsPreferenceController", t)
            }

            try {
                val clazz2 = classLoader.loadClass("com.android.settings.biometrics.fingerprint.FingerprintSettings\$FingerprintSettingsFragment")
                clazz2.declaredMethods.find {
                    it.name == "isScreenOffUnlockSupported" || it.name == "isScreenOffUnlcokSupported"
                }?.let {
                    hook(it).intercept(ConstantHooker(true))
                }
                log(Log.INFO, TAG, "Hooked FingerprintSettingsFragment")
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "Failed to hook FingerprintSettingsFragment", t)
            }
        }

        try {
            val clazz = classLoader.loadClass("android.hardware.display.AmbientDisplayConfiguration")
            clazz.declaredMethods.filter { it.name == "screenOffUdfpsEnabled" }.forEach { method ->
                hook(method).intercept(AmbientDisplayHooker())
            }
            clazz.declaredMethods.filter { it.name == "udfpsLongPressSensorType" }.forEach { method ->
                hook(method).intercept(ConstantHooker("com.google.sensor.long_press"))
            }
            log(Log.INFO, TAG, "Hooked AmbientDisplayConfiguration")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook AmbientDisplayConfiguration", t)
        }

        hookDeviceConfig(classLoader)

        if (packageName == "com.android.systemui") {
            try {
                val rippleClazz = classLoader.loadClass("com.android.systemui.biometrics.AuthRippleController")
                rippleClazz.declaredMethods.find { it.name == "onViewAttached" }?.let {
                    hook(it).intercept(AuthRippleControllerHooker())
                }
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "Failed to hook AuthRippleController", t)
            }

            try {
                val unlockClazz = classLoader.loadClass("com.android.systemui.statusbar.phone.BiometricUnlockController")
                val authMethod = unlockClazz.declaredMethods.find { it.name == "onBiometricAuthenticated" }
                if (authMethod != null) {
                    hook(authMethod).intercept(BiometricUnlockHooker())
                }
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "Failed to hook BiometricUnlockController", t)
            }

            try {
                val faceInteractorClazz = classLoader.loadClass("com.android.systemui.deviceentry.domain.interactor.SystemUIDeviceEntryFaceAuthInteractor")
                val runFaceAuthMethod = faceInteractorClazz.declaredMethods.find {
                    it.name == "runFaceAuth" && it.parameterCount == 2
                }
                if (runFaceAuthMethod != null) {
                    hook(runFaceAuthMethod).intercept(DisableFaceUnlockDuringUnlockHooker())
                    log(Log.INFO, TAG, "Hooked runFaceAuth to prevent camera flicker")
                }
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "Failed to hook Face Auth Interactor", t)
            }

            try {
                val clazz = classLoader.loadClass("com.android.keyguard.KeyguardUpdateMonitor")
                clazz.declaredMethods.find { it.name == "isFingerprintDetectionRunning" }?.let {
                    hook(it).intercept(KeyguardUpdateMonitorHooker())
                    log(Log.INFO, TAG, "Hooked KeyguardUpdateMonitor")
                }
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "Failed to hook KeyguardUpdateMonitor", t)
            }

            try {
                val clazz = classLoader.loadClass("com.android.systemui.biometrics.UdfpsController")
                val method = clazz.declaredMethods.find { m ->
                    m.name == "onFingerDown" && m.parameterCount >= 10
                }
                if (method != null) {
                    hook(method).intercept(UdfpsControllerHooker())
                    log(Log.INFO, TAG, "Hooked UdfpsController")
                }
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "Failed to hook UdfpsController", t)
            }
        }
    }

    private fun hookDeviceConfig(classLoader: ClassLoader) {
        try {
            val deviceConfigClazz = classLoader.loadClass("android.provider.DeviceConfig")
            for (method in deviceConfigClazz.declaredMethods) {
                if (Modifier.isStatic(method.modifiers)) {
                    when (method.name) {
                        "getBoolean" -> if (method.parameterCount == 3) {
                            hook(method).intercept(DeviceConfigBooleanHooker())
                        }
                        "getString" -> if (method.parameterCount == 3) {
                            hook(method).intercept(DeviceConfigStringHooker())
                        }
                        "getProperty" -> if (method.parameterCount == 2) {
                            hook(method).intercept(DeviceConfigPropertyHooker())
                        }
                        "getInt" -> if (method.parameterCount == 3) {
                            hook(method).intercept(DeviceConfigIntHooker())
                        }
                    }
                }
            }
            log(Log.INFO, TAG, "Hooked DeviceConfig")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook DeviceConfig", t)
        }

        try {
            val propertiesClazz = classLoader.loadClass("android.provider.DeviceConfig\$Properties")
            for (method in propertiesClazz.declaredMethods) {
                when (method.name) {
                    "getBoolean" -> if (method.parameterCount == 2) {
                        hook(method).intercept(PropertiesBooleanHooker())
                    }
                    "getString" -> if (method.parameterCount == 2) {
                        hook(method).intercept(PropertiesStringHooker())
                    }
                    "getInt" -> if (method.parameterCount == 2) {
                        hook(method).intercept(PropertiesIntHooker())
                    }
                }
            }
            log(Log.INFO, TAG, "Hooked DeviceConfig.Properties")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook DeviceConfig.Properties", t)
        }
    }

    private fun isScreenOffUdfpsEnabled(context: Context): Boolean {
        return try {
            val value = Settings.Secure.getInt(context.contentResolver, "screen_off_udfps_enabled", -1)
            if (value == -1) {
                try {
                    Settings.Secure.putInt(context.contentResolver, "screen_off_udfps_enabled", 1)
                } catch (_: Throwable) {}
                true
            } else {
                value == 1
            }
        } catch (_: Throwable) {
            true
        }
    }

    inner class ConstantHooker(private val value: Any) : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any = value
    }

    inner class DeviceConfigBooleanHooker : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any {
            val namespace = chain.getArg(0) as? String
            val name = chain.getArg(1) as? String
            if (namespace == "biometrics" && name == "screen_off_udfps_enabled") {
                return true
            }
            return chain.proceed()
        }
    }

    inner class DeviceConfigStringHooker : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val namespace = chain.getArg(0) as? String
            val name = chain.getArg(1) as? String
            if (namespace == "biometrics" && name == "screen_off_udfps_enabled") {
                return "true"
            }
            if (namespace == "latency_tracker" && name == "refresh_rate_switching_policy") {
                return "1"
            }
            return chain.proceed()
        }
    }

    inner class DeviceConfigPropertyHooker : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val namespace = chain.getArg(0) as? String
            val name = chain.getArg(1) as? String
            if (namespace == "biometrics" && name == "screen_off_udfps_enabled") {
                return "true"
            }
            if (namespace == "latency_tracker" && name == "refresh_rate_switching_policy") {
                return "1"
            }
            return chain.proceed()
        }
    }

    inner class DeviceConfigIntHooker : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any {
            val namespace = chain.getArg(0) as? String
            val name = chain.getArg(1) as? String
            if (namespace == "latency_tracker" && name == "refresh_rate_switching_policy") {
                return 1
            }
            return chain.proceed()
        }
    }

    inner class PropertiesBooleanHooker : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any {
            val name = chain.getArg(0) as? String
            if (name == "screen_off_udfps_enabled") {
                return true
            }
            return chain.proceed()
        }
    }

    inner class PropertiesStringHooker : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val name = chain.getArg(0) as? String
            if (name == "screen_off_udfps_enabled") {
                return "true"
            }
            if (name == "refresh_rate_switching_policy") {
                return "1"
            }
            return chain.proceed()
        }
    }

    inner class PropertiesIntHooker : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any {
            val name = chain.getArg(0) as? String
            if (name == "refresh_rate_switching_policy") {
                return 1
            }
            return chain.proceed()
        }
    }

    inner class AmbientDisplayHooker : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val instance = chain.thisObject ?: return chain.proceed()
            try {
                val mContextField = instance.javaClass.getDeclaredField("mContext").apply { isAccessible = true }
                val context = mContextField.get(instance) as Context
                if (isScreenOffUdfpsEnabled(context)) return true
            } catch (e: Exception) {
                log(Log.ERROR, TAG, "Failed in AmbientDisplayHooker", e)
            }
            return chain.proceed()
        }
    }

    inner class DisableFaceUnlockDuringUnlockHooker : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val instance = chain.thisObject // SystemUIDeviceEntryFaceAuthInteractor
            try {
                val kumField = instance.javaClass.getDeclaredField("keyguardUpdateMonitor").apply { isAccessible = true }
                val kum = kumField.get(instance)

                val mDeviceInteractiveField = kum.javaClass.getDeclaredField("mDeviceInteractive").apply { isAccessible = true }
                if (!mDeviceInteractiveField.getBoolean(kum)) return null

                val mKeyguardGoingAwayField = kum.javaClass.getDeclaredField("mKeyguardGoingAway").apply { isAccessible = true }
                if (mKeyguardGoingAwayField.getBoolean(kum)) return null

                val authControllerField = kum.javaClass.getDeclaredField("mAuthController").apply { isAccessible = true }
                val authController = authControllerField.get(kum)
                val udfpsControllerField = authController.javaClass.getDeclaredField("mUdfpsController").apply { isAccessible = true }
                val udfpsController = udfpsControllerField.get(authController)
                if (udfpsController != null) {
                    val mOnFingerDownField = udfpsController.javaClass.getDeclaredField("mOnFingerDown").apply { isAccessible = true }
                    if (mOnFingerDownField.getBoolean(udfpsController)) return null
                }
            } catch (e: Exception) {
                log(Log.ERROR, TAG, "Failed in DisableFaceUnlockDuringUnlockHooker", e)
            }
            return chain.proceed()
        }
    }

    inner class AuthRippleControllerHooker : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            authRippleController = chain.thisObject
            return chain.proceed()
        }
    }

    inner class BiometricUnlockHooker : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val biometricSourceType = chain.getArg(1)
            val result = chain.proceed()
            if (biometricSourceType.toString() == "FINGERPRINT" && authRippleController != null) {
                try {
                    val controllerClass = authRippleController!!.javaClass
                    val showRippleMethod = controllerClass.declaredMethods.find {
                        it.name.contains("showUnlockRippleInternal") || it.name == "showUnlockedRipple"
                    }
                    if (showRippleMethod != null) {
                        showRippleMethod.isAccessible = true
                        if (Modifier.isStatic(showRippleMethod.modifiers)) showRippleMethod.invoke(null, authRippleController, biometricSourceType)
                        else showRippleMethod.invoke(authRippleController)
                    }
                } catch (e: Exception) {
                    log(Log.ERROR, TAG, "Failed in BiometricUnlockHooker", e)
                }
            }
            return result
        }
    }

    inner class KeyguardUpdateMonitorHooker : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            if (result as? Boolean == true) return true

            val instance = chain.thisObject ?: return result
            try {
                var userId = 0
                try {
                    val interactorField = instance.javaClass.getDeclaredField("mSelectedUserInteractor").apply { isAccessible = true }
                    val interactor = interactorField.get(instance)
                    val getUserIdMethod = interactor.javaClass.getDeclaredMethod("getSelectedUserId")
                    userId = getUserIdMethod.invoke(interactor) as Int
                } catch (e: Exception) {
                    try {
                        val getSelectedUserIdMethod = instance.javaClass.getDeclaredMethod("getSelectedUserId")
                        userId = getSelectedUserIdMethod.invoke(instance) as Int
                    } catch (e2: Exception) {
                        userId = 0
                    }
                }

                val mFaceMethod = instance.javaClass.declaredMethods.find { it.name == "getUserFaceAuthenticated" }
                if (mFaceMethod != null) {
                    mFaceMethod.isAccessible = true
                    if (mFaceMethod.invoke(instance, userId) as Boolean) return result
                }

                val context = instance.javaClass.getDeclaredField("mContext").apply { isAccessible = true }.get(instance) as Context
                if (isScreenOffUdfpsEnabled(context)) {
                    val isInteractive = instance.javaClass.getDeclaredField("mDeviceInteractive").apply { isAccessible = true }.getBoolean(instance)
                    if (!isInteractive) {
                        return true
                    }
                }
            } catch (e: Exception) {
                log(Log.ERROR, TAG, "Error in KeyguardUpdateMonitorHooker", e)
            }
            return result
        }
    }

    inner class UdfpsControllerHooker : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val instance = chain.thisObject
            if (instance != null) {
                try {
                    val clazz = instance.javaClass
                    val context = clazz.getDeclaredField("mContext").apply { isAccessible = true }.get(instance) as Context
                    if (isScreenOffUdfpsEnabled(context)) {
                        try {
                            clazz.getDeclaredField("mIgnoreRefreshRate").apply { isAccessible = true }.setBoolean(instance, true)
                        } catch (e: Exception) {
                            log(Log.ERROR, TAG, "Failed to set mIgnoreRefreshRate", e)
                        }
                        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                        if (!powerManager.isInteractive) {
                            try {
                                clazz.getDeclaredField("mIsAodInterruptActive").apply { isAccessible = true }.setBoolean(instance, true)
                            } catch (e: Exception) {
                                log(Log.ERROR, TAG, "Failed to set mIsAodInterruptActive", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    log(Log.ERROR, TAG, "Failed to apply UdfpsController hooks", e)
                }
            }
            return chain.proceed()
        }
    }
}