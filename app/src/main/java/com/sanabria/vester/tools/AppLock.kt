// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.tools

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.sanabria.vester.R

/**
 * Bloqueo de la app con huella, cara o el bloqueo de pantalla del móvil.
 *
 * La app queda bloqueada al abrirla y cuando pasa más de [GRACE_MILLIS] en segundo plano. Cada pantalla que se
 * muestra con la app bloqueada se tapa con un fondo opaco ([guard]) hasta que se desbloquea.
 */
object AppLock {
    private const val GRACE_MILLIS = 20_000L
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    private var locked = true
    private var startedActivities = 0
    private var backgroundedAt = 0L
    private var promptShowing = false

    /** Sigue las pantallas abiertas para saber cuándo la app pasa a segundo plano. */
    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (startedActivities == 0 && System.currentTimeMillis() - backgroundedAt > GRACE_MILLIS) locked = true
                startedActivities++
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities--
                if (startedActivities == 0) {
                    backgroundedAt = System.currentTimeMillis()
                    // Al salir de la app los importes vuelven a ocultarse (modo discreto)
                    AppSettings.revealed = false
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /** true si el móvil tiene huella, cara o bloqueo de pantalla con el que desbloquear. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** Se acaba de activar el bloqueo (ya autenticado): no hace falta volver a pedirlo ahora. */
    fun markUnlocked() {
        locked = false
    }

    /** Si hace falta, tapa [activity] y pide desbloquear. Se llama desde el `onStart` de cada pantalla. */
    fun guard(activity: AppCompatActivity) {
        if (!AppSettings.lockEnabled || !locked) return
        if (!isAvailable(activity)) {
            // El usuario quitó el bloqueo de pantalla del móvil: no se puede pedir nada, así que no se encierra a nadie
            locked = false
            return
        }
        val overlay = buildOverlay(activity) { prompt(activity, it) }
        activity.addContentView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        prompt(activity, overlay)
    }

    private fun prompt(activity: AppCompatActivity, overlay: View) {
        if (promptShowing) return
        promptShowing = true
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.unlock_title))
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptShowing = false
                    locked = false
                    (overlay.parent as? ViewGroup)?.removeView(overlay)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Cancelado: se queda el fondo con el botón "Desbloquear" para volver a intentarlo
                    promptShowing = false
                }
            },
        ).authenticate(info)
    }

    private fun buildOverlay(activity: AppCompatActivity, retry: (View) -> Unit): View {
        val density = activity.resources.displayMetrics.density
        val background = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorSurface, 0)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(
                TextView(activity).apply {
                    text = activity.getString(R.string.locked_title)
                    setTextAppearance(R.style.TextAppearance_App_Headline)
                    setTextColor(MaterialColors.getColor(this, android.R.attr.textColorPrimary))
                    gravity = Gravity.CENTER
                }
            )
        }
        val overlay = FrameLayout(activity).apply {
            setBackgroundColor(background)
            isClickable = true
            isFocusable = true
            addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        content.addView(
            MaterialButton(activity).apply {
                text = activity.getString(R.string.unlock)
                setIconResource(R.drawable.ic_m_lock)
                setOnClickListener { retry(overlay) }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (24 * density).toInt()
            },
        )
        return overlay
    }
}
