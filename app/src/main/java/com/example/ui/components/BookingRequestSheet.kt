package com.example.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BookingRequest
import com.example.data.model.BookingValidator
import com.example.data.model.ProviderProfile
import com.example.ui.theme.TrolleyTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

private val TIME_PRESETS = listOf("09:00", "11:00", "14:00", "16:00", "17:30")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookingRequestSheet(
    initialProvider: ProviderProfile?,
    existingBooking: BookingRequest?,
    bookableProviders: List<ProviderProfile>,
    isBusy: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirmNewBooking: (provider: ProviderProfile, dateIso: String, time24h: String, notes: String) -> Unit,
    onConfirmReschedule: (booking: BookingRequest, dateIso: String, time24h: String, notes: String) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isReschedule = existingBooking != null

    val initialSelectedProviderId = remember(initialProvider, existingBooking, bookableProviders) {
        initialProvider?.id
            ?: existingBooking?.providerId
            ?: bookableProviders.firstOrNull()?.id.orEmpty()
    }
    var selectedProviderId by remember(initialSelectedProviderId) {
        mutableStateOf(initialSelectedProviderId)
    }

    val activeProvider: ProviderProfile? = remember(selectedProviderId, initialProvider, bookableProviders) {
        when {
            initialProvider != null -> initialProvider
            else -> bookableProviders.firstOrNull { it.id == selectedProviderId }
        }
    }

    val today = remember { LocalDate.now() }
    val datePresets = remember(today) {
        val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
        listOf(
            today to "Today · ${today.format(formatter)}",
            today.plusDays(1) to "Tomorrow · ${today.plusDays(1).format(formatter)}",
            today.plusDays(2) to today.plusDays(2).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)),
            today.plusDays(3) to today.plusDays(3).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)),
        )
    }

    var scheduledDate by remember(existingBooking?.id) {
        mutableStateOf(
            existingBooking?.scheduledDate?.takeIf { BookingValidator.isValidScheduledDate(it) }
                ?: today.plusDays(1).toString()
        )
    }
    var scheduledTime by remember(existingBooking?.id) {
        mutableStateOf(
            existingBooking?.scheduledTime?.takeIf { BookingValidator.isValidScheduledTime(it) }
                ?: "09:00"
        )
    }
    var notes by remember(existingBooking?.id) {
        mutableStateOf(existingBooking?.notes.orEmpty())
    }
    var localError by remember { mutableStateOf<String?>(null) }

    val providerPickerOptions = remember(bookableProviders) {
        bookableProviders.map { pro ->
            val previewSuffix = if (pro.listingStatus != "published") " (Own preview)" else ""
            PickerOption(
                value = pro.id,
                label = pro.businessName + previewSuffix,
                hint = "${pro.categoryName} · ${pro.formattedLocation}",
                keywords = "${pro.categoryName} ${pro.city} ${pro.businessAddress.orEmpty()}",
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isBusy) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("booking_request_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isReschedule) "RESCHEDULE BOOKING" else "BOOK A SERVICE",
                        style = MaterialTheme.typography.labelSmall,
                        color = TrolleyTheme.colors.orange,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isReschedule) "Choose a new date & time" else "Request an appointment",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(
                    onClick = { if (!isBusy) onDismiss() },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("close_booking_sheet_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close booking dialog",
                        tint = TrolleyTheme.colors.mutedInk,
                    )
                }
            }

            // Linked Provider Section
            if (isReschedule && existingBooking != null) {
                LinkedProviderSummaryCard(
                    businessName = existingBooking.providerBusinessName,
                    categoryName = existingBooking.categoryName,
                    location = existingBooking.providerLocation,
                    isPreview = false,
                )
            } else {
                if (initialProvider == null && bookableProviders.isNotEmpty()) {
                    SearchablePickerField(
                        label = "Service provider",
                        value = selectedProviderId,
                        options = providerPickerOptions,
                        onSelect = {
                            selectedProviderId = it
                            localError = null
                        },
                        placeholder = "Select a local provider",
                        searchPlaceholder = "Search business name, category or city",
                        leadingIcon = Icons.Rounded.Storefront,
                        compact = true,
                        testTag = "booking_provider_picker",
                    )
                }

                if (activeProvider != null) {
                    LinkedProviderSummaryCard(
                        businessName = activeProvider.businessName,
                        categoryName = activeProvider.categoryName,
                        location = activeProvider.formattedLocation.ifBlank { "${activeProvider.city}, ${activeProvider.countryCode}" },
                        isPreview = activeProvider.listingStatus != "published",
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = TrolleyTheme.colors.orangeSoft,
                        border = BorderStroke(1.dp, TrolleyTheme.colors.orange.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "No published providers are available to book yet. Create your own provider profile in My Business to test booking end to end, or wait for reviewed listings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            HorizontalDivider(color = TrolleyTheme.colors.hairline)

            // Date Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PREFERRED DATE",
                    style = MaterialTheme.typography.labelSmall,
                    color = TrolleyTheme.colors.mutedInk,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    datePresets.forEach { (date, label) ->
                        val iso = date.toString()
                        val selected = scheduledDate == iso
                        val interaction = remember(iso) { MutableInteractionSource() }
                        Surface(
                            onClick = {
                                scheduledDate = iso
                                localError = null
                            },
                            interactionSource = interaction,
                            shape = RoundedCornerShape(20.dp),
                            color = if (selected) TrolleyTheme.colors.lime else MaterialTheme.colorScheme.background,
                            border = BorderStroke(
                                1.dp,
                                if (selected) TrolleyTheme.colors.lime else TrolleyTheme.colors.hairline,
                            ),
                            modifier = Modifier
                                .trolleySpringPress(interaction, pressedScale = 0.96f)
                                .minimumInteractiveComponentSize()
                                .testTag("booking_date_preset_$iso"),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (selected) TrolleyTheme.colors.limeDeep else TrolleyTheme.colors.mutedInk,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            )
                        }
                    }
                }

                val datePickerInteraction = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = {
                        val parsed = runCatching { LocalDate.parse(scheduledDate) }.getOrNull() ?: today
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val picked = LocalDate.of(year, month + 1, dayOfMonth)
                                scheduledDate = picked.toString()
                                localError = null
                            },
                            parsed.year,
                            parsed.monthValue - 1,
                            parsed.dayOfMonth,
                        ).apply {
                            val cal = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            datePicker.minDate = cal.timeInMillis
                        }.show()
                    },
                    interactionSource = datePickerInteraction,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .trolleySpringPress(datePickerInteraction)
                        .minimumInteractiveComponentSize()
                        .testTag("booking_date_picker"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = TrolleyTheme.colors.orange,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Selected date: $scheduledDate (tap for calendar)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // Time Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PREFERRED TIME",
                    style = MaterialTheme.typography.labelSmall,
                    color = TrolleyTheme.colors.mutedInk,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TIME_PRESETS.forEach { slot ->
                        val selected = scheduledTime == slot
                        val interaction = remember(slot) { MutableInteractionSource() }
                        Surface(
                            onClick = {
                                scheduledTime = slot
                                localError = null
                            },
                            interactionSource = interaction,
                            shape = RoundedCornerShape(20.dp),
                            color = if (selected) TrolleyTheme.colors.lime else MaterialTheme.colorScheme.background,
                            border = BorderStroke(
                                1.dp,
                                if (selected) TrolleyTheme.colors.lime else TrolleyTheme.colors.hairline,
                            ),
                            modifier = Modifier
                                .trolleySpringPress(interaction, pressedScale = 0.96f)
                                .minimumInteractiveComponentSize()
                                .testTag("booking_time_preset_${slot.replace(":", "")}"),
                        ) {
                            Text(
                                text = slot,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (selected) TrolleyTheme.colors.limeDeep else TrolleyTheme.colors.mutedInk,
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                            )
                        }
                    }
                }

                val timePickerInteraction = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = {
                        val parts = scheduledTime.split(":")
                        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
                        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        TimePickerDialog(
                            context,
                            { _, selectedHour, selectedMinute ->
                                scheduledTime = String.format(Locale.ENGLISH, "%02d:%02d", selectedHour, selectedMinute)
                                localError = null
                            },
                            hour,
                            minute,
                            true,
                        ).show()
                    },
                    interactionSource = timePickerInteraction,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .trolleySpringPress(timePickerInteraction)
                        .minimumInteractiveComponentSize()
                        .testTag("booking_time_picker"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccessTime,
                        contentDescription = null,
                        tint = TrolleyTheme.colors.limeAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Selected time: $scheduledTime (tap for custom time)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // Job / Access Notes
            OutlinedTextField(
                value = notes,
                onValueChange = {
                    if (it.length <= 600) {
                        notes = it
                        localError = null
                    }
                },
                label = { Text("Job details & access notes (optional)") },
                placeholder = { Text("Describe the work needed, gate instructions, or urgency…") },
                supportingText = { Text("${notes.length}/600 characters") },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TrolleyTheme.colors.limeAccent,
                    unfocusedBorderColor = TrolleyTheme.colors.hairline,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("booking_notes_input"),
            )

            val shownError = localError ?: errorMessage
            if (!shownError.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("booking_error_banner"),
                ) {
                    Text(
                        text = shownError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            val submitInteraction = remember { MutableInteractionSource() }
            Button(
                onClick = {
                    if (!BookingValidator.isValidScheduledDate(scheduledDate)) {
                        localError = "Please select today or a future date."
                        return@Button
                    }
                    if (!BookingValidator.isValidScheduledTime(scheduledTime)) {
                        localError = "Please pick a valid time in 24h HH:mm format."
                        return@Button
                    }
                    if (notes.trim().length > 600) {
                        localError = "Notes must be 600 characters or fewer."
                        return@Button
                    }
                    if (isReschedule && existingBooking != null) {
                        onConfirmReschedule(existingBooking, scheduledDate, scheduledTime, notes)
                    } else {
                        val target = activeProvider
                        if (target == null) {
                            localError = "Choose a service provider first."
                        } else {
                            onConfirmNewBooking(target, scheduledDate, scheduledTime, notes)
                        }
                    }
                },
                enabled = !isBusy && (isReschedule || activeProvider != null),
                interactionSource = submitInteraction,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .trolleySpringPress(submitInteraction)
                    .minimumInteractiveComponentSize()
                    .testTag("submit_booking_button"),
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(
                        if (isReschedule) R.string.booking_save_schedule else R.string.booking_send_request
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun LinkedProviderSummaryCard(
    businessName: String,
    categoryName: String,
    location: String,
    isPreview: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("booking_linked_provider_card"),
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = if (isPreview) TrolleyTheme.colors.orangeSoft else TrolleyTheme.colors.limeSoft,
                modifier = Modifier.size(42.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Storefront,
                    contentDescription = null,
                    tint = if (isPreview) TrolleyTheme.colors.orange else TrolleyTheme.colors.limeAccent,
                    modifier = Modifier.padding(9.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = businessName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isPreview) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = TrolleyTheme.colors.orangeSoft,
                        ) {
                            Text(
                                text = "Preview",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = TrolleyTheme.colors.orange,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.VerifiedUser,
                            contentDescription = "Published provider",
                            tint = TrolleyTheme.colors.limeAccent,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TrolleyTheme.colors.limeAccent,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = TrolleyTheme.colors.orange,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = location,
                        style = MaterialTheme.typography.bodySmall,
                        color = TrolleyTheme.colors.mutedInk,
                    )
                }
            }
        }
    }
}
