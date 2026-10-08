package com.spendwise.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class CategoryTotal(val category: Category, val total: Long)
data class MerchantTotal(val name: String, val total: Long)
data class SpentAmount(val spentAt: Long, val amountMinor: Long)

/** Remote wins only if strictly newer. Equal timestamps = same version, nothing to do. */
fun shouldApplyRemote(localUpdatedAt: Long?, remoteUpdatedAt: Long): Boolean =
    localUpdatedAt == null || remoteUpdatedAt > localUpdatedAt

@Dao
abstract class ExpenseDao {

    // Null filter params mean "no filter". LIKE wildcards in user input are escaped by the repository.
    @Query(
        """
        SELECT * FROM expenses
        WHERE userId = :uid AND isDeleted = 0
          AND (:category IS NULL OR category = :category)
          AND (:fromMs IS NULL OR spentAt >= :fromMs)
          AND (:toMs IS NULL OR spentAt < :toMs)
          AND (:q = '' OR merchant LIKE '%' || :q || '%' ESCAPE '\' OR note LIKE '%' || :q || '%' ESCAPE '\')
        ORDER BY spentAt DESC, createdAt DESC
        """
    )
    abstract fun observe(uid: String, category: Category?, fromMs: Long?, toMs: Long?, q: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    abstract fun observeById(id: String): Flow<ExpenseEntity?>

    @Query("SELECT * FROM expenses WHERE id = :id")
    abstract suspend fun getById(id: String): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE userId = :uid AND isDeleted = 0 ORDER BY spentAt DESC")
    abstract suspend fun getAllActive(uid: String): List<ExpenseEntity>

    @Upsert abstract suspend fun upsert(e: ExpenseEntity)

    // ---- analytics ----
    @Query(
        """
        SELECT category, SUM(amountMinor) AS total FROM expenses
        WHERE userId = :uid AND isDeleted = 0 AND spentAt >= :fromMs AND spentAt < :toMs
        GROUP BY category
        """
    )
    abstract fun observeCategoryTotals(uid: String, fromMs: Long, toMs: Long): Flow<List<CategoryTotal>>

    @Query(
        """
        SELECT MIN(merchant) AS name, SUM(amountMinor) AS total FROM expenses
        WHERE userId = :uid AND isDeleted = 0 AND spentAt >= :fromMs AND spentAt < :toMs
          AND merchant IS NOT NULL AND merchant != ''
        GROUP BY lower(merchant) ORDER BY total DESC LIMIT :limit
        """
    )
    abstract fun observeTopMerchants(uid: String, fromMs: Long, toMs: Long, limit: Int): Flow<List<MerchantTotal>>

    // Bucketing by month happens in Kotlin (java.time, device zone) because SQLite's 'localtime' is unreliable on Android.
    @Query("SELECT spentAt, amountMinor FROM expenses WHERE userId = :uid AND isDeleted = 0 AND spentAt >= :fromMs AND spentAt < :toMs")
    abstract fun observeAmounts(uid: String, fromMs: Long, toMs: Long): Flow<List<SpentAmount>>

    // ---- sync ----
    @Query(
        """
        SELECT * FROM expenses WHERE userId = :uid AND syncState != 'SYNCED'
        ORDER BY CASE syncState WHEN 'PENDING' THEN 0 ELSE 1 END, updatedAt LIMIT :limit
        """
    )
    abstract suspend fun pending(uid: String, limit: Int): List<ExpenseEntity>

    /** Only flips to SYNCED if the row wasn't edited while the request was in flight. */
    @Query("UPDATE expenses SET syncState = 'SYNCED' WHERE id = :id AND updatedAt = :updatedAt")
    abstract suspend fun markSyncedIfUnchanged(id: String, updatedAt: Long)

    @Query("UPDATE expenses SET syncState = 'FAILED' WHERE id IN (:ids) AND syncState = 'PENDING'")
    abstract suspend fun markFailed(ids: List<String>)

    /** Apply server rows atomically with the LWW check, so a concurrent local edit can't be clobbered. */
    @Transaction
    open suspend fun applyRemote(items: List<ExpenseEntity>) {
        for (item in items) {
            if (shouldApplyRemote(getById(item.id)?.updatedAt, item.updatedAt)) upsert(item)
        }
    }
}
