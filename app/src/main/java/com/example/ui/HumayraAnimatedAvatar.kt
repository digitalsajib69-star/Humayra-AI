package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun HumayraAnimatedAvatar(
    currentExpression: String, // "idle", "thinking", "speaking", "happy", "surprised"
    isSpeaking: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatarLoops")

    // Breathing scale animation to simulate organic life loop
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "respire"
    )

    // Eyelid blink timer animation (every 3 seconds)
    val blinkProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3000
                0.0f at 0
                0.0f at 2800
                1.0f at 2900 // quick blink down
                0.0f at 3000 // blink open the eyes
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "blink"
    )

    // Speaking mouth height vibration loop
    val mouthSpeakY by infiniteTransition.animateFloat(
        initialValue = 2f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(220, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mouth"
    )

    // Orbit coordinates for thinking rotating nodes
    val orbitalRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(130.dp)
            .scale(breathingScale)
    ) {
        // Core Halo Ring
        val animatedHaloSize by animateFloatAsState(
            targetValue = if (isSpeaking) 1.25f else if (currentExpression == "thinking") 1.15f else 1.05f,
            animationSpec = if (isSpeaking) {
                infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse)
            } else {
                tween(500)
            },
            label = "speakHalo"
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val haloColor = when (currentExpression) {
                "speaking" -> Color(0xFFEC407A).copy(alpha = 0.3f)
                "thinking" -> Color(0xFF00E5FF).copy(alpha = 0.3f)
                "happy" -> Color(0xFFFFD54F).copy(alpha = 0.3f)
                else -> Color(0xFF00E5FF).copy(alpha = 0.15f)
            }
            drawCircle(
                color = haloColor,
                radius = (size.minDimension / 2.2f) * animatedHaloSize
            )
        }

        // Layered Custom Portrait
        Box(
            modifier = Modifier
                .size(105.dp)
                .clip(CircleShape)
                .border(
                    width = 3.dp,
                    brush = Brush.linearGradient(
                        colors = when (currentExpression) {
                            "speaking" -> listOf(Color(0xFFEC407A), Color(0xFFFFD54F))
                            "thinking" -> listOf(Color(0xFF00E5FF), Color(0xFFECEFF1))
                            "happy" -> listOf(Color(0xFFFFD54F), Color(0xFF81C784))
                            else -> listOf(Color(0xFF00E5FF), Color(0xFFB15DFF))
                        }
                    ),
                    shape = CircleShape
                )
        ) {
            // Humayra photo base
            Image(
                painter = painterResource(id = R.drawable.img_humayra),
                contentDescription = "Humayra Base portrait",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Animated vector overlays mapping facial expressions
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Eyes coords
                val leftEyeX = w * 0.35f
                val rightEyeX = w * 0.65f
                val eyesY = h * 0.44f

                // Blushing cheeks effect for HAPPY state
                if (currentExpression == "happy" || currentExpression == "speaking") {
                    drawCircle(
                        color = Color(0xFFEC407A).copy(alpha = 0.4f),
                        radius = 12f,
                        center = Offset(w * 0.25f, h * 0.52f)
                    )
                    drawCircle(
                        color = Color(0xFFEC407A).copy(alpha = 0.4f),
                        radius = 12f,
                        center = Offset(w * 0.75f, h * 0.52f)
                    )
                }

                // EYES RENDERER mapping expression states
                when (currentExpression) {
                    "happy" -> {
                        // Curved glowing smiley eyes
                        drawArc(
                            color = Color(0xFFFFD54F),
                            startAngle = 180f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = Offset(leftEyeX - 10f, eyesY - 8f),
                            size = Size(20f, 16f),
                            style = Stroke(width = 3.5f)
                        )
                        drawArc(
                            color = Color(0xFFFFD54F),
                            startAngle = 180f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = Offset(rightEyeX - 10f, eyesY - 8f),
                            size = Size(20f, 16f),
                            style = Stroke(width = 3.5f)
                        )
                    }
                    "surprised" -> {
                        // Wide round glowing blue eyes!
                        drawCircle(
                            color = Color(0xFF00E5FF),
                            radius = 9f,
                            center = Offset(leftEyeX, eyesY)
                        )
                        drawCircle(
                            color = Color(0xFF00E5FF),
                            radius = 9f,
                            center = Offset(rightEyeX, eyesY)
                        )
                        drawCircle(
                            color = Color.Black,
                            radius = 3.5f,
                            center = Offset(leftEyeX, eyesY)
                        )
                        drawCircle(
                            color = Color.Black,
                            radius = 3.5f,
                            center = Offset(rightEyeX, eyesY)
                        )
                    }
                    "thinking" -> {
                        // Horizontal glowing scanning tracker
                        val scanY = eyesY + (Math.sin(orbitalRotation.toDouble() / 15.0).toFloat() * 10f)
                        drawLine(
                            color = Color(0xFF00E5FF).copy(alpha = 0.8f),
                            start = Offset(w * 0.15f, scanY),
                            end = Offset(w * 0.85f, scanY),
                            strokeWidth = 3f
                        )
                    }
                    else -> {
                        // Default standard blinks
                        if (blinkProgress > 0.8f) {
                            // blink eyes shut draw line
                            drawLine(
                                color = Color.White,
                                start = Offset(leftEyeX - 10f, eyesY),
                                end = Offset(leftEyeX + 10f, eyesY),
                                strokeWidth = 3.5f
                            )
                            drawLine(
                                color = Color.White,
                                start = Offset(rightEyeX - 10f, eyesY),
                                end = Offset(rightEyeX + 10f, eyesY),
                                strokeWidth = 3.5f
                            )
                        } else {
                            // normal eyes
                            drawCircle(
                                color = Color.White,
                                radius = 7f,
                                center = Offset(leftEyeX, eyesY)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 7f,
                                center = Offset(rightEyeX, eyesY)
                            )
                            drawCircle(
                                color = Color.Black,
                                radius = 3.5f,
                                center = Offset(leftEyeX, eyesY)
                            )
                            drawCircle(
                                color = Color.Black,
                                radius = 3.5f,
                                center = Offset(rightEyeX, eyesY)
                            )
                        }
                    }
                }

                // MOUTH RENDERER mapping expression states
                val mouthY = h * 0.65f
                val mouthW = 28f

                when (currentExpression) {
                    "speaking" -> {
                        val activeMouthHeight = mouthSpeakY.coerceAtLeast(3f)
                        drawArc(
                            color = Color(0xFFEC407A),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = true,
                            topLeft = Offset((w / 2) - (mouthW / 2), mouthY - (activeMouthHeight / 2)),
                            size = Size(mouthW, activeMouthHeight)
                        )
                    }
                    "happy" -> {
                        // Wide happy smiling mouth arc
                        drawArc(
                            color = Color(0xFFFFD54F),
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = Offset((w / 2) - (mouthW / 2), mouthY - 10f),
                            size = Size(mouthW, 20f),
                            style = Stroke(width = 3.5f)
                        )
                    }
                    "surprised" -> {
                        // "O" shape surprised circle
                        drawCircle(
                            color = Color(0xFF00E5FF),
                            radius = 6.5f,
                            center = Offset(w / 2, mouthY)
                        )
                    }
                    else -> {
                        // Gentle smiles
                        drawArc(
                            color = Color.White,
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = Offset((w / 2) - 10f, mouthY - 4f),
                            size = Size(20f, 10f),
                            style = Stroke(width = 2.5f)
                        )
                    }
                }
            }
        }

        // Orbiting glowing dots for THINKING state
        if (currentExpression == "thinking") {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rad = Math.toRadians(orbitalRotation.toDouble())
                val orbitRadius = (size.minDimension / 2.1f)
                val dotX = (size.width / 2) + orbitRadius * Math.cos(rad).toFloat()
                val dotY = (size.height / 2) + orbitRadius * Math.sin(rad).toFloat()

                drawCircle(
                    color = Color(0xFF00E5FF),
                    radius = 5.dp.toPx(),
                    center = Offset(dotX, dotY)
                )

                // draw a second orbiting dot
                val rad2 = rad + Math.PI
                val dot2X = (size.width / 2) + orbitRadius * Math.cos(rad2).toFloat()
                val dot2Y = (size.height / 2) + orbitRadius * Math.sin(rad2).toFloat()
                drawCircle(
                    color = Color(0xFFEC407A),
                    radius = 4.dp.toPx(),
                    center = Offset(dot2X, dot2Y)
                )
            }
        }
    }
}
