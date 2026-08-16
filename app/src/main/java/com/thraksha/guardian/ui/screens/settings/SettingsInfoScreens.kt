package com.thraksha.guardian.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thraksha.guardian.BuildConfig
import com.thraksha.guardian.ui.components.TRLogo
import com.thraksha.guardian.ui.components.TRLogoSize
import com.thraksha.guardian.ui.components.Wordmark
import com.thraksha.guardian.ui.design.ThemePreference
import com.thraksha.guardian.ui.design.ThrakshaCard
import com.thraksha.guardian.ui.design.ThrakshaDisclosure
import com.thraksha.guardian.ui.design.ThrakshaDivider
import com.thraksha.guardian.ui.design.ThrakshaFactRow
import com.thraksha.guardian.ui.design.ThrakshaFootnote
import com.thraksha.guardian.ui.design.ThrakshaPageColumn
import com.thraksha.guardian.ui.design.ThrakshaScaffold
import com.thraksha.guardian.ui.design.ThrakshaSectionHeader
import com.thraksha.guardian.ui.design.ThrakshaSpacing
import kotlinx.coroutines.launch

/**
 * APPEARANCE — the one genuinely configurable presentation setting.
 *
 * Both explicit choices and "match system" are honoured by the theme; the preference is
 * stored in the same encrypted config table as everything else.
 */
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by ThemePreference.current.collectAsStateWithLifecycle()

    ThrakshaScaffold(title = "Appearance", onBack = onBack) { padding ->
        ThrakshaPageColumn(padding = padding) {
            ThrakshaCard(contentPadding = PaddingValues(vertical = ThrakshaSpacing.sm)) {
                ThemePreference.entries.forEachIndexed { index, preference ->
                    if (index > 0) ThrakshaDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = preference == current,
                                role = Role.RadioButton,
                                onClick = { scope.launch { ThemePreference.set(context, preference) } },
                            )
                            .heightIn(min = ThrakshaSpacing.touchTarget)
                            .padding(
                                horizontal = ThrakshaSpacing.lg,
                                vertical = ThrakshaSpacing.sm,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ThrakshaSpacing.md),
                    ) {
                        RadioButton(selected = preference == current, onClick = null)
                        Text(
                            text = preference.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            ThrakshaFootnote(
                "Light and dark use the same layout — only the colours change.",
            )
        }
    }
}

/**
 * PRIVACY & DATA — what is actually stored, and where.
 *
 * Every claim here is one the implementation supports: SQLCipher-encrypted storage with a
 * Keystore-wrapped passphrase, a hash-chained audit trail, on-device inference and no
 * network path for any of it.
 */
@Composable
fun PrivacyDataScreen(onBack: () -> Unit) {
    ThrakshaScaffold(title = "Privacy & data", onBack = onBack) { padding ->
        ThrakshaPageColumn(padding = padding) {
            PrivacyPoint(
                title = "Understanding happens on this phone",
                body = "When you ask Thraksha for a routine, the language model runs " +
                    "locally on this device. Your request is not sent to a server.",
                technical = listOf(
                    "Runtime" to "LiteRT-LM, on-device",
                    "Model" to "Pinned local file, integrity-checked before use",
                    "Network use for inference" to "None",
                ),
            )
            PrivacyPoint(
                title = "Scanning reads only what is on this device",
                body = "Thraksha inspects the apps installed here — what they declare, " +
                    "what Android currently allows them to do, and their signing and " +
                    "package fingerprints. Nothing about them is uploaded.",
                technical = listOf(
                    "Source" to "Android PackageManager on this device",
                    "Threat intelligence" to "Signed pack shipped inside the app",
                    "Uploads" to "None",
                ),
            )
            PrivacyPoint(
                title = "What is stored, and how",
                body = "Findings, routine snapshots and the audit trail are stored in an " +
                    "encrypted database in Thraksha's private app storage.",
                technical = listOf(
                    "Database" to "SQLCipher-encrypted",
                    "Key" to "Wrapped by the Android Keystore",
                    "Location" to "App-private storage",
                ),
            )
            PrivacyPoint(
                title = "The audit trail can detect tampering",
                body = "Every recorded event carries a fingerprint of the event before " +
                    "it, so a removed or edited entry breaks the chain.",
                technical = listOf(
                    "Chain" to "SHA-256 over id, time, type, detail and previous hash",
                    "Mode" to "Append-only",
                ),
            )
            PrivacyPoint(
                title = "Network Guard sees destinations, not contents",
                body = "When it is on, Thraksha reads where one app's connections are " +
                    "going. It does not read what is inside them.",
                technical = listOf(
                    "Scope" to "One package, routed through a local tunnel",
                    "Recorded" to "Protocol, destination address and port, size",
                    "Not recorded" to "Payload contents",
                ),
            )

            ThrakshaFootnote(
                "No security tool can promise a device is free of every threat. Thraksha " +
                    "reports what it checked and what it found, and says so when a check " +
                    "could not be completed.",
            )
        }
    }
}

@Composable
private fun PrivacyPoint(
    title: String,
    body: String,
    technical: List<Pair<String, String>>,
) {
    ThrakshaCard {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(ThrakshaSpacing.xs))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(ThrakshaSpacing.sm))
        ThrakshaDisclosure("Technical details") {
            technical.forEach { (label, value) -> ThrakshaFactRow(label, value) }
        }
    }
}

/**
 * ABOUT — identity and build facts.
 *
 * Uses the existing in-repo brand mark. The Stitch-generated monogram and the
 * acknowledgement list it invented (Suricata, OSSEC, Zeek, YARA) are not used: none of
 * those components are in this app.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    ThrakshaScaffold(title = "About", onBack = onBack) { padding ->
        ThrakshaPageColumn(padding = padding, horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(ThrakshaSpacing.lg))
            TRLogo(size = TRLogoSize.LG, withBackground = true)
            Spacer(Modifier.height(ThrakshaSpacing.md))
            Wordmark(fontSize = 22)
            Text(
                text = "Guardian",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(ThrakshaSpacing.xs))
            Text(
                text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ThrakshaCard {
                Text(
                    text = "Thraksha Guardian checks what the apps on this phone are able " +
                        "to do, watches one app's outbound connections when you ask it to, " +
                        "and runs simple device routines you start yourself — all on the " +
                        "device, with a tamper-evident record of everything it did.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                ThrakshaSectionHeader("Build")
                ThrakshaCard {
                    ThrakshaFactRow("Application ID", BuildConfig.APPLICATION_ID)
                    ThrakshaFactRow("Build type", BuildConfig.BUILD_TYPE)
                    ThrakshaFactRow("Version name", BuildConfig.VERSION_NAME)
                    ThrakshaFactRow("Version code", BuildConfig.VERSION_CODE.toString())
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                ThrakshaSectionHeader("Open-source components")
                ThrakshaCard {
                    Text(
                        text = "Thraksha Guardian is built on these open-source projects.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(ThrakshaSpacing.sm))
                    ThrakshaDisclosure("Components and licences") {
                        Components.forEach { (name, licence) ->
                            ThrakshaFactRow(name, licence)
                        }
                    }
                }
            }

            ThrakshaFootnote(
                "This build is a demonstration. It has no account system, no analytics and " +
                    "no server component.",
            )
        }
    }
}

/**
 * Actual third-party dependencies of this module. Kept in sync with `app/build.gradle.kts`
 * by hand — an entry here that is not a real dependency would be exactly the kind of
 * invented content Phase 11B removes.
 */
private val Components = listOf(
    "AndroidX Core, AppCompat, Activity, Lifecycle" to "Apache-2.0",
    "Jetpack Compose (UI, Material 3, Material Icons)" to "Apache-2.0",
    "AndroidX Room" to "Apache-2.0",
    "AndroidX SQLite" to "Apache-2.0",
    "SQLCipher for Android (Zetetic)" to "BSD-style",
    "Kotlin Coroutines" to "Apache-2.0",
    "kotlinx.serialization" to "Apache-2.0",
    "Google AI Edge LiteRT-LM" to "Apache-2.0",
    "Material Components for Android" to "Apache-2.0",
)
