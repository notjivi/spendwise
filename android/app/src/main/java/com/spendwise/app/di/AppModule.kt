package com.spendwise.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.spendwise.app.data.local.AppDatabase
import com.spendwise.app.data.local.BudgetDao
import com.spendwise.app.data.local.ExpenseDao
import com.spendwise.app.data.remote.FirebaseTokenProvider
import com.spendwise.app.data.remote.TokenProvider
import com.spendwise.app.sync.SyncTrigger
import com.spendwise.app.sync.WorkManagerSyncTrigger
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "spendwise.db").build()

    @Provides fun expenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()
    @Provides fun budgetDao(db: AppDatabase): BudgetDao = db.budgetDao()

    @Provides @Singleton
    fun dataStore(@ApplicationContext ctx: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { ctx.preferencesDataStoreFile("secure_prefs") }

    @Provides @Singleton fun firebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    // Injected (not Clock.systemDefaultZone() at call sites) so tests can freeze time.
    @Provides @Singleton fun clock(): Clock = Clock.systemDefaultZone()

    // Lives as long as the process; SupervisorJob so one failing child doesn't cancel the rest.
    @Provides @Singleton @ApplicationScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun tokenProvider(impl: FirebaseTokenProvider): TokenProvider
    @Binds abstract fun syncTrigger(impl: WorkManagerSyncTrigger): SyncTrigger
}
