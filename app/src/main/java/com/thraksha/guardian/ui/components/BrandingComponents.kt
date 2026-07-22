package com.thraksha.guardian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thraksha.guardian.ui.theme.DarkForestGreen
import com.thraksha.guardian.ui.theme.GoldenYellow
import com.thraksha.guardian.ui.theme.StrongGreen

@Composable
fun TRLogo(
    modifier: Modifier = Modifier,
    size: TRLogoSize = TRLogoSize.MD,
    withBackground: Boolean = false
) {
    val boxSize = when (size) {
        TRLogoSize.SM -> 32.dp
        TRLogoSize.MD -> 64.dp
        TRLogoSize.LG -> 96.dp
    }

    val fontSize = when (size) {
        TRLogoSize.SM -> 20.sp
        TRLogoSize.MD -> 40.sp
        TRLogoSize.LG -> 60.sp
    }

    val logoContent = @Composable {
        Box(
            modifier = Modifier.size(boxSize),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "T",
                color = StrongGreen,
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = if (size == TRLogoSize.SM) (-2).dp else (-4).dp)
            )
            Text(
                text = "R",
                color = GoldenYellow,
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = if (size == TRLogoSize.SM) 2.dp else 4.dp)
            )
        }
    }

    if (withBackground) {
        Box(
            modifier = modifier
                .size(boxSize)
                .clip(RoundedCornerShape(size = if (size == TRLogoSize.SM) 6.dp else 12.dp))
                .background(DarkForestGreen),
            contentAlignment = Alignment.Center
        ) {
            logoContent()
        }
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            logoContent()
        }
    }
}

enum class TRLogoSize {
    SM, MD, LG
}

@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    fontSize: Int = 24
) {
    Text(
        text = "THRAKSHA",
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp,
        modifier = modifier
    )
}