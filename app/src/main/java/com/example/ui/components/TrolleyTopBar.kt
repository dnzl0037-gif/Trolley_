package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
    isSignedIn: Boolean,
    unreadMessagesCount: Int = 0,
    onOpenMenu: () -> Unit,
    onOpenMessages: () -> Unit = {},
    onOpenAccountOrBusiness: () -> Unit,
    onOpenProfile: () -> Unit = onOpenAccountOrBusiness,
) {
    val menuInteraction = remember { MutableInteractionSource() }
    val messagesInteraction = remember { MutableInteractionSource() }
    val profileInteraction = remember { MutableInteractionSource() }
    val ctaInteraction = remember { MutableInteractionSource() }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                        modifier = Modifier.trolleySpringPress(menuInteraction, pressedScale = 0.92f),
                    ) {
                        IconButton(
                            onClick = onOpenMenu,
                            interactionSource = menuInteraction,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("dashboard_menu_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = stringResource(R.string.open_dashboard_menu_desc),
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Image(
                        painter = painterResource(id = R.drawable.trolley_logo),
                        contentDescription = stringResource(R.string.app_name),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = buildAnnotatedString {
                            append(stringResource(R.string.brand_wordmark))
                            withStyle(SpanStyle(color = TrolleyTheme.colors.orange)) {
                                append(".")
                            }
                        },
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        letterSpacing = (-1.1).sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isSignedIn) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                            modifier = Modifier.trolleySpringPress(messagesInteraction, pressedScale = 0.92f),
                        ) {
                            IconButton(
                                onClick = onOpenMessages,
                                interactionSource = messagesInteraction,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("top_bar_messages_button"),
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (unreadMessagesCount > 0) {
                                            Badge(
                                                containerColor = TrolleyTheme.colors.orange,
                                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                            ) {
                                                Text(unreadMessagesCount.coerceAtMost(9).toString())
                                            }
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChatBubbleOutline,
                                        contentDescription = stringResource(R.string.chat_inbox_title),
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                            }
                        }

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
