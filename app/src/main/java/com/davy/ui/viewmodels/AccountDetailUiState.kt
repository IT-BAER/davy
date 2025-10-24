package com.davy.ui.viewmodels

import androidx.compose.runtime.Immutable
import com.davy.domain.model.Account
import com.davy.domain.model.AddressBook
import com.davy.domain.model.Calendar
import com.davy.domain.model.WebCalSubscription

/**
 * Consolidated UI state for AccountDetailScreen.
 * Using single data class marked @Immutable allows Compose to skip unnecessary recompositions
 * when state hasn't changed, dramatically improving performance.
 * 
 * Best Practice: Consolidate multiple StateFlows into single UiState to minimize recompositions.
 * Reference: https://developer.android.com/develop/ui/compose/performance/stability
 */
@Immutable
data class AccountDetailUiState(
    val account: Account? = null,
    val calendars: List<Calendar> = emptyList(),
    val addressBooks: List<AddressBook> = emptyList(),
    val webCalSubscriptions: List<WebCalSubscription> = emptyList(),
    val isSyncing: Boolean = false,
    val syncingCalendarIds: Set<Long> = emptySet(),
    val syncingAddressBookIds: Set<Long> = emptySet(),
    val isRefreshingCollections: Boolean = false,
    val accountDeleted: Boolean = false,
    val isTestingCredentials: Boolean = false,
    val credentialTestResult: String? = null
) {
    /**
     * Derived property using inline computation - no additional state tracking needed.
     * This is efficiently computed only when accessed, following Android best practices.
     */
    val isDoingFullSync: Boolean
        get() = isSyncing && syncingCalendarIds.isEmpty() && syncingAddressBookIds.isEmpty()
}
