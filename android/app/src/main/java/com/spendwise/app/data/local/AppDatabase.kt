package com.spendwise.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// exportSchema=true + committed JSON in app/schemas: required to write/test real migrations later.
// We deliberately do NOT use fallbackToDestructiveMigration(): it would silently delete unsynced expenses.
@Database(entities = [ExpenseEntity::class, BudgetEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetDao(): BudgetDao
}
