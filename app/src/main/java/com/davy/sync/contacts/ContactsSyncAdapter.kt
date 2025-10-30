package com.davy.sync.contacts

import android.accounts.Account
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import com.davy.data.repository.AccountRepository
import com.davy.data.sync.CardDAVSyncService
import com.davy.sync.account.AndroidAccountManager
import android.content.ContentResolver
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
        fun androidAccountManager(): AndroidAccountManager
        fun contactContentProviderAdapter(): com.davy.data.contentprovider.ContactContentProviderAdapter
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

        // Determine sync origin/intent
        val isManual = extras.getBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false)
        val isExpedited = extras.getBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, false)
        val isUploadOnly = extras.getBoolean(ContentResolver.SYNC_EXTRAS_UPLOAD, false)
        val isInitialize = extras.getBoolean(ContentResolver.SYNC_EXTRAS_INITIALIZE, false)
        
        // Identify sync trigger source for better debugging
        val syncTrigger = when {
            isManual && isExpedited -> "MANUAL (user-initiated)"
            isManual -> "MANUAL"
            isExpedited -> "EXPEDITED"
            isUploadOnly -> "UPLOAD_ONLY"
            isInitialize -> "INITIALIZE"
            else -> "AUTO_SYNC (content-triggered)"
        }
        
        Timber.d("🔄 Sync trigger: $syncTrigger")
        Timber.d("Sync flags: manual=$isManual, expedited=$isExpedited, uploadOnly=$isUploadOnly, initialize=$isInitialize")

        // Policy: Block Android auto-sync unless this specific address book has dirty contacts
        // Even for UPLOAD_ONLY triggers, verify this address book actually needs syncing
        var effectiveUploadOnly = isUploadOnly
        if (!isManual && !isExpedited) {
            // Check if THIS specific address book/account has dirty contacts
            try {
                val androidAccountManager = entryPoint.androidAccountManager()
                val provider = entryPoint.contactContentProviderAdapter()
                var hasDirtyContacts = false

                if (account.type == AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK) {
                    val dirty = provider.getDirtyRawContactIds(account.name, AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK)
                    hasDirtyContacts = dirty.isNotEmpty()
                    Timber.d("Auto-sync gate: address-book account '${account.name}' has ${dirty.size} dirty contact(s)")
                } else {
                    val addressBooks = androidAccountManager.getAddressBookAccounts(account.name)
                    for (ab in addressBooks) {
                        val dirty = provider.getDirtyRawContactIds(ab.name, AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK)
                        if (dirty.isNotEmpty()) { hasDirtyContacts = true; break }
                    }
                    Timber.d("Auto-sync gate: main account '${account.name}' with hasDirtyContacts=$hasDirtyContacts across ${addressBooks.size} address books")
                }

                if (!hasDirtyContacts) {
                    Timber.w("🚫 BLOCKING Contacts Auto-Sync for '${account.name}' (no dirty contacts found)")
                    return
                } else {
                    Timber.d("✅ Allowing auto-sync for '${account.name}' (has dirty contacts), forcing PUSH-ONLY")
                    effectiveUploadOnly = true
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed auto-sync gate check for '${account.name}'; blocking auto-sync to be safe")
                return
            }
        }

        if (effectiveUploadOnly && !isManual && !isExpedited) {
            Timber.d("Allowing system-triggered upload-only sync (auto) for immediate push")
        }

        // CRITICAL: Use runBlocking to ensure sync completes before returning
        // Without this, the sync adapter returns immediately and sync doesn't actually happen
        // Handle InterruptedException when Android cancels the sync
        try {
            kotlinx.coroutines.runBlocking {
                try {
                    val androidAccountManager = entryPoint.androidAccountManager()
                    val isAddressBookAccount = account.type == AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK

                    if (isAddressBookAccount) {
                        // Request came for a specific address book account
                        val mainAccountName = androidAccountManager.getMainAccountName(account)
                        val addressBookId = androidAccountManager.getAddressBookId(account)

                        if (mainAccountName == null || addressBookId == null) {
                            Timber.w("Address book sync request missing metadata: mainAccountName=$mainAccountName, addressBookId=$addressBookId")
                            syncResult.stats.numIoExceptions++
                            return@runBlocking
                        }

                        val davyAccount = entryPoint.accountRepository().getByAccountName(mainAccountName)
                        if (davyAccount == null) {
                            Timber.w("DAVy main account not found for address book account: ${account.name}")
                            syncResult.stats.numIoExceptions++
                            return@runBlocking
                        }

                        Timber.tag(TAG).d("Starting CardDAV sync for address book id=%s (account: %s)", addressBookId, davyAccount.accountName)
                        Timber.d("Starting CardDAV sync for address book id=$addressBookId (account: ${davyAccount.accountName})")
                        val cardDAVResult = entryPoint.cardDAVSyncService().syncAddressBook(davyAccount, addressBookId, pushOnly = effectiveUploadOnly)

                        // Update sync result stats
                        syncResult.stats.numInserts += cardDAVResult.contactsDownloaded.toLong()
                        syncResult.stats.numUpdates += cardDAVResult.contactsUploaded.toLong()
                        syncResult.stats.numDeletes += cardDAVResult.contactsDeleted.toLong()
                        if (cardDAVResult.errors > 0) syncResult.stats.numIoExceptions += cardDAVResult.errors.toLong()
                    } else {
                        // Request came for a main DAVy account (or system-initiated manual)
                        val davyAccount = entryPoint.accountRepository().getByAccountName(account.name)
                        if (davyAccount == null) {
                            Timber.w("DAVy account not found for Android account: ${account.name} - will retry")
                            syncResult.stats.numIoExceptions++
                            return@runBlocking
                        }

                        Timber.tag(TAG).d("Starting CardDAV sync for DAVy account: %s", davyAccount.accountName)
                        Timber.d("Starting CardDAV sync for DAVy account: ${davyAccount.accountName}")
                        val cardDAVResult = entryPoint.cardDAVSyncService().syncAccount(davyAccount, pushOnly = effectiveUploadOnly)
                        
                        // Update sync result stats
                        syncResult.stats.numInserts += cardDAVResult.contactsDownloaded.toLong()
                        syncResult.stats.numUpdates += cardDAVResult.contactsUploaded.toLong()
                        syncResult.stats.numDeletes += cardDAVResult.contactsDeleted.toLong()
                        if (cardDAVResult.errors > 0) syncResult.stats.numIoExceptions += cardDAVResult.errors.toLong()
                    }
                    Timber.tag(TAG).d("============================================")
                    Timber.tag(TAG).d("CardDAV sync COMPLETED")
                    Timber.tag(TAG).d("(stats aggregated above)")
                    Timber.tag(TAG).d("============================================")
                    Timber.d("============================================")
                    Timber.d("CardDAV sync COMPLETED (stats aggregated above)")
                    Timber.d("============================================")
                    
                    
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
