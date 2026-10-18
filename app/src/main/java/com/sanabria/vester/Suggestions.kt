// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import com.sanabria.vester.tools.smoothAnimations
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sanabria.vester.databinding.ActivitySuggestionsBinding
import com.sanabria.vester.databinding.ItemSuggestionBinding
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.tools.attachSwipeActions
import com.sanabria.vester.tools.bindEmpty
import com.sanabria.vester.tools.showWhen
import com.sanabria.vester.room.Suggestion
import com.sanabria.vester.tools.PaymentNotificationListener

/**
 * Movimientos detectados en notificaciones (Google Wallet, bancos, Gmail...). Cada uno se puede apuntar, revisándolo
 * antes en el formulario de transacción, o descartar. Si falta el acceso a las notificaciones se explica cómo darlo.
 */
class Suggestions : BaseActivity() {

    private lateinit var binding: ActivitySuggestionsBinding
    private val dataViewModel: DataViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySuggestionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbarSuggestions, getString(R.string.suggestions))

        val adapter = SuggestionAdapter()
        binding.recyclerSuggestions.adapter = adapter
        binding.recyclerSuggestions.layoutManager = LinearLayoutManager(this)
        binding.recyclerSuggestions.smoothAnimations()
        // Mismos gestos que en el resto de listas: a la derecha se apunta y a la izquierda se descarta
        binding.recyclerSuggestions.attachSwipeActions(
            canSwipe = { it in 0 until adapter.itemCount },
            onSwipeLeft = { dataViewModel.deleteSuggestion(adapter.currentList[it].id) },
            onSwipeRight = { accept(adapter.currentList[it]) },
            leftIcon = R.drawable.ic_m_close,
            rightColor = R.color.positive,
            rightIcon = R.drawable.ic_m_check,
        )
        binding.emptyView.bindEmpty(R.drawable.ic_m_bell, R.string.no_suggestions_title, R.string.no_suggestions_hint)
        dataViewModel.suggestions.observe(this) { suggestions ->
            adapter.submitList(suggestions)
            binding.emptyView.showWhen(suggestions.isEmpty())
        }
        binding.grantAccess.setOnClickListener { openAccessSettings() }
    }

    override fun onResume() {
        super.onResume()
        // Puede volver de los ajustes con el acceso ya concedido
        binding.accessCard.visibility = if (PaymentNotificationListener.isEnabled(this)) View.GONE else View.VISIBLE
    }

    private fun openAccessSettings() {
        val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                PaymentNotificationListener.componentName(this).flattenToString(),
            )
        } else {
            null
        }
        val general = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        runCatching { startActivity(detail ?: general) }.onFailure { startActivity(general) }
    }

    private fun accept(suggestion: Suggestion) {
        startActivity(
            MakeTransaction.intent(
                this,
                amount = suggestion.value,
                concept = suggestion.concept,
                suggestionId = suggestion.id,
                date = suggestion.date.time,
            )
        )
    }

    private inner class SuggestionAdapter : ListAdapter<Suggestion, SuggestionAdapter.SuggestionViewHolder>(DIFF) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            SuggestionViewHolder(ItemSuggestionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) = with(holder.binding) {
            val suggestion = getItem(position)
            suggestionAmount.text = formatMoney(suggestion.value)
            suggestionAmount.setTextColor(
                ContextCompat.getColor(this@Suggestions, if (suggestion.value >= 0) R.color.positive else R.color.negative)
            )
            suggestionConcept.text = suggestion.concept
            suggestionSource.text = getString(R.string.date_and_account, formatDateTime(suggestion.date), suggestion.source)
            suggestionText.text = suggestion.text
            suggestionAccept.setOnClickListener { accept(suggestion) }
            suggestionDiscard.setOnClickListener { dataViewModel.deleteSuggestion(suggestion.id) }
        }

        inner class SuggestionViewHolder(val binding: ItemSuggestionBinding) : RecyclerView.ViewHolder(binding.root)
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Suggestion>() {
            override fun areItemsTheSame(oldItem: Suggestion, newItem: Suggestion) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Suggestion, newItem: Suggestion) = oldItem == newItem
        }
    }
}
