package com.thraksha.guardian.security.network

import com.thraksha.guardian.security.inventory.DemoAppRegistry

/**
 * The Phase 7 critical safety invariant (guide §28): the tunnel may only ever contain
 * the one controlled sample. There is deliberately NO code path that establishes a
 * tunnel without a per-app allowlist — the prohibited state
 * (`addRoute(0.0.0.0/0)` + no allowed package = device-wide capture) is unrepresentable
 * through this object, and the service refuses startup if scoping fails.
 */
object VpnScope {

    /** The complete set of packages allowed into the tunnel. Exactly one, by design. */
    val ALLOWED_PACKAGES: Set<String> = setOf(DemoAppRegistry.VILLAIN_CALLER)

    /**
     * The only route the tunnel claims: the RFC 5737 TEST-NET-3 documentation block that
     * contains the demo target. Even the scoped app's other traffic (it has none)
     * would bypass the tunnel — interception is as narrow as the demo itself.
     */
    const val ROUTE_ADDRESS = "203.0.113.0"
    const val ROUTE_PREFIX = 24

    fun isAllowed(packageName: String): Boolean = packageName in ALLOWED_PACKAGES
}
