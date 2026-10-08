package com.spendwise.app.data.repo

import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import android.content.Context
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.spendwise.app.core.AppError
import com.spendwise.app.core.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val uid: String) : AuthState
}

@Singleton
class AuthRepository @Inject constructor(private val auth: FirebaseAuth) {

    /** Firebase persists the session itself; the listener fires immediately with the restored user (or null). */
    val authState: Flow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { fa ->
            val uid = fa.currentUser?.uid
            trySend(if (uid != null) AuthState.SignedIn(uid) else AuthState.SignedOut)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    val uidFlow: Flow<String?> = authState.map { (it as? AuthState.SignedIn)?.uid }.distinctUntilChanged()

    fun currentUid(): String? = auth.currentUser?.uid

    suspend fun signIn(email: String, password: String): AppResult<Unit> =
        runAuth { auth.signInWithEmailAndPassword(email.trim(), password).await() }

    suspend fun register(email: String, password: String): AppResult<Unit> =
        runAuth { auth.createUserWithEmailAndPassword(email.trim(), password).await() }

    suspend fun signInWithGoogleIdToken(idToken: String): AppResult<Unit> =
        runAuth { auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await() }

    fun signOut() = auth.signOut()

    private suspend fun runAuth(block: suspend () -> Unit): AppResult<Unit> = try {
        block()
        AppResult.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Failure(AppError.Validation(e.toMessage()))
    }

    private fun Exception.toMessage(): String = when (this) {
        // Same message for "no such user" and "wrong password": don't reveal which emails exist.
        is FirebaseAuthInvalidUserException, is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password"
        is FirebaseAuthUserCollisionException -> "An account with this email already exists"
        is FirebaseAuthWeakPasswordException -> "Password is too weak"
        is FirebaseNetworkException -> "No internet connection"
        is FirebaseTooManyRequestsException -> "Too many attempts. Try again later"
        else -> "Could not sign in. Please try again"
    }
}

/** Credential Manager replaces the deprecated GoogleSignIn client. Needs an ACTIVITY context to show UI. */
suspend fun requestGoogleIdToken(activityContext: Context, serverClientId: String): AppResult<String> = try {
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
        .build()
    val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        AppResult.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
    } else {
        AppResult.Failure(AppError.Unknown("Unsupported credential"))
    }
} catch (e: GetCredentialCancellationException) {
    AppResult.Failure(AppError.Validation("Sign-in cancelled"))
} catch (e: GetCredentialException) {
    AppResult.Failure(AppError.Unknown("Google sign-in failed"))
}
