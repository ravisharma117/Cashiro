package com.ritesh.cashiro.data.security

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecoveryApiTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun api(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = "{}",
        capture: ((String, String) -> Unit)? = null,
        fail: Boolean = false
    ): RecoveryApi {
        val engine = MockEngine { request ->
            if (fail) throw IOException("offline")
            capture?.invoke(request.url.toString(), String(request.body.toByteArray()))
            respond(body, status, jsonHeaders)
        }
        return RecoveryApi(HttpClient(engine), "https://api.example.test")
    }

    @Test
    fun sendCodeSendsToTheRightRouteWithTheRightBody() = runTest {
        var url = ""
        var sent = ""
        val result = api(
            body = """{"ok":true,"challengeId":"abc123","expiresInSeconds":600,"resendAfterSeconds":60}""",
            capture = { u, b -> url = u; sent = b }
        ).sendCode("person@example.com", RecoveryPurpose.RECOVER, "install-id-0123456789")

        assertEquals("https://api.example.test/api/paisaiq/recovery/send", url)
        assertTrue(sent.contains("\"email\":\"person@example.com\""))
        assertTrue(sent.contains("\"purpose\":\"recover\""))
        assertTrue(sent.contains("\"installId\":\"install-id-0123456789\""))
        assertEquals(RecoveryResult.Success(CodeSent("abc123", 600, 60)), result)
    }

    @Test
    fun setupPurposeUsesItsOwnWireName() = runTest {
        var sent = ""
        api(body = """{"challengeId":"abc"}""", capture = { _, b -> sent = b })
            .sendCode("a@b.co", RecoveryPurpose.SETUP, "install-id-0123456789")
        assertTrue(sent.contains("\"purpose\":\"setup\""))
    }

    @Test
    fun verifyCodeSendsChallengeCodeAndInstall() = runTest {
        var url = ""
        var sent = ""
        val result = api(
            body = """{"ok":true,"verified":true,"purpose":"recover"}""",
            capture = { u, b -> url = u; sent = b }
        ).verifyCode("abc123", "482913", "install-id-0123456789")

        assertEquals("https://api.example.test/api/paisaiq/recovery/verify", url)
        assertTrue(sent.contains("\"challengeId\":\"abc123\""))
        assertTrue(sent.contains("\"code\":\"482913\""))
        assertEquals(RecoveryResult.Success(Unit), result)
    }

    @Test
    fun wrongCodeReportsAttemptsLeft() = runTest {
        val result = api(
            status = HttpStatusCode.BadRequest,
            body = """{"error":"That code is not valid or has expired.","attemptsLeft":3}"""
        ).verifyCode("abc123", "000000", "install-id-0123456789")

        val failure = result as RecoveryResult.Failure
        assertEquals(RecoveryFailure.INVALID_CODE, failure.reason)
        assertEquals(3, failure.attemptsLeft)
    }

    @Test
    fun badEmailOnSendIsInvalidEmailNotInvalidCode() = runTest {
        val result = api(
            status = HttpStatusCode.BadRequest,
            body = """{"error":"That does not look like an email address"}"""
        ).sendCode("nope", RecoveryPurpose.SETUP, "install-id-0123456789")

        assertEquals(RecoveryFailure.INVALID_EMAIL, (result as RecoveryResult.Failure).reason)
    }

    @Test
    fun tooManyRequestsCarriesTheRetryTime() = runTest {
        val result = api(
            status = HttpStatusCode.TooManyRequests,
            body = """{"error":"A code was just sent.","retryAfterSeconds":42}"""
        ).sendCode("a@b.co", RecoveryPurpose.RECOVER, "install-id-0123456789")

        val failure = result as RecoveryResult.Failure
        assertEquals(RecoveryFailure.TOO_MANY, failure.reason)
        assertEquals(42, failure.retryAfterSeconds)
    }

    @Test
    fun serverNotSetUpOrMailFailingIsUnavailable() = runTest {
        for (status in listOf(HttpStatusCode.ServiceUnavailable, HttpStatusCode.BadGateway)) {
            val result = api(status = status, body = """{"error":"x"}""")
                .sendCode("a@b.co", RecoveryPurpose.RECOVER, "install-id-0123456789")
            assertEquals(RecoveryFailure.UNAVAILABLE, (result as RecoveryResult.Failure).reason)
        }
    }

    @Test
    fun noConnectionIsNetwork() = runTest {
        val result = api(fail = true).sendCode("a@b.co", RecoveryPurpose.RECOVER, "install-id-0123456789")
        assertEquals(RecoveryFailure.NETWORK, (result as RecoveryResult.Failure).reason)
    }

    @Test
    fun aSuccessWithoutAChallengeIdIsNotTrusted() = runTest {
        val result = api(body = """{"ok":true}""")
            .sendCode("a@b.co", RecoveryPurpose.RECOVER, "install-id-0123456789")
        assertEquals(RecoveryFailure.UNKNOWN, (result as RecoveryResult.Failure).reason)
    }

    @Test
    fun anHtmlErrorPageDoesNotCrash() = runTest {
        val result = api(status = HttpStatusCode.InternalServerError, body = "<html>oops</html>")
            .verifyCode("abc123", "000000", "install-id-0123456789")
        assertEquals(RecoveryFailure.UNKNOWN, (result as RecoveryResult.Failure).reason)
    }
}
