package com.thraksha.demo.villaincaller

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

/**
 * VillainCaller's entire UI.
 *
 * The "Run Demo Behaviour" button is intentionally inert. Everything Thraksha detects
 * about this app is static and lives in the manifest, so the button must not imply that
 * pressing it is what makes the app suspicious — and it must not actually touch photos,
 * contacts, the package list or the network.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(BACKGROUND)
            setPadding(48.dp, 96.dp, 48.dp, 48.dp)
        }

        root.addView(label("VillainCaller", 32f, Color.WHITE, bold = true))
        root.addView(label("Demo role: Caller ID", 18f, ACCENT, topMargin = 12.dp))
        root.addView(
            label(
                "CONTROLLED DEMO SAMPLE — SUSPICIOUS PROFILE",
                14f,
                ACCENT,
                bold = true,
                topMargin = 32.dp,
            ),
        )
        root.addView(
            label(
                "This sample declares the same caller-ID permissions as GoodCaller, plus " +
                    "capabilities a caller-ID app has no reason to hold:\n\n" +
                    "  •  READ_MEDIA_IMAGES — photo library access\n" +
                    "  •  QUERY_ALL_PACKAGES — list every installed app\n" +
                    "  •  ACCESS_FINE_LOCATION — precise location\n\n" +
                    "Thraksha reads these from the manifest and reports the mismatch " +
                    "against the caller-ID baseline.",
                15f,
                MUTED,
                topMargin = 24.dp,
            ),
        )

        root.addView(
            Button(this).apply {
                text = "Run Demo Behaviour"
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(0, 32.dp, 0, 0) }
                setOnClickListener {
                    Toast.makeText(
                        this@MainActivity,
                        "No-op. This sample never reads photos, contacts, the package " +
                            "list or the network.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )

        root.addView(
            label(
                "Harmless by construction: the profile/antivirus signals are DECLARED " +
                    "capabilities in the manifest, not actions this app performs on your " +
                    "data. It reads no user data.",
                13f,
                WARNING,
                topMargin = 32.dp,
            ),
        )

        // ----- Phase 7 Network Demo -----
        root.addView(
            label(
                "NETWORK DEMO",
                14f,
                ACCENT,
                bold = true,
                topMargin = 40.dp,
            ),
        )
        root.addView(
            label(
                "Sends synthetic demo data to a reserved RFC 5737 TEST-NET address " +
                    "($DEMO_TARGET_IP:$DEMO_TARGET_PORT) over UDP. No personal data is used, " +
                    "and no real server is contacted. Thraksha's Network Guard reads only the " +
                    "destination metadata of this packet.",
                14f,
                MUTED,
                topMargin = 16.dp,
            ),
        )
        root.addView(
            Button(this).apply {
                text = "Send Demo Outbound Packet"
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(0, 20.dp, 0, 0) }
                setOnClickListener { sendDemoPacket() }
            },
        )

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(BACKGROUND)
                addView(
                    root,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            },
        )
    }

    /**
     * Sends one fixed synthetic UDP datagram to the reserved TEST-NET demo address.
     *
     * This is the ONLY network action in the sample. The payload is a constant marker
     * string — no personal data, no user data, nothing read from the device. The packet
     * is fire-and-forget: no reply is expected or required, and no real server exists at
     * the destination. Its purpose is solely to give Thraksha's Network Guard a genuine
     * outbound packet whose destination metadata it can observe.
     */
    private fun sendDemoPacket() {
        Toast.makeText(this, "Outbound demo attempt submitted.", Toast.LENGTH_SHORT).show()
        thread(name = "villain-demo-udp") {
            runCatching {
                DatagramSocket().use { socket ->
                    val payload = DEMO_PAYLOAD.toByteArray(Charsets.US_ASCII)
                    socket.send(
                        DatagramPacket(
                            payload,
                            payload.size,
                            InetAddress.getByName(DEMO_TARGET_IP),
                            DEMO_TARGET_PORT,
                        ),
                    )
                }
            }
        }
    }

    private fun label(
        text: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false,
        topMargin: Int = 0,
    ): TextView = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        gravity = Gravity.CENTER_HORIZONTAL
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(0, topMargin, 0, 0) }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private companion object {
        const val BACKGROUND = 0xFF2A1010.toInt()
        const val ACCENT = 0xFFFF5252.toInt()
        const val MUTED = 0xFFD9C2C2.toInt()
        const val WARNING = 0xFFFFB300.toInt()

        // RFC 5737 TEST-NET-3 documentation address — reserved, non-routable on the
        // public internet, and NOT a real (malicious or otherwise) endpoint.
        const val DEMO_TARGET_IP = "203.0.113.113"
        const val DEMO_TARGET_PORT = 443
        const val DEMO_PAYLOAD = "THRAKSHA_DEMO_NETWORK_PROBE"
    }
}
