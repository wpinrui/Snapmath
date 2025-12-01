package com.wpinrui.snapmath.data.preferences

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wpinrui.snapmath.BuildConfig

class ApiKeyManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "snapmath_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_OPENAI_API_KEY = "openai_api_key"
    }

    /**
     * Gets the active API key.
     * Priority: User-provided key > BuildConfig key
     */
    fun getApiKey(): String {
        val userKey = prefs.getString(KEY_OPENAI_API_KEY, null)
        if (!userKey.isNullOrBlank()) {
            return userKey
        }
        return BuildConfig.OPENAI_API_KEY
    }

    /**
     * Saves a user-provided API key.
     */
    fun setApiKey(apiKey: String) {
        prefs.edit().putString(KEY_OPENAI_API_KEY, apiKey).apply()
    }

    /**
     * Clears the user-provided API key (falls back to BuildConfig).
     */
    fun clearApiKey() {
        prefs.edit().remove(KEY_OPENAI_API_KEY).apply()
    }

    /**
     * Checks if a valid API key is available.
     */
    fun hasApiKey(): Boolean {
        return getApiKey().isNotBlank()
    }

    /**
     * Checks if user has provided their own key.
     */
    fun hasUserProvidedKey(): Boolean {
        return !prefs.getString(KEY_OPENAI_API_KEY, null).isNullOrBlank()
    }
}
