package com.spendwise.app.data

import com.spendwise.app.core.AppError
import com.spendwise.app.core.AppResult
import com.spendwise.app.data.remote.AuthInterceptor
import com.spendwise.app.data.remote.TokenAuthenticator
import com.spendwise.app.data.remote.TokenProvider
import com.spendwise.app.data.remote.safeApiCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException

class SafeApiCallTest {
    private fun http(code: Int, body: String = "{}", headers: Map<String, String> = emptyMap()): HttpException {
        val raw = okhttp3.Response.Builder()
            .request(Request.Builder().url("http://localhost/").build()).protocol(Protocol.HTTP_1_1)
            .code(code).message("x").apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        return HttpException(retrofit2.Response.error<Any>(body.toResponseBody("application/json".toMediaType()), raw))
    }

    private fun failure(e: Throwable): AppError = runBlocking {
        (safeApiCall<Unit> { throw e } as AppResult.Failure).error
    }

    @Test fun successIsWrapped() = runBlocking {
        assertEquals(AppResult.Success(5), safeApiCall { 5 })
    }

    @Test fun ioExceptionMeansOffline() = assertEquals(AppError.Offline, failure(IOException("timeout")))

    @Test fun http401And403MeanUnauthorized() {
        assertEquals(AppError.Unauthorized, failure(http(401)))
        assertEquals(AppError.Unauthorized, failure(http(403)))
    }

    @Test fun http422ExtractsFastApiDetailString() {
        val e = failure(http(422, """{"detail":"This doesn't look like an expense"}"""))
        assertEquals(AppError.Validation("This doesn't look like an expense"), e)
    }

    @Test fun http422WithFieldErrorListFallsBackToGenericMessage() {
        val e = failure(http(422, """{"detail":[{"loc":["body","text"],"msg":"too long"}]}"""))
        assertEquals(AppError.Validation("Invalid request"), e)
    }

    @Test fun http429CarriesRetryAfter() = assertEquals(AppError.RateLimited(12), failure(http(429, headers = mapOf("Retry-After" to "12"))))

    @Test fun http5xxIsServerError() = assertEquals(AppError.Server(502), failure(http(502)))

    @Test fun cancellationIsNeverSwallowed() {
        var rethrown = false
        try { failure(CancellationException("cancelled")) } catch (e: CancellationException) { rethrown = true }
        assertTrue(rethrown)
    }
}

class TokenAuthenticatorTest {
    private class FakeTokens(var normal: String?, var refreshed: String?) : TokenProvider {
        var refreshCalls = 0
        override fun getToken(forceRefresh: Boolean): String? = if (forceRefresh) { refreshCalls++; refreshed } else normal
    }

    private fun response(token: String, prior: Response? = null): Response =
        Response.Builder()
            .request(Request.Builder().url("http://localhost/x").header("Authorization", "Bearer $token").build())
            .protocol(Protocol.HTTP_1_1).code(401).message("Unauthorized").priorResponse(prior).build()

    @Test fun refreshesTokenAndRetriesOnce() {
        val tokens = FakeTokens("old", "new")
        val retry = TokenAuthenticator(tokens).authenticate(null, response("old"))
        assertEquals("Bearer new", retry!!.header("Authorization"))
        assertEquals(1, tokens.refreshCalls)
    }

    @Test fun givesUpIfAlreadyRetried() {
        val tokens = FakeTokens("old", "new")
        assertNull(TokenAuthenticator(tokens).authenticate(null, response("new", prior = response("old"))))
        assertEquals("must not even try to refresh again", 0, tokens.refreshCalls)
    }

    @Test fun givesUpIfRefreshReturnsSameTokenOrNothing() {
        assertNull(TokenAuthenticator(FakeTokens("t", "t")).authenticate(null, response("t")))
        assertNull(TokenAuthenticator(FakeTokens("t", null)).authenticate(null, response("t")))
    }

    @Test fun interceptorAttachesBearerWhenSignedIn() {
        val tokens = FakeTokens("abc", null)
        var seen: String? = "unset"
        val chain = io.mockk.mockk<okhttp3.Interceptor.Chain>()
        io.mockk.every { chain.request() } returns Request.Builder().url("http://localhost/").build()
        io.mockk.every { chain.proceed(any()) } answers { seen = firstArg<Request>().header("Authorization"); response("abc") }
        AuthInterceptor(tokens).intercept(chain)
        assertEquals("Bearer abc", seen)

        tokens.normal = null // signed out: no header at all
        AuthInterceptor(tokens).intercept(chain)
        assertNull(seen)
    }
}
