package com.esper.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esper.android.ui.theme.CosmicSlate
import com.esper.android.ui.theme.IndigoAccent
import com.esper.android.ui.theme.IndigoGlow
import com.esper.android.ui.theme.TextPrimary

/**
 * A custom pull-to-refresh layout built from scratch using NestedScrollConnection.
 */
@Composable
fun PullToRefreshLayout(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    refreshThresholdDp: Float = 80f,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { refreshThresholdDp.dp.toPx() }

    var pullDistance by remember { mutableFloatStateOf(0f) }

    val animatedOffset by animateFloatAsState(
        targetValue = if (isRefreshing) thresholdPx * 0.85f else pullDistance,
        animationSpec = tween(durationMillis = 200),
        label = "pullOffset"
    )

    val rotationAngle by animateFloatAsState(
        targetValue = if (isRefreshing) 720f else (pullDistance / thresholdPx) * 360f,
        animationSpec = if (isRefreshing) tween(durationMillis = 800) else tween(durationMillis = 100),
        label = "pullRotation"
    )

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pullDistance > 0f && available.y < 0f) {
                    val consumed = available.y.coerceAtLeast(-pullDistance)
                    pullDistance += consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    val friction = 0.5f
                    val newDistance = pullDistance + (available.y * friction)
                    pullDistance = newDistance.coerceAtMost(thresholdPx * 1.6f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullDistance >= thresholdPx && !isRefreshing) {
                    onRefresh()
                }
                pullDistance = 0f
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // A graphics layer keeps the pull offset on the render
                    // thread. `Modifier.offset` would re-measure and re-place
                    // the whole lazy grid on every touch frame.
                    translationY = animatedOffset * 0.65f
                }
        ) {
            content()
        }

        if (animatedOffset > 4f || isRefreshing) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CosmicSlate,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, IndigoAccent.copy(alpha = 0.5f)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = animatedOffset * 0.55f
                    }
                    .padding(top = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(IndigoAccent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Sync",
                            tint = Color.White,
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(rotationAngle)
                        )
                    }
                    Text(
                        text = if (isRefreshing) "Syncing notebooks..." else if (pullDistance >= thresholdPx) "Release to refresh" else "Pull down to refresh",
                        color = if (isRefreshing) IndigoGlow else TextPrimary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
