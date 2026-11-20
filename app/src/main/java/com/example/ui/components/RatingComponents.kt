package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StarHalf
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.VerifiedUser
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.ProviderRatingSummary
import com.example.data.model.ProviderReview
import com.example.ui.theme.TrolleyTheme
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun StarRatingBadge(
    summary: ProviderRatingSummary?,
    modifier: Modifier = Modifier,
) {
    val hasReviews = summary != null && summary.reviewCount > 0
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (hasReviews) TrolleyTheme.colors.limeSoft else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (hasReviews) TrolleyTheme.colors.lime else TrolleyTheme.colors.hairline,
        ),
        modifier = modifier.testTag("star_rating_badge"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (hasReviews) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                contentDescription = null,
                tint = if (hasReviews) TrolleyTheme.colors.orange else TrolleyTheme.colors.mutedInk,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = if (hasReviews) {
                    val unit = if (summary!!.reviewCount == 1) "review" else "reviews"
                    "${summary.formattedAverage} (${summary.reviewCount} $unit)"
                } else {
                    "No reviews yet"
                },
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = if (hasReviews) MaterialTheme.colorScheme.onSurface else TrolleyTheme.colors.mutedInk,
            )
        }
    }
}

@Composable
fun StarDisplayRow(
    rating: Double,
    modifier: Modifier = Modifier,
    starSize: Dp = 18.dp,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (i in 1..5) {
            val icon = when {
                rating >= i - 0.25 -> Icons.Rounded.Star
                rating >= i - 0.75 -> Icons.AutoMirrored.Rounded.StarHalf
                else -> Icons.Rounded.StarOutline
            }
            val tint = if (rating >= i - 0.75) TrolleyTheme.colors.orange else TrolleyTheme.colors.mutedInk
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(starSize),
            )
        }
    }
}

@Composable
fun InteractiveStarSelector(
    selectedRating: Int,
    onSelectRating: (Int) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (star in 1..5) {
            val active = star <= selectedRating
            val interaction = remember(star) { MutableInteractionSource() }
            IconButton(
                onClick = { onSelectRating(star) },
                enabled = enabled,
                interactionSource = interaction,
                modifier = Modifier
                    .trolleySpringPress(interaction, pressedScale = 0.88f)
                    .minimumInteractiveComponentSize()
                    .testTag("review_star_$star"),
            ) {
                Icon(
                    imageVector = if (active) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    contentDescription = "Rate $star out of 5 stars",
                    tint = if (active) TrolleyTheme.colors.orange else TrolleyTheme.colors.mutedInk,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$selectedRating / 5",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun ProviderRatingOverviewCard(
    summary: ProviderRatingSummary,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
        modifier = modifier
            .fillMaxWidth()
            .testTag("provider_rating_overview_card"),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = if (summary.reviewCount > 0) summary.formattedAverage else "—",
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("provider_average_rating_value"),
                    )
                    StarDisplayRow(
                        rating = summary.averageRating,
                        starSize = 18.dp,
                    )
                    Text(
                        text = if (summary.reviewCount == 1) {
                            "1 customer review"
                        } else {
                            "${summary.reviewCount} customer reviews"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TrolleyTheme.colors.mutedInk,
                        modifier = Modifier.testTag("provider_review_count_label"),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    for (stars in 5 downTo 1) {
                        val count = summary.ratingCounts[stars] ?: 0
                        val fraction = if (summary.reviewCount > 0) {
                            (count.toFloat() / summary.reviewCount.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "$stars★",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = TrolleyTheme.colors.mutedInk,
                                modifier = Modifier.width(24.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                if (fraction > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(TrolleyTheme.colors.orange),
                                    )
                                }
                            }
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = TrolleyTheme.colors.mutedInk,
                                modifier = Modifier.width(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewFeedbackCard(
    review: ProviderReview,
    isOwnReview: Boolean,
    modifier: Modifier = Modifier,
) {
    val dateLabel = remember(review.updatedAt, review.createdAt) {
        val ts = review.updatedAt ?: review.createdAt
        if (ts != null) {
            SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(ts.toDate())
        } else {
            "Just now"
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isOwnReview) TrolleyTheme.colors.lime else TrolleyTheme.colors.hairline,
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("review_item_card_${review.id}"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = if (isOwnReview) TrolleyTheme.colors.limeSoft else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = if (isOwnReview) TrolleyTheme.colors.limeAccent else TrolleyTheme.colors.mutedInk,
                            modifier = Modifier.padding(7.dp),
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = review.reviewerName.ifBlank { "Trolley Member" },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (isOwnReview) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = TrolleyTheme.colors.lime,
                                ) {
                                    Text(
                                        text = "You",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = TrolleyTheme.colors.limeDeep,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = TrolleyTheme.colors.mutedInk,
                        )
                    }
                }

                StarDisplayRow(
                    rating = review.rating.toDouble(),
                    starSize = 16.dp,
                )
            }

            if (!review.bookingId.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = TrolleyTheme.colors.limeAccent,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "Verified booking interaction",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TrolleyTheme.colors.limeAccent,
                    )
                }
            }

            Text(
                text = review.comment,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
