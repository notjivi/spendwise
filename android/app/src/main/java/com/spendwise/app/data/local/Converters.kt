package com.spendwise.app.data.local

import androidx.room.TypeConverter

/** Explicit enum<->String converters: we control the stored value and the fallback for unknown ones. */
class Converters {
    @TypeConverter fun categoryToString(c: Category): String = c.name
    @TypeConverter fun stringToCategory(s: String): Category = Category.fromWire(s)
    @TypeConverter fun paymentToString(p: PaymentMethod): String = p.name
    @TypeConverter fun stringToPayment(s: String): PaymentMethod = PaymentMethod.fromWire(s)
    @TypeConverter fun syncToString(s: SyncState): String = s.name
    @TypeConverter fun stringToSync(s: String): SyncState = SyncState.valueOf(s)
}
