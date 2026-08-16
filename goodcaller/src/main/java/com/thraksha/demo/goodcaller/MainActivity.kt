package com.thraksha.demo.goodcaller

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * GoodCaller's entire UI. Built in code so the module carries no layout XML, no Compose
 * and no resources beyond a string — the sample should be obviously inert.
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

        root.addView(label("GoodCaller", 32f, Color.WHITE, bold = true))
        root.addView(label("Demo role: Caller ID", 18f, ACCENT, topMargin = 12.dp))
        root.addView(
            label(
                "CONTROLLED DEMO SAMPLE — WELL-BEHAVED",
                14f,
                ACCENT,
                bold = true,
                topMargin = 32.dp,
            ),
        )
        root.addView(
            label(
                "This sample requests only the capabilities a caller-ID app needs: " +
                    "phone state, contacts, call log, network and notifications.\n\n" +
                    "It requests no photo or media access and no ability to list the " +
                    "other apps installed on this device.\n\n" +
                    "Thraksha should classify it as CALLER_ID and report zero findings.",
                15f,
                MUTED,
                topMargin = 24.dp,
            ),
        )
        root.addView(
            label(
                "Harmless by construction: this app reads no user data, stores nothing " +
                    "and makes no network requests.",
                13f,
                MUTED,
                topMargin = 32.dp,
            ),
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
        const val BACKGROUND = 0xFF102A16.toInt()
        const val ACCENT = 0xFF00C853.toInt()
        const val MUTED = 0xFFB8C9BC.toInt()
    }
}
