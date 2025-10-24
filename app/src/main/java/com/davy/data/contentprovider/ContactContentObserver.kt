package com.davy.data.contentprovider

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/**
 * ContentObserver for detecting changes to Android Contacts.
 *
 * This observer monitors:
 * - Changes to RawContacts (contact creation/deletion)
 * - Changes to Data rows (contact field modifications)
 *
 * The observer emits events when contacts are modified by:
 * - User editing in Android Contacts app
 * - Other sync adapters (Google Contacts, Exchange, etc.)
 * - System operations
 *
 * This allows DAVy to detect local changes and upload them to the CardDAV server.
 */
class ContactContentObserver @Inject constructor() {

    /**
     * Type of contact change detected.
     */
    enum class ChangeType {
        /**
         * RawContacts table changed (contact added/deleted).
         */
        RAW_CONTACTS,

        /**
         * Data table changed (contact fields modified).
         */
        DATA,

        /**
         * Unknown change type.
         */
        UNKNOWN
    }

    /**
     * Event emitted when contacts change.
     *
     * @property changeType The type of change detected
     * @property uri The URI that changed
     */
    data class ContactChangeEvent(
        val changeType: ChangeType,
        val uri: Uri
    )

    /**
     * Observes contact changes as a Flow.
     *
     * This returns a cold Flow that starts observing when collected
     * and stops when the Flow is canceled.
     *
     * @param contentResolver The ContentResolver to use for observation
     * @return Flow of ContactChangeEvent
     */
    fun observeContactChanges(contentResolver: ContentResolver): Flow<ContactChangeEvent> = callbackFlow {
        val handler = Handler(Looper.getMainLooper())

        // Observer for RawContacts changes
        val rawContactsObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(ContactChangeEvent(ChangeType.RAW_CONTACTS, uri ?: ContactsContract.RawContacts.CONTENT_URI))
            }
        }

        // Observer for Data changes
        val dataObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(ContactChangeEvent(ChangeType.DATA, uri ?: ContactsContract.Data.CONTENT_URI))
            }
        }

        // Register observers
        contentResolver.registerContentObserver(
            ContactsContract.RawContacts.CONTENT_URI,
            true,
            rawContactsObserver
        )

        contentResolver.registerContentObserver(
            ContactsContract.Data.CONTENT_URI,
            true,
            dataObserver
        )

        // Unregister when Flow is canceled
        awaitClose {
            contentResolver.unregisterContentObserver(rawContactsObserver)
            contentResolver.unregisterContentObserver(dataObserver)
        }
    }

    /**
     * Observes changes to a specific account's contacts.
     *
     * This filters changes to only include contacts belonging to the specified account.
     *
     * @param contentResolver The ContentResolver to use for observation
     * @param accountName The account name to filter by
     * @param accountType The account type to filter by
     * @return Flow of ContactChangeEvent for the specified account
     */
    fun observeAccountContactChanges(
        contentResolver: ContentResolver,
        accountName: String,
        accountType: String
    ): Flow<ContactChangeEvent> = callbackFlow {
        val handler = Handler(Looper.getMainLooper())

        // Build URI for account-specific RawContacts
        val accountUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
            .build()

        // Observer for account-specific changes
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                // Emit event for this account
                trySend(ContactChangeEvent(ChangeType.RAW_CONTACTS, uri ?: accountUri))
            }
        }

        // Register observer
        contentResolver.registerContentObserver(
            accountUri,
            true,
            observer
        )

        // Unregister when Flow is canceled
        awaitClose {
            contentResolver.unregisterContentObserver(observer)
        }
    }

    /**
     * Creates a simple ContentObserver for manual registration.
     *
     * This is useful when you need to register an observer outside of a Flow context.
     *
     * @param onChange Callback invoked when content changes
     * @return ContentObserver instance
     */
    fun createObserver(onChange: (Uri?) -> Unit): ContentObserver {
        val handler = Handler(Looper.getMainLooper())
        return object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                onChange(uri)
            }
        }
    }

    /**
     * Registers an observer for RawContacts changes.
     *
     * @param contentResolver The ContentResolver to use
     * @param observer The ContentObserver to register
     */
    fun registerRawContactsObserver(
        contentResolver: ContentResolver,
        observer: ContentObserver
    ) {
        contentResolver.registerContentObserver(
            ContactsContract.RawContacts.CONTENT_URI,
            true,
            observer
        )
    }

    /**
     * Registers an observer for Data changes.
     *
     * @param contentResolver The ContentResolver to use
     * @param observer The ContentObserver to register
     */
    fun registerDataObserver(
        contentResolver: ContentResolver,
        observer: ContentObserver
    ) {
        contentResolver.registerContentObserver(
            ContactsContract.Data.CONTENT_URI,
            true,
            observer
        )
    }

    /**
     * Unregisters a ContentObserver.
     *
     * @param contentResolver The ContentResolver to use
     * @param observer The ContentObserver to unregister
     */
    fun unregisterObserver(
        contentResolver: ContentResolver,
        observer: ContentObserver
    ) {
        contentResolver.unregisterContentObserver(observer)
    }
}
