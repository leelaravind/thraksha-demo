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
                
                Text(
                    text = "To keep Thraksha's \"eyes and ears\" active 24/7, you must disable battery optimization for this app.\n\nOn the next screen, please select \"Unrestricted\" or disable optimization.",
                    color = TextWhite,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "This ensures the AI stays awake even when your phone is in your pocket.",
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
