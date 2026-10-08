package com.spendwise.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.repo.AuthRepository
import com.spendwise.app.data.repo.AuthState
import com.spendwise.app.security.AppLockManager
import com.spendwise.app.security.LockState
import com.spendwise.app.sync.SyncTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthGateViewModel @Inject constructor(
    auth: AuthRepository,
    private val lock: AppLockManager,
    private val syncTrigger: SyncTrigger,
) : ViewModel() {
    val authState: StateFlow<AuthState> = auth.authState.stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)
    val lockState: StateFlow<LockState> = lock.state

    /** Splash stays up until we know BOTH who the user is and whether the app is locked. */
    val isLoading: StateFlow<Boolean> = combine(authState, lockState) { a, l ->
        a is AuthState.Loading || (a is AuthState.SignedIn && l == LockState.Unknown)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        // Pull this user's data as soon as they sign in (fresh install / new device).
        viewModelScope.launch {
            authState.filterIsInstance<AuthState.SignedIn>().collect { syncTrigger.requestSync() }
        }
    }

    fun unlock() = lock.unlock()
}
