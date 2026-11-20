package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.catalog.GeographyCatalog
import com.example.data.catalog.ServiceTaxonomy
import com.example.data.model.ProviderProfile
import com.example.data.model.ReviewCalculator
import com.example.data.model.UiState
import com.example.ui.components.InteractiveStarSelector
import com.example.ui.components.ProviderRatingOverviewCard
import com.example.ui.components.ReviewFeedbackCard
import com.example.ui.components.StarRatingBadge
import com.example.ui.components.trolleySpringPress
import com.example.ui.theme.TrolleyTheme
import com.example.ui.viewmodel.ProviderDetailViewModel

@Composable
fun ProviderDetailScreen(
    viewModel: ProviderDetailViewModel,
    currentUserId: String,
    onBackClick: () -> Unit,
    onRequestBooking: (ProviderProfile) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    var selectedStars by rememberSaveable { mutableIntStateOf(5) }
    var commentText by rememberSaveable { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val snapshot = (state as? UiState.Success)?.data
    val existingReview = snapshot?.ownReview

    LaunchedEffect(existingReview?.id, existingReview?.rating, existingReview?.comment) {
        if (existingReview != null) {
            selectedStars = existingReview.rating.coerceIn(1, 5)
            commentText = existingReview.comment
        }
    }

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
                .testTag("provider_detail_screen"),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 14.dp,
                bottom = contentPadding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "detail_back_bar") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val backInteraction = remember { MutableInteractionSource() }
                    OutlinedButton(
                        onClick = onBackClick,
                        interactionSource = backInteraction,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                        modifier = Modifier
                            .trolleySpringPress(backInteraction)
                            .minimumInteractiveComponentSize()
                            .testTag("provider_detail_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Back")
                    }
                }
            }

            if (notice != null) {
                item(key = "detail_notice") {
                    val active = notice!!
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (active.isError) MaterialTheme.colorScheme.errorContainer else TrolleyTheme.colors.limeSoft,
                        border = BorderStroke(
                            1.dp,
                            if (active.isError) MaterialTheme.colorScheme.error else TrolleyTheme.colors.lime,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("provider_detail_notice"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = active.message,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (active.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
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

            when (val currentState = state) {
                UiState.Loading -> {
                    item(key = "detail_loading") {
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
                                Text(
                                    text = "Loading provider profile & reviews…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TrolleyTheme.colors.mutedInk,
                                )
                            }
                        }
                    }
                }

                is UiState.Error -> {
                    item(key = "detail_error") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = currentState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    val data = currentState.data
                    val provider = data.provider
                    if (provider == null) {
                        item(key = "detail_not_found") {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = "Provider profile unavailable",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "This listing is unpublished or no longer available.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TrolleyTheme.colors.mutedInk,
                                    )
                                }
                            }
                        }
                    } else {
                        item(key = "provider_profile_hero") {
                            val familyName = ServiceTaxonomy.findBySlug(provider.categorySlug)?.family
                                ?: provider.categoryName
                            val countryLabel = GeographyCatalog.getCountryLabel(provider.countryCode)
                            val isOwnPreview = data.isOwnProfile && provider.listingStatus != "published"

                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("provider_profile_hero_card"),
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(13.dp),
                                            color = if (isOwnPreview) TrolleyTheme.colors.orangeSoft else TrolleyTheme.colors.limeSoft,
                                            modifier = Modifier.size(48.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Storefront,
                                                contentDescription = null,
                                                tint = if (isOwnPreview) TrolleyTheme.colors.orange else TrolleyTheme.colors.limeAccent,
                                                modifier = Modifier.padding(11.dp),
                                            )
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(20.dp),
                                                color = if (isOwnPreview) TrolleyTheme.colors.orangeSoft else TrolleyTheme.colors.lime,
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.VerifiedUser,
                                                        contentDescription = null,
                                                        tint = if (isOwnPreview) TrolleyTheme.colors.orange else TrolleyTheme.colors.limeDeep,
                                                        modifier = Modifier.size(12.dp),
                                                    )
                                                    Text(
                                                        text = if (isOwnPreview) "Your preview listing" else "Published pro",
                                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                        color = if (isOwnPreview) TrolleyTheme.colors.orange else TrolleyTheme.colors.limeDeep,
                                                    )
                                                }
                                            }

                                            Text(
                                                text = provider.businessName,
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.testTag("provider_detail_business_name"),
                                            )
                                            Text(
                                                text = "${provider.categoryName} · $familyName",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = TrolleyTheme.colors.limeAccent,
                                            )
                                        }
                                    }

                                    StarRatingBadge(summary = data.summary)

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.LocationOn,
                                                contentDescription = null,
                                                tint = TrolleyTheme.colors.orange,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            val addressLine = provider.businessAddress?.takeIf { it.isNotBlank() }
                                            Text(
                                                text = if (addressLine != null) {
                                                    "$addressLine, ${provider.city}, $countryLabel"
                                                } else {
                                                    "${provider.city}, $countryLabel"
                                                },
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }

                                    if (!provider.description.isNullOrBlank()) {
                                        Text(
                                            text = provider.description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TrolleyTheme.colors.mutedInk,
                                        )
                                    }

                                    val bookInteraction = remember { MutableInteractionSource() }
                                    Button(
                                        onClick = { onRequestBooking(provider) },
                                        interactionSource = bookInteraction,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .trolleySpringPress(bookInteraction)
                                            .minimumInteractiveComponentSize()
                                            .testTag("provider_detail_book_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CalendarMonth,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.map_request_booking))
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        val dirInteraction = remember { MutableInteractionSource() }
                                        OutlinedButton(
                                            onClick = {
                                                val coords = GeographyCatalog.approximateCoordinates(provider.countryCode, provider.city)
                                                val lat = provider.latitude ?: coords.first
                                                val lon = provider.longitude ?: coords.second
                                                val label = Uri.encode("${provider.businessName}, ${provider.formattedLocation}")
                                                val geoUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon($label)")
                                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, geoUri)) }
                                            },
                                            interactionSource = dirInteraction,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                                            modifier = Modifier
                                                .weight(1f)
                                                .trolleySpringPress(dirInteraction)
                                                .minimumInteractiveComponentSize(),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Directions,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(R.string.map_open_directions))
                                        }

                                        val contactInteraction = remember { MutableInteractionSource() }
                                        OutlinedButton(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                    setData(Uri.parse("mailto:gmrboy2009@gmail.com"))
                                                    putExtra(
                                                        Intent.EXTRA_SUBJECT,
                                                        "Trolley Enquiry: ${provider.businessName} (${provider.city})",
                                                    )
                                                }
                                                runCatching { context.startActivity(intent) }
                                            },
                                            interactionSource = contactInteraction,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                                            modifier = Modifier
                                                .weight(1f)
                                                .trolleySpringPress(contactInteraction)
                                                .minimumInteractiveComponentSize(),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.MailOutline,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(R.string.map_contact_pro))
                                        }
                                    }
                                }
                            }
                        }

                        // Average Rating Score & Star Breakdown
                        item(key = "rating_overview") {
                            ProviderRatingOverviewCard(summary = data.summary)
                        }

                        // Submit or Update Customer Review Card
                        item(key = "leave_review_form") {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("leave_review_card"),
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        text = if (existingReview != null) "YOUR SUBMITTED REVIEW" else "RATE THIS PROVIDER",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TrolleyTheme.colors.orange,
                                    )
                                    Text(
                                        text = if (existingReview != null) {
                                            "Update your rating or feedback"
                                        } else {
                                            "Share feedback for ${provider.businessName}"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )

                                    InteractiveStarSelector(
                                        selectedRating = selectedStars,
                                        onSelectRating = {
                                            selectedStars = it
                                            localError = null
                                        },
                                        enabled = !isBusy,
                                    )

                                    OutlinedTextField(
                                        value = commentText,
                                        onValueChange = {
                                            if (it.length <= 1000) {
                                                commentText = it
                                                localError = null
                                            }
                                        },
                                        enabled = !isBusy,
                                        label = { Text("Your review & experience") },
                                        placeholder = { Text("Share timeliness, workmanship, and communication feedback…") },
                                        supportingText = { Text("${commentText.trim().length}/1000 characters (min 2)") },
                                        minLines = 3,
                                        maxLines = 6,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = TrolleyTheme.colors.limeAccent,
                                            unfocusedBorderColor = TrolleyTheme.colors.hairline,
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("review_comment_input"),
                                    )

                                    if (!localError.isNullOrBlank()) {
                                        Text(
                                            text = localError!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        val submitInteraction = remember { MutableInteractionSource() }
                                        Button(
                                            onClick = {
                                                if (!ReviewCalculator.isValidRating(selectedStars)) {
                                                    localError = "Select 1 to 5 stars."
                                                    return@Button
                                                }
                                                if (!ReviewCalculator.isValidComment(commentText)) {
                                                    localError = "Write at least 2 characters of feedback."
                                                    return@Button
                                                }
                                                viewModel.submitReview(
                                                    provider = provider,
                                                    rating = selectedStars,
                                                    comment = commentText,
                                                    relatedBookingId = data.relatedBookingId,
                                                    isUpdate = existingReview != null,
                                                )
                                            },
                                            enabled = !isBusy,
                                            interactionSource = submitInteraction,
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .trolleySpringPress(submitInteraction)
                                                .minimumInteractiveComponentSize()
                                                .testTag("submit_review_button"),
                                        ) {
                                            if (isBusy) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            Text(
                                                stringResource(
                                                    if (existingReview != null) R.string.review_update_btn else R.string.review_submit_btn
                                                )
                                            )
                                        }

                                        if (existingReview != null) {
                                            TextButton(
                                                onClick = {
                                                    commentText = ""
                                                    selectedStars = 5
                                                    viewModel.deleteMyReview()
                                                },
                                                enabled = !isBusy,
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("delete_review_button"),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteOutline,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = stringResource(R.string.review_delete_btn),
                                                    color = MaterialTheme.colorScheme.error,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Reviews List Header
                        item(key = "reviews_list_header") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                HorizontalDivider(color = TrolleyTheme.colors.hairline)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.reviews_eyebrow),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TrolleyTheme.colors.orange,
                                )
                                Text(
                                    text = "${stringResource(R.string.reviews_section_title)} (${data.reviews.size})",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }

                        if (data.reviews.isEmpty()) {
                            item(key = "reviews_empty") {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("reviews_empty_card"),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.review_empty_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = stringResource(R.string.review_empty_body),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TrolleyTheme.colors.mutedInk,
                                        )
                                    }
                                }
                            }
                        } else {
                            items(data.reviews, key = { it.id }) { review ->
                                ReviewFeedbackCard(
                                    review = review,
                                    isOwnReview = review.customerId == currentUserId,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
