package com.ritesh.cashiro.data.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.ritesh.cashiro.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Looks up a contact's name from a phone number, to help recognise who a payment came from.
 *
 * Only a single number is looked up at a time, only when the user switched it on and allowed the
 * permission, and the name is used for matching and then dropped. Nothing is stored or logged.
 */
@Singleton
class ContactNameLookup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: UserPreferencesRepository
) {
    suspend fun nameFor(phone: String): String? = withContext(Dispatchers.IO) {
        if (!preferences.repaymentUseContacts.first()) return@withContext null
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return@withContext null

        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone))
        runCatching {
            context.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
    }
}
