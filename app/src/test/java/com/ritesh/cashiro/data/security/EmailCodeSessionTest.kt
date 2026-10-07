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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailCodeSessionTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = mutableListOf<Pair<String, String>>()

    private fun session(
        sendStatus: HttpStatusCode = HttpStatusCode.OK,
        verifyStatus: HttpStatusCode = HttpStatusCode.OK
    ): EmailCodeSession {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            requests += path to String(request.body.toByteArray())
            if (path.endsWith("/send")) {
                respond("""{"ok":true,"challengeId":"chal-1","expiresInSeconds":600,"resendAfterSeconds":60}""", sendStatus, json)
            } else {
                respond("""{"ok":true,"verified":true}""", verifyStatus, json)
            }
        }
        return EmailCodeSession(RecoveryApi(HttpClient(engine), "https://api.example.test")) { "install-id-0123456789" }
    }

    @Test
    fun verifyBeforeSendFailsWithoutCallingTheServer() = runTest {
        val result = session().verify("123456")
        assertEquals(RecoveryFailure.INVALID_CODE, (result as RecoveryResult.Failure).reason)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun sendNormalisesTheAddressAndRemembersTheChallenge() = runTest {
        val session = session()
        assertFalse(session.hasChallenge)

        session.send("  Person@Example.COM ", RecoveryPurpose.RECOVER)

        assertTrue(session.hasChallenge)
        assertTrue(requests.single().second.contains("\"email\":\"person@example.com\""))
    }

    @Test
    fun verifyUsesTheRememberedChallengeAndClearsItOnSuccess() = runTest {
        val session = session()
        session.send("a@b.co", RecoveryPurpose.RECOVER)

        val result = session.verify(" 482913 ")

        assertEquals(RecoveryResult.Success(Unit), result)
        assertTrue(requests.last().second.contains("\"challengeId\":\"chal-1\""))
        assertTrue(requests.last().second.contains("\"code\":\"482913\""))
        assertFalse(session.hasChallenge)
    }

    @Test
    fun aWrongCodeKeepsTheChallengeSoTheyCanTryAgain() = runTest {
        val session = session(verifyStatus = HttpStatusCode.BadRequest)
        session.send("a@b.co", RecoveryPurpose.RECOVER)

        session.verify("000000")

        assertTrue(session.hasChallenge)
    }

    @Test
    fun aFailedSendLeavesNoChallenge() = runTest {
        val session = session(sendStatus = HttpStatusCode.TooManyRequests)
        session.send("a@b.co", RecoveryPurpose.RECOVER)
        assertFalse(session.hasChallenge)
    }

    @Test
    fun plausibleEmails() {
        assertTrue(EmailCodeSession.isPlausibleEmail("person@example.com"))
        assertTrue(EmailCodeSession.isPlausibleEmail("  Person+tag@sub.example.co.in "))
        assertFalse(EmailCodeSession.isPlausibleEmail("person"))
        assertFalse(EmailCodeSession.isPlausibleEmail("person@"))
        assertFalse(EmailCodeSession.isPlausibleEmail("person@example"))
        assertFalse(EmailCodeSession.isPlausibleEmail("a b@example.com"))
        assertFalse(EmailCodeSession.isPlausibleEmail(""))
        assertFalse(EmailCodeSession.isPlausibleEmail("a@" + "b".repeat(260) + ".com"))
    }

    @Test
    fun codeMustBeSixDigits() {
        assertTrue(EmailCodeSession.isValidCode("012345"))
        assertFalse(EmailCodeSession.isValidCode("12345"))
        assertFalse(EmailCodeSession.isValidCode("1234567"))
        assertFalse(EmailCodeSession.isValidCode("12345a"))
    }

    @Test
    fun maskKeepsTwoLettersAndTheDomain() {
        assertEquals("pe****@example.com", EmailCodeSession.mask("person@example.com"))
        assertEquals("ab***@example.com", EmailCodeSession.mask("ab@example.com"))
        assertEquals("a***@example.com", EmailCodeSession.mask("a@example.com"))
        assertEquals("***", EmailCodeSession.mask("not-an-address"))
    }
}
