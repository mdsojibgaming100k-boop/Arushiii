package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.service.LiveServiceState

@Composable
fun VoiceOrbVisualizer(
    state: LiveServiceState,
    amplitude: Float,
    isMuted: Boolean,
    onOrbClick: () -> Unit,
    modifier: Modifier = Modifier,
    statusText: String? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbPulse")

    // Pulsating ring scale for idle breathing
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RingPulse"
    )

    // Continuous Radiating Ripple Waves (for Listening, Processing, and Speaking)
    val waveProgress1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WaveProgress1"
    )

    val waveProgress2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 650, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WaveProgress2"
    )

    val waveProgress3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WaveProgress3"
    )

    // Processing rotation angle for Connecting / ExecutingTool
    val processingRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ProcessingRotation"
    )

    // Dynamic scale driven by audio amplitude
    val targetAmplitudeScale = 1.0f + (amplitude * 0.45f)
    val animatedScale = remember { Animatable(1.0f) }

    LaunchedEffect(targetAmplitudeScale) {
        animatedScale.animateTo(
            targetValue = targetAmplitudeScale,
            animationSpec = tween(durationMillis = 60, easing = FastOutSlowInEasing)
        )
    }

    // Soft Cream Palette Colors
    val primaryColor = when (state) {
        is LiveServiceState.Speaking -> Color(0xFFD97706) // Rich Warm Amber Gold
        is LiveServiceState.Listening -> Color(0xFFC2410C) // Warm Coral / Terracotta
        is LiveServiceState.ExecutingTool -> Color(0xFF0284C7) // Sky Blue Processing
        is LiveServiceState.Connecting -> Color(0xFFEAB308) // Sun Yellow Processing
        is LiveServiceState.Connected -> Color(0xFFD97706)
        is LiveServiceState.Error -> Color(0xFFDC2626) // Soft Red
        is LiveServiceState.Disconnected -> Color(0xFF94A3B8) // Slate Gray
    }

    val secondaryColor = when (state) {
        is LiveServiceState.Speaking -> Color(0xFFF59E0B) // Bright Gold
        is LiveServiceState.Listening -> Color(0xFFEA580C) // Light Terracotta
        is LiveServiceState.ExecutingTool -> Color(0xFF38BDF8) // Light Sky
        is LiveServiceState.Connecting -> Color(0xFFFDE047)
        is LiveServiceState.Connected -> Color(0xFFF59E0B)
        is LiveServiceState.Error -> Color(0xFFEF4444)
        is LiveServiceState.Disconnected -> Color(0xFFCBD5E1)
    }

    val displayStatusText = statusText ?: when (state) {
        is LiveServiceState.Disconnected -> "কল দিন — আরুষি শুনছে"
        is LiveServiceState.Connecting -> "আরুষি প্রস্তুত হচ্ছে..."
        is LiveServiceState.Connected -> "সজীব এআই যুক্ত আছে"
        is LiveServiceState.Listening -> if (isMuted) "মাইক্রোফোন মিউট করা" else "কথা বলুন — আরুষি শুনছে..."
        is LiveServiceState.Speaking -> "আরুষি কথা বলছে..."
        is LiveServiceState.ExecutingTool -> "প্রসেসিং হচ্ছে: ${state.toolName}..."
        is LiveServiceState.Error -> "ত্রুটি: ${state.message}"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.testTag("voice_orb_container")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(240.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOrbClick
                )
        ) {
            // Background Canvas for animated warm cream pulsating ripple waves
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerOffset = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = (size.minDimension / 2f) * 0.52f

                // Radiating Pulsing Ripple Waves during Listening, Speaking, or Tool Execution
                val isPulsing = state is LiveServiceState.Listening ||
                        state is LiveServiceState.Speaking ||
                        state is LiveServiceState.ExecutingTool ||
                        state is LiveServiceState.Connecting

                if (isPulsing) {
                    val waves = listOf(waveProgress1, waveProgress2, waveProgress3)
                    waves.forEach { progress ->
                        // Scale expands from 1.0f to 2.2f
                        val rippleScale = 1.0f + (progress * 1.2f) * animatedScale.value
                        // Alpha fades from 0.55 down to 0.0
                        val rippleAlpha = ((1f - progress) * 0.55f).coerceIn(0f, 1f)
                        val strokeWidth = (3f * (1f - progress * 0.5f)).dp.toPx()

                        // Outer Ripple Ring
                        drawCircle(
                            color = primaryColor.copy(alpha = rippleAlpha),
                            radius = baseRadius * rippleScale,
                            center = centerOffset,
                            style = Stroke(width = strokeWidth)
                        )

                        // Soft secondary inner ripple aura
                        drawCircle(
                            color = secondaryColor.copy(alpha = rippleAlpha * 0.4f),
                            radius = baseRadius * (rippleScale * 0.88f),
                            center = centerOffset,
                            style = Stroke(width = (strokeWidth * 1.5f))
                        )
                    }
                }

                // Processing Orbital Halo (when ExecutingTool or Connecting)
                if (state is LiveServiceState.ExecutingTool || state is LiveServiceState.Connecting) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                primaryColor.copy(alpha = 0.4f),
                                secondaryColor,
                                primaryColor
                            ),
                            center = centerOffset
                        ),
                        radius = baseRadius * 1.15f,
                        center = centerOffset,
                        style = Stroke(width = 5.dp.toPx())
                    )
                }

                // Breathing Core Aura
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = if (isPulsing) 0.35f else 0.18f),
                            secondaryColor.copy(alpha = if (isPulsing) 0.20f else 0.08f),
                            Color.Transparent
                        ),
                        center = centerOffset,
                        radius = baseRadius * 1.4f * animatedScale.value
                    ),
                    radius = baseRadius * 1.4f * animatedScale.value,
                    center = centerOffset
                )
            }

            // Core Avatar Sphere (Bangladeshi Female Identity)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size((135 * animatedScale.value).dp)
                    .clip(CircleShape)
                    .shadow(12.dp, CircleShape, ambientColor = primaryColor, spotColor = secondaryColor)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFFFDF9),
                                Color(0xFFF3EFE6)
                            )
                        )
                    )
                    .border(3.dp, primaryColor, CircleShape)
            ) {
                // Bangladeshi AI Assistant Avatar Image
                Image(
                    painter = painterResource(id = R.drawable.sojib_ai_cream_icon_1792906435459),
                    contentDescription = "SOJIB AI Assistant Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Small Action Badge Overlay
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(primaryColor)
                ) {
                    Icon(
                        imageVector = when {
                            state is LiveServiceState.Connecting -> Icons.Default.Sync
                            state is LiveServiceState.ExecutingTool -> Icons.Default.SmartToy
                            state is LiveServiceState.Speaking -> Icons.Default.Call
                            isMuted -> Icons.Default.MicOff
                            else -> Icons.Default.Mic
                        },
                        contentDescription = "Status Icon",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Status Pill Badge
        Surface(
            shape = CircleShape,
            color = Color(0xFFFFFFFF),
            shadowElevation = 4.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f)),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = displayStatusText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = primaryColor
                ),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp)
            )
        }
    }
}
