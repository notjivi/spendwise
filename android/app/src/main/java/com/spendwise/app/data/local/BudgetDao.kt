package com.spendwise.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class BudgetDao {
    @Query("SELECT * FROM budgets WHERE userId = :uid AND isDeleted = 0")
    abstract fun observe(uid: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id")
    abstract suspend fun getById(id: String): BudgetEntity?

    @Upsert abstract suspend fun upsert(b: BudgetEntity)

    @Query(
        """
        SELECT * FROM budgets WHERE userId = :uid AND syncState != 'SYNCED'
        ORDER BY CASE syncState WHEN 'PENDING' THEN 0 ELSE 1 END, updatedAt LIMIT :limit
        """
    )
    abstract suspend fun pending(uid: String, limit: Int): List<BudgetEntity>

    @Query("UPDATE budgets SET syncState = 'SYNCED' WHERE id = :id AND updatedAt = :updatedAt")
    abstract suspend fun markSyncedIfUnchanged(id: String, updatedAt: Long)

    @Query("UPDATE budgets SET syncState = 'FAILED' WHERE id IN (:ids) AND syncState = 'PENDING'")
    abstract suspend fun markFailed(ids: List<String>)

    @Transaction
    open suspend fun applyRemote(items: List<BudgetEntity>) {
        for (item in items) {
            if (shouldApplyRemote(getById(item.id)?.updatedAt, item.updatedAt)) upsert(item)
        }
    }
}
