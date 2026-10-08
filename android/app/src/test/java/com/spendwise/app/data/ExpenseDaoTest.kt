package com.spendwise.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.spendwise.app.data.local.AppDatabase
import com.spendwise.app.data.local.Category
import com.spendwise.app.data.local.ExpenseDao
import com.spendwise.app.data.local.SyncState
import com.spendwise.app.data.repo.escapeLike
import com.spendwise.app.expense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Real SQLite (via Robolectric) + real Room-generated SQL: this is what catches bad @Query strings. */
@RunWith(RobolectricTestRunner::class)
class ExpenseDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ExpenseDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.expenseDao()
    }

    @After fun tearDown() = db.close()

    private suspend fun list(q: String = "", c: Category? = null, from: Long? = null, to: Long? = null, uid: String = "u1") =
        dao.observe(uid, c, from, to, escapeLike(q)).first().map { it.id }

    @Test fun hidesSoftDeletedAndOtherUsersRows() = runBlocking {
        dao.upsert(expense("1")); dao.upsert(expense("2", deleted = true)); dao.upsert(expense("3", userId = "other"))
        assertEquals(listOf("1"), list())
    }

    @Test fun ordersNewestSpendFirst() = runBlocking {
        dao.upsert(expense("old", spentAt = 1)); dao.upsert(expense("new", spentAt = 9))
        assertEquals(listOf("new", "old"), list())
    }

    @Test fun filtersByCategoryAndHalfOpenDateRange() = runBlocking {
        dao.upsert(expense("food-in", spentAt = 100)); dao.upsert(expense("food-edge", spentAt = 200))
        dao.upsert(expense("bills", spentAt = 150, category = Category.BILLS))
        assertEquals(listOf("food-in"), list(c = Category.FOOD, from = 100, to = 200)) // `to` is exclusive
        assertEquals(listOf("bills", "food-in"), list(from = 100, to = 200))
    }

    @Test fun searchMatchesMerchantOrNoteCaseInsensitively() = runBlocking {
        dao.upsert(expense("a", merchant = "Swiggy")); dao.upsert(expense("b", merchant = null, note = "dinner at SWIG")); dao.upsert(expense("c", merchant = "Uber"))
        assertEquals(setOf("a"), list("swiggy").toSet())
        assertEquals(setOf("b"), list("dinner").toSet())
        assertEquals(setOf("a", "b"), list("swi").toSet())
    }

    @Test fun searchTreatsPercentAsLiteral() = runBlocking {
        dao.upsert(expense("pct", merchant = "50% off store")); dao.upsert(expense("plain", merchant = "500 mart"))
        assertEquals(listOf("pct"), list("50%"))
    }

    @Test fun flowReEmitsAfterWrites() = runBlocking {
        dao.observe("u1", null, null, null, "").test {
            assertEquals(0, awaitItem().size)
            dao.upsert(expense("1"))
            assertEquals(1, awaitItem().size)
            dao.upsert(expense("1", deleted = true))
            assertEquals(0, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun categoryTotalsExcludeDeletedAndOutOfRange() = runBlocking {
        dao.upsert(expense("a", amount = 100, spentAt = 10)); dao.upsert(expense("b", amount = 50, spentAt = 20))
        dao.upsert(expense("c", amount = 999, spentAt = 20, deleted = true)); dao.upsert(expense("d", amount = 7, spentAt = 99))
        dao.upsert(expense("e", amount = 30, spentAt = 15, category = Category.BILLS))
        val totals = dao.observeCategoryTotals("u1", 0, 50).first().associate { it.category to it.total }
        assertEquals(mapOf(Category.FOOD to 150L, Category.BILLS to 30L), totals)
    }

    @Test fun topMerchantsGroupCaseInsensitivelyAndSkipBlank() = runBlocking {
        dao.upsert(expense("a", amount = 100, merchant = "Swiggy")); dao.upsert(expense("b", amount = 200, merchant = "swiggy"))
        dao.upsert(expense("c", amount = 250, merchant = "Uber")); dao.upsert(expense("d", amount = 900, merchant = null)); dao.upsert(expense("e", amount = 900, merchant = ""))
        val top = dao.observeTopMerchants("u1", 0, 10_000, 3).first()
        assertEquals(listOf(300L, 250L), top.map { it.total })
        assertEquals("Swiggy", top.first().name.replaceFirstChar { it.uppercase() })
    }

    @Test fun pendingListsUnsyncedPendingBeforeFailed() = runBlocking {
        dao.upsert(expense("synced", sync = SyncState.SYNCED)); dao.upsert(expense("failed", sync = SyncState.FAILED, updatedAt = 1))
        dao.upsert(expense("pending", sync = SyncState.PENDING, updatedAt = 5))
        assertEquals(listOf("pending", "failed"), dao.pending("u1", 10).map { it.id })
    }

    @Test fun markSyncedOnlyIfRowUnchangedSinceSend() = runBlocking {
        dao.upsert(expense("a", updatedAt = 5))
        dao.upsert(expense("a", updatedAt = 6)) // user edited while the request was in flight
        dao.markSyncedIfUnchanged("a", 5)
        assertEquals(SyncState.PENDING, dao.getById("a")!!.syncState)
        dao.markSyncedIfUnchanged("a", 6)
        assertEquals(SyncState.SYNCED, dao.getById("a")!!.syncState)
    }

    @Test fun applyRemoteIsLastWriteWins() = runBlocking {
        dao.upsert(expense("a", amount = 100, updatedAt = 10))
        dao.applyRemote(listOf(expense("a", amount = 1, updatedAt = 5), expense("b", amount = 2, updatedAt = 5)))
        assertEquals(100L, dao.getById("a")!!.amountMinor) // older remote ignored
        assertEquals(2L, dao.getById("b")!!.amountMinor) // unknown row inserted
        dao.applyRemote(listOf(expense("a", amount = 300, updatedAt = 11, sync = SyncState.SYNCED)))
        assertEquals(300L, dao.getById("a")!!.amountMinor)
        assertEquals(SyncState.SYNCED, dao.getById("a")!!.syncState)
    }

    @Test fun markFailedLeavesSyncedRowsAlone() = runBlocking {
        dao.upsert(expense("p")); dao.upsert(expense("s", sync = SyncState.SYNCED))
        dao.markFailed(listOf("p", "s"))
        assertEquals(SyncState.FAILED, dao.getById("p")!!.syncState)
        assertEquals(SyncState.SYNCED, dao.getById("s")!!.syncState)
        assertNull(dao.getById("missing"))
    }
}
