package com.davy.domain.usecase

import com.davy.data.repository.AddressBookRepository
import com.davy.domain.model.AddressBook
import javax.inject.Inject

/**
 * Use case for retrieving address books for an account.
 */
class GetAddressBooksUseCase @Inject constructor(
    private val addressBookRepository: AddressBookRepository
) {
    
    /**
     * Get all address books for an account.
     * 
     * @param accountId The account ID
     * @return List of address books
     */
    suspend operator fun invoke(accountId: Long): List<AddressBook> {
        return addressBookRepository.getByAccountId(accountId)
    }
}
