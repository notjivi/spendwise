package com.spendwise.app.data.repo

import com.spendwise.app.data.local.Category
import com.spendwise.app.data.local.CategoryTotal
import com.spendwise.app.data.local.ExpenseDao
import com.spendwise.app.data.local.ExpenseEntity
import com.spendwise.app.data.local.MerchantTotal
import com.spendwise.app.data.local.PaymentMethod
import com.spendwise.app.data.local.SpentAmount
import com.spendwise.app.data.local.SyncState
import com.spendwise.app.sync.SyncTrigger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ExpenseFilter(
    val query: String = "",
    val category: Category? = null,
    val fromMs: Long? = null,
    val toMs: Long? = null, // exclusive
)

data class ExpenseDraft(
    val id: String?, // null = new
    val amountMinor: Long,
    val currency: String,
    val category: Category,
    val merchant: String?,
    val note: String?,
    val paymentMethod: PaymentMethod,
    val spentAt: Long,
)

/** updatedAt must strictly increase per row, otherwise two edits in the same millisecond tie and LWW can't order them. */
internal fun nextTimestamp(now: Long, previous: Long?): Long =
    if (previous != null && previous >= now) previous + 1 else now

/** Escape LIKE wildcards so searching "50%" matches the text, not everything. */
internal fun escapeLike(s: String) = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ExpenseRepository @Inject constructor(
    private val dao: ExpenseDao,
    private val auth: AuthRepository,
    private val syncTrigger: SyncTrigger,
    private val clock: Clock,
) {
    // The UI only ever reads Room. Network results land in Room via sync, and flows re-emit.
    private fun <T> forUser(block: (String) -> Flow<T>): Flow<T> =
        auth.uidFlow.flatMapLatest { uid -> if (uid == null) emptyFlow() else block(uid) }

    fun observeExpenses(f: ExpenseFilter): Flow<List<ExpenseEntity>> =
        forUser { uid -> dao.observe(uid, f.category, f.fromMs, f.toMs, escapeLike(f.query)) }

    fun observeExpense(id: String): Flow<ExpenseEntity?> = dao.observeById(id)

    suspend fun getExpense(id: String): ExpenseEntity? = dao.getById(id)

    fun observeCategoryTotals(fromMs: Long, toMs: Long): Flow<List<CategoryTotal>> =
        forUser { dao.observeCategoryTotals(it, fromMs, toMs) }

    fun observeTopMerchants(fromMs: Long, toMs: Long, limit: Int = 3): Flow<List<MerchantTotal>> =
        forUser { dao.observeTopMerchants(it, fromMs, toMs, limit) }

    fun observeAmounts(fromMs: Long, toMs: Long): Flow<List<SpentAmount>> =
        forUser { dao.observeAmounts(it, fromMs, toMs) }

    suspend fun exportAll(): List<ExpenseEntity> =
        auth.currentUid()?.let { dao.getAllActive(it) }.orEmpty()

    suspend fun save(d: ExpenseDraft): String {
        val uid = checkNotNull(auth.currentUid()) { "Not signed in" }
        val existing = d.id?.let { dao.getById(it) }?.takeIf { it.userId == uid }
        val now = clock.millis()
        val entity = ExpenseEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            userId = uid,
            amountMinor = d.amountMinor,
            currency = d.currency,
            category = d.category,
            merchant = d.merchant?.trim()?.ifBlank { null },
            note = d.note?.trim()?.ifBlank { null },
            paymentMethod = d.paymentMethod,
            spentAt = d.spentAt,
            createdAt = existing?.createdAt ?: now,
            updatedAt = nextTimestamp(now, existing?.updatedAt),
            isDeleted = false,
            syncState = SyncState.PENDING,
        )
        dao.upsert(entity) // write locally FIRST: this is what makes every CRUD offline-capable
        syncTrigger.requestSync()
        return entity.id
    }

    suspend fun delete(id: String) = setDeleted(id, true)

    suspend fun undoDelete(id: String) = setDeleted(id, false)

    private suspend fun setDeleted(id: String, deleted: Boolean) {
        val e = dao.getById(id) ?: return
        dao.upsert(e.copy(isDeleted = deleted, updatedAt = nextTimestamp(clock.millis(), e.updatedAt), syncState = SyncState.PENDING))
        syncTrigger.requestSync()
    }
}
