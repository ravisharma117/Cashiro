package com.ritesh.cashiro.data.security

/**
 * One email-code exchange: ask for a code, then check what the person typed.
 *
 * It remembers the challenge id the server returned, so callers deal only with the
 * address and the code. Used both when confirming a recovery email and when recovering a
 * forgotten PIN.
 */
class EmailCodeSession(
    private val api: RecoveryApi,
    private val installId: () -> String
) {
    private var challengeId: String? = null

    /** True once a code has been sent and not yet successfully checked. */
    val hasChallenge: Boolean get() = challengeId != null

    suspend fun send(email: String, purpose: RecoveryPurpose): RecoveryResult<CodeSent> {
        val result = api.sendCode(normalise(email), purpose, installId())
        if (result is RecoveryResult.Success) challengeId = result.value.challengeId
        return result
    }

    suspend fun verify(code: String): RecoveryResult<Unit> {
        val id = challengeId ?: return RecoveryResult.Failure(RecoveryFailure.INVALID_CODE)
        val result = api.verifyCode(id, code.trim(), installId())
        if (result is RecoveryResult.Success) challengeId = null
        return result
    }

    companion object {
        const val CODE_LENGTH = 6
        private const val MAX_EMAIL_LENGTH = 254
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun normalise(email: String): String = email.trim().lowercase()

        /** A light check that it looks like an address; the mailbox itself is the real test. */
        fun isPlausibleEmail(email: String): Boolean {
            val clean = normalise(email)
            return clean.length <= MAX_EMAIL_LENGTH && EMAIL.matches(clean)
        }

        fun isValidCode(code: String): Boolean =
            code.length == CODE_LENGTH && code.all { it in '0'..'9' }

        /** `pe****@example.com` - recognisable to its owner, not enough to guess. */
        fun mask(email: String): String {
            val clean = normalise(email)
            val at = clean.indexOf('@')
            if (at < 0) return "***"
            val local = clean.substring(0, at)
            val domain = clean.substring(at)
            val visible = local.take(2)
            return visible + "*".repeat((local.length - visible.length).coerceAtLeast(3)) + domain
        }
    }
}
