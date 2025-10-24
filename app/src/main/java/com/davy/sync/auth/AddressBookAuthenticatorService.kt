package com.davy.sync.auth

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.os.bundleOf
import com.davy.R

/**
 * Account authenticator for address book accounts (separate account type).
 * 
 * Following reference implementation architecture: each CardDAV address book gets its own Android account
 * with a different account type (com.davy.addressbook) to enable read-write access.
 */
class AddressBookAuthenticatorService : Service() {

    private lateinit var accountAuthenticator: AccountAuthenticator

    override fun onCreate() {
        accountAuthenticator = AccountAuthenticator(this)
    }

    override fun onBind(intent: Intent?) =
        accountAuthenticator.iBinder.takeIf { intent?.action == AccountManager.ACTION_AUTHENTICATOR_INTENT }

    private class AccountAuthenticator(
        val context: Context
    ) : AbstractAccountAuthenticator(context) {

        // Address book accounts cannot be added directly - they're created programmatically
        override fun addAccount(
            response: AccountAuthenticatorResponse?,
            accountType: String?,
            authTokenType: String?,
            requiredFeatures: Array<String>?,
            options: Bundle?
        ) = bundleOf(
            AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE to response,
            AccountManager.KEY_ERROR_CODE to AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION,
            AccountManager.KEY_ERROR_MESSAGE to context.getString(R.string.account_prefs_use_app)
        )

        override fun editProperties(response: AccountAuthenticatorResponse?, accountType: String?) = null
        override fun getAuthTokenLabel(p0: String?) = null
        override fun confirmCredentials(p0: AccountAuthenticatorResponse?, p1: Account?, p2: Bundle?) = null
        override fun updateCredentials(p0: AccountAuthenticatorResponse?, p1: Account?, p2: String?, p3: Bundle?) = null
        override fun getAuthToken(p0: AccountAuthenticatorResponse?, p1: Account?, p2: String?, p3: Bundle?) = null
        
        // Return null to match reference implementation behavior
        // This is required for the Contacts app to recognize the account correctly
        override fun hasFeatures(
            response: AccountAuthenticatorResponse?,
            account: Account?,
            features: Array<String>?
        ) = null
    }
}
