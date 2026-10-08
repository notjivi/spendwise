package com.spendwise.app.data.remote

import com.spendwise.app.core.AppError
import com.spendwise.app.core.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException
import java.io.IOException

/** Turns every failure mode into a typed AppError. Never swallows CancellationException. */
suspend fun <T> safeApiCall(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e // structured concurrency: a cancelled coroutine must stay cancelled
} catch (e: HttpException) {
    AppResult.Failure(e.toAppError())
} catch (e: IOException) {
    AppResult.Failure(AppError.Offline) // includes timeouts and DNS failures
} catch (e: SerializationException) {
    AppResult.Failure(AppError.Unknown("Unexpected server response"))
}

internal fun HttpException.toAppError(): AppError = when (code()) {
    401, 403 -> AppError.Unauthorized
    400, 422 -> AppError.Validation(errorDetail() ?: "Invalid request")
    429 -> AppError.RateLimited(response()?.headers()?.get("Retry-After")?.toLongOrNull())
    in 500..599 -> AppError.Server(code())
    else -> AppError.Unknown("Request failed (${code()})")
}

// FastAPI errors look like {"detail": "text"} (ours) or {"detail": [ ...field errors... ]} (its own validation).
private fun HttpException.errorDetail(): String? = runCatching {
    val body = response()?.errorBody()?.string().orEmpty()
    val detail = (Json.parseToJsonElement(body) as? JsonObject)?.get("detail")
    (detail as? JsonPrimitive)?.contentOrNull
}.getOrNull()
