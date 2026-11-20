package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.ChatValidator
import com.example.data.model.ProviderProfile
import com.example.data.model.UiState
import com.example.ui.components.trolleySpringPress
import com.example.ui.theme.TrolleyTheme
import com.example.ui.viewmodel.ChatThreadViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ChatThreadScreen(
    viewModel: ChatThreadViewModel,
    currentUserId: String,
    partnerFallbackName: String,
    categoryFallbackName: String,
    onBack: () -> Unit,
    onRequestBooking: (ProviderProfile) -> Unit,
    onViewProviderProfile: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    BackHandler(onBack = onBack)

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    var draftText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val snapshot = (state as? UiState.Success)?.data
    val messages = snapshot?.messages.orEmpty()
    val thread = snapshot?.thread
    val providerProfile = snapshot?.providerProfile
    val isProviderSide = snapshot?.isCurrentUserProvider == true

    LaunchedEffect(thread?.id, thread?.updatedAt, thread?.lastSenderId) {
        viewModel.markThreadAsReadIfNeeded(thread)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val headerTitle = when {
        thread != null -> thread.partnerTitleFor(currentUserId)
        !isProviderSide -> providerProfile?.businessName ?: partnerFallbackName
        else -> partnerFallbackName
    }.ifBlank { "Conversation" }

    val headerSubtitle = if (isProviderSide) {
        "Pre-booking client inquiry · ${thread?.providerCategoryName ?: categoryFallbackName}"
    } else {
        providerProfile?.categoryName ?: thread?.providerCategoryName ?: categoryFallbackName
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = contentPadding.calculateBottomPadding())
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Conversation Trust Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 700.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("chat_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = providerProfile != null) {
                                providerProfile?.let { onViewProviderProfile(it.id) }
                            },
                    ) {
                        Text(
                            text = headerTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("chat_partner_title"),
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = TrolleyTheme.colors.limeAccent,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = headerSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = TrolleyTheme.colors.mutedInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // Pre-booking-to-Booking Conversion CTA
                    if (!isProviderSide && providerProfile != null) {
                        val bookInteraction = remember { MutableInteractionSource() }
                        Button(
                            onClick = { onRequestBooking(providerProfile) },
                            interactionSource = bookInteraction,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TrolleyTheme.colors.lime,
                                contentColor = TrolleyTheme.colors.limeDeep,
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .trolleySpringPress(bookInteraction)
                                .minimumInteractiveComponentSize()
                                .testTag("chat_book_now_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = stringResource(R.string.chat_book_now_cta),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }

                Surface(
                    color = TrolleyTheme.colors.limeSoft,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.chat_trust_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = TrolleyTheme.colors.limeDeep,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                    )
                }
                HorizontalDivider(color = TrolleyTheme.colors.hairline)
            }
        }

        if (notice != null) {
            val activeNotice = notice!!
            Surface(
                color = if (activeNotice.isError) MaterialTheme.colorScheme.errorContainer else TrolleyTheme.colors.limeSoft,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 700.dp)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = activeNotice.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (activeNotice.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = viewModel::clearNotice,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
                    }
                }
            }
        }

        // Messages List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 700.dp),
        ) {
            when (val current = state) {
                is UiState.Loading -> {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Opening secure conversation…", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                is UiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(18.dp),
                    ) {
                        Text(
                            text = current.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                is UiState.Success -> {
                    if (messages.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(20.dp)
                                .testTag("chat_empty_state_card"),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = TrolleyTheme.colors.orange,
                                    modifier = Modifier.size(28.dp),
                                )
                                Text(
                                    text = stringResource(R.string.chat_empty_thread_title),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.chat_empty_thread_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TrolleyTheme.colors.mutedInk,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("chat_messages_list"),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                ChatMessageBubble(
                                    message = msg,
                                    isMine = msg.senderId == currentUserId,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Smart 1-Tap Pre-Booking Prompt Chips (Customer side)
        if (!isProviderSide) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 700.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChatValidator.PRE_BOOKING_PROMPTS.forEachIndexed { idx, prompt ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("chat_quick_prompt_$idx")
                            .clickable(enabled = !isSending) {
                                viewModel.sendMessage(
                                    rawBody = prompt,
                                    messageType = "pre_booking_inquiry",
                                )
                            },
                    ) {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        // Bottom Message Composer
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 700.dp),
        ) {
            Column {
                HorizontalDivider(color = TrolleyTheme.colors.hairline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = draftText,
                        onValueChange = { if (it.length <= ChatValidator.MAX_MESSAGE_LENGTH) draftText = it },
                        enabled = !isSending,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.chat_input_placeholder),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TrolleyTheme.colors.limeAccent,
                            unfocusedBorderColor = TrolleyTheme.colors.hairline,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input"),
                    )

                    val sendInteraction = remember { MutableInteractionSource() }
                    Button(
                        onClick = {
                            val type = if (!isProviderSide && messages.isEmpty()) "pre_booking_inquiry" else "text"
                            viewModel.sendMessage(
                                rawBody = draftText,
                                messageType = type,
                                onSent = { draftText = "" },
                            )
                        },
                        enabled = !isSending && draftText.trim().isNotEmpty(),
                        interactionSource = sendInteraction,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                        modifier = Modifier
                            .trolleySpringPress(sendInteraction)
                            .minimumInteractiveComponentSize()
                            .testTag("chat_send_button"),
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send message",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    isMine: Boolean,
) {
    val timeLabel = remember(message.sentAt, message.clientSentMillis) {
        val date = message.sentAt?.toDate()
        if (date != null) {
            SimpleDateFormat("HH:mm", Locale.ENGLISH).format(date)
        } else {
            "Sending…"
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 16.dp,
            ),
            color = if (isMine) TrolleyTheme.colors.lime else MaterialTheme.colorScheme.surface,
            border = if (isMine) null else BorderStroke(1.dp, TrolleyTheme.colors.hairline),
            modifier = Modifier
                .widthIn(max = 310.dp)
                .testTag("chat_bubble_${message.id}"),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isMine) "You" else message.senderName,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isMine) TrolleyTheme.colors.limeDeep else TrolleyTheme.colors.orange,
                    )
                    if (message.messageType == "pre_booking_inquiry") {
                        Text(
                            text = "· Pre-booking inquiry",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isMine) TrolleyTheme.colors.limeDeep.copy(alpha = 0.78f) else TrolleyTheme.colors.mutedInk,
                        )
                    }
                }

                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isMine) TrolleyTheme.colors.limeDeep else MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isMine) TrolleyTheme.colors.limeDeep.copy(alpha = 0.72f) else TrolleyTheme.colors.mutedInk,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}
