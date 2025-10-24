package com.davy.domain.usecase

import com.davy.data.repository.AddressBookRepository
import com.davy.data.repository.AccountRepository
import com.davy.domain.model.AddressBook
import com.davy.sync.SyncManager
import com.davy.sync.account.AndroidAccountManager
import com.davy.data.contentprovider.ContactContentProviderAdapter
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for updating address book settings.
 *
 * Clean approach for sync toggle:
 * - When sync is disabled: (future) remove from Contacts Provider if needed
 * - When sync is enabled: trigger sync for THIS address book only
 */
class UpdateAddressBookUseCase @Inject constructor(
    private val addressBookRepository: AddressBookRepository,
    private val accountRepository: AccountRepository,
    private val androidAccountManager: AndroidAccountManager,
    private val contactContentProviderAdapter: ContactContentProviderAdapter,
    private val syncManager: SyncManager
) {

    /**
     * Update an address book and trigger item-scoped sync when enabling.
     */
    suspend operator fun invoke(addressBook: AddressBook) {
        // Fetch old state to detect transitions
        val old = addressBookRepository.getById(addressBook.id)

        // Persist change first
        addressBookRepository.update(addressBook)

        if (old != null) {
            // Transition: enabled -> disabled
            if (old.syncEnabled && !addressBook.syncEnabled) {
                Timber.d("[ADDRBOOK_DISABLE] Disabling contacts sync for '${addressBook.displayName}'")
                val account = accountRepository.getById(addressBook.accountId)
                val mainAccountName = account?.accountName
                if (mainAccountName != null) {
                    // Try to determine the Android account name
                    val androidAccountName = addressBook.androidAccountName
                        ?: androidAccountManager.findAddressBookAccount(mainAccountName, addressBook.id)?.name

                    // 1) Delete all contacts for this per-address-book account from Contacts provider
                    if (androidAccountName != null) {
                        val deleted = contactContentProviderAdapter.deleteAllContactsForAccount(
                            androidAccountName,
                            AndroidAccountManager.ACCOUNT_TYPE_ADDRESS_BOOK
                        )
                        Timber.d("[ADDRBOOK_DISABLE] Deleted $deleted raw contacts for account: $androidAccountName")
                    } else {
                        Timber.w("[ADDRBOOK_DISABLE] Could not determine Android account name; skipping provider cleanup")
                    }

                    // 2) Remove the per-address-book Android account
                    val removed = androidAccountManager.removeAddressBookAccountById(mainAccountName, addressBook.id)
                    Timber.d("[ADDRBOOK_DISABLE] Address book account removed: $removed")

                    // 3) Clear androidAccountName in DB
                    val cleared = addressBook.copy(androidAccountName = null)
                    addressBookRepository.update(cleared)
                }
            }

            // Transition: disabled -> enabled
            if (!old.syncEnabled && addressBook.syncEnabled) {
                Timber.d("[ADDRBOOK_ENABLE] Enabling contacts sync for '${addressBook.displayName}'")
                val account = accountRepository.getById(addressBook.accountId)
                val mainAccountName = account?.accountName
                if (mainAccountName != null) {
                    // Ensure per-address-book Android account exists and persist its name
                    val created = androidAccountManager.createAddressBookAccount(
                        mainAccountName = mainAccountName,
                        addressBookName = addressBook.displayName,
                        addressBookId = addressBook.id,
                        addressBookUrl = addressBook.url
                    )
                    if (created != null) {
                        addressBookRepository.update(addressBook.copy(androidAccountName = created.name))
                        Timber.d("[ADDRBOOK_ENABLE] Created/ensured address book account: ${created.name}")
                    } else {
                        Timber.w("[ADDRBOOK_ENABLE] Failed to create address book Android account; contacts won't appear until created")
                    }

                    // Trigger item-scoped sync for this address book only
                    syncManager.syncAddressBook(addressBook.accountId, addressBook.id)
                }
            }
        }
    }
}
