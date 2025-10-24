package com.davy.sync.contacts

import android.accounts.Account
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import com.davy.data.repository.AccountRepository
import com.davy.data.sync.CardDAVSyncService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * SyncAdapter for Android Contacts integration.
 * 
 * This adapter integrates with Android's sync framework to allow
 * DAVy contacts to sync with the Android Contacts app.
 */
class ContactsSyncAdapter(
    context: Context,
    autoInitialize: Boolean
) : AbstractThreadedSyncAdapter(context, autoInitialize) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ContactsSyncAdapterEntryPoint {
        fun accountRepository(): AccountRepository
        fun cardDAVSyncService(): CardDAVSyncService
    }

    private val entryPoint: ContactsSyncAdapterEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ContactsSyncAdapterEntryPoint::class.java
        )
    }

    override fun onPerformSync(
        account: Account,
        extras: Bundle,
        authority: String,
        provider: ContentProviderClient,
        syncResult: SyncResult
    ) {
        val TAG = "ContactsSyncAdapter"
        Timber.tag(TAG).d("============================================")
        Timber.tag(TAG).d("onPerformSync STARTED")
        Timber.tag(TAG).d("Account: %s, Authority: %s", account.name, authority)
        Timber.tag(TAG).d("============================================")
        Timber.d("============================================")
        Timber.d("ContactsSyncAdapter: onPerformSync STARTED")
        Timber.d("Account: ${account.name}, Authority: $authority")
        Timber.d("============================================")

        // CRITICAL: Use runBlocking to ensure sync completes before returning
        // Without this, the sync adapter returns immediately and sync doesn't actually happen
        // Handle InterruptedException when Android cancels the sync
        try {
            kotlinx.coroutines.runBlocking {
                try {
                    // Find the DAVy account matching this Android account
                    val davyAccount = entryPoint.accountRepository()
                        .getByAccountName(account.name)
                    
                    if (davyAccount == null) {
                        // Likely a transient state immediately after rename; do not mark as auth error
                        Timber.tag(TAG).w("DAVy account not found for Android account: %s (maybe rename in progress) - will retry", account.name)
                        Timber.w("DAVy account not found for Android account: ${account.name} (maybe rename in progress) - will retry")
                        syncResult.stats.numIoExceptions++
                        return@runBlocking
                    }

                    // Perform CardDAV sync
                    Timber.tag(TAG).d("Starting CardDAV sync for DAVy account: %s", davyAccount.accountName)
                    Timber.d("Starting CardDAV sync for DAVy account: ${davyAccount.accountName}")
                    val cardDAVResult = entryPoint.cardDAVSyncService().syncAccount(davyAccount)
                    
                    Timber.tag(TAG).d("============================================")
                    Timber.tag(TAG).d("CardDAV sync COMPLETED")
                    Timber.tag(TAG).d("Downloaded: %s", cardDAVResult.contactsDownloaded)
                    Timber.tag(TAG).d("Uploaded: %s", cardDAVResult.contactsUploaded)
                    Timber.tag(TAG).d("Deleted: %s", cardDAVResult.contactsDeleted)
                    Timber.tag(TAG).d("Errors: %s", cardDAVResult.errors)
                    Timber.tag(TAG).d("============================================")
                    Timber.d("============================================")
                    Timber.d("CardDAV sync COMPLETED")
                    Timber.d("Downloaded: ${cardDAVResult.contactsDownloaded}")
                    Timber.d("Uploaded: ${cardDAVResult.contactsUploaded}")
                    Timber.d("Deleted: ${cardDAVResult.contactsDeleted}")
                    Timber.d("Errors: ${cardDAVResult.errors}")
                    Timber.d("============================================")
                    
                    // Update sync result stats
                    syncResult.stats.numInserts += cardDAVResult.contactsDownloaded.toLong()
                    syncResult.stats.numUpdates += cardDAVResult.contactsUploaded.toLong()
                    syncResult.stats.numDeletes += cardDAVResult.contactsDeleted.toLong()
                    
                    if (cardDAVResult.errors > 0) {
                        syncResult.stats.numIoExceptions += cardDAVResult.errors.toLong()
                    }
                    
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "ContactsSyncAdapter sync FAILED with exception")
                    Timber.e(e, "ContactsSyncAdapter sync FAILED with exception")
                    syncResult.stats.numIoExceptions++
                }
            }
        } catch (e: InterruptedException) {
            Timber.tag(TAG).w(e, "ContactsSyncAdapter sync was interrupted/canceled")
            Timber.w(e, "ContactsSyncAdapter sync was interrupted/canceled")
            Thread.currentThread().interrupt() // Restore interrupt status
        }
        
        Timber.tag(TAG).d("ContactsSyncAdapter: onPerformSync FINISHED")
        Timber.d("ContactsSyncAdapter: onPerformSync FINISHED")
    }

    override fun onSyncCanceled() {
        super.onSyncCanceled()
        Timber.d("ContactsSyncAdapter: Sync canceled")
    }
}
