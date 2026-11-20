package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.TrolleyTheme

@Composable
fun TrolleyTopBar(
    darkTheme: Boolean,
    isSignedIn: Boolean,
    onToggleTheme: () -> Unit,
    onOpenAccountOrBusiness: () -> Unit,
    onOpenProfile: () -> Unit = onOpenAccountOrBusiness,
) {
    val themeInteraction = remember { MutableInteractionSource() }
    val profileInteraction = remember { MutableInteractionSource() }
    val ctaInteraction = remember { MutableInteractionSource() }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.foundation.layout.Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.trolley_logo),
                        contentDescription = stringResource(R.string.app_name),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(11.dp)),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = buildAnnotatedString {
                            append(stringResource(R.string.brand_wordmark))
                            withStyle(SpanStyle(color = TrolleyTheme.colors.orange)) {
                                append(".")
                            }
                        },
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 23.sp,
                        letterSpacing = (-1.1).sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                        modifier = Modifier.trolleySpringPress(themeInteraction, pressedScale = 0.92f),
                    ) {
                        IconButton(
                            onClick = onToggleTheme,
                            interactionSource = themeInteraction,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("theme_toggle_button"),
                        ) {
                            Icon(
                                imageVector = if (darkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = stringResource(R.string.toggle_theme_desc),
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    if (isSignedIn) {
                        Surface(
                            shape = CircleShape,
                            color = TrolleyTheme.colors.lime,
                            modifier = Modifier.trolleySpringPress(profileInteraction, pressedScale = 0.92f),
                        ) {
                            IconButton(
                                onClick = onOpenProfile,
                                interactionSource = profileInteraction,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("top_bar_profile_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = stringResource(R.string.nav_profile),
                                    tint = TrolleyTheme.colors.limeDeep,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = onOpenAccountOrBusiness,
                        interactionSource = ctaInteraction,
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .trolleySpringPress(ctaInteraction)
                            .minimumInteractiveComponentSize()
                            .testTag("top_bar_business_cta"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = if (isSignedIn) "My business" else "Get started",
                                style = MaterialTheme.typography.labelLarge,
                                fontSize = 12.sp,
                            )
                            Icon(
                                imageVector = Icons.Rounded.Storefront,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = TrolleyTheme.colors.hairline)
        }
    }
}
