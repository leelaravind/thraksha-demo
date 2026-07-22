package com.thraksha.guardian.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Production accessibility service for UI automation: gestures, global actions,
 * and node-tree traversal. Event monitoring uses debouncing to avoid log floods.
 */
class ThrakshaAccessibilityService : AccessibilityService() {

    private val lastEventTimeMs = mutableMapOf<Int, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility service connected")

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = NOTIFICATION_TIMEOUT_MS
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "Accessibility service unbinding")
        lastEventTimeMs.clear()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!shouldProcessEvent(event.eventType)) return

        val packageName = event.packageName?.toString() ?: UNKNOWN_PACKAGE
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                Log.d(TAG, "[Window] pkg=$packageName class=${event.className}")
                logActiveWindowNodeCount()
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                Log.d(TAG, "[Click] pkg=$packageName class=${event.className} text=${event.text}")
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                Log.d(TAG, "[Content] pkg=$packageName")
            }
        }
    }

    // region Gesture execution

    fun performTap(
        x: Float,
        y: Float,
        callback: ((GestureResult) -> Unit)? = null,
    ): GestureResult {
        val path = Path().apply { moveTo(x, y) }
        return dispatchGesturePath(path, TAP_DURATION_MS, callback)
    }

    fun performSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = DEFAULT_SWIPE_DURATION_MS,
        callback: ((GestureResult) -> Unit)? = null,
    ): GestureResult {
        if (durationMs < MIN_GESTURE_DURATION_MS) {
            val failure = GestureResult.Failure("Swipe duration below minimum ($MIN_GESTURE_DURATION_MS ms)")
            callback?.invoke(failure)
            return failure
        }
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        return dispatchGesturePath(path, durationMs, callback)
    }

    fun clickNodeWithText(text: String, ignoreCase: Boolean = true): ActionResult {
        if (text.isBlank()) {
            return ActionResult.Failure("Search text is blank")
        }

        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "clickNodeWithText: no active window root")
            return ActionResult.Failure("Active window root is null")
        }

        return try {
            val target = findNodeByText(root, text, ignoreCase)
            if (target == null) {
                Log.w(TAG, "clickNodeWithText: no node found for text=\"$text\"")
                ActionResult.Failure("No node found with text: $text")
            } else {
                try {
                    performClickOnNode(target)
                } finally {
                    target.recycle()
                }
            }
        } finally {
            root.recycle()
        }
    }

    // endregion

    // region Global actions

    fun performGlobalBack(): ActionResult = runGlobalAction(GLOBAL_ACTION_BACK, "Back")

    fun performGlobalHome(): ActionResult = runGlobalAction(GLOBAL_ACTION_HOME, "Home")

    fun performGlobalRecents(): ActionResult =
        runGlobalAction(GLOBAL_ACTION_RECENTS, "Recents")

    // endregion

    // region Action schema

    /**
     * Executes a structured automation action (for the on-device agent loop).
     */
    fun executeAction(action: AutomationAction): ActionResult = when (action) {
        is AutomationAction.Tap -> {
            when (val result = performTap(action.x, action.y)) {
                is GestureResult.Success -> ActionResult.Success
                is GestureResult.Failure -> ActionResult.Failure(result.reason)
            }
        }
        is AutomationAction.Swipe -> {
            when (
                val result = performSwipe(
                    action.startX,
                    action.startY,
                    action.endX,
                    action.endY,
                    action.durationMs,
                )
            ) {
                is GestureResult.Success -> ActionResult.Success
                is GestureResult.Failure -> ActionResult.Failure(result.reason)
            }
        }
        is AutomationAction.ClickText -> clickNodeWithText(action.text, action.ignoreCase)
        AutomationAction.GlobalBack -> performGlobalBack()
        AutomationAction.GlobalHome -> performGlobalHome()
        AutomationAction.GlobalRecents -> performGlobalRecents()
    }

    sealed class AutomationAction {
        data class Tap(val x: Float, val y: Float) : AutomationAction()
        data class Swipe(
            val startX: Float,
            val startY: Float,
            val endX: Float,
            val endY: Float,
            val durationMs: Long = DEFAULT_SWIPE_DURATION_MS,
        ) : AutomationAction()
        data class ClickText(val text: String, val ignoreCase: Boolean = true) : AutomationAction()
        data object GlobalBack : AutomationAction()
        data object GlobalHome : AutomationAction()
        data object GlobalRecents : AutomationAction()
    }

    // endregion

    // region Node traversal

    fun findNodeByText(
        root: AccessibilityNodeInfo?,
        text: String,
        ignoreCase: Boolean = true,
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        val matcher: (CharSequence?) -> Boolean = if (ignoreCase) {
            { value -> value?.toString()?.equals(text, ignoreCase = true) == true }
        } else {
            { value -> value?.toString() == text }
        }

        if (matcher(root.text) || matcher(root.contentDescription)) {
            return AccessibilityNodeInfo.obtain(root)
        }

        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val match = findNodeByText(child, text, ignoreCase)
            child.recycle()
            if (match != null) return match
        }
        return null
    }

    fun findClickableNodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        if (root == null) return results
        collectClickableNodes(root, results)
        return results
    }

    private fun collectClickableNodes(
        node: AccessibilityNodeInfo,
        results: MutableList<AccessibilityNodeInfo>,
    ) {
        if (node.isClickable && node.isVisibleToUser) {
            results.add(AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectClickableNodes(child, results)
            child.recycle()
        }
    }

    private fun performClickOnNode(node: AccessibilityNodeInfo): ActionResult {
        val clickable = findClickableTarget(node)
        return try {
            if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "Clicked node: ${nodeSummary(clickable)}")
                ActionResult.Success
            } else {
                Log.w(TAG, "Click action failed for node: ${nodeSummary(clickable)}")
                ActionResult.Failure("ACTION_CLICK returned false")
            }
        } finally {
            if (clickable !== node) {
                clickable.recycle()
            }
        }
    }

    private fun findClickableTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var current: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        while (current != null) {
            if (current.isClickable) return current
            val parent = current.parent
            current.recycle()
            current = parent
        }
        return AccessibilityNodeInfo.obtain(node)
    }

    private fun countNodes(node: AccessibilityNodeInfo?): Int {
        if (node == null) return 0
        var count = 1
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            count += countNodes(child)
            child.recycle()
        }
        return count
    }

    // endregion

    // region Internal helpers

    private fun shouldProcessEvent(eventType: Int): Boolean {
        val now = System.currentTimeMillis()
        val last = lastEventTimeMs[eventType] ?: 0L
        if (now - last < EVENT_DEBOUNCE_MS) return false
        lastEventTimeMs[eventType] = now
        return true
    }

    private fun logActiveWindowNodeCount() {
        val root = rootInActiveWindow ?: run {
            Log.d(TAG, "Active window root is null")
            return
        }
        try {
            Log.d(TAG, "Active window node count: ${countNodes(root)}")
        } finally {
            root.recycle()
        }
    }

    private fun dispatchGesturePath(
        path: Path,
        durationMs: Long,
        callback: ((GestureResult) -> Unit)?,
    ): GestureResult {
        return try {
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val dispatched = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        Log.d(TAG, "Gesture completed")
                        callback?.invoke(GestureResult.Success)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        Log.w(TAG, "Gesture cancelled")
                        callback?.invoke(GestureResult.Failure("Gesture cancelled"))
                    }
                },
                null,
            )
            if (dispatched) {
                GestureResult.Success
            } else {
                val failure = GestureResult.Failure("dispatchGesture returned false")
                Log.e(TAG, failure.reason)
                callback?.invoke(failure)
                failure
            }
        } catch (e: Exception) {
            val failure = GestureResult.Failure("Gesture dispatch error: ${e.message}")
            Log.e(TAG, failure.reason, e)
            callback?.invoke(failure)
            failure
        }
    }

    private fun runGlobalAction(action: Int, label: String): ActionResult {
        return try {
            if (performGlobalAction(action)) {
                Log.d(TAG, "Global action succeeded: $label")
                ActionResult.Success
            } else {
                Log.w(TAG, "Global action failed: $label")
                ActionResult.Failure("Global action $label returned false")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Global action error: $label", e)
            ActionResult.Failure("Global action $label error: ${e.message}")
        }
    }

    private fun nodeSummary(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val id = node.viewIdResourceName.orEmpty()
        return "text=$text desc=$desc id=$id class=${node.className}"
    }

    // endregion

    sealed class GestureResult {
        data object Success : GestureResult()
        data class Failure(val reason: String) : GestureResult()
    }

    sealed class ActionResult {
        data object Success : ActionResult()
        data class Failure(val reason: String) : ActionResult()
    }

    companion object {
        private const val TAG = "ThrakshaA11y"
        private const val UNKNOWN_PACKAGE = "unknown"
        private const val EVENT_DEBOUNCE_MS = 1_000L
        private const val NOTIFICATION_TIMEOUT_MS = 100L
        private const val TAP_DURATION_MS = 50L
        private const val DEFAULT_SWIPE_DURATION_MS = 300L
        private const val MIN_GESTURE_DURATION_MS = 1L

        @Volatile
        var instance: ThrakshaAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }
}
