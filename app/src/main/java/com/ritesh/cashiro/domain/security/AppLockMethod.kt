package com.ritesh.cashiro.domain.security

/**
 * How the app proves it is the owner at the lock screen.
 */
enum class AppLockMethod {
    /** The phone's own fingerprint, face or screen lock. How the app locked before app PINs existed. */
    DEVICE_CREDENTIAL,

    /** A 4-digit PIN owned by the app, with an optional fingerprint or face shortcut. */
    APP_PIN;

    companion object {
        fun fromName(name: String?): AppLockMethod =
            entries.firstOrNull { it.name == name } ?: DEVICE_CREDENTIAL
    }
}
