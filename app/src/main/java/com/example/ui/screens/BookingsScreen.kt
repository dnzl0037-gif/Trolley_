package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.BookingRequest
import com.example.data.model.ProviderProfile
import com.example.data.model.UiState
import com.example.ui.components.trolleySpringPress
import com.example.ui.theme.TrolleyTheme
import com.example.ui.viewmodel.BookingsViewModel
import java.util.Locale

@Composable
fun BookingsScreen(
    viewModel: BookingsViewModel,
    onOpenNewBookingSheet: () -> Unit,
    onRescheduleBooking: (BookingRequest) -> Unit,
    onViewProviderProfile: (String) -> Unit = {},
    onMessageParticipant: (BookingRequest) -> Unit = {},
    onBrowseProvidersClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val customerState by viewModel.customerBookingsState.collectAsStateWithLifecycle()
    val providerState by viewModel.providerBookingsState.collectAsStateWithLifecycle()
    val bookableState by viewModel.bookableProvidersState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val customerCount = (customerState as? UiState.Success)?.data?.size ?: 0
    val providerCount = (providerState as? UiState.Success)?.data?.size ?: 0
    val hasBookableProviders = (bookableState as? UiState.Success<List<ProviderProfile>>)?.data?.isNotEmpty() == true

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
                .testTag("bookings_screen_list"),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 20.dp,
                bottom = contentPadding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "bookings_header") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EyebrowLabel(text = stringResource(R.string.bookings_eyebrow))
                    Text(
                        text = stringResource(R.string.bookings_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.bookings_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TrolleyTheme.colors.mutedInk,
                    )
                }
            }

            if (notice != null) {
                item(key = "bookings_notice") {
                    val activeNotice = notice!!
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (activeNotice.isError) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            TrolleyTheme.colors.limeSoft
                        },
                        border = BorderStroke(
                            1.dp,
                            if (activeNotice.isError) MaterialTheme.colorScheme.error else TrolleyTheme.colors.lime,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bookings_notice_banner"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = activeNotice.message,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (activeNotice.isError) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = viewModel::clearNotice,
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Dismiss notification",
                                    tint = TrolleyTheme.colors.mutedInk,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "bookings_actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val newBookingInteraction = remember { MutableInteractionSource() }
                    Button(
                        onClick = onOpenNewBookingSheet,
                        enabled = hasBookableProviders && !isBusy,
                        interactionSource = newBookingInteraction,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .trolleySpringPress(newBookingInteraction)
                            .minimumInteractiveComponentSize()
                            .testTag("new_booking_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New booking", style = MaterialTheme.typography.labelLarge)
                    }

                    val browseInteraction = remember { MutableInteractionSource() }
                    OutlinedButton(
                        onClick = onBrowseProvidersClick,
                        interactionSource = browseInteraction,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                        modifier = Modifier
                            .weight(1f)
                            .trolleySpringPress(browseInteraction)
                            .minimumInteractiveComponentSize()
                            .testTag("bookings_browse_pros_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Explore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Browse pros", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            item(key = "bookings_tabs") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BookingTabChip(
                        label = "${stringResource(R.string.bookings_tab_mine)} ($customerCount)",
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("bookings_tab_mine"),
                    )
                    BookingTabChip(
                        label = "${stringResource(R.string.bookings_tab_incoming)} ($providerCount)",
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("bookings_tab_incoming"),
                    )
                }
            }

            val activeState = if (selectedTab == 0) customerState else providerState
            when (activeState) {
                UiState.Loading -> {
                    item(key = "bookings_loading") {
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
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    text = "Syncing live bookings from Firestore…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TrolleyTheme.colors.mutedInk,
                                )
                            }
                        }
                    }
                }

                is UiState.Error -> {
                    item(key = "bookings_error") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bookings_error_card"),
                        ) {
                            Text(
                                text = activeState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    val itemsList = activeState.data
                    if (itemsList.isEmpty()) {
                        item(key = "bookings_empty_$selectedTab") {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("bookings_empty_card"),
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = if (selectedTab == 0) {
                                            "No booking requests yet"
                                        } else {
                                            "No incoming client requests yet"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = if (selectedTab == 0) {
                                            "Pick a published professional from Discover or Map View to choose a service date and time."
                                        } else {
                                            "When customers request an appointment with your business profile, their dates and job notes appear here."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TrolleyTheme.colors.mutedInk,
                                    )
                                    if (selectedTab == 0) {
                                        TextButton(
                                            onClick = onBrowseProvidersClick,
                                            modifier = Modifier.minimumInteractiveComponentSize(),
                                        ) {
                                            Text(
                                                text = "Open Discover & Map",
                                                color = TrolleyTheme.colors.orange,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        items(itemsList, key = { it.id }) { booking ->
                            BookingItemCard(
                                booking = booking,
                                isProviderView = selectedTab == 1,
                                isBusy = isBusy,
                                onViewProviderProfile = { onViewProviderProfile(booking.providerId) },
                                onMessageParticipant = { onMessageParticipant(booking) },
                                onReschedule = { onRescheduleBooking(booking) },
                                onCustomerCancel = {
                                    viewModel.updateStatus(booking, nextStatus = "cancelled", asProvider = false)
                                },
                                onProviderAccept = {
                                    viewModel.updateStatus(booking, nextStatus = "accepted", asProvider = true)
                                },
                                onProviderDecline = {
                                    viewModel.updateStatus(booking, nextStatus = "declined", asProvider = true)
                                },
                                onProviderComplete = {
                                    viewModel.updateStatus(booking, nextStatus = "completed", asProvider = true)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) TrolleyTheme.colors.lime else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) TrolleyTheme.colors.lime else TrolleyTheme.colors.hairline,
        ),
        modifier = modifier
            .trolleySpringPress(interaction, pressedScale = 0.97f)
            .minimumInteractiveComponentSize(),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = if (selected) TrolleyTheme.colors.limeDeep else TrolleyTheme.colors.mutedInk,
            )
        }
    }
}

@Composable
private fun BookingItemCard(
    booking: BookingRequest,
    isProviderView: Boolean,
    isBusy: Boolean,
    onViewProviderProfile: () -> Unit,
    onMessageParticipant: () -> Unit,
    onReschedule: () -> Unit,
    onCustomerCancel: () -> Unit,
    onProviderAccept: () -> Unit,
    onProviderDecline: () -> Unit,
    onProviderComplete: () -> Unit,
) {
    val (statusBg, statusFg) = when (booking.status) {
        "accepted" -> TrolleyTheme.colors.lime to TrolleyTheme.colors.limeDeep
        "completed" -> TrolleyTheme.colors.limeSoft to TrolleyTheme.colors.limeAccent
        "declined", "cancelled" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> TrolleyTheme.colors.orangeSoft to TrolleyTheme.colors.orange
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("booking_card_${booking.id}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(38.dp),
                    ) {
                        Icon(
                            imageVector = if (isProviderView) Icons.Rounded.Person else Icons.Rounded.Storefront,
                            contentDescription = null,
                            tint = TrolleyTheme.colors.limeAccent,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                    Column {
                        Text(
                            text = if (isProviderView) booking.customerName else booking.providerBusinessName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (isProviderView) {
                                "${booking.categoryName} · ${booking.providerBusinessName}"
                            } else {
                                booking.categoryName
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = TrolleyTheme.colors.limeAccent,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = statusBg,
                ) {
                    Text(
                        text = booking.status.replaceFirstChar { it.titlecase(Locale.ENGLISH) },
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = statusFg,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            // Date, time & linked location strip
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = TrolleyTheme.colors.orange,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = booking.scheduledDate,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccessTime,
                                contentDescription = null,
                                tint = TrolleyTheme.colors.limeAccent,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = booking.scheduledTime,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = TrolleyTheme.colors.mutedInk,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = booking.providerLocation,
                            style = MaterialTheme.typography.bodySmall,
                            color = TrolleyTheme.colors.mutedInk,
                        )
                    }
                }
            }

            if (!booking.notes.isNullOrBlank()) {
                Text(
                    text = booking.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (!isProviderView) {
                val rateInteraction = remember { MutableInteractionSource() }
                TextButton(
                    onClick = onViewProviderProfile,
                    interactionSource = rateInteraction,
                    modifier = Modifier
                        .trolleySpringPress(rateInteraction)
                        .minimumInteractiveComponentSize()
                        .testTag("booking_rate_${booking.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.StarOutline,
                        contentDescription = null,
                        tint = TrolleyTheme.colors.orange,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (booking.status == "completed" || booking.status == "accepted") {
                            "View profile & rate provider"
                        } else {
                            "View provider profile & reviews"
                        },
                        color = TrolleyTheme.colors.limeAccent,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (booking.customerId != booking.providerUserId) {
                val chatInteraction = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = onMessageParticipant,
                    interactionSource = chatInteraction,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .trolleySpringPress(chatInteraction)
                        .minimumInteractiveComponentSize()
                        .testTag("booking_chat_${booking.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChatBubbleOutline,
                        contentDescription = null,
                        tint = TrolleyTheme.colors.limeAccent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isProviderView) "Message client" else "Chat with provider",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Contextual actions matching Firestore rules state machine
            if (!isProviderView && (booking.canCustomerReschedule || booking.canCustomerCancel)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (booking.canCustomerReschedule) {
                        val rescheduleInteraction = remember { MutableInteractionSource() }
                        OutlinedButton(
                            onClick = onReschedule,
                            enabled = !isBusy,
                            interactionSource = rescheduleInteraction,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                            modifier = Modifier
                                .weight(1f)
                                .trolleySpringPress(rescheduleInteraction)
                                .minimumInteractiveComponentSize()
                                .testTag("booking_reschedule_${booking.id}"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.EditCalendar,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reschedule")
                        }
                    }
                    if (booking.canCustomerCancel) {
                        val cancelInteraction = remember { MutableInteractionSource() }
                        TextButton(
                            onClick = onCustomerCancel,
                            enabled = !isBusy,
                            interactionSource = cancelInteraction,
                            modifier = Modifier
                                .weight(1f)
                                .trolleySpringPress(cancelInteraction)
                                .minimumInteractiveComponentSize()
                                .testTag("booking_cancel_${booking.id}"),
                        ) {
                            Text(
                                text = "Cancel request",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            if (isProviderView && (booking.canProviderRespond || booking.canProviderComplete)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (booking.canProviderRespond) {
                        val declineInteraction = remember { MutableInteractionSource() }
                        OutlinedButton(
                            onClick = onProviderDecline,
                            enabled = !isBusy,
                            interactionSource = declineInteraction,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                            modifier = Modifier
                                .weight(1f)
                                .trolleySpringPress(declineInteraction)
                                .minimumInteractiveComponentSize()
                                .testTag("booking_decline_${booking.id}"),
                        ) {
                            Text("Decline", color = MaterialTheme.colorScheme.error)
                        }

                        val acceptInteraction = remember { MutableInteractionSource() }
                        Button(
                            onClick = onProviderAccept,
                            enabled = !isBusy,
                            interactionSource = acceptInteraction,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .trolleySpringPress(acceptInteraction)
                                .minimumInteractiveComponentSize()
                                .testTag("booking_accept_${booking.id}"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Accept")
                        }
                    } else if (booking.canProviderComplete) {
                        val completeInteraction = remember { MutableInteractionSource() }
                        Button(
                            onClick = onProviderComplete,
                            enabled = !isBusy,
                            interactionSource = completeInteraction,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TrolleyTheme.colors.orange,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .trolleySpringPress(completeInteraction)
                                .minimumInteractiveComponentSize()
                                .testTag("booking_complete_${booking.id}"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DoneAll,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mark completed")
                        }
                    }
                }
            }
        }
    }
}
