package com.spendwise.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun parsesPlainAndDecimalAmounts() {
        assertEquals(45_000L, Money.parseToMinor("450"))
        assertEquals(45_050L, Money.parseToMinor("450.50"))
        assertEquals(125_075L, Money.parseToMinor("1,250.75"))
        assertEquals(5L, Money.parseToMinor("0.05"))
    }

    @Test fun halfRupeeStringIsFiftyPaise() = assertEquals(45_050L, Money.parseToMinor("450.5"))

    @Test fun rejectsInvalidInput() {
        listOf("", "abc", "0", "0.00", "-5", "1.234", "1e3", "12.", ".5", "1 000", "99999999999").forEach {
            assertNull("should reject '$it'", Money.parseToMinor(it))
        }
    }

    @Test fun floatingPointTrapIsAvoided() {
        // 0.1 + 0.2 != 0.3 in Double. In minor units it is exact.
        assertEquals(30L, Money.parseToMinor("0.10")!! + Money.parseToMinor("0.20")!!)
    }

    @Test fun roundTripsThroughEditableString() {
        assertEquals("450.00", Money.toInput(45_000))
        assertEquals("0.05", Money.toInput(5))
        assertEquals(45_000L, Money.parseToMinor(Money.toInput(45_000)))
    }

    @Test fun formatsRupees() = assertEquals("₹450.00", Money.format(45_000))
}
