package com.davy.ui.navigation

/**
 * Navigation routes for DAVy app.
 * 
 * Defines all navigation destinations as sealed class for type safety.
 */
sealed class Route(val route: String) {
    
    /**
     * Splash screen - shown while checking account status
     */
    object Splash : Route("splash")
    
    /**
     * Onboarding screen - shown when no accounts configured
     */
    object Onboarding : Route("onboarding")
    
    /**
     * Account setup screen - for adding new Nextcloud account
     */
    object AccountSetup : Route("account_setup")
    
    /**
     * Main sync screen - shows sync status and controls
     */
    object SyncHome : Route("sync_home")
    
    /**
     * Settings screen - app and account settings
     */
    object Settings : Route("settings")
    
    /**
     * Account list screen - manage multiple accounts
     */
    object AccountList : Route("account_list")
    
    /**
     * Calendar list screen - view and manage calendars
     */
    object CalendarList : Route("calendar_list")
    
    /**
     * Address book list screen - view and manage address books
     */
    object AddressBookList : Route("address_book_list")
    
    /**
     * Contact list screen - view contacts for an address book
     * @param addressBookId Address book ID as route parameter
     */
    data class ContactList(val addressBookId: Long) : Route("contacts/{addressBookId}") {
        companion object {
            const val routePattern = "contacts/{addressBookId}"
            fun createRoute(addressBookId: Long) = "contacts/$addressBookId"
        }
    }

    /**
     * Task list screen - view and manage task lists
     */
    object TaskListScreen : Route("task_lists")

    /**
     * Task screen - view tasks for a task list
     * @param taskListId Task list ID as route parameter
     */
    data class TaskScreen(val taskListId: Long) : Route("tasks/{taskListId}") {
        companion object {
            const val routePattern = "tasks/{taskListId}"
            fun createRoute(taskListId: Long) = "tasks/$taskListId"
        }
    }
    
    /**
     * Sync status screen - view sync status for all accounts
     */
    object SyncStatus : Route("sync_status")
    
    /**
     * Sync configuration screen - per-account sync settings
     * @param accountId Account ID as route parameter
     */
    data class SyncConfiguration(val accountId: Long) : Route("sync_config/{accountId}") {
        companion object {
            const val routePattern = "sync_config/{accountId}"
            fun createRoute(accountId: String) = "sync_config/$accountId"
        }
    }
    
    /**
     * Conflict resolution screen - resolve sync conflicts
     * @param conflictId Conflict ID as route parameter
     */
    data class ConflictResolution(val conflictId: Long) : Route("conflict/{conflictId}") {
        companion object {
            const val routePattern = "conflict/{conflictId}"
            fun createRoute(conflictId: Long) = "conflict/$conflictId"
        }
    }
    
    /**
     * About screen
     */
    object About : Route("about")
    
    /**
     * Privacy Policy screen
     */
    object PrivacyPolicy : Route("privacy_policy")
    
    /**
     * Terms of Service screen
     */
    object TermsOfService : Route("terms_of_service")
    
    /**
     * First-time user onboarding tour
     */
    object OnboardingTour : Route("onboarding_tour")
}
