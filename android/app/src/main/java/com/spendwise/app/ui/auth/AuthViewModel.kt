package com.spendwise.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.core.AppResult
import com.spendwise.app.data.repo.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** Pure function => trivially unit-testable. Returns an error message or null if valid. */
fun validateCredentials(email: String, password: String, isRegister: Boolean): String? = when {
    !EMAIL.matches(email.trim()) -> "Enter a valid email address"
    isRegister && password.length < 8 -> "Password must be at least 8 characters"
    password.isEmpty() -> "Enter your password"
    else -> null
}

@HiltViewModel
class AuthViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onEmail(v: String) = _state.update { it.copy(email = v, error = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, error = null) }
    fun onError(message: String) = _state.update { it.copy(loading = false, error = message) }

    fun submit(isRegister: Boolean) {
        val s = _state.value
        if (s.loading) return // ignore double taps
        validateCredentials(s.email, s.password, isRegister)?.let { return onError(it) }
        run { if (isRegister) auth.register(s.email, s.password) else auth.signIn(s.email, s.password) }
    }

    fun onGoogleToken(idToken: String) = run { auth.signInWithGoogleIdToken(idToken) }

    private fun run(call: suspend () -> AppResult<Unit>) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            // On success the auth listener flips AuthState and AppRoot swaps the whole nav graph; nothing to navigate here.
            when (val r = call()) {
                is AppResult.Success -> _state.update { it.copy(loading = false) }
                is AppResult.Failure -> onError(r.error.message)
            }
        }
    }
}
