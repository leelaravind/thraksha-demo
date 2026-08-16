package com.thraksha.guardian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.thraksha.guardian.ui.theme.CardSurfaceDark
import com.thraksha.guardian.ui.theme.GoldenYellow
import com.thraksha.guardian.ui.theme.TextSecondaryDark
import com.thraksha.guardian.ui.theme.TextWhite

@Composable
fun WhitelistingDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TRLogo(size = TRLogoSize.SM)
                
                Text(
                    text = "Samsung Optimization",
                    color = GoldenYellow,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                // Phase 12.1 §18/§19: this dialog previously claimed Thraksha's "eyes and
                // ears" were "active 24/7" and that exempting it "ensures the AI stays
                // awake". Neither is true — Thraksha monitors only while its services are
                // running, and a battery-optimisation exemption makes Android less likely
                // to pause them, it does not guarantee anything. The wording below states
                // what the app actually does.
                Text(
                    text = "Samsung's battery optimisation can pause Thraksha's background " +
                        "services while you are not using the app. Exempting Thraksha makes " +
                        "it less likely they are paused.\n\nOn the next screen, please select " +
                        "\"Unrestricted\" or turn optimisation off.",
                    color = TextWhite,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Protection monitoring is available while Thraksha is active. " +
                        "This setting helps it keep running — it does not guarantee " +
                        "uninterrupted operation.",
                    color = TextSecondaryDark,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldenYellow),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Go to Settings", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Maybe Later", color = TextSecondaryDark)
                }
            }
        }
    }
}
