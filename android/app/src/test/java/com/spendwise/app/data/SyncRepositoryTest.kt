package com.spendwise.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spendwise.app.data.local.AppDatabase
import com.spendwise.app.data.local.SyncState
import com.spendwise.app.data.remote.ApiService
import com.spendwise.app.data.remote.ExpenseDto
import com.spendwise.app.data.remote.RejectedDto
import com.spendwise.app.data.remote.SyncRequestDto
import com.spendwise.app.data.remote.SyncResponseDto
import com.spendwise.app.data.repo.AuthRepository
import com.spendwise.app.data.repo.SyncOutcome
import com.spendwise.app.data.repo.SyncRepository
import com.spendwise.app.expense
import com.spendwise.app.expenseDto
import com.spendwise.app.security.SecureStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.HttpException
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class SyncRepositoryTest {
    private lateinit var db: AppDatabase
    private val api = mockk<ApiService>()
    private val auth = mockk<AuthRepository>()
    private val store = mockk<SecureStore>(relaxed = true)
    private lateinit var repo: SyncRepository
    private val expenses get() = db.expenseDao()

    private fun ok(accepted: List<String> = emptyList(), rejected: List<RejectedDto> = emptyList(), changes: List<ExpenseDto> = emptyList(), time: Long = 1_000) =
        SyncResponseDto(accepted, rejected, changes, time)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        every { auth.currentUid() } returns "u1"
        coEvery { store.getString(any()) } returns null
        coEvery { api.syncBudgets(any()) } returns SyncResponseDto(emptyList(), emptyList(), emptyList(), 1L)
        repo = SyncRepository(auth, api, db.expenseDao(), db.budgetDao(), store)
    }

    @After fun tearDown() = db.close()

    @Test fun pushesPendingMarksSyncedAndSavesCursor() = runBlocking {
        expenses.upsert(expense("a", updatedAt = 5))
        val sent = slot<SyncRequestDto<ExpenseDto>>()
        coEvery { api.syncExpenses(capture(sent)) } returns ok(accepted = listOf("a"), time = 1_000)

        assertEquals(SyncOutcome.DONE, repo.syncAll())

        assertEquals(listOf("a"), sent.captured.changes.map { it.id })
        assertEquals(0L, sent.captured.lastSyncAt)
        assertEquals(SyncState.SYNCED, expenses.getById("a")!!.syncState)
        coVerify { store.putString("cursor_expenses_u1", "1000") }
    }

    @Test fun sendsSavedCursorAsLastSyncAt() = runBlocking {
        coEvery { store.getString("cursor_expenses_u1") } returns "777"
        val sent = slot<SyncRequestDto<ExpenseDto>>()
        coEvery { api.syncExpenses(capture(sent)) } returns ok()
        repo.syncAll()
        assertEquals(777L, sent.captured.lastSyncAt)
    }

    @Test fun offlineKeepsRowsPendingAndAsksForRetry() = runBlocking {
        expenses.upsert(expense("a"))
        coEvery { api.syncExpenses(any()) } throws IOException("no network")

        assertEquals(SyncOutcome.RETRY, repo.syncAll())

        assertEquals(SyncState.PENDING, expenses.getById("a")!!.syncState)
        coVerify(exactly = 0) { store.putString(any(), any()) } // cursor must NOT advance on failure
    }

    @Test fun serverErrorIsRetriedButBadRequestIsPermanentFailure() = runBlocking {
        expenses.upsert(expense("a"))
        fun http(code: Int) = HttpException(retrofit2.Response.error<Any>(code, "{}".toResponseBody("application/json".toMediaType())))

        coEvery { api.syncExpenses(any()) } throws http(503)
        assertEquals(SyncOutcome.RETRY, repo.syncAll())
        assertEquals(SyncState.PENDING, expenses.getById("a")!!.syncState)

        coEvery { api.syncExpenses(any()) } throws http(422)
        assertEquals(SyncOutcome.FAILED, repo.syncAll())
        assertEquals(SyncState.FAILED, expenses.getById("a")!!.syncState)
    }

    @Test fun editDuringFlightStaysPending() = runBlocking {
        expenses.upsert(expense("a", amount = 100, updatedAt = 5))
        coEvery { api.syncExpenses(any()) } coAnswers {
            expenses.upsert(expense("a", amount = 200, updatedAt = 6)) // user edits mid-request
            ok(accepted = listOf("a"))
        }
        repo.syncAll()
        val row = expenses.getById("a")!!
        assertEquals(200L, row.amountMinor)
        assertEquals("the newer edit still has to be pushed", SyncState.PENDING, row.syncState)
    }

    @Test fun appliesNewerServerRowsAndIgnoresOlderOnes() = runBlocking {
        expenses.upsert(expense("mine", amount = 100, updatedAt = 10, sync = SyncState.SYNCED))
        coEvery { api.syncExpenses(any()) } returns ok(
            changes = listOf(expenseDto("mine", amount = 1, updatedAt = 5), expenseDto("new-from-other-device", amount = 42, updatedAt = 7)),
        )
        repo.syncAll()
        assertEquals(100L, expenses.getById("mine")!!.amountMinor)
        val pulled = expenses.getById("new-from-other-device")!!
        assertEquals(42L, pulled.amountMinor)
        assertEquals(SyncState.SYNCED, pulled.syncState)
        assertEquals("userId comes from the session, not the wire", "u1", pulled.userId)
    }

    @Test fun staleRejectionIsResolvedByServerWinner() = runBlocking {
        expenses.upsert(expense("a", amount = 100, updatedAt = 10))
        coEvery { api.syncExpenses(any()) } returns ok(
            rejected = listOf(RejectedDto("a", "stale")),
            changes = listOf(expenseDto("a", amount = 999, updatedAt = 20)),
        )
        assertEquals(SyncOutcome.DONE, repo.syncAll())
        val row = expenses.getById("a")!!
        assertEquals(999L, row.amountMinor)
        assertEquals(SyncState.SYNCED, row.syncState)
    }

    @Test fun deleteTombstoneFromServerHidesRowLocally() = runBlocking {
        expenses.upsert(expense("a", updatedAt = 10, sync = SyncState.SYNCED))
        coEvery { api.syncExpenses(any()) } returns ok(changes = listOf(expenseDto("a", updatedAt = 11, deleted = true)))
        repo.syncAll()
        assertEquals(true, expenses.getById("a")!!.isDeleted)
    }

    @Test fun hardRejectionMarksFailed() = runBlocking {
        expenses.upsert(expense("a"))
        coEvery { api.syncExpenses(any()) } returns ok(rejected = listOf(RejectedDto("a", "forbidden")))
        repo.syncAll()
        assertEquals(SyncState.FAILED, expenses.getById("a")!!.syncState)
    }

    @Test fun signedOutDoesNothing() = runBlocking {
        every { auth.currentUid() } returns null
        assertEquals(SyncOutcome.DONE, repo.syncAll())
        coVerify(exactly = 0) { api.syncExpenses(any()) }
    }
}
