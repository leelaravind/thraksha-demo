package com.thraksha.guardian

import com.thraksha.guardian.security.network.NetworkPacketParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure IPv4/UDP parser tests over handcrafted fixtures (guide §26). No real network.
 * The overriding invariant: no input, however malformed, may throw — every bad path is
 * a typed [NetworkPacketParser.Result], never an exception in the packet loop.
 */
class NetworkPacketParserTest {

    private val pkg = "com.thraksha.demo.villaincaller"

    /** Builds a valid IPv4/UDP packet: 20-byte IP header + 8-byte UDP header + payload. */
    private fun udpPacket(
        srcIp: IntArray = intArrayOf(10, 113, 0, 2),
        dstIp: IntArray = intArrayOf(203, 0, 113, 113),
        srcPort: Int = 40000,
        dstPort: Int = 443,
        payload: ByteArray = "PROBE".toByteArray(),
        ihl: Int = 5,
    ): ByteArray {
        val ipHeaderLen = ihl * 4
        val udpLen = 8 + payload.size
        val total = ipHeaderLen + udpLen
        val p = ByteArray(total)
        p[0] = ((4 shl 4) or ihl).toByte()
        p[2] = (total shr 8).toByte()
        p[3] = (total and 0xFF).toByte()
        p[9] = 17 // UDP
        for (i in 0..3) p[12 + i] = srcIp[i].toByte()
        for (i in 0..3) p[16 + i] = dstIp[i].toByte()
        val u = ipHeaderLen
        p[u] = (srcPort shr 8).toByte(); p[u + 1] = (srcPort and 0xFF).toByte()
        p[u + 2] = (dstPort shr 8).toByte(); p[u + 3] = (dstPort and 0xFF).toByte()
        p[u + 4] = (udpLen shr 8).toByte(); p[u + 5] = (udpLen and 0xFF).toByte()
        payload.copyInto(p, u + 8)
        return p
    }

    private fun parse(p: ByteArray, len: Int = p.size) =
        NetworkPacketParser.parse(p, len, pkg, timestamp = 1000L)

    @Test
    fun validUdpPacket_parsesDestinationAndPorts() {
        val result = parse(udpPacket())
        assertTrue(result is NetworkPacketParser.Result.Parsed)
        val obs = (result as NetworkPacketParser.Result.Parsed).observation
        assertEquals("203.0.113.113", obs.destinationIp)
        assertEquals(443, obs.destinationPort)
        assertEquals("10.113.0.2", obs.sourceIp)
        assertEquals(40000, obs.sourcePort)
        assertEquals(4, obs.ipVersion)
        assertEquals("UDP", obs.protocolName)
        assertEquals(pkg, obs.packageName)
    }

    @Test
    fun packetLength_reflectsIpTotalLength() {
        val p = udpPacket(payload = "THRAKSHA_DEMO_NETWORK_PROBE".toByteArray())
        val obs = (parse(p) as NetworkPacketParser.Result.Parsed).observation
        assertEquals(p.size, obs.packetLength)
    }

    @Test
    fun payloadWindow_pointsAtTheUdpPayload() {
        val payload = "THRAKSHA_DEMO_NETWORK_PROBE".toByteArray()
        val p = udpPacket(payload = payload)
        val parsed = parse(p) as NetworkPacketParser.Result.Parsed
        assertEquals(payload.size, parsed.payload.length)
        val extracted = p.copyOfRange(
            parsed.payload.offset,
            parsed.payload.offset + parsed.payload.length,
        )
        assertEquals("THRAKSHA_DEMO_NETWORK_PROBE", String(extracted))
    }

    @Test
    fun variableIhl_isHonoured() {
        // IHL 6 => 24-byte IP header (4 option bytes). Ports must still be found.
        val p = udpPacket(ihl = 6)
        val obs = (parse(p) as NetworkPacketParser.Result.Parsed).observation
        assertEquals(443, obs.destinationPort)
    }

    @Test
    fun truncatedIpHeader_isMalformed_notThrown() {
        assertTrue(parse(ByteArray(10)) is NetworkPacketParser.Result.Malformed)
    }

    @Test
    fun invalidIhl_isMalformed() {
        val p = udpPacket()
        p[0] = ((4 shl 4) or 4).toByte() // IHL 4 < 5
        assertTrue(parse(p) is NetworkPacketParser.Result.Malformed)
    }

    @Test
    fun truncatedUdpHeader_isMalformed() {
        val p = udpPacket()
        // Claim length only reaches into the middle of the UDP header.
        assertTrue(parse(p, len = 22) is NetworkPacketParser.Result.Malformed)
    }

    @Test
    fun invalidUdpLength_isMalformed() {
        val p = udpPacket()
        val u = 20
        p[u + 4] = 0; p[u + 5] = 3 // UDP length 3 < 8
        assertTrue(parse(p) is NetworkPacketParser.Result.Malformed)
    }

    @Test
    fun udpLengthExceedingIpTotal_isMalformed() {
        val p = udpPacket()
        val u = 20
        p[u + 4] = 0xFF.toByte(); p[u + 5] = 0xFF.toByte()
        assertTrue(parse(p) is NetworkPacketParser.Result.Malformed)
    }

    @Test
    fun nonUdpProtocol_isUnsupported() {
        val p = udpPacket()
        p[9] = 6 // TCP
        assertTrue(parse(p) is NetworkPacketParser.Result.Unsupported)
    }

    @Test
    fun ipv6_isUnsupported() {
        val p = ByteArray(40)
        p[0] = (6 shl 4).toByte()
        assertTrue(parse(p) is NetworkPacketParser.Result.Unsupported)
    }

    @Test
    fun declaredLengthBeyondBuffer_isMalformed() {
        assertTrue(parse(udpPacket(), len = 100000) is NetworkPacketParser.Result.Malformed)
    }

    @Test
    fun emptyPacket_isMalformed() {
        assertTrue(parse(ByteArray(0)) is NetworkPacketParser.Result.Malformed)
    }

    @Test
    fun parsingIsDeterministic() {
        val p = udpPacket()
        val a = parse(p) as NetworkPacketParser.Result.Parsed
        val b = parse(p) as NetworkPacketParser.Result.Parsed
        assertEquals(a.observation, b.observation)
    }

    @Test
    fun fuzz_randomBytesNeverThrow() {
        val rng = java.util.Random(42)
        repeat(2000) {
            val len = rng.nextInt(60)
            val bytes = ByteArray(len).also { rng.nextBytes(it) }
            // Must return a typed result, never throw.
            parse(bytes, len)
        }
    }
}
