package com.spendwise.app.export

import com.spendwise.app.core.Money
import com.spendwise.app.data.local.ExpenseEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CsvExporter {
    private val HEADER = listOf("date", "amount", "currency", "category", "merchant", "note", "payment_method")
    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun toCsv(rows: List<ExpenseEntity>, zone: ZoneId): String = buildString {
        append(HEADER.joinToString(",")).append("\r\n") // RFC 4180 line endings
        for (e in rows) {
            val cells = listOf(
                DATE.format(Instant.ofEpochMilli(e.spentAt).atZone(zone)),
                Money.toInput(e.amountMinor),
                e.currency,
                e.category.name,
                text(e.merchant),
                text(e.note),
                e.paymentMethod.name,
            )
            append(cells.joinToString(",") { quote(it) }).append("\r\n")
        }
    }

    /**
     * CSV injection: a merchant like "=HYPERLINK(...)" would execute as a formula when opened in Excel/Sheets.
     * Prefix risky leading characters with an apostrophe so it stays inert text.
     */
    private fun text(s: String?): String {
        val v = s.orEmpty()
        return if (v.isNotEmpty() && v[0] in "=+-@\t\r") "'$v" else v
    }

    private fun quote(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}
