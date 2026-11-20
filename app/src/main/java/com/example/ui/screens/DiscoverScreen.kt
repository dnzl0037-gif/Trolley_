package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.catalog.GeographyCatalog
import com.example.data.catalog.ServiceTaxonomy
import com.example.data.model.ProviderProfile
import com.example.data.model.ProviderRatingSummary
import com.example.data.model.UiState
import com.example.ui.components.PickerOption
import com.example.ui.components.ProviderMapView
import com.example.ui.components.SearchablePickerField
import com.example.ui.components.StarRatingBadge
import com.example.ui.components.trolleySpringPress
import com.example.ui.theme.TrolleyTheme
import com.example.ui.viewmodel.DiscoveryUiState
import com.example.ui.viewmodel.DiscoveryViewMode
import com.example.ui.viewmodel.NearbyProvidersSnapshot
import kotlinx.coroutines.launch
import java.text.NumberFormat

private data class FamilyHighlight(
    val title: String,
    val detail: String,
    val targetFamily: String,
    val icon: ImageVector,
    val orangeAccent: Boolean = false,
)

private val familyHighlights = listOf(
    FamilyHighlight("Home & repairs", "Plumbing, electrical, roofing and more", "Plumbing", Icons.Rounded.Build),
    FamilyHighlight("Business services", "Accounting, legal, IT and marketing", "Accounting", Icons.Rounded.BusinessCenter, orangeAccent = true),
    FamilyHighlight("Beauty & wellness", "Hair, beauty, fitness and personal care", "Beauty", Icons.Rounded.ContentCut),
    FamilyHighlight("Events & creative", "Photography, catering and event planning", "Photography", Icons.Rounded.CameraAlt),
    FamilyHighlight("Transport & auto", "Mechanics, moving and delivery", "Automotive repair", Icons.Rounded.DirectionsCar, orangeAccent = true),
    FamilyHighlight("Learning & care", "Tutors, childcare and elder care", "Tutoring", Icons.Rounded.School),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(
    uiState: DiscoveryUiState,
    isSignedIn: Boolean,
    nearbyProvidersState: UiState<NearbyProvidersSnapshot>?,
    onQueryChange: (String) -> Unit,
    onDiscoveryCountryChange: (String) -> Unit,
    onChooseCategory: (String) -> Unit,
    onSelectServiceType: (String, String?) -> Unit,
    onClearFilters: () -> Unit,
    onViewModeChange: (DiscoveryViewMode) -> Unit,
    onToggleShowAll: () -> Unit,
    onBrowseAll: () -> Unit,
    onListBusinessClick: () -> Unit,
    onSignInClick: () -> Unit,
    onRequestBooking: (ProviderProfile) -> Unit = {},
    onViewProviderProfile: (ProviderProfile) -> Unit = {},
    onContactClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val allServicesLabel = stringResource(R.string.all_services)

    val countryOptions = remember {
        listOf(PickerOption(value = "", label = "Anywhere")) +
            GeographyCatalog.countries.map {
                PickerOption(value = it.code, label = it.label, keywords = it.region)
            }
    }

    val directoryPickerOptions = remember {
        ServiceTaxonomy.categories.map {
            PickerOption(
                value = it.name,
                label = it.name,
                hint = it.family,
                keywords = it.slug,
            )
        }
    }

    val selectedCountryCode = uiState.preferences.discoveryCountry
    val locationNote = remember(selectedCountryCode) {
        if (selectedCountryCode.isBlank()) {
            "Exploring from anywhere."
        } else {
            val label = GeographyCatalog.getCountryLabel(selectedCountryCode)
            val region = GeographyCatalog.getRegionForCountry(selectedCountryCode)
            if (region.isNotBlank()) "Exploring from $label, $region." else "Exploring from $label."
        }
    }

    val indexedCountFormatted = remember {
        NumberFormat.getIntegerInstance().format(ServiceTaxonomy.subcategoryCount)
    }

    val hasActiveFilter = uiState.query.isNotBlank() || uiState.activeCategory != allServicesLabel
    val topCategoryChips = remember(allServicesLabel) {
        listOf(allServicesLabel) + ServiceTaxonomy.familyNames
    }
    val directoryItemIndex = if (hasActiveFilter) 4 else 3

    val snapshot = (nearbyProvidersState as? UiState.Success)?.data
    val filteredRealProviders = remember(snapshot, uiState.query, uiState.activeCategory, allServicesLabel) {
        if (snapshot == null) {
            emptyList()
        } else {
            val publishedIds = snapshot.publishedProviders.map { it.id }.toSet()
            val own = snapshot.ownProfile?.takeIf { it.id !in publishedIds }
            val candidates = snapshot.publishedProviders + listOfNotNull(own)
            val trimmedQuery = uiState.query.trim().lowercase(java.util.Locale.ENGLISH)
            candidates.filter { profile ->
                val familyName = ServiceTaxonomy.findBySlug(profile.categorySlug)?.family ?: profile.categoryName
                val matchesCategory = uiState.activeCategory == allServicesLabel ||
                    familyName.equals(uiState.activeCategory, ignoreCase = true) ||
                    profile.categoryName.equals(uiState.activeCategory, ignoreCase = true)
                val matchesQuery = trimmedQuery.isBlank() ||
                    profile.businessName.lowercase(java.util.Locale.ENGLISH).contains(trimmedQuery) ||
                    profile.categoryName.lowercase(java.util.Locale.ENGLISH).contains(trimmedQuery) ||
                    familyName.lowercase(java.util.Locale.ENGLISH).contains(trimmedQuery) ||
                    profile.city.lowercase(java.util.Locale.ENGLISH).contains(trimmedQuery) ||
                    profile.businessAddress.orEmpty().lowercase(java.util.Locale.ENGLISH).contains(trimmedQuery)
                matchesCategory && matchesQuery
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Pinned Top Search & Category Filter Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("top_search_bar"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_placeholder),
                            maxLines = 1,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = TrolleyTheme.colors.mutedInk,
                        )
                    },
                    trailingIcon = {
                        if (uiState.query.isNotBlank()) {
                            IconButton(
                                onClick = { onQueryChange("") },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("clear_search_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search keyword",
                                    tint = TrolleyTheme.colors.mutedInk,
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Filter by category below",
                                tint = TrolleyTheme.colors.mutedInk,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            coroutineScope.launch { listState.animateScrollToItem(0) }
                        },
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TrolleyTheme.colors.limeAccent,
                        unfocusedBorderColor = TrolleyTheme.colors.hairline,
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_input"),
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(topCategoryChips, key = { it }) { categoryName ->
                        val selected = uiState.activeCategory == categoryName
                        val chipInteraction = remember(categoryName) { MutableInteractionSource() }
                        Surface(
                            onClick = { onChooseCategory(categoryName) },
                            interactionSource = chipInteraction,
                            shape = RoundedCornerShape(20.dp),
                            color = if (selected) TrolleyTheme.colors.lime else MaterialTheme.colorScheme.background,
                            border = BorderStroke(
                                1.dp,
                                if (selected) TrolleyTheme.colors.lime else TrolleyTheme.colors.hairline,
                            ),
                            modifier = Modifier
                                .trolleySpringPress(chipInteraction, pressedScale = 0.95f)
                                .minimumInteractiveComponentSize()
                                .testTag("top_filter_chip_${categoryName.lowercase().replace(' ', '_')}"),
                        ) {
                            Text(
                                text = categoryName,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (selected) TrolleyTheme.colors.limeDeep else TrolleyTheme.colors.mutedInk,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DiscoveryModeChip(
                        label = stringResource(R.string.view_mode_directory),
                        icon = Icons.AutoMirrored.Rounded.ViewList,
                        selected = uiState.viewMode == DiscoveryViewMode.DIRECTORY,
                        onClick = { onViewModeChange(DiscoveryViewMode.DIRECTORY) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("view_mode_directory_button"),
                    )
                    DiscoveryModeChip(
                        label = stringResource(R.string.view_mode_map),
                        icon = Icons.Rounded.Map,
                        selected = uiState.viewMode == DiscoveryViewMode.MAP,
                        onClick = { onViewModeChange(DiscoveryViewMode.MAP) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("view_mode_map_button"),
                    )
                }
            }
        }
        HorizontalDivider(color = TrolleyTheme.colors.hairline)

        Crossfade(
            targetState = uiState.viewMode,
            label = "discover_view_mode",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { mode ->
            if (mode == DiscoveryViewMode.MAP) {
                ProviderMapView(
                    isSignedIn = isSignedIn,
                    providersState = nearbyProvidersState,
                    query = uiState.query,
                    activeCategory = uiState.activeCategory,
                    allServicesLabel = allServicesLabel,
                    selectedCountryCode = selectedCountryCode,
                    darkTheme = uiState.preferences.darkTheme,
                    contentPadding = contentPadding,
                    onSignInClick = onSignInClick,
                    onEditOwnListingClick = onListBusinessClick,
                    onRequestBooking = onRequestBooking,
                    onViewProviderProfile = onViewProviderProfile,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 14.dp,
                        bottom = contentPadding.calculateBottomPadding() + 28.dp,
                    ),
                ) {
            // 0. Live Filtered Results Panel (when filtering by keyword or category)
            if (hasActiveFilter) {
                item(key = "live_search_results") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, TrolleyTheme.colors.limeAccent),
                        shadowElevation = 2.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (uiState.activeCategory != allServicesLabel) {
                                            "FILTERED · ${uiState.activeCategory.uppercase()}"
                                        } else {
                                            "FILTERED RESULTS"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TrolleyTheme.colors.orange,
                                    )
                                    Text(
                                        text = "${uiState.matchingCategories.size} matching service categories",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.testTag("live_filter_count"),
                                    )
                                }
                                val clearInteraction = remember { MutableInteractionSource() }
                                TextButton(
                                    onClick = onClearFilters,
                                    interactionSource = clearInteraction,
                                    modifier = Modifier
                                        .trolleySpringPress(clearInteraction)
                                        .testTag("clear_all_filters_button"),
                                ) {
                                    Text(
                                        text = stringResource(R.string.clear_search),
                                        color = TrolleyTheme.colors.orange,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }

                            if (uiState.matchingCategories.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.directory_no_matches),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TrolleyTheme.colors.mutedInk,
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    uiState.matchingCategories.take(12).forEach { category ->
                                        Surface(
                                            onClick = {
                                                onSelectServiceType(category.name, category.family)
                                            },
                                            shape = RoundedCornerShape(9.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                                            ) {
                                                Text(
                                                    text = category.name,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                                Text(
                                                    text = category.family,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                                    color = TrolleyTheme.colors.mutedInk,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (filteredRealProviders.isNotEmpty()) {
                                HorizontalDivider(color = TrolleyTheme.colors.hairline)
                                Text(
                                    text = "BOOKABLE PROFILES (${filteredRealProviders.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TrolleyTheme.colors.limeAccent,
                                )
                                filteredRealProviders.forEach { pro ->
                                    DiscoverBookableProviderRow(
                                        profile = pro,
                                        ratingSummary = snapshot?.ratingSummaries?.get(pro.id),
                                        isOwnPreview = pro.id == snapshot?.ownProfile?.id && pro.listingStatus != "published",
                                        onOpenProfile = { onViewProviderProfile(pro) },
                                        onRequestBooking = { onRequestBooking(pro) },
                                    )
                                }
                            } else {
                                Text(
                                    text = "$locationNote Published pros in matching categories will appear here once reviewed and approved.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TrolleyTheme.colors.mutedInk,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            // 1. Hero Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .widthIn(max = 880.dp),
                ) {
                    EyebrowLabel(text = stringResource(R.string.hero_eyebrow))
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = buildAnnotatedString {
                            append(stringResource(R.string.hero_title_line1))
                            append("\n")
                            withStyle(SpanStyle(color = TrolleyTheme.colors.limeAccent)) {
                                append(stringResource(R.string.hero_title_line2))
                            }
                        },
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.hero_lead),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TrolleyTheme.colors.mutedInk,
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    // Location & Action Panel
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                        shadowElevation = 4.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SearchablePickerField(
                                label = "Location",
                                value = selectedCountryCode,
                                options = countryOptions,
                                onSelect = onDiscoveryCountryChange,
                                placeholder = "Anywhere",
                                searchPlaceholder = "Search country",
                                leadingIcon = Icons.Rounded.LocationOn,
                                compact = true,
                                modifier = Modifier.weight(1f),
                                testTag = "discovery_country_picker",
                            )

                            val findProInteraction = remember { MutableInteractionSource() }
                            Button(
                                onClick = {
                                    coroutineScope.launch { listState.animateScrollToItem(directoryItemIndex) }
                                },
                                interactionSource = findProInteraction,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TrolleyTheme.colors.orange,
                                    contentColor = Color.White,
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                modifier = Modifier
                                    .trolleySpringPress(findProInteraction)
                                    .minimumInteractiveComponentSize()
                                    .testTag("find_pro_button"),
                            ) {
                                Text(
                                    text = stringResource(R.string.find_a_pro),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VerifiedUser,
                            contentDescription = null,
                            tint = TrolleyTheme.colors.limeAccent,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "${stringResource(R.string.hero_meta_real)}  ·  ${stringResource(R.string.hero_meta_no_pressure)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$locationNote Saved on this device only. Directory results are service types, not providers in that location.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TrolleyTheme.colors.mutedInk,
                    modifier = Modifier.testTag("discovery_location_note"),
                )

                Spacer(modifier = Modifier.height(22.dp))
                HeroArtBanner()
            }
        }

        // 2. Trust Strip
        item {
            Spacer(modifier = Modifier.height(26.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TrustStripItem(Icons.Rounded.VerifiedUser, stringResource(R.string.trust_strip_1), Modifier.weight(1f))
                        TrustStripItem(Icons.Rounded.Lock, stringResource(R.string.trust_strip_2), Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TrustStripItem(Icons.Rounded.LocationOn, stringResource(R.string.trust_strip_3), Modifier.weight(1f))
                        TrustStripItem(Icons.Rounded.ChatBubbleOutline, stringResource(R.string.trust_strip_4), Modifier.weight(1f))
                    }
                }
            }
        }

        // 3. Family Highlights (6 category cards)
        item {
            Spacer(modifier = Modifier.height(34.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        EyebrowLabel(text = stringResource(R.string.explore_eyebrow))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = buildAnnotatedString {
                                append(stringResource(R.string.explore_title_prefix))
                                withStyle(SpanStyle(color = TrolleyTheme.colors.limeAccent)) {
                                    append(stringResource(R.string.explore_title_accent))
                                }
                            },
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    val browseAllInteraction = remember { MutableInteractionSource() }
                    TextButton(
                        onClick = {
                            onBrowseAll()
                            coroutineScope.launch { listState.animateScrollToItem(directoryItemIndex) }
                        },
                        interactionSource = browseAllInteraction,
                        modifier = Modifier
                            .trolleySpringPress(browseAllInteraction)
                            .testTag("browse_all_button"),
                    ) {
                        Text(
                            text = if (uiState.showAllTaxonomy) stringResource(R.string.show_less) else stringResource(R.string.browse_all_services),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.ArrowOutward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    familyHighlights.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            pair.forEach { highlight ->
                                CategoryHighlightCard(
                                    item = highlight,
                                    onClick = {
                                        onChooseCategory(highlight.targetFamily)
                                        coroutineScope.launch {
                                            val targetIndex = if (highlight.targetFamily != allServicesLabel || uiState.query.isNotBlank()) 4 else 3
                                            listState.animateScrollToItem(targetIndex)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Service Directory Browser
        item {
            Spacer(modifier = Modifier.height(26.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                shape = RoundedCornerShape(17.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.directory_kicker),
                                style = MaterialTheme.typography.labelSmall,
                                color = TrolleyTheme.colors.orange,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.directory_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                        ) {
                            Text(
                                text = "$indexedCountFormatted service types indexed",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = TrolleyTheme.colors.mutedInk,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    SearchablePickerField(
                        label = "Jump to a service type",
                        value = if (directoryPickerOptions.any { it.value == uiState.query }) uiState.query else "",
                        options = directoryPickerOptions,
                        onSelect = { selectedService ->
                            val family = ServiceTaxonomy.categories.firstOrNull { it.name == selectedService }?.family
                            onSelectServiceType(selectedService, family)
                        },
                        placeholder = "Find a service type",
                        searchPlaceholder = "Search service or family",
                        compact = true,
                        testTag = "directory_picker",
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Family filter chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val chipNames = listOf(stringResource(R.string.all_services)) +
                            ServiceTaxonomy.familyNames.take(8)
                        chipNames.forEach { name ->
                            val selected = uiState.activeCategory == name
                            Surface(
                                onClick = { onChooseCategory(name) },
                                shape = RoundedCornerShape(24.dp),
                                color = if (selected) TrolleyTheme.colors.lime else Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) TrolleyTheme.colors.lime else TrolleyTheme.colors.hairline,
                                ),
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (selected) TrolleyTheme.colors.limeDeep else TrolleyTheme.colors.mutedInk,
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = TrolleyTheme.colors.hairline)
                    Spacer(modifier = Modifier.height(14.dp))

                    if (hasActiveFilter) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "MATCHING SERVICE TYPES",
                                style = MaterialTheme.typography.labelSmall,
                                color = TrolleyTheme.colors.mutedInk,
                            )
                            Text(
                                text = "${uiState.matchingCategories.size} results",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = TrolleyTheme.colors.mutedInk,
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        if (uiState.matchingCategories.isEmpty()) {
                            Text(
                                text = stringResource(R.string.directory_no_matches),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TrolleyTheme.colors.mutedInk,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                uiState.matchingCategories.take(24).forEach { category ->
                                    Surface(
                                        onClick = { onSelectServiceType(category.name, category.family) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = category.name,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Icon(
                                                imageVector = Icons.Rounded.ArrowOutward,
                                                contentDescription = null,
                                                modifier = Modifier.size(13.dp),
                                                tint = TrolleyTheme.colors.mutedInk,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(11.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(38.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = TrolleyTheme.colors.mutedInk,
                                    modifier = Modifier.padding(9.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.directory_empty_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.directory_empty_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TrolleyTheme.colors.mutedInk,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        TextButton(
                            onClick = onToggleShowAll,
                            modifier = Modifier.testTag("toggle_categories_button"),
                        ) {
                            Text(
                                text = if (uiState.showAllTaxonomy) stringResource(R.string.hide_categories) else stringResource(R.string.see_categories),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (uiState.showAllTaxonomy) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    AnimatedVisibility(visible = uiState.showAllTaxonomy) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            HorizontalDivider(color = TrolleyTheme.colors.hairline)
                            ServiceTaxonomy.categoryFamilies.forEach { (family, subs) ->
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = family,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        subs.forEach { sub ->
                                            Surface(
                                                onClick = { onSelectServiceType(sub, family) },
                                                shape = RoundedCornerShape(7.dp),
                                                color = MaterialTheme.colorScheme.background,
                                                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                                            ) {
                                                Text(
                                                    text = sub,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = TrolleyTheme.colors.mutedInk,
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (filteredRealProviders.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = TrolleyTheme.colors.hairline)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "AVAILABLE SERVICE PROFILES (${filteredRealProviders.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = TrolleyTheme.colors.limeAccent,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            filteredRealProviders.take(6).forEach { pro ->
                                DiscoverBookableProviderRow(
                                    profile = pro,
                                    ratingSummary = snapshot?.ratingSummaries?.get(pro.id),
                                    isOwnPreview = pro.id == snapshot?.ownProfile?.id && pro.listingStatus != "published",
                                    onOpenProfile = { onViewProviderProfile(pro) },
                                    onRequestBooking = { onRequestBooking(pro) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Provider CTA Banner + Bottom Note
        item {
            Spacer(modifier = Modifier.height(26.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = TrolleyTheme.colors.bannerDark,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(13.dp),
                            color = TrolleyTheme.colors.lime,
                            modifier = Modifier.size(46.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Storefront,
                                contentDescription = null,
                                tint = TrolleyTheme.colors.limeDeep,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                        Text(
                            text = stringResource(R.string.provider_cta_eyebrow),
                            style = MaterialTheme.typography.labelSmall,
                            color = TrolleyTheme.colors.lime,
                        )
                        Text(
                            text = buildAnnotatedString {
                                append(stringResource(R.string.provider_cta_title_prefix))
                                withStyle(SpanStyle(color = TrolleyTheme.colors.lime)) {
                                    append(stringResource(R.string.provider_cta_title_accent))
                                }
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            color = TrolleyTheme.colors.bannerDarkInk,
                        )
                        Text(
                            text = stringResource(R.string.provider_cta_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TrolleyTheme.colors.bannerDarkMuted,
                        )
                        val listBusinessInteraction = remember { MutableInteractionSource() }
                        Button(
                            onClick = onListBusinessClick,
                            interactionSource = listBusinessInteraction,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TrolleyTheme.colors.orange,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .trolleySpringPress(listBusinessInteraction)
                                .minimumInteractiveComponentSize()
                                .testTag("list_business_cta"),
                        ) {
                            Text(
                                text = stringResource(R.string.list_your_business),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Rounded.ArrowOutward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HelpOutline,
                            contentDescription = null,
                            tint = TrolleyTheme.colors.mutedInk,
                            modifier = Modifier.padding(9.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.bottom_note_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "${stringResource(R.string.bottom_note_body)} Contact: ${stringResource(R.string.support_email)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TrolleyTheme.colors.mutedInk,
                        )
                    }
                    val contactInteraction = remember { MutableInteractionSource() }
                    TextButton(
                        onClick = onContactClick,
                        interactionSource = contactInteraction,
                        modifier = Modifier
                            .trolleySpringPress(contactInteraction)
                            .testTag("contact_email_button"),
                    ) {
                        Text(
                            text = stringResource(R.string.get_in_touch),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        }
        }
        }
    }
}
}

@Composable
private fun DiscoveryModeChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) TrolleyTheme.colors.lime else MaterialTheme.colorScheme.background,
        border = BorderStroke(
            1.dp,
            if (selected) TrolleyTheme.colors.lime else TrolleyTheme.colors.hairline,
        ),
        modifier = modifier
            .trolleySpringPress(interaction, pressedScale = 0.97f)
            .minimumInteractiveComponentSize(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) TrolleyTheme.colors.limeDeep else TrolleyTheme.colors.mutedInk,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = if (selected) TrolleyTheme.colors.limeDeep else TrolleyTheme.colors.mutedInk,
            )
        }
    }
}

@Composable
fun EyebrowLabel(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(23.dp)
                .height(2.dp)
                .background(TrolleyTheme.colors.orange),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = TrolleyTheme.colors.orange,
        )
    }
}

@Composable
private fun HeroArtBanner() {
    val lineColor = TrolleyTheme.colors.hairline
    val lime = TrolleyTheme.colors.lime

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(290.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 1.2.dp.toPx())
            val cx = size.width / 2f
            val cy = size.height / 2f
            rotate(degrees = -22f, pivot = Offset(cx, cy)) {
                drawOval(
                    color = lineColor,
                    topLeft = Offset(cx - 150.dp.toPx(), cy - 115.dp.toPx()),
                    size = Size(300.dp.toPx(), 230.dp.toPx()),
                    style = stroke,
                )
            }
            rotate(degrees = 35f, pivot = Offset(cx, cy)) {
                drawOval(
                    color = lineColor,
                    topLeft = Offset(cx - 115.dp.toPx(), cy - 145.dp.toPx()),
                    size = Size(230.dp.toPx(), 290.dp.toPx()),
                    style = stroke,
                )
            }
        }

        // Center lime circle + logo
        Surface(
            shape = CircleShape,
            color = lime,
            shadowElevation = 10.dp,
            modifier = Modifier.size(196.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.trolley_logo),
                    contentDescription = stringResource(R.string.app_name),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(88.dp)
                        .offset(y = (-12).dp)
                        .rotate(-7f)
                        .clip(RoundedCornerShape(22.dp)),
                )
                Surface(
                    shape = RoundedCornerShape(30.dp),
                    color = Color(0xFF171916),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp, start = 10.dp, end = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(lime),
                        )
                        Text(
                            text = stringResource(R.string.hero_art_caption),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = Color.White,
                        )
                    }
                }
            }
        }

        // Floating top-right card
        HeroFloatingCard(
            title = "Home repairs",
            subtitle = "Find a local specialist",
            icon = Icons.Rounded.Build,
            iconBg = TrolleyTheme.colors.limeSoft,
            iconTint = TrolleyTheme.colors.limeAccent,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 4.dp),
        )

        // Floating bottom-left card
        HeroFloatingCard(
            title = "Business services",
            subtitle = "Experts for every stage",
            icon = Icons.Rounded.BusinessCenter,
            iconBg = TrolleyTheme.colors.orangeSoft,
            iconTint = TrolleyTheme.colors.orange,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 14.dp, start = 4.dp),
        )
    }
}

@Composable
private fun HeroFloatingCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = iconBg,
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.padding(6.dp),
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                    color = TrolleyTheme.colors.mutedInk,
                )
            }
        }
    }
}

@Composable
private fun TrustStripItem(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TrolleyTheme.colors.limeAccent,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = TrolleyTheme.colors.mutedInk,
        )
    }
}

@Composable
private fun CategoryHighlightCard(
    item: FamilyHighlight,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardInteraction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = cardInteraction,
        modifier = modifier
            .trolleySpringPress(cardInteraction)
            .minimumInteractiveComponentSize(),
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = if (item.orangeAccent) TrolleyTheme.colors.orange else TrolleyTheme.colors.limeAccent,
                        modifier = Modifier.padding(9.dp),
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ArrowOutward,
                    contentDescription = null,
                    tint = TrolleyTheme.colors.mutedInk,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = item.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TrolleyTheme.colors.mutedInk,
                    minLines = 2,
                )
            }
        }
    }
}

@Composable
private fun DiscoverBookableProviderRow(
    profile: ProviderProfile,
    ratingSummary: ProviderRatingSummary?,
    isOwnPreview: Boolean,
    onOpenProfile: () -> Unit,
    onRequestBooking: () -> Unit,
) {
    Surface(
        onClick = onOpenProfile,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("discover_provider_card_${profile.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (isOwnPreview) "${profile.businessName} (Preview)" else profile.businessName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${profile.categoryName} · ${profile.formattedLocation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TrolleyTheme.colors.mutedInk,
                )
                StarRatingBadge(summary = ratingSummary)
            }
            val interaction = remember { MutableInteractionSource() }
            Button(
                onClick = onRequestBooking,
                interactionSource = interaction,
                shape = RoundedCornerShape(9.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .trolleySpringPress(interaction)
                    .minimumInteractiveComponentSize()
                    .testTag("discover_book_button_${profile.id}"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text("Book", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
