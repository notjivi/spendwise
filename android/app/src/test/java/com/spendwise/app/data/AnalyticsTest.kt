package com.spendwise.app.data

import com.spendwise.app.data.local.SpentAmount
import com.spendwise.app.data.local.shouldApplyRemote
import com.spendwise.app.data.repo.AlertLevel
import com.spendwise.app.data.repo.alertLevel
import com.spendwise.app.data.repo.buildTrend
import com.spendwise.app.data.repo.escapeLike
import com.spendwise.app.data.repo.groupByDay
import com.spendwise.app.data.repo.nextTimestamp
import com.spendwise.app.expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class AnalyticsTest {
    private val utc = ZoneOffset.UTC
    private fun ms(date: String) = LocalDate.parse(date).atTime(12, 0).toInstant(utc).toEpochMilli()

    @Test fun groupsByDayNewestFirstWithTotals() {
        val groups = groupByDay(
            listOf(expense("a", amount = 100, spentAt = ms("2025-10-01")), expense("b", amount = 50, spentAt = ms("2025-10-02")), expense("c", amount = 25, spentAt = ms("2025-10-01"))),
            utc,
        )
        assertEquals(listOf(LocalDate.parse("2025-10-02"), LocalDate.parse("2025-10-01")), groups.map { it.date })
        assertEquals(listOf(50L, 125L), groups.map { it.totalMinor })
    }

    @Test fun trendAlwaysHasSixMonthsWithZeroFill() {
        val trend = buildTrend(listOf(SpentAmount(ms("2025-10-05"), 300), SpentAmount(ms("2025-08-10"), 100), SpentAmount(ms("2025-08-11"), 50)), YearMonth.of(2025, 10), utc)
        assertEquals(6, trend.size)
        assertEquals(YearMonth.of(2025, 5), trend.first().month)
        assertEquals(YearMonth.of(2025, 10), trend.last().month)
        assertEquals(listOf(0L, 0L, 0L, 150L, 0L, 300L), trend.map { it.totalMinor })
    }

    @Test fun alertThresholdsAtExactBoundaries() {
        assertEquals(AlertLevel.NONE, alertLevel(7_999, 10_000))
        assertEquals(AlertLevel.WARN, alertLevel(8_000, 10_000)) // exactly 80%
        assertEquals(AlertLevel.WARN, alertLevel(9_999, 10_000))
        assertEquals(AlertLevel.OVER, alertLevel(10_000, 10_000)) // exactly 100%
        assertEquals(AlertLevel.OVER, alertLevel(50_000, 10_000))
        assertEquals(AlertLevel.NONE, alertLevel(5_000, 0)) // no budget
    }

    @Test fun lastWriteWinsRule() {
        assertTrue(shouldApplyRemote(null, 5))
        assertTrue(shouldApplyRemote(5, 6))
        assertFalse("equal = same version", shouldApplyRemote(5, 5))
        assertFalse(shouldApplyRemote(6, 5))
    }

    @Test fun timestampsStrictlyIncreasePerRow() {
        assertEquals(100L, nextTimestamp(100, null))
        assertEquals(101L, nextTimestamp(100, 100)) // same-millisecond edit
        assertEquals(201L, nextTimestamp(100, 200)) // device clock went backwards
        assertEquals(100L, nextTimestamp(100, 99))
    }

    @Test fun likeWildcardsAreEscaped() = assertEquals("50\\% off\\_x\\\\", escapeLike("50% off_x\\"))
}
