package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.PsCamTheme
import com.example.ui.theme.PsOrangePrimary

@Composable
fun PsCamLogoIcon(
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val colors = PsCamTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(colors.surface)
            .border(1.5.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ps_cam_logo),
            contentDescription = "PS Cam Logo",
            modifier = Modifier.size(size * 0.85f)
        )
    }
}

@Composable
fun PsCamLogoHeader(
    modifier: Modifier = Modifier
) {
    val colors = PsCamTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        PsCamLogoIcon(size = 38.dp)
        Spacer(modifier = Modifier.width(10.dp))
        androidx.compose.foundation.layout.Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "PS",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.textPrimary,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = ".",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.primary
                )
                Text(
                    text = "Cam",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.textPrimary,
                    letterSpacing = (-0.5).sp
                )
            }
            Text(
                text = "Segurança P2P & Nuvem",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
                letterSpacing = 0.2.sp
            )
        }
    }
}
