package com.davy

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule

/**
 * Base class for Compose UI tests.
 * 
 * Provides Compose test rule and common test utilities.
 * All Compose UI tests should extend this class.
 */
abstract class ComposeTestRule {
    
    /**
     * Compose test rule for launching composables in tests.
     */
    @get:Rule
    val composeTestRule = createComposeRule()
    
    /**
     * Helper function to wait for compose idle state.
     */
    protected fun waitForIdle() {
        composeTestRule.waitForIdle()
    }
}
