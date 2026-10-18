// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sanabria.vester.databinding.ActivityGoalsBinding
import com.sanabria.vester.databinding.ItemGoalBinding
import com.sanabria.vester.datamanager.DataViewModel
import com.sanabria.vester.datamanager.combine
import com.sanabria.vester.room.Goal
import com.sanabria.vester.tools.bindEmpty
import com.sanabria.vester.tools.showWhen
import com.sanabria.vester.tools.smoothAnimations
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Una meta con lo ahorrado hasta ahora y su ritmo.
 *
 * @property current Céntimos ahorrados: el saldo de la cuenta enlazada o lo apuntado a mano.
 * @property monthlyPace Lo que se ahorra de media al mes (las últimas semanas) si la meta tiene cuenta; null si no se sabe.
 */
data class GoalRow(val goal: Goal, val current: Long, val monthlyPace: Long?)

/**
 * Metas de ahorro. Las enlazadas a una cuenta (una hucha, por ejemplo) se miden con su saldo y calculan cuándo se
 * alcanzarán al ritmo de los últimos tres meses; las demás se rellenan apuntando dinero a mano con "Añadir".
 */
class Goals : BaseActivity() {

    private lateinit var binding: ActivityGoalsBinding
    private val dataViewModel: DataViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGoalsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, getString(R.string.goals))

        val adapter = GoalAdapter(
            onClick = { startActivity(GoalForm.intent(this, it.goal.goalId)) },
            onAdd = ::askAmount,
        )
        binding.recyclerGoals.adapter = adapter
        binding.recyclerGoals.layoutManager = LinearLayoutManager(this)
        binding.recyclerGoals.smoothAnimations()
        binding.emptyView.bindEmpty(R.drawable.ic_m_flag, R.string.no_goals_title, R.string.no_goals_hint)
        binding.fab.setOnClickListener { startActivity(GoalForm.intent(this)) }

        combine(dataViewModel.goals, dataViewModel.allAccounts) { goals, accounts -> goals to accounts.associateBy { it.accountId } }
            .observe(this) { (goals, accounts) ->
                binding.emptyView.showWhen(goals.isEmpty())
                lifecycleScope.launch {
                    val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(PACE_DAYS)
                    val rows = goals.map { goal ->
                        val account = goal.accountId?.let { accounts[it] }
                        if (account == null) {
                            GoalRow(goal, goal.saved, null)
                        } else {
                            val net = dataViewModel.accountNetSince(account.accountId, since)
                            GoalRow(goal, account.total.coerceAtLeast(0), net * 30 / PACE_DAYS)
                        }
                    }
                    adapter.submitList(rows)
                }
            }
    }

    /** Pide cuánto se añade a una meta apuntada a mano. */
    private fun askAmount(row: GoalRow) {
        val density = resources.displayMetrics.density
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.value)
            setupMoneyInput()
        }
        val holder = FrameLayout(this).apply {
            val margin = (24 * density).toInt()
            setPadding(margin, (8 * density).toInt(), margin, 0)
            addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this)
            .setTitle(row.goal.label)
            .setView(holder)
            .setPositiveButton(R.string.add) { _, _ ->
                val amount = parseMoney(input.text)
                if (amount != null && amount > 0) dataViewModel.addToGoal(row.goal.goalId, amount)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private class GoalAdapter(
        private val onClick: (GoalRow) -> Unit,
        private val onAdd: (GoalRow) -> Unit,
    ) : ListAdapter<GoalRow, GoalAdapter.GoalViewHolder>(DIFF) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            GoalViewHolder(ItemGoalBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: GoalViewHolder, position: Int) = holder.bind(getItem(position), onClick, onAdd)

        class GoalViewHolder(private val binding: ItemGoalBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(row: GoalRow, onClick: (GoalRow) -> Unit, onAdd: (GoalRow) -> Unit) = with(binding) {
                val context = root.context
                val goal = row.goal
                goalIcon.text = goal.icon.ifEmpty { "🎯" }
                goalIcon.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ColorUtils.setAlphaComponent(goal.color, 0x33))
                }
                goalName.text = goal.name
                goalAmounts.text = context.getString(R.string.goal_progress, formatMoney(row.current), formatMoney(goal.target))
                val percent = if (goal.target > 0) (row.current * 100 / goal.target).toInt() else 0
                goalPercent.text = "$percent%"
                goalProgress.setIndicatorColor(goal.color)
                goalProgress.setProgressCompat(percent.coerceIn(0, 100), true)

                val reached = row.current >= goal.target
                goalStatus.text = when {
                    reached -> context.getString(R.string.goal_reached)
                    goal.targetDate != null -> context.getString(
                        R.string.goal_save_per_month,
                        formatMoney(neededPerMonth(goal.target - row.current, goal.targetDate)),
                        formatDate(goal.targetDate),
                    )
                    else -> context.getString(R.string.goal_missing, formatMoney(goal.target - row.current))
                }
                val pace = row.monthlyPace
                goalForecast.visibility = if (reached || pace == null) View.GONE else View.VISIBLE
                if (!reached && pace != null) {
                    goalForecast.text = if (pace > 0) {
                        context.getString(R.string.goal_pace, formatMonthYear(monthsFromNow((goal.target - row.current + pace - 1) / pace)))
                    } else {
                        context.getString(R.string.goal_no_pace)
                    }
                }
                // Con cuenta enlazada el dinero lo mueven los movimientos; sin cuenta se apunta a mano
                goalAdd.visibility = if (goal.accountId == null && !reached) View.VISIBLE else View.GONE
                goalAdd.setOnClickListener { onAdd(row) }
                goalCard.setOnClickListener { onClick(row) }
            }
        }

        private companion object {
            val DIFF = object : DiffUtil.ItemCallback<GoalRow>() {
                override fun areItemsTheSame(oldItem: GoalRow, newItem: GoalRow) = oldItem.goal.goalId == newItem.goal.goalId
                override fun areContentsTheSame(oldItem: GoalRow, newItem: GoalRow) = oldItem == newItem
            }
        }
    }

    private companion object {
        /** Días de movimientos que se miran para calcular el ritmo de ahorro (tres meses). */
        const val PACE_DAYS = 90L
    }
}

/** Lo que hay que ahorrar cada mes ([remaining] céntimos) para llegar a [targetDate]; como poco, el mes entero. */
fun neededPerMonth(remaining: Long, targetDate: Date): Long {
    val days = TimeUnit.MILLISECONDS.toDays(targetDate.time - System.currentTimeMillis()).coerceAtLeast(30)
    val months = (days + 29) / 30
    return (remaining + months - 1) / months
}

/** Fecha dentro de [months] meses desde hoy. */
fun monthsFromNow(months: Long): Date = Calendar.getInstance().apply {
    add(Calendar.MONTH, months.coerceIn(0, 1200).toInt())
}.time

/** "mar 2027". */
fun formatMonthYear(date: Date): String = java.text.SimpleDateFormat("LLL yyyy", java.util.Locale.getDefault()).format(date)
