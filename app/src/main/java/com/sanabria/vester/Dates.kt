// Copyright (C) 2022-2026 Eduardo Sanabria Gago
// SPDX-License-Identifier: GPL-3.0-only

package com.sanabria.vester

import android.content.ContextWrapper
import android.content.Context
import android.text.format.DateUtils
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputLayout
import java.text.DateFormat
import java.util.Calendar
import com.sanabria.vester.tools.AppSettings
import java.util.Date
import java.util.TimeZone

/** Inicio (00:00) del día de [timeMillis] en la zona horaria del móvil. */
fun startOfDay(timeMillis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timeMillis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Inicio del día siguiente a [dayStart] (respeta los cambios de hora). */
fun nextDay(dayStart: Long): Long = Calendar.getInstance().apply {
    timeInMillis = dayStart
    add(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

/**
 * Inicio (00:00) del mes de presupuestos que contiene a [timeMillis]. Empieza el día elegido en los ajustes
 * ([AppSettings.monthStartDay], el 1 por defecto): con el 25, el "mes" de octubre va del 25 de septiembre al 24 de octubre.
 */
fun startOfMonth(timeMillis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDay(timeMillis)
    val startDay = AppSettings.monthStartDay
    if (get(Calendar.DAY_OF_MONTH) < startDay) add(Calendar.MONTH, -1)
    set(Calendar.DAY_OF_MONTH, startDay)
}.timeInMillis

/** [timeMillis] desplazado [months] meses (negativo hacia atrás). */
fun addMonths(timeMillis: Long, months: Int): Long = Calendar.getInstance().apply {
    timeInMillis = timeMillis
    add(Calendar.MONTH, months)
}.timeInMillis

/** Inicio del mes siguiente al que empieza en [monthStart]. */
fun nextMonth(monthStart: Long): Long = addMonths(monthStart, 1)

/** El día de [dayMillis] con la hora actual, para que los movimientos del mismo día queden ordenados. */
fun withCurrentTime(dayMillis: Long): Date {
    val now = Calendar.getInstance()
    return Date(Calendar.getInstance().apply {
        timeInMillis = dayMillis
        set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY))
        set(Calendar.MINUTE, now.get(Calendar.MINUTE))
        set(Calendar.SECOND, now.get(Calendar.SECOND))
        set(Calendar.MILLISECOND, now.get(Calendar.MILLISECOND))
    }.timeInMillis)
}

/** "2 dic 2022". */
fun formatDate(date: Date): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(date)

/** "2 dic 2022 17:22". */
fun formatDateLongTime(date: Date): String =
    "${formatDate(date)} ${DateFormat.getTimeInstance(DateFormat.SHORT).format(date)}"

/** "2/12/22 17:22". */
fun formatDateTime(date: Date): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(date)


/** "17:22". */
fun formatTime(date: Date): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(date)

/** Título de un día en las listas: "Hoy", "Ayer" o "lun, 12 oct" (con el año si no es el actual). */
fun formatDayHeader(context: Context, dayStart: Long): String {
    val today = startOfDay(System.currentTimeMillis())
    if (dayStart == today) return context.getString(R.string.today)
    if (dayStart == startOfDay(today - 1)) return context.getString(R.string.yesterday)
    val sameYear = Calendar.getInstance().apply { timeInMillis = dayStart }.get(Calendar.YEAR) ==
        Calendar.getInstance().get(Calendar.YEAR)
    var flags = DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL
    if (!sameYear) flags = flags or DateUtils.FORMAT_SHOW_YEAR
    return DateUtils.formatDateTime(context, dayStart, flags)
}

/**
 * Convierte un TextView (o el campo de texto de un campo Material) en un selector de fecha: muestra la fecha y,
 * al pulsarlo, abre el calendario de Material. Devuelve una función para leer la fecha elegida (en milisegundos).
 *
 * @param futureOnly true para que solo se puedan elegir días posteriores a hoy (por ejemplo, la próxima vez de un
 *   movimiento programado).
 */
fun TextView.bindDatePicker(initial: Long = System.currentTimeMillis(), futureOnly: Boolean = false): () -> Long {
    var selected = initial
    text = formatDate(Date(selected))
    val open = open@{
        val activity = context.findActivity() as? AppCompatActivity ?: return@open
        val builder = MaterialDatePicker.Builder.datePicker().setSelection(localDayToUtc(selected))
        if (futureOnly) {
            val tomorrow = localDayToUtc(nextDay(startOfDay(System.currentTimeMillis())))
            builder.setCalendarConstraints(
                CalendarConstraints.Builder().setStart(tomorrow).setValidator(DateValidatorPointForward.from(tomorrow)).build()
            )
        }
        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { utcDay ->
            selected = utcDayToLocal(utcDay, keepTimeOf = selected)
            text = formatDate(Date(selected))
        }
        picker.show(activity.supportFragmentManager, "datePicker")
    }
    setOnClickListener { open() }
    generateSequence(parent) { it.parent }.filterIsInstance<TextInputLayout>().firstOrNull()?.setEndIconOnClickListener { open() }
    return { selected }
}

/** El calendario de Material trabaja en UTC: el día local de [localMillis] como medianoche UTC. */
private fun localDayToUtc(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** Lo contrario: el día elegido (medianoche UTC) como día local, con la hora de [keepTimeOf]. */
private fun utcDayToLocal(utcMillis: Long, keepTimeOf: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        timeInMillis = keepTimeOf
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private tailrec fun Context.findActivity(): Context? = when (this) {
    is AppCompatActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Como [bindDatePicker] pero con fecha opcional: el campo empieza vacío si [initial] es null. Una vez elegida una fecha,
 * el icono del campo pasa a ser una cruz que la quita. Devuelve una función para leer la fecha (null si está vacío).
 */
fun TextView.bindOptionalDatePicker(initial: Long?): () -> Long? {
    var selected = initial
    val layout = generateSequence(parent) { it.parent }.filterIsInstance<TextInputLayout>().firstOrNull()
    fun refresh() {
        text = selected?.let { formatDate(Date(it)) }.orEmpty()
        layout?.setEndIconDrawable(if (selected == null) R.drawable.ic_m_calendar else R.drawable.ic_m_close)
        layout?.endIconContentDescription = if (selected == null) null else context.getString(R.string.clear_date)
    }
    val open = open@{
        val activity = context.findActivity() as? AppCompatActivity ?: return@open
        val picker = MaterialDatePicker.Builder.datePicker()
            .setSelection(localDayToUtc(selected ?: System.currentTimeMillis()))
            .build()
        picker.addOnPositiveButtonClickListener { utcDay ->
            selected = utcDayToLocal(utcDay, keepTimeOf = selected ?: System.currentTimeMillis())
            refresh()
        }
        picker.show(activity.supportFragmentManager, "optionalDatePicker")
    }
    setOnClickListener { open() }
    layout?.setEndIconOnClickListener {
        if (selected == null) {
            open()
        } else {
            selected = null
            refresh()
        }
    }
    refresh()
    return { selected }
}

/** Inicio del mes natural (día 1, 00:00) de [timeMillis], sin tener en cuenta el día de inicio de los presupuestos. */
fun calendarMonthStart(timeMillis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDay(timeMillis)
    set(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis
