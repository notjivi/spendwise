package com.spendwise.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.spendwise.app.notifications.BudgetMonitor
import com.spendwise.app.notifications.BudgetNotifier
import com.spendwise.app.sync.WorkManagerSyncTrigger
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SpendWiseApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncTrigger: WorkManagerSyncTrigger
    @Inject lateinit var notifier: BudgetNotifier
    @Inject lateinit var budgetMonitor: BudgetMonitor

    // Required so WorkManager builds SyncWorker through Hilt (constructor injection).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannel()
        syncTrigger.schedulePeriodic()
        budgetMonitor.start()
    }
}
