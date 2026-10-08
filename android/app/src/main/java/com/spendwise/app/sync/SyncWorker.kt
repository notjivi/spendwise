package com.spendwise.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.spendwise.app.data.repo.SyncOutcome
import com.spendwise.app.data.repo.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (syncRepository.syncAll()) {
        SyncOutcome.DONE -> Result.success()
        // retry() uses the exponential backoff set on the request; cap attempts so we never loop forever.
        SyncOutcome.RETRY -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        SyncOutcome.FAILED -> Result.failure()
    }

    private companion object { const val MAX_ATTEMPTS = 5 }
}
