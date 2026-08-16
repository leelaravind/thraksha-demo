package com.thraksha.guardian.ui.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Deep-link focus target for the dashboard (Phase 8.1 notification path, guide §20):
 * a security notification carries the package it is about; tapping it lands the user on
 * that app's evidence card. UI-only state — no security semantics.
 */
object UiFocus {

    private val _focusPackage = MutableStateFlow<String?>(null)
    val focusPackage: StateFlow<String?> = _focusPackage.asStateFlow()

    fun requestFocus(packageName: String) {
        _focusPackage.value = packageName
    }

    /** The dashboard consumed the focus request. */
    fun clear() {
        _focusPackage.value = null
    }
}
