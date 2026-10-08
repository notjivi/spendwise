package com.spendwise.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Abstraction so repositories can ask for a sync without knowing about WorkManager (and tests can fake it). */
interface SyncTrigger {
    fun requestSync()
}

@Singleton
class WorkManagerSyncTrigger @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncTrigger {

    private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    private val workManager get() = WorkManager.getInstance(context)

    /**
     * One-time work with a CONNECTED constraint: if offline it simply waits and runs the moment
     * connectivity returns. APPEND_OR_REPLACE so an edit made DURING a running sync gets its own pass.
     */
    override fun requestSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(connected)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork("sync_now", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Safety net: pulls changes made on other devices even when this device isn't editing. 15 min is WorkManager's minimum. */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(connected)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork("sync_periodic", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
