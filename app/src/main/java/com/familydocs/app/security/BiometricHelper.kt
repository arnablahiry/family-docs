package com.familydocs.app.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricHelper {
    /** True when the phone itself has a screen lock (PIN, pattern, password). */
    fun deviceSecure(context: Context): Boolean =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isDeviceSecure

    /**
     * Asks for the phone's own screen lock (or a fingerprint enrolled on it): the second check, after Google,
     * before a forgotten Lararium passcode can be replaced.
     */
    fun confirmDevice(activity: FragmentActivity, onSuccess: () -> Unit, onFail: (String) -> Unit) {
        val allowed = if (android.os.Build.VERSION.SDK_INT >= 30) BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            else BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail(errString.toString())
        }).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Confirm it's you")
                .setSubtitle("Use this phone's screen lock to reset your Lararium passcode")
                .setAllowedAuthenticators(allowed)
                .build(),
        )
    }

    fun available(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(activity: FragmentActivity, title: String, onSuccess: () -> Unit, onFail: (String) -> Unit = {}) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail(errString.toString())
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("Use your fingerprint")
                .setNegativeButtonText("Use passcode")
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .build(),
        )
    }
}
