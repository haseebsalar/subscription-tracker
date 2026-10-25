package com.subtracker.app

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

/** Base for all screens. Adds the optional app lock (fingerprint, face, PIN, pattern). */
open class BaseActivity : AppCompatActivity() {

    private var overlay: View? = null

    override fun onStart() {
        super.onStart()
        if (!Prefs.lockEnabled(this) || !lockAvailable(this)) {
            removeOverlay()
            return
        }
        if (overlay != null) return
        val fresh = unlocked && SystemClock.elapsedRealtime() - leftAt < GRACE_MS
        if (!fresh) {
            unlocked = false
            showOverlay()
            authenticate()
        }
    }

    override fun onStop() {
        super.onStop()
        leftAt = SystemClock.elapsedRealtime()
    }

    private fun showOverlay() {
        val content = findViewById<ViewGroup>(android.R.id.content)
        val view = layoutInflater.inflate(R.layout.lock_overlay, content, false)
        view.findViewById<View>(R.id.unlockButton).setOnClickListener { authenticate() }
        content.addView(view)
        overlay = view
    }

    private fun removeOverlay() {
        overlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        overlay = null
    }

    private fun authenticate() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked = true
                    leftAt = SystemClock.elapsedRealtime()
                    removeOverlay()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_title))
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }

    companion object {
        private const val GRACE_MS = 30_000L
        private const val AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

        var unlocked = false
        var leftAt = 0L

        fun lockAvailable(context: android.content.Context): Boolean =
            BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
                BiometricManager.BIOMETRIC_SUCCESS

        /** Call when the user has just turned the lock on, so it does not lock them out at once. */
        fun markUnlocked() {
            unlocked = true
            leftAt = SystemClock.elapsedRealtime()
        }
    }
}
