package com.spendwise.app.core

import com.spendwise.app.export.CsvExporter
import com.spendwise.app.expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class CsvExporterTest {
    private val utc = ZoneOffset.UTC

    @Test fun headerAndRowUseCrLfAndPlainDecimalAmount() {
        val csv = CsvExporter.toCsv(listOf(expense("a", amount = 45_050, spentAt = 0)), utc)
        val lines = csv.split("\r\n")
        assertEquals("date,amount,currency,category,merchant,note,payment_method", lines[0])
        assertEquals("1970-01-01 00:00,450.50,INR,FOOD,Swiggy,,UPI", lines[1])
    }

    @Test fun quotesCommasQuotesAndNewlines() {
        val csv = CsvExporter.toCsv(listOf(expense("a", merchant = "Tom, \"Jerry\"", note = "line1\nline2")), utc)
        assertTrue(csv.contains("\"Tom, \"\"Jerry\"\"\""))
        assertTrue(csv.contains("\"line1\nline2\""))
    }

    @Test fun neutralisesSpreadsheetFormulas() {
        val csv = CsvExporter.toCsv(listOf(expense("a", merchant = "=HYPERLINK(\"http://evil\")", note = "@SUM(A1)")), utc)
        assertTrue("formula must be prefixed", csv.contains("'=HYPERLINK"))
        assertTrue(csv.contains("'@SUM(A1)"))
    }
}
