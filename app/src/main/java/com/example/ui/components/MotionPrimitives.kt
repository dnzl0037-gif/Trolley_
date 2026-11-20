package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.LightOrange
import com.example.ui.theme.ManropeFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

fun Modifier.trolleySpringPress(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.965f,
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "trolley_button_press",
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun TrolleySplashOverlay(
    onFinished: () -> Unit,
) {
    var visible by remember { mutableStateOf(true) }
    val logoScale = remember { Animatable(0.65f) }
    val logoAlpha = remember { Animatable(0f) }
    val letters = remember { listOf("t", "r", "o", "l", "l", "e", "y", ".") }
    val letterAlphas = remember { letters.map { Animatable(0f) } }
    val letterOffsets = remember { letters.map { Animatable(18f) } }

    LaunchedEffect(Unit) {
        launch {
            logoAlpha.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
        }
        launch {
            logoScale.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
        delay(320)
        letters.forEachIndexed { index, _ ->
            launch {
                delay(index * 65L)
                launch { letterAlphas[index].animateTo(1f, tween(280)) }
                launch {
                    letterOffsets[index].animateTo(
                        0f,
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                }
            }
        }
        delay(1350)
        visible = false
        delay(380)
        onFinished()
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(durationMillis = 360)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("splash_loading_screen"),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.trolley_logo),
                    contentDescription = stringResource(R.string.app_name),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(128.dp)
                        .scale(logoScale.value)
                        .alpha(logoAlpha.value)
                        .clip(RoundedCornerShape(28.dp)),
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    letters.forEachIndexed { index, ch ->
                        Text(
                            text = ch,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 40.sp,
                            letterSpacing = (-1.4).sp,
                            color = if (ch == ".") LightOrange else Color.White,
                            modifier = Modifier
                                .alpha(letterAlphas[index].value)
                                .offset {
                                    IntOffset(0, letterOffsets[index].value.roundToInt())
                                },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                CircularProgressIndicator(
                    modifier = Modifier
                        .size(22.dp)
                        .alpha(logoAlpha.value * 0.7f),
                    color = LightOrange,
                    strokeWidth = 2.2.dp,
                )
            }
        }
    }
}
