package com.ritesh.cashiro.di

import android.content.Context
import com.ritesh.cashiro.core.Constants
import com.ritesh.cashiro.data.security.EncryptedPrefsSecureStore
import com.ritesh.cashiro.data.security.RecoveryApi
import com.ritesh.cashiro.data.security.SecureStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import javax.inject.Singleton

/**
 * Wiring for the app PIN and its email recovery.
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideSecureStore(@ApplicationContext context: Context): SecureStore =
        EncryptedPrefsSecureStore(context, "paisaiq_secure_prefs")

    @Provides
    @Singleton
    fun provideRecoveryApi(): RecoveryApi {
        val client = HttpClient(Android) {
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 20_000
            }
        }
        return RecoveryApi(client, Constants.Links.API_BASE_URL)
    }
}
