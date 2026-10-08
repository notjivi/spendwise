package com.spendwise.app.data.remote

import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

private fun Request.withBearer(token: String): Request =
    newBuilder().header("Authorization", "Bearer $token").build()

/** Attaches the current Firebase ID token to every request. */
class AuthInterceptor @Inject constructor(private val tokens: TokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokens.getToken(forceRefresh = false)
        val request = if (token != null) chain.request().withBearer(token) else chain.request()
        return chain.proceed(request)
    }
}

/**
 * Called by OkHttp on a 401. Force-refresh the token and retry once. Returning null = give up
 * (the caller then sees the 401). The loop guard stops infinite retries if the server keeps rejecting.
 */
class TokenAuthenticator @Inject constructor(private val tokens: TokenProvider) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponseCount() >= 1) return null
        val fresh = tokens.getToken(forceRefresh = true) ?: return null
        if (response.request.header("Authorization") == "Bearer $fresh") return null // refresh gave the same token
        return response.request.withBearer(fresh)
    }

    private fun Response.priorResponseCount(): Int {
        var n = 0
        var r = priorResponse
        while (r != null) { n++; r = r.priorResponse }
        return n
    }
}
