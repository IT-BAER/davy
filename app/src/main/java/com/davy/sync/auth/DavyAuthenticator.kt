package com.davy.sync.auth

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.NetworkErrorException
import android.content.Context
import android.os.Bundle

/**
 * Account authenticator for DAVy accounts.
 * 
 * This is required by Android's sync framework but we don't actually
 * use it for authentication - we manage accounts in our own database.
 */
class DavyAuthenticator(context: Context) : AbstractAccountAuthenticator(context) {
    
    override fun addAccount(
        response: AccountAuthenticatorResponse?,
        accountType: String?,
        authTokenType: String?,
        requiredFeatures: Array<out String>?,
        options: Bundle?
    ): Bundle {
        // Not supported - we manage accounts through DAVy's UI
        return Bundle()
    }
    
    override fun getAuthToken(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        authTokenType: String?,
        options: Bundle?
    ): Bundle {
        throw UnsupportedOperationException()
    }
    
    override fun getAuthTokenLabel(authTokenType: String?): String {
        throw UnsupportedOperationException()
    }
    
    override fun confirmCredentials(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        options: Bundle?
    ): Bundle {
        return Bundle()
    }
    
    override fun updateCredentials(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        authTokenType: String?,
        options: Bundle?
    ): Bundle {
        throw UnsupportedOperationException()
    }
    
    override fun hasFeatures(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        features: Array<out String>?
    ): Bundle {
        val result = Bundle()
        result.putBoolean(android.accounts.AccountManager.KEY_BOOLEAN_RESULT, false)
        return result
    }
    
    override fun editProperties(
        response: AccountAuthenticatorResponse?,
        accountType: String?
    ): Bundle {
        throw UnsupportedOperationException()
    }
}
