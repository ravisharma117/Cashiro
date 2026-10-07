package com.ritesh.cashiro.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Small key-value storage for secrets, behind an interface so [PinStore] can be tested
 * on a plain JVM.
 */
interface SecureStore {
    fun getString(key: String): String?
    fun getInt(key: String, default: Int): Int
    fun getLong(key: String, default: Long): Long
    fun putString(key: String, value: String)
    fun putInt(key: String, value: Int)
    fun putLong(key: String, value: Long)
    fun remove(vararg keys: String)
}

/**
 * Encrypted on the phone with a key held in the Android Keystore. Mirrors how the cloud
 * credentials are stored; if the Keystore is unusable it falls back to private,
 * unencrypted preferences rather than losing the lock.
 */
class EncryptedPrefsSecureStore(context: Context, name: String) : SecureStore {

    private val prefs: SharedPreferences by lazy {
        try {
            EncryptedSharedPreferences.create(
                name,
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "Encrypted preferences unavailable, using private preferences", e)
            context.getSharedPreferences("${name}_fallback", Context.MODE_PRIVATE)
        }
    }

    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)

    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()

    override fun remove(vararg keys: String) {
        val editor = prefs.edit()
        keys.forEach { editor.remove(it) }
        editor.apply()
    }

    private companion object {
        const val TAG = "SecureStore"
    }
}
