package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.ChatThread
import com.example.data.model.UiState
import com.example.ui.components.trolleySpringPress
import com.example.ui.theme.TrolleyTheme
import com.example.ui.viewmodel.MessagesInboxViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessagesInboxScreen(
    viewModel: MessagesInboxViewModel,
    currentUserId: String,
    onOpenThread: (ChatThread) -> Unit,
    onBrowseProvidersClick: () -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    BackHandler(onBack = onBack)

    val state by viewModel.threadsState.collectAsStateWithLifecycle()
    var filterTab by rememberSaveable { mutableIntStateOf(0) } // 0 = All, 1 = Unread

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .testTag("messages_inbox_list"),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 14.dp,
                bottom = contentPadding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "inbox_header") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("messages_inbox_back_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.chat_inbox_eyebrow),
                                style = MaterialTheme.typography.labelSmall,
                                color = TrolleyTheme.colors.orange,
                            )
                            Text(
                                text = stringResource(R.string.chat_inbox_title),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.chat_inbox_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TrolleyTheme.colors.mutedInk,
                    )
                }
            }

            when (val current = state) {
                is UiState.Loading -> {
                    item(key = "inbox_loading") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Syncing live conversations…", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                is UiState.Error -> {
                    item(key = "inbox_error") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = current.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    val allThreads = current.data
                    val unreadThreads = allThreads.filter { it.hasUnreadFor(currentUserId) }
                    val visibleThreads = if (filterTab == 1) unreadThreads else allThreads

                    item(key = "inbox_filters") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = filterTab == 0,
                                onClick = { filterTab = 0 },
                                label = { Text("All (${allThreads.size})") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TrolleyTheme.colors.lime,
                                    selectedLabelColor = TrolleyTheme.colors.limeDeep,
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("inbox_filter_all"),
                            )
                            FilterChip(
                                selected = filterTab == 1,
                                onClick = { filterTab = 1 },
                                leadingIcon = if (unreadThreads.isNotEmpty()) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.MarkChatUnread,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                } else null,
                                label = { Text("Unread (${unreadThreads.size})") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TrolleyTheme.colors.lime,
                                    selectedLabelColor = TrolleyTheme.colors.limeDeep,
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("inbox_filter_unread"),
                            )
                        }
                    }

                    if (visibleThreads.isEmpty()) {
                        item(key = "inbox_empty") {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("messages_inbox_empty_card"),
                            ) {
                                Column(
                                    modifier = Modifier.padding(22.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChatBubbleOutline,
                                        contentDescription = null,
                                        tint = TrolleyTheme.colors.orange,
                                        modifier = Modifier.size(30.dp),
                                    )
                                    Text(
                                        text = if (filterTab == 1) {
                                            "You're all caught up"
                                        } else {
                                            "No pre-booking conversations yet"
                                        },
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = if (filterTab == 1) {
                                            "No unread messages from customers or service providers right now."
                                        } else {
                                            "Open any service provider profile on Discover or Map View and tap Chat before booking to start a real-time discussion."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TrolleyTheme.colors.mutedInk,
                                    )
                                    if (filterTab == 0) {
                                        val ctaInteraction = remember { MutableInteractionSource() }
                                        Button(
                                            onClick = onBrowseProvidersClick,
                                            interactionSource = ctaInteraction,
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .trolleySpringPress(ctaInteraction)
                                                .minimumInteractiveComponentSize()
                                                .testTag("inbox_browse_pros_button"),
                                        ) {
                                            Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Browse nearby pros")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        items(visibleThreads, key = { it.id }) { thread ->
                            ChatThreadSummaryCard(
                                thread = thread,
                                currentUserId = currentUserId,
                                onClick = { onOpenThread(thread) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatThreadSummaryCard(
    thread: ChatThread,
    currentUserId: String,
    onClick: () -> Unit,
) {
    val unread = thread.hasUnreadFor(currentUserId)
    val isProviderSide = currentUserId == thread.providerUserId
    val formattedTime = remember(thread.updatedAt, thread.createdAt) {
        val date = (thread.updatedAt ?: thread.createdAt)?.toDate()
        if (date == null) "Just now" else formatShortTimestamp(date)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (unread) 1.5.dp else 1.dp,
            if (unread) TrolleyTheme.colors.limeAccent else TrolleyTheme.colors.hairline,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chat_thread_card_${thread.id}")
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isProviderSide) TrolleyTheme.colors.orangeSoft else TrolleyTheme.colors.limeSoft,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Storefront,
                    contentDescription = null,
                    tint = if (isProviderSide) TrolleyTheme.colors.orange else TrolleyTheme.colors.limeAccent,
                    modifier = Modifier.padding(10.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = thread.partnerTitleFor(currentUserId),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unread) TrolleyTheme.colors.limeAccent else TrolleyTheme.colors.mutedInk,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (isProviderSide) TrolleyTheme.colors.orange.copy(alpha = 0.14f) else TrolleyTheme.colors.lime,
                    ) {
                        Text(
                            text = if (isProviderSide) "CLIENT INQUIRY" else thread.providerCategoryName,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isProviderSide) TrolleyTheme.colors.orange else TrolleyTheme.colors.limeDeep,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = TrolleyTheme.colors.limeAccent,
                        modifier = Modifier.size(13.dp),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val prefix = if (thread.lastSenderId == currentUserId) "You: " else ""
                    Text(
                        text = "$prefix${thread.lastMessageText}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = if (unread) MaterialTheme.colorScheme.onSurface else TrolleyTheme.colors.mutedInk,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (unread) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(TrolleyTheme.colors.orange),
                        )
                    }
                }
            }
        }
    }
}

private fun formatShortTimestamp(date: Date): String {
    val diffMs = System.currentTimeMillis() - date.time
    val oneDayMs = 24 * 60 * 60 * 1000L
    val pattern = if (diffMs < oneDayMs) "HH:mm" else "MMM d"
    return SimpleDateFormat(pattern, Locale.ENGLISH).format(date)
}
