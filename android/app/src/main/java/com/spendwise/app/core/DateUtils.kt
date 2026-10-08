package com.spendwise.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

fun LocalDate.startMillis(zone: ZoneId): Long = atStartOfDay(zone).toInstant().toEpochMilli()

fun Long.toLocalDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

/** Material date pickers speak "UTC midnight"; convert to/from a plain date to avoid off-by-one days. */
fun utcPickerMillisToDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()

fun LocalDate.toUtcPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** Half-open [fromMs, toMs): the end is the first instant of the NEXT month, so no ms is double counted. */
data class MonthBounds(val fromMs: Long, val toMs: Long)

fun YearMonth.bounds(zone: ZoneId) = MonthBounds(
    fromMs = atDay(1).startMillis(zone),
    toMs = plusMonths(1).atDay(1).startMillis(zone),
)
