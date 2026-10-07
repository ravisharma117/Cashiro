package com.ritesh.cashiro.data.security

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Why a code is being asked for. */
enum class RecoveryPurpose(val wireName: String) {
    /** Confirming a recovery email while setting up the PIN. */
    SETUP("setup"),

    /** Resetting a forgotten PIN. */
    RECOVER("recover")
}

data class CodeSent(
    val challengeId: String,
    val expiresInSeconds: Int,
    val resendAfterSeconds: Int
)

enum class RecoveryFailure {
    /** The address was not accepted. */
    INVALID_EMAIL,

    /** Wrong or expired code. */
    INVALID_CODE,

    /** Too many requests or too many wrong codes; wait or ask for a new code. */
    TOO_MANY,

    /** The server could not send mail or is not set up for this yet. */
    UNAVAILABLE,

    /** No connection, or the server did not answer in time. */
    NETWORK,

    UNKNOWN
}

sealed interface RecoveryResult<out T> {
    data class Success<T>(val value: T) : RecoveryResult<T>

    data class Failure(
        val reason: RecoveryFailure,
        val message: String? = null,
        val attemptsLeft: Int? = null,
        val retryAfterSeconds: Int? = null
    ) : RecoveryResult<Nothing>
}

@Serializable
private data class SendBody(val email: String, val purpose: String, val installId: String)

@Serializable
private data class SendReply(
    val challengeId: String = "",
    val expiresInSeconds: Int = 600,
    val resendAfterSeconds: Int = 60
)

@Serializable
private data class VerifyBody(val challengeId: String, val code: String, val installId: String)

@Serializable
private data class ErrorReply(
    val error: String? = null,
    val attemptsLeft: Int? = null,
    @SerialName("retryAfterSeconds") val retryAfterSeconds: Int? = null
)

/**
 * Talks to the NAX IT Solutions API to mail a code to the recovery address and check it.
 *
 * The only network use of the PIN feature. It sends the recovery email address (so the
 * server can mail it), the typed code and a random install id; never the PIN. Nothing is
 * sent unless the person starts recovery or confirms a recovery email.
 */
class RecoveryApi(
    private val client: HttpClient,
    private val baseUrl: String
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun sendCode(
        email: String,
        purpose: RecoveryPurpose,
        installId: String
    ): RecoveryResult<CodeSent> = call(
        path = "/api/paisaiq/recovery/send",
        body = json.encodeToString(SendBody.serializer(), SendBody(email, purpose.wireName, installId)),
        onBadRequest = RecoveryFailure.INVALID_EMAIL
    ) { text ->
        val reply = json.decodeFromString(SendReply.serializer(), text)
        if (reply.challengeId.isBlank()) null
        else CodeSent(reply.challengeId, reply.expiresInSeconds, reply.resendAfterSeconds)
    }

    suspend fun verifyCode(
        challengeId: String,
        code: String,
        installId: String
    ): RecoveryResult<Unit> = call(
        path = "/api/paisaiq/recovery/verify",
        body = json.encodeToString(VerifyBody.serializer(), VerifyBody(challengeId, code, installId)),
        onBadRequest = RecoveryFailure.INVALID_CODE
    ) { Unit }

    private suspend fun <T> call(
        path: String,
        body: String,
        onBadRequest: RecoveryFailure,
        parse: (String) -> T?
    ): RecoveryResult<T> {
        val response = try {
            client.post("$baseUrl$path") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                setBody(body)
            }
        } catch (e: Exception) {
            return RecoveryResult.Failure(RecoveryFailure.NETWORK)
        }

        val text = try {
            response.bodyAsText()
        } catch (e: Exception) {
            ""
        }

        if (response.status.isSuccess()) {
            val value = try {
                parse(text)
            } catch (e: Exception) {
                null
            }
            return if (value != null) RecoveryResult.Success(value)
            else RecoveryResult.Failure(RecoveryFailure.UNKNOWN)
        }

        val error = try {
            json.decodeFromString(ErrorReply.serializer(), text)
        } catch (e: Exception) {
            ErrorReply()
        }

        val reason = when (response.status.value) {
            400 -> onBadRequest
            429 -> RecoveryFailure.TOO_MANY
            502, 503 -> RecoveryFailure.UNAVAILABLE
            else -> RecoveryFailure.UNKNOWN
        }
        return RecoveryResult.Failure(
            reason = reason,
            message = error.error,
            attemptsLeft = error.attemptsLeft,
            retryAfterSeconds = error.retryAfterSeconds
        )
    }
}
