package com.spendwise.app.data.repo

import com.spendwise.app.core.toLocalDate
import com.spendwise.app.data.local.ExpenseEntity
import com.spendwise.app.data.local.SpentAmount
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class DayGroup(val date: LocalDate, val items: List<ExpenseEntity>, val totalMinor: Long)

fun groupByDay(items: List<ExpenseEntity>, zone: ZoneId): List<DayGroup> =
    items.groupBy { it.spentAt.toLocalDate(zone) }
        .map { (date, list) -> DayGroup(date, list, list.sumOf { it.amountMinor }) }
        .sortedByDescending { it.date }

data class MonthTotal(val month: YearMonth, val totalMinor: Long)

/** Always returns exactly [months] buckets (zeros included) so the chart axis doesn't jump around. */
fun buildTrend(amounts: List<SpentAmount>, endMonth: YearMonth, zone: ZoneId, months: Int = 6): List<MonthTotal> {
    val byMonth = amounts
        .groupBy { YearMonth.from(it.spentAt.toLocalDate(zone)) }
        .mapValues { (_, v) -> v.sumOf { it.amountMinor } }
    return (months - 1 downTo 0).map { back ->
        val m = endMonth.minusMonths(back.toLong())
        MonthTotal(m, byMonth[m] ?: 0L)
    }
}

enum class AlertLevel { NONE, WARN, OVER }

/** 80% warns, 100% is over. Integer math only (no float rounding at the boundary). */
fun alertLevel(spentMinor: Long, limitMinor: Long): AlertLevel = when {
    limitMinor <= 0 -> AlertLevel.NONE
    spentMinor >= limitMinor -> AlertLevel.OVER
    spentMinor * 100 >= limitMinor * 80 -> AlertLevel.WARN
    else -> AlertLevel.NONE
}
