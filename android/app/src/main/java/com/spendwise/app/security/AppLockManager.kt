package com.spendwise.app.security

import android.os.SystemClock
import com.spendwise.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

enum class LockState { Unknown, Locked, Unlocked }

@Singleton
class AppLockManager @Inject constructor(
    private val store: SecureStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    // null = setting not read yet. Treating "unknown" as "unlocked" would flash the content before the lock appears.
    private val enabledOrNull: StateFlow<Boolean?> =
        store.observeString(KEY).map<String?, Boolean?> { it == "1" }.stateIn(scope, SharingStarted.Eagerly, null)

    private val unlocked = MutableStateFlow(false)
    private var backgroundedAt: Long? = null

    val enabled: StateFlow<Boolean> = enabledOrNull.map { it == true }.stateIn(scope, SharingStarted.Eagerly, false)

    val state: StateFlow<LockState> = combine(enabledOrNull, unlocked) { enabled, unlocked ->
        when {
            enabled == null -> LockState.Unknown
            enabled && !unlocked -> LockState.Locked
            else -> LockState.Unlocked
        }
    }.stateIn(scope, SharingStarted.Eagerly, LockState.Unknown)

    fun unlock() { unlocked.value = true }

    suspend fun setEnabled(value: Boolean) {
        store.putString(KEY, if (value) "1" else "0")
        unlocked.value = true // the user just proved themselves (or turned it off)
    }

    fun onBackgrounded() { backgroundedAt = SystemClock.elapsedRealtime() }

    /** Short grace period so system dialogs (file picker, permission prompt) don't re-lock the app. */
    fun onForegrounded() {
        val at = backgroundedAt ?: return
        if (SystemClock.elapsedRealtime() - at > GRACE_MS) unlocked.value = false
        backgroundedAt = null
    }

    private companion object {
        const val KEY = "app_lock_enabled"
        const val GRACE_MS = 30_000L
    }
}
