package com.thraksha.guardian.security.network

/**
 * Pure IPv4/UDP header parser for packets read from the TUN.
 *
 * Produces metadata only — it renders no security verdict (that is the evaluator's job)
 * and never throws: every malformed input path returns [Result.Malformed] instead, so
 * nothing can escape into the packet loop. All reads are bounds-checked against the
 * declared [length], never the backing array size alone.
 *
 * Phase 7 supports exactly what the controlled demo emits: **IPv4 + UDP**. Anything else
 * is [Result.Unsupported] — honest, safe, and out of scope by design (guide §9).
 */
object NetworkPacketParser {

    /** Offset+length of the UDP payload inside the original buffer, for the forwarder. */
    data class PayloadWindow(val offset: Int, val length: Int)

    sealed interface Result {
        data class Parsed(
            val observation: NetworkObservation,
            val payload: PayloadWindow,
        ) : Result

        data class Unsupported(val reason: String) : Result

        data class Malformed(val reason: String) : Result
    }

    private const val MIN_IPV4_HEADER = 20
    private const val UDP_HEADER = 8
    private const val PROTOCOL_UDP = 17

    fun parse(
        buffer: ByteArray,
        length: Int,
        packageName: String,
        timestamp: Long,
    ): Result {
        if (length <= 0) return Result.Malformed("empty packet")
        if (length > buffer.size) return Result.Malformed("declared length exceeds buffer")
        if (length < MIN_IPV4_HEADER) return Result.Malformed("shorter than an IPv4 header")

        val versionAndIhl = buffer[0].toInt() and 0xFF
        val version = versionAndIhl shr 4
        if (version == 6) return Result.Unsupported("IPv6 is not parsed in this phase")
        if (version != 4) return Result.Malformed("unknown IP version $version")

        val ihl = versionAndIhl and 0x0F
        if (ihl < 5) return Result.Malformed("invalid IHL $ihl (< 5)")
        val ipHeaderLength = ihl * 4
        if (length < ipHeaderLength) return Result.Malformed("truncated IPv4 header")

        val totalLength = readU16(buffer, 2)
        if (totalLength < ipHeaderLength) return Result.Malformed("IP total length < header")
        if (totalLength > length) return Result.Malformed("IP total length exceeds packet")

        val protocol = buffer[9].toInt() and 0xFF
        if (protocol != PROTOCOL_UDP) {
            return Result.Unsupported("protocol $protocol is not parsed in this phase (UDP only)")
        }

        val udpStart = ipHeaderLength
        if (length < udpStart + UDP_HEADER) return Result.Malformed("truncated UDP header")

        val sourcePort = readU16(buffer, udpStart)
        val destinationPort = readU16(buffer, udpStart + 2)
        val udpLength = readU16(buffer, udpStart + 4)
        if (udpLength < UDP_HEADER) return Result.Malformed("UDP length $udpLength < 8")
        if (udpStart + udpLength > totalLength) {
            return Result.Malformed("UDP length exceeds IP total length")
        }

        val observation = NetworkObservation(
            timestamp = timestamp,
            packageName = packageName,
            ipVersion = 4,
            protocol = PROTOCOL_UDP,
            protocolName = "UDP",
            sourceIp = ipv4(buffer, 12),
            sourcePort = sourcePort,
            destinationIp = ipv4(buffer, 16),
            destinationPort = destinationPort,
            packetLength = totalLength,
        )
        return Result.Parsed(
            observation = observation,
            payload = PayloadWindow(
                offset = udpStart + UDP_HEADER,
                length = udpLength - UDP_HEADER,
            ),
        )
    }

    private fun readU16(buffer: ByteArray, offset: Int): Int =
        ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)

    private fun ipv4(buffer: ByteArray, offset: Int): String =
        (0..3).joinToString(".") { (buffer[offset + it].toInt() and 0xFF).toString() }
}
