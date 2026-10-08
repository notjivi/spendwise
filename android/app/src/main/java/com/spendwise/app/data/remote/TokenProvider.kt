package com.spendwise.app.data.remote

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import javax.inject.Singleton

/** Blocking on purpose: OkHttp interceptors/authenticators already run on OkHttp's own worker threads. */
interface TokenProvider {
    fun getToken(forceRefresh: Boolean): String?
}

@Singleton
class FirebaseTokenProvider @Inject constructor(private val auth: FirebaseAuth) : TokenProvider {
    // Firebase caches the ID token (~1h) and only hits the network when it's near expiry or forced.
    override fun getToken(forceRefresh: Boolean): String? = runCatching {
        auth.currentUser?.let { Tasks.await(it.getIdToken(forceRefresh)).token }
    }.getOrNull()
}
