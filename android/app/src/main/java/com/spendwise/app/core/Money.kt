package com.spendwise.app.core

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * All money is a Long of minor units (paise). Floating point can't represent 0.10 exactly, so sums
 * drift; integers are exact. We only convert to a decimal string at the UI edge.
 */
object Money {
    private val INPUT = Regex("""^\d{1,10}(\.\d{1,2})?$""")

    fun format(minor: Long, currency: String = "INR"): String {
        val nf = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build())
        nf.currency = Currency.getInstance(currency)
        return nf.format(BigDecimal.valueOf(minor, 2))
    }

    /** "450", "450.5", "1,250.75" -> minor units. Null if invalid, zero, or has >2 decimals. */
    fun parseToMinor(input: String): Long? {
        val s = input.trim().replace(",", "")
        if (!INPUT.matches(s)) return null
        return BigDecimal(s).movePointRight(2).longValueExact().takeIf { it > 0 }
    }

    /** Minor units -> plain editable string, e.g. 45000 -> "450.00". */
    fun toInput(minor: Long): String = BigDecimal.valueOf(minor, 2).toPlainString()
}
