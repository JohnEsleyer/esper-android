package com.esper.android.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.ui.theme.BorderSlate
import com.esper.android.ui.theme.CosmicSlate
import com.esper.android.ui.theme.SpaceBlack

@Composable
fun ShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "ShimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerTranslate"
    )

    return Brush.linearGradient(
        colors = listOf(
            CosmicSlate,
            BorderSlate.copy(alpha = 0.5f),
            CosmicSlate
        ),
        start = Offset(translateAnim - 450f, translateAnim - 450f),
        end = Offset(translateAnim, translateAnim)
    )
}

/**
 * Large rectangular placeholder for visual canvases, web simulations, and video streams.
 */
@Composable
fun SimulationSkeleton(
    modifier: Modifier = Modifier,
    label: String = "Rendering companion simulation..."
) {
    val shimmer = ShimmerBrush()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBlack)
            .padding(12.dp)
    ) {
        // Large shimmering simulation viewport rectangle
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .background(shimmer)
                .border(1.dp, BorderSlate.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            // HUD Status Badge overlay
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.3f)),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF818CF8))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        color = Color(0xFF818CF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun EditorSkeleton(modifier: Modifier = Modifier) {
    val shimmer = ShimmerBrush()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBlack)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.width(220.dp).height(24.dp).clip(RoundedCornerShape(6.dp)).background(shimmer))
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
        Box(modifier = Modifier.fillMaxWidth(0.85f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
        Box(modifier = Modifier.fillMaxWidth(0.92f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
        Spacer(modifier = Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(8.dp)).background(shimmer))
        Spacer(modifier = Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
    }
}
