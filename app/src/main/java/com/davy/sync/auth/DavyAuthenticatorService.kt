package com.davy.sync.auth

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Service for the DAVy account authenticator.
 * 
 * This service is required by Android's account framework.
 */
class DavyAuthenticatorService : Service() {
    
    private var authenticator: DavyAuthenticator? = null
    
    override fun onCreate() {
        super.onCreate()
        authenticator = DavyAuthenticator(this)
    }
    
    override fun onBind(intent: Intent): IBinder {
        return authenticator!!.iBinder
    }
}
