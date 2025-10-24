package com.davy.domain.usecase

import com.davy.data.repository.AccountRepository
import com.davy.domain.model.Account
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case to get all accounts.
 */
class GetAllAccountsUseCase @Inject constructor(
    private val accountRepository: AccountRepository
) {
    operator fun invoke(): Flow<List<Account>> {
        return accountRepository.getAllFlow()
    }
}
