// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Window
import androidx.activity.viewModels
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import com.sanabria.vester.databinding.ActivityAccountTransactionBinding
import com.sanabria.vester.datamanager.DataViewModel

/**
 * Detalle de una cuenta en su propia pantalla (móvil). Se abre con una transición en la que la tarjeta de la cuenta
 * se "abre" hasta ocupar la pantalla; el contenido es el de [AccountDetail].
 */
class AccountTransaction : BaseActivity() {

    private lateinit var binding: ActivityAccountTransactionBinding
    private val dataViewModel: DataViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())
        super.onCreate(savedInstanceState)
        binding = ActivityAccountTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // La pantalla entera es el elemento compartido con la tarjeta de la principal
        findViewById<android.view.View>(android.R.id.content).transitionName = TRANSITION_NAME
        val transform = { MaterialContainerTransform().apply {
            addTarget(android.R.id.content)
            duration = 320
            scrimColor = Color.TRANSPARENT
            fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
        } }
        window.sharedElementEnterTransition = transform()
        window.sharedElementReturnTransition = transform()

        val detail = AccountDetail(this, binding.detail, dataViewModel, onDeleted = { finish() }, onColor = { setStatusBarColor(it) })
        detail.select(intent.getIntExtra(EXTRA_ACCOUNT_ID, -1))
    }

    companion object {
        const val TRANSITION_NAME = "account_detail"
        private const val EXTRA_ACCOUNT_ID = "accountId"

        fun intent(context: Context, accountId: Int): Intent =
            Intent(context, AccountTransaction::class.java).putExtra(EXTRA_ACCOUNT_ID, accountId)
    }
}
