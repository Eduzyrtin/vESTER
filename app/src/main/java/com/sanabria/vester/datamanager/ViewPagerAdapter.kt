// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester.datamanager

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.sanabria.vester.databinding.OnboardingPageBinding

/** Página de la presentación inicial. */
data class ScreenItem(@StringRes val title: Int, @StringRes val description: Int, @DrawableRes val image: Int)

/** Páginas de la presentación inicial para el ViewPager2. */
class ViewPagerAdapter(private val screens: List<ScreenItem>) : RecyclerView.Adapter<ViewPagerAdapter.PageViewHolder>() {

    override fun getItemCount() = screens.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        PageViewHolder(OnboardingPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) = with(holder.binding) {
        val screen = screens[position]
        introTitle.setText(screen.title)
        introDescription.setText(screen.description)
        introIMG.setImageResource(screen.image)
    }

    class PageViewHolder(val binding: OnboardingPageBinding) : RecyclerView.ViewHolder(binding.root)
}
