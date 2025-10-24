package com.davy.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure storage for account credentials using EncryptedSharedPreferences.
 * 
 * Credentials are encrypted using AES256-GCM and keys are stored in Android Keystore.
 * 
 * @property context Application context
 */
@Singleton
class CredentialStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    
    private val sharedPreferences: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    /**
     * Store password for an account.
     * 
     * @param accountId The account ID
     * @param password The password to store
     */
    fun storePassword(accountId: Long, password: String) {
        sharedPreferences.edit()
            .putString(getPasswordKey(accountId), password)
            .apply()
    }
    
    /**
     * Retrieve password for an account.
     * 
     * @param accountId The account ID
     * @return The stored password, or null if not found
     */
    fun getPassword(accountId: Long): String? {
        return sharedPreferences.getString(getPasswordKey(accountId), null)
    }
    
    /**
     * Store authentication token for an account.
     * 
     * @param accountId The account ID
     * @param token The token to store
     */
    fun storeToken(accountId: Long, token: String) {
        sharedPreferences.edit()
            .putString(getTokenKey(accountId), token)
            .apply()
    }
    
    /**
     * Retrieve authentication token for an account.
     * 
     * @param accountId The account ID
     * @return The stored token, or null if not found
     */
    fun getToken(accountId: Long): String? {
        return sharedPreferences.getString(getTokenKey(accountId), null)
    }
    
    /**
     * Delete all credentials for an account.
     * 
     * @param accountId The account ID
     */
    fun deleteCredentials(accountId: Long) {
        sharedPreferences.edit()
            .remove(getPasswordKey(accountId))
            .remove(getTokenKey(accountId))
            .apply()
    }
    
    /**
     * Check if credentials exist for an account.
     * 
     * @param accountId The account ID
     * @return True if password or token exists
     */
    fun hasCredentials(accountId: Long): Boolean {
        return sharedPreferences.contains(getPasswordKey(accountId)) ||
                sharedPreferences.contains(getTokenKey(accountId))
    }
    
    /**
     * Clear all stored credentials (for debugging/testing only).
     */
    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }
    
    private fun getPasswordKey(accountId: Long): String {
        return "${KEY_PREFIX_PASSWORD}_$accountId"
    }
    
    private fun getTokenKey(accountId: Long): String {
        return "${KEY_PREFIX_TOKEN}_$accountId"
    }
    
    companion object {
        private const val PREFS_FILE_NAME = "davy_secure_credentials"
        private const val KEY_PREFIX_PASSWORD = "account_password"
        private const val KEY_PREFIX_TOKEN = "account_token"
    }
}
