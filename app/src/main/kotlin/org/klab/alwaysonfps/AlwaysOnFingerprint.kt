package org.klab.alwaysonfps

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Field
import java.lang.reflect.Modifier

class AlwaysOnFingerprint : XposedModule() {

    private companion object {
        const val TAG = "AlwaysOnFPS"
    }

    private var authRippleController: Any? = null

    private fun getFieldRecursively(clazz: Class<*>?, name: String): Field? {
        var current = clazz
        while (current != null && current != Any::class.java) {
            try {
                val field = current.getDeclaredField(name)
                field.isAccessible = true
                return field
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun getFieldValue(obj: Any?, name: String): Any? {
        if (obj == null) return null
        return try {
            getFieldRecursively(obj.javaClass, name)?.get(obj)
        } catch (t: Throwable) {
            null
        }
    }

    private fun setFieldValue(obj: Any?, name: String, value: Any?): Boolean {
        if (obj == null) return false
        return try {
            getFieldRecursively(obj.javaClass, name)?.set(obj, value)
            true
        } catch (t: Throwable) {
            false
        }
    }

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
                Log.i(TAG, "Hooked FingerprintSettingsScreenOffUnlockUdfpsPreferenceController")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to hook FingerprintSettingsScreenOffUnlockUdfpsPreferenceController", t)
            }

            try {
                val clazz2 = classLoader.loadClass("com.android.settings.biometrics.fingerprint.FingerprintSettings\$FingerprintSettingsFragment")
                clazz2.declaredMethods.find {
                    it.name == "isScreenOffUnlockSupported" || it.name == "isScreenOffUnlcokSupported"
                }?.let {
                    hook(it).intercept(ConstantHooker(true))
                }
                Log.i(TAG, "Hooked FingerprintSettingsFragment")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to hook FingerprintSettingsFragment", t)
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
            Log.i(TAG, "Hooked AmbientDisplayConfiguration in $packageName")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook AmbientDisplayConfiguration in $packageName", t)
        }

        hookDeviceConfig(classLoader)

        if (packageName == "com.android.systemui") {
            try {
                val rippleClazz = classLoader.loadClass("com.android.systemui.biometrics.AuthRippleController")
                rippleClazz.declaredMethods.find { it.name == "onViewAttached" }?.let {
                    hook(it).intercept(AuthRippleControllerHooker())
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to hook AuthRippleController", t)
            }

            try {
                val unlockClazz = classLoader.loadClass("com.android.systemui.statusbar.phone.BiometricUnlockController")
                val authMethod = unlockClazz.declaredMethods.find { it.name == "onBiometricAuthenticated" }
                if (authMethod != null) {
                    hook(authMethod).intercept(BiometricUnlockHooker())
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to hook BiometricUnlockController", t)
            }

            try {
                val faceInteractorClazz = classLoader.loadClass("com.android.systemui.deviceentry.domain.interactor.SystemUIDeviceEntryFaceAuthInteractor")
                val runFaceAuthMethod = faceInteractorClazz.declaredMethods.find {
                    it.name == "runFaceAuth" && it.parameterCount == 2
                }
                if (runFaceAuthMethod != null) {
                    hook(runFaceAuthMethod).intercept(DisableFaceUnlockDuringUnlockHooker())
                    Log.i(TAG, "Hooked runFaceAuth to prevent camera flicker")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to hook Face Auth Interactor", t)
            }

            try {
                val clazz = classLoader.loadClass("com.android.keyguard.KeyguardUpdateMonitor")
                clazz.declaredMethods.filter { it.name == "isFingerprintDetectionRunning" }.forEach {
                    hook(it).intercept(KeyguardUpdateMonitorHooker())
                }
                clazz.declaredMethods.filter { it.name == "shouldListenForFingerprint" }.forEach {
                    hook(it).intercept(ShouldListenForFingerprintHooker())
                }
                Log.i(TAG, "Hooked KeyguardUpdateMonitor (isFingerprintDetectionRunning & shouldListenForFingerprint)")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to hook KeyguardUpdateMonitor", t)
            }

            try {
                val clazz = classLoader.loadClass("com.android.systemui.biometrics.UdfpsController")
                clazz.declaredMethods.filter { m ->
                    m.name == "onFingerDown" && m.parameterCount >= 8
                }.forEach { method ->
                    hook(method).intercept(UdfpsControllerHooker())
                }
                Log.i(TAG, "Hooked UdfpsController.onFingerDown")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to hook UdfpsController", t)
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
            Log.i(TAG, "Hooked DeviceConfig")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook DeviceConfig", t)
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
            Log.i(TAG, "Hooked DeviceConfig.Properties")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook DeviceConfig.Properties", t)
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
        override fun intercept(chain: XposedInterface.Chain): Any = true
    }

    inner class ShouldListenForFingerprintHooker : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            if (result as? Boolean == true) return true
            val instance = chain.thisObject ?: return result
            val isInteractive = getFieldValue(instance, "mDeviceInteractive") as? Boolean
            if (isInteractive == false) {
                return true
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
                val isInteractive = getFieldValue(instance, "mDeviceInteractive") as? Boolean
                if (isInteractive == false) {
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in KeyguardUpdateMonitorHooker", e)
            }
            return result
        }
    }

    inner class DisableFaceUnlockDuringUnlockHooker : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val instance = chain.thisObject
            try {
                val kum = getFieldValue(instance, "keyguardUpdateMonitor")
                if (kum != null) {
                    val mDeviceInteractive = getFieldValue(kum, "mDeviceInteractive") as? Boolean
                    if (mDeviceInteractive == false) return null

                    val mKeyguardGoingAway = getFieldValue(kum, "mKeyguardGoingAway") as? Boolean
                    if (mKeyguardGoingAway == true) return null

                    val authController = getFieldValue(kum, "mAuthController")
                    if (authController != null) {
                        val udfpsController = getFieldValue(authController, "mUdfpsController")
                        if (udfpsController != null) {
                            val mOnFingerDown = getFieldValue(udfpsController, "mOnFingerDown") as? Boolean
                            if (mOnFingerDown == true) return null
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed in DisableFaceUnlockDuringUnlockHooker", e)
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
                    Log.e(TAG, "Failed in BiometricUnlockHooker", e)
                }
            }
            return result
        }
    }

    inner class UdfpsControllerHooker : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val instance = chain.thisObject
            if (instance != null) {
                try {
                    setFieldValue(instance, "mIgnoreRefreshRate", true)
                    val context = getFieldValue(instance, "mContext") as? Context
                    val powerManager = context?.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                    if (powerManager?.isInteractive == false) {
                        setFieldValue(instance, "mIsAodInterruptActive", true)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed in UdfpsControllerHooker", e)
                }
            }
            return chain.proceed()
        }
    }
}