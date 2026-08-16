package com.thraksha.guardian.automation.executors

import android.content.Context
import com.thraksha.guardian.automation.ActionSupport
import com.thraksha.guardian.automation.ActionTarget
import com.thraksha.guardian.automation.AutomationCapability
import com.thraksha.guardian.automation.SnapshotEntry

/**
 * The Phase 9 executor contract (guide §8): each executor owns exactly one narrow
 * Android capability and implements the full honest lifecycle —
 * support check → current-state read → execute → verify → restore → verify-restored.
 *
 * Rules every implementation follows:
 *  * [verify]/[verifyRestored] RE-READ real Android state; calling an API is never
 *    treated as success (the Phase 4–8.1 principle carried into automation);
 *  * [snapshot] captures the ACTUAL current state before any mutation (guide §9);
 *  * an unanswerable read yields `readable = false` — never a guessed default;
 *  * executors are only ever invoked by the AutomationEngine after the safety policy
 *    validated the target (guide §28: nothing else may call them).
 */
/**
 * Bounded settle-poll for asynchronously-propagating framework state (observed on
 * Samsung: `setInterruptionFilter` reflects in `currentInterruptionFilter` a moment
 * later). Still verification against REAL state — the check simply tolerates
 * propagation delay, and a state that never materialises within the bound stays false.
 */
internal fun awaitState(timeoutMs: Long = 2_000, check: () -> Boolean): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (true) {
        if (runCatching(check).getOrDefault(false)) return true
        if (System.currentTimeMillis() >= deadline) return false
        Thread.sleep(50)
    }
}

interface AutomationExecutor {

    val capability: AutomationCapability

    /** Can this capability run right now, and if not, why / what must the user do? */
    fun support(context: Context): Pair<ActionSupport, String>

    /** Read the actual current state (guide §9). Must not mutate anything. */
    fun snapshot(context: Context): SnapshotEntry

    /** Apply the validated target state. Returns false / throws on refusal. */
    fun apply(context: Context, target: ActionTarget): Boolean

    /** Re-read Android state and confirm it matches [target]. */
    fun verify(context: Context, target: ActionTarget): Boolean

    /** Re-apply the snapshot's actual previous state. */
    fun restore(context: Context, entry: SnapshotEntry): Boolean

    /** Re-read Android state and confirm it matches the snapshot. */
    fun verifyRestored(context: Context, entry: SnapshotEntry): Boolean
}
