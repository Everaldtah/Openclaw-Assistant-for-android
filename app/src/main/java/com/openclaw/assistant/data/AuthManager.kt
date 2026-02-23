package com.openclaw.assistant.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.openclaw.assistant.util.Constants

class AuthManager(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveJwt(jwt: String) = prefs.edit().putString(KEY_JWT, jwt).apply()
    fun getJwt(): String = prefs.getString(KEY_JWT, "") ?: ""

    fun saveServerUrl(url: String) = prefs.edit().putString(KEY_SERVER_URL, url).apply()
    fun getServerUrl(): String = prefs.getString(KEY_SERVER_URL, Constants.WS_URL_DEFAULT) ?: Constants.WS_URL_DEFAULT

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val KEY_JWT = "jwt"
        private const val KEY_SERVER_URL = "server_url"
    }
}
