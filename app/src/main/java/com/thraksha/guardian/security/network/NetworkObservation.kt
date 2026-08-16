package com.thraksha.guardian.security.network

/**
 * Metadata of one packet genuinely read from the TUN — everything Thraksha may honestly
 * claim to know about an intercepted network attempt (guide §2): addressing, protocol,
 * size, timing, and which controlled app is routed through the tunnel.
 *
 * Deliberately **no payload field**. Payload bytes are needed transiently by the
 * forwarding primitive, but they never enter security-domain objects, findings, events
 * or the audit trail.
 */
data class NetworkObservation(
    val timestamp: Long,
    /** The app attributed to this packet: the tunnel is scoped to exactly one package. */
    val packageName: String,
    val ipVersion: Int,
    /** IANA protocol number (17 = UDP). */
    val protocol: Int,
    val protocolName: String,
    val sourceIp: String,
    val sourcePort: Int,
    val destinationIp: String,
    val destinationPort: Int,
    val packetLength: Int,
) {
    fun endpoint(): String = "$destinationIp:$destinationPort"

    fun summary(): String =
        "$protocolName $packetLength B → ${endpoint()} (from $packageName)"
}
