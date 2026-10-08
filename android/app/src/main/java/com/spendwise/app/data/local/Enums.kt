package com.spendwise.app.data.local

enum class Category(val label: String) {
    FOOD("Food"), TRANSPORT("Transport"), SHOPPING("Shopping"), BILLS("Bills"),
    ENTERTAINMENT("Entertainment"), HEALTH("Health"), EDUCATION("Education"), TRAVEL("Travel"), OTHER("Other");

    companion object {
        /** Unknown values (e.g. a category added by a newer app version) degrade to OTHER instead of crashing. */
        fun fromWire(s: String?): Category = entries.firstOrNull { it.name == s } ?: OTHER
    }
}

enum class PaymentMethod(val label: String) {
    CASH("Cash"), UPI("UPI"), CARD("Card"), NET_BANKING("Net banking"), OTHER("Other");

    companion object {
        fun fromWire(s: String?): PaymentMethod = entries.firstOrNull { it.name == s } ?: OTHER
    }
}

enum class SyncState { PENDING, SYNCED, FAILED }
