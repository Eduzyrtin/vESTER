// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.sanabria.vester.databinding.ActivityOnBoardingBinding
import com.sanabria.vester.datamanager.ScreenItem
import com.sanabria.vester.datamanager.ViewPagerAdapter

/**
 * Pantalla de entrada de la app. La primera vez enseña la presentación; las siguientes salta directamente a la
 * principal. Desde "Ayuda" se abre con [helpIntent] y al terminar simplemente se cierra.
 */
class OnBoarding : BaseActivity() {

    private lateinit var binding: ActivityOnBoardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Pantalla de inicio: el logo sobre el color de fondo, que se desvanece al abrir la app
        installSplashScreen().setOnExitAnimationListener { splash ->
            splash.view.animate().alpha(0f).scaleX(1.08f).scaleY(1.08f).setDuration(220)
                .withEndAction { splash.remove() }.start()
        }
        super.onCreate(savedInstanceState)
        val fromHelp = intent.getBooleanExtra(EXTRA_FROM_HELP, false)
        if (!fromHelp && introSeen()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        binding = ActivityOnBoardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setStatusBarColor(themeColor(android.R.attr.colorBackground))

        val screens = listOf(
            ScreenItem(R.string.multiple_accounts, R.string.accounts_description, R.drawable.account),
            ScreenItem(R.string.transactions_listed, R.string.transactions_description, R.drawable.transactions),
            ScreenItem(R.string.easy_visuals, R.string.easy_description, R.drawable.graphics),
            ScreenItem(R.string.keep_record, R.string.keep_description, R.drawable.records),
            ScreenItem(R.string.shortcuts, R.string.shortcuts_description, R.drawable.shortcuts),
        )
        binding.viewPager.adapter = ViewPagerAdapter(screens)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { _, _ -> }.attach()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val last = position == screens.lastIndex
                if (last && binding.buttonStarted.visibility != View.VISIBLE) {
                    binding.buttonStarted.startAnimation(AnimationUtils.loadAnimation(this@OnBoarding, R.anim.btn_started_anim))
                }
                binding.buttonStarted.visibility = if (last) View.VISIBLE else View.INVISIBLE
                binding.tabLayout.visibility = if (last) View.INVISIBLE else View.VISIBLE
            }
        })

        binding.buttonStarted.setOnClickListener {
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(PREF_INTRO_SEEN, true) }
            if (!fromHelp) startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun introSeen(): Boolean =
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(PREF_INTRO_SEEN, false)

    companion object {
        // Mismos nombres que en las versiones anteriores para no volver a enseñar la presentación al actualizar
        private const val PREFS = "myPrefs"
        private const val PREF_INTRO_SEEN = "isIntroOpened"
        private const val EXTRA_FROM_HELP = "fromHelp"

        fun helpIntent(context: Context): Intent =
            Intent(context, OnBoarding::class.java).putExtra(EXTRA_FROM_HELP, true)
    }
}
