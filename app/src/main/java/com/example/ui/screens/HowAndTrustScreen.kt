package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.trolleySpringPress
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.TrolleyTheme

@Composable
fun HowAndTrustScreen(
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
    onContactClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val privacyInteraction = remember { MutableInteractionSource() }
    val termsInteraction = remember { MutableInteractionSource() }
    val contactInteraction = remember { MutableInteractionSource() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            top = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // How it works section
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 26.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EyebrowLabel(text = stringResource(R.string.how_eyebrow))
                    Text(
                        text = buildAnnotatedString {
                            append(stringResource(R.string.how_title_prefix))
                            withStyle(SpanStyle(color = TrolleyTheme.colors.limeAccent)) {
                                append(stringResource(R.string.how_title_accent))
                            }
                        },
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.how_aside),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TrolleyTheme.colors.mutedInk,
                    )

                    StepCard(
                        number = "01",
                        icon = Icons.Rounded.Search,
                        title = stringResource(R.string.step_1_title),
                        body = stringResource(R.string.step_1_body),
                    )
                    StepCard(
                        number = "02",
                        icon = Icons.Rounded.PersonOutline,
                        title = stringResource(R.string.step_2_title),
                        body = stringResource(R.string.step_2_body),
                    )
                    StepCard(
                        number = "03",
                        icon = Icons.Rounded.ChatBubbleOutline,
                        title = stringResource(R.string.step_3_title),
                        body = stringResource(R.string.step_3_body),
                    )
                }
            }
        }

        // Trust & Safety card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
            ) {
                Column {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        EyebrowLabel(text = stringResource(R.string.trust_eyebrow))
                        Text(
                            text = buildAnnotatedString {
                                append(stringResource(R.string.trust_title_prefix))
                                withStyle(SpanStyle(color = TrolleyTheme.colors.limeAccent)) {
                                    append(stringResource(R.string.trust_title_accent))
                                }
                            },
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.trust_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TrolleyTheme.colors.mutedInk,
                        )

                        TrustPointRow(
                            icon = Icons.Rounded.Verified,
                            title = stringResource(R.string.trust_point_1_title),
                            body = stringResource(R.string.trust_point_1_body),
                        )
                        TrustPointRow(
                            icon = Icons.Rounded.Lock,
                            title = stringResource(R.string.trust_point_2_title),
                            body = stringResource(R.string.trust_point_2_body),
                        )
                        TrustPointRow(
                            icon = Icons.Rounded.StarOutline,
                            title = stringResource(R.string.trust_point_3_title),
                            body = stringResource(R.string.trust_point_3_body),
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = onOpenPrivacy,
                            interactionSource = privacyInteraction,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .trolleySpringPress(privacyInteraction)
                                .minimumInteractiveComponentSize()
                                .testTag("privacy_approach_button"),
                        ) {
                            Text(
                                text = stringResource(R.string.our_privacy_approach),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = onOpenTerms,
                                interactionSource = termsInteraction,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                                modifier = Modifier
                                    .weight(1f)
                                    .trolleySpringPress(termsInteraction)
                                    .minimumInteractiveComponentSize()
                                    .testTag("terms_of_service_button"),
                            ) {
                                Text(stringResource(R.string.terms_of_service_btn))
                            }
                            OutlinedButton(
                                onClick = onContactClick,
                                interactionSource = contactInteraction,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                                modifier = Modifier
                                    .weight(1f)
                                    .trolleySpringPress(contactInteraction)
                                    .minimumInteractiveComponentSize()
                                    .testTag("trust_contact_button"),
                            ) {
                                Text("Email us")
                            }
                        }

                        Text(
                            text = "Service inquiries: ${stringResource(R.string.support_email)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TrolleyTheme.colors.mutedInk,
                        )
                    }

                    // Green Shield Visual footer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(270.dp)
                            .background(TrolleyTheme.colors.lime),
                        contentAlignment = Alignment.Center,
                    ) {
                        val ringColor = TrolleyTheme.colors.limeDeep.copy(alpha = 0.24f)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = Stroke(width = 1.2.dp.toPx())
                            drawCircle(color = ringColor, radius = 115.dp.toPx(), style = stroke)
                            drawCircle(color = ringColor, radius = 85.dp.toPx(), style = stroke)
                        }
                        Surface(
                            shape = RoundedCornerShape(36.dp),
                            color = TrolleyTheme.colors.limeDeep,
                            shadowElevation = 10.dp,
                            modifier = Modifier
                                .size(118.dp)
                                .rotate(-8f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = "Trust & Safety shield",
                                    tint = TrolleyTheme.colors.lime,
                                    modifier = Modifier
                                        .size(58.dp)
                                        .rotate(8f),
                                )
                            }
                        }

                        ShieldPill(
                            text = "Privacy first",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 26.dp, end = 24.dp),
                        )
                        ShieldPill(
                            text = "Real profiles",
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 18.dp),
                        )
                        ShieldPill(
                            text = "Safer contact",
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 44.dp, end = 28.dp),
                        )

                        Text(
                            text = "DISCOVER  ·  GROW  ·  TRUST",
                            style = MaterialTheme.typography.labelSmall,
                            color = TrolleyTheme.colors.limeDeep,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    number: String,
    icon: ImageVector,
    title: String,
    body: String,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = number,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    color = TrolleyTheme.colors.orange,
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = TrolleyTheme.colors.lime,
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = TrolleyTheme.colors.limeDeep,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = TrolleyTheme.colors.mutedInk,
                )
            }
        }
    }
}

@Composable
private fun TrustPointRow(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TrolleyTheme.colors.limeAccent,
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = TrolleyTheme.colors.mutedInk,
            )
        }
    }
}

@Composable
private fun ShieldPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 4.dp,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = TrolleyTheme.colors.limeDeep,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}
