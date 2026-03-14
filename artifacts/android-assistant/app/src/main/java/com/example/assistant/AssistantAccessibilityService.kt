package com.example.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * AssistantAccessibilityService.kt
 * Accessibility service for Aria.
 * Allows the assistant to:
 *  - Read screen content
 *  - Navigate back/home/recents
 *  - Perform global actions on behalf of the user
 */
class AssistantAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AssistantAccessibilityService? = null

        fun isEnabled() = instance != null

        fun goHome()    = instance?.performGlobalAction(GLOBAL_ACTION_HOME)
        fun goBack()    = instance?.performGlobalAction(GLOBAL_ACTION_BACK)
        fun showRecent()= instance?.performGlobalAction(GLOBAL_ACTION_RECENTS)
        fun showNotif() = instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        fun lockScreen()= instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        fun screenshot()= instance?.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo.apply {
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Observe events — extend here to read on-screen text or react to app changes
    }

    override fun onInterrupt() {
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
