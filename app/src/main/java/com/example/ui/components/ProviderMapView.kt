package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.R
import com.example.data.catalog.GeographyCatalog
import com.example.data.catalog.ServiceTaxonomy
import com.example.data.location.AddressGeocoder
import com.example.data.location.GeocodeResult
import com.example.data.model.MapProviderMarker
import com.example.data.model.ProviderProfile
import com.example.data.model.ProviderRatingSummary
import com.example.data.model.UiState
import com.example.ui.theme.TrolleyTheme
import com.example.ui.viewmodel.NearbyProvidersSnapshot
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.util.Locale

@Composable
fun ProviderMapView(
    isSignedIn: Boolean,
    providersState: UiState<NearbyProvidersSnapshot>?,
    query: String,
    activeCategory: String,
    allServicesLabel: String,
    selectedCountryCode: String,
    darkTheme: Boolean,
    contentPadding: PaddingValues,
    onSignInClick: () -> Unit,
    onEditOwnListingClick: () -> Unit,
    onRequestBooking: (ProviderProfile) -> Unit = {},
    onViewProviderProfile: (ProviderProfile) -> Unit = {},
    onMessageProvider: (ProviderProfile) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    remember(context) {
        val baseDir = File(context.cacheDir, "osmdroid")
        val tileDir = File(baseDir, "tiles")
        tileDir.mkdirs()
        Configuration.getInstance().apply {
            osmdroidBasePath = baseDir
            osmdroidTileCache = tileDir
            userAgentValue = "${context.packageName}/${BuildConfig.VERSION_NAME}"
        }
        true
    }

    var userLocation by remember {
        mutableStateOf(AddressGeocoder.fetchLastKnownCoarseLocation(context))
    }
    var locationNotice by remember { mutableStateOf<String?>(null) }
    var selectedProviderId by remember { mutableStateOf<String?>(null) }

    val countryCenter = remember(selectedCountryCode) {
        GeographyCatalog.approximateCoordinates(selectedCountryCode.ifBlank { "ZW" })
    }

    val snapshot = (providersState as? UiState.Success)?.data
    val allCandidateProfiles = remember(snapshot) {
        if (snapshot == null) {
            emptyList()
        } else {
            val publishedIds = snapshot.publishedProviders.map { it.id }.toSet()
            val own = snapshot.ownProfile?.takeIf { it.id !in publishedIds }
            snapshot.publishedProviders + listOfNotNull(own)
        }
    }

    val geocodedCache = remember { mutableStateMapOf<String, GeocodeResult>() }

    LaunchedEffect(allCandidateProfiles) {
        allCandidateProfiles.forEach { profile ->
            if (profile.latitude == null || profile.longitude == null) {
                val cacheKey = "${profile.id}|${profile.formattedLocation}"
                if (!geocodedCache.containsKey(cacheKey)) {
                    geocodedCache[cacheKey] = AddressGeocoder.geocodeBusinessAddress(
                        context = context,
                        businessAddress = profile.businessAddress.orEmpty(),
                        city = profile.city,
                        countryCode = profile.countryCode,
                    )
                }
            }
        }
    }

    val markers = remember(
        allCandidateProfiles,
        snapshot,
        query,
        activeCategory,
        allServicesLabel,
        userLocation,
        countryCenter,
        geocodedCache.size,
    ) {
        val trimmedQuery = query.trim().lowercase(Locale.ENGLISH)
        val ownId = snapshot?.ownProfile?.id
        val refPoint = userLocation ?: countryCenter

        allCandidateProfiles.mapNotNull { profile ->
            val category = ServiceTaxonomy.findBySlug(profile.categorySlug)
            val familyName = category?.family ?: profile.categoryName

            val matchesCategory = activeCategory == allServicesLabel ||
                familyName.equals(activeCategory, ignoreCase = true) ||
                profile.categoryName.equals(activeCategory, ignoreCase = true)

            val matchesQuery = trimmedQuery.isBlank() ||
                profile.businessName.lowercase(Locale.ENGLISH).contains(trimmedQuery) ||
                profile.categoryName.lowercase(Locale.ENGLISH).contains(trimmedQuery) ||
                familyName.lowercase(Locale.ENGLISH).contains(trimmedQuery) ||
                profile.city.lowercase(Locale.ENGLISH).contains(trimmedQuery) ||
                profile.businessAddress.orEmpty().lowercase(Locale.ENGLISH).contains(trimmedQuery) ||
                profile.description.orEmpty().lowercase(Locale.ENGLISH).contains(trimmedQuery)

            if (!matchesCategory || !matchesQuery) return@mapNotNull null

            val cacheKey = "${profile.id}|${profile.formattedLocation}"
            val coords = if (profile.latitude != null && profile.longitude != null) {
                GeocodeResult(
                    latitude = profile.latitude,
                    longitude = profile.longitude,
                    isExactAddress = !profile.businessAddress.isNullOrBlank(),
                )
            } else {
                geocodedCache[cacheKey] ?: GeographyCatalog.approximateCoordinates(
                    profile.countryCode,
                    profile.city,
                ).let { GeocodeResult(it.first, it.second, isExactAddress = false) }
            }

            val distance = AddressGeocoder.haversineDistanceKm(
                lat1 = refPoint.first,
                lon1 = refPoint.second,
                lat2 = coords.latitude,
                lon2 = coords.longitude,
            )

            MapProviderMarker(
                profile = profile,
                familyName = familyName,
                latitude = coords.latitude,
                longitude = coords.longitude,
                isExactAddress = coords.isExactAddress,
                isOwnPreview = profile.id == ownId && profile.listingStatus != "published",
                distanceKm = distance,
            )
        }.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
    }

    val selectedMarker = remember(markers, selectedProviderId) {
        markers.firstOrNull { it.profile.id == selectedProviderId }
    }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val resolved = AddressGeocoder.fetchLastKnownCoarseLocation(context)
            if (resolved != null) {
                userLocation = resolved
                locationNotice = null
                mapViewRef?.controller?.animateTo(GeoPoint(resolved.first, resolved.second), 13.5, 450L)
            } else {
                locationNotice = "Location enabled; waiting for a coarse fix on this device."
            }
        } else {
            val countryName = GeographyCatalog.getCountryLabel(selectedCountryCode.ifBlank { "ZW" })
            locationNotice = "Centered on $countryName. Allow approximate location to sort by distance."
        }
    }

    // Recenter when discovery country changes cleanly if no specific marker is focused
    LaunchedEffect(selectedCountryCode) {
        if (selectedMarker == null && userLocation == null) {
            val target = GeographyCatalog.approximateCoordinates(selectedCountryCode.ifBlank { "ZW" })
            mapViewRef?.controller?.animateTo(GeoPoint(target.first, target.second), 11.5, 450L)
        }
    }

    // Center softly onto the nearest marker once markers load for the first time
    LaunchedEffect(markers.firstOrNull()?.profile?.id) {
        val first = markers.firstOrNull()
        if (first != null && selectedMarker == null) {
            mapViewRef?.controller?.animateTo(GeoPoint(first.latitude, first.longitude), 12.5, 450L)
        }
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            val startTarget = userLocation ?: countryCenter
            controller.setZoom(if (userLocation != null) 13.0 else 11.5)
            controller.setCenter(GeoPoint(startTarget.first, startTarget.second))
        }
    }.also { mapViewRef = it }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    val limeArgb = TrolleyTheme.colors.lime.toArgb()
    val limeDeepArgb = TrolleyTheme.colors.limeDeep.toArgb()
    val orangeArgb = TrolleyTheme.colors.orange.toArgb()
    val whiteArgb = Color.White.toArgb()
    val userDotArgb = MaterialTheme.colorScheme.secondary.toArgb()

    val darkTileFilter = remember {
        ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    -0.88f, 0f, 0f, 0f, 240f,
                    0f, -0.88f, 0f, 0f, 240f,
                    0f, 0f, -0.88f, 0f, 240f,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("provider_map_view"),
    ) {
        AndroidView(
            factory = { mapView },
            update = { view ->
                view.overlayManager.tilesOverlay.setColorFilter(if (darkTheme) darkTileFilter else null)
                view.overlays.clear()

                view.overlays.add(
                    MapEventsOverlay(
                        object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                selectedProviderId = null
                                return true
                            }

                            override fun longPressHelper(p: GeoPoint?): Boolean = false
                        }
                    )
                )

                userLocation?.let { (lat, lon) ->
                    val userMarker = Marker(view).apply {
                        position = GeoPoint(lat, lon)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "Your approximate area"
                        icon = createUserLocationDrawable(context, userDotArgb, whiteArgb)
                        setOnMarkerClickListener { _, _ -> true }
                    }
                    view.overlays.add(userMarker)
                }

                markers.forEach { markerModel ->
                    val isSelected = markerModel.profile.id == selectedProviderId
                    val fillColor = when {
                        isSelected -> orangeArgb
                        markerModel.isOwnPreview -> orangeArgb
                        else -> limeArgb
                    }
                    val borderColor = if (isSelected || markerModel.isOwnPreview) whiteArgb else limeDeepArgb

                    val mapMarker = Marker(view).apply {
                        position = GeoPoint(markerModel.latitude, markerModel.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = markerModel.profile.businessName
                        snippet = markerModel.profile.categoryName
                        icon = createPinDrawable(
                            context = context,
                            fillColor = fillColor,
                            borderColor = borderColor,
                            innerDotColor = if (isSelected || markerModel.isOwnPreview) whiteArgb else limeDeepArgb,
                            selected = isSelected,
                        )
                        setOnMarkerClickListener { clickedMarker, _ ->
                            selectedProviderId = markerModel.profile.id
                            view.controller.animateTo(clickedMarker.position, 14.5.coerceAtLeast(view.zoomLevelDouble), 350L)
                            true
                        }
                    }
                    view.overlays.add(mapMarker)
                }

                view.invalidate()
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Top floating status bar on Map
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                    shadowElevation = 4.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        if (providersState is UiState.Loading && isSignedIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = "Loading nearby pros…",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = TrolleyTheme.colors.orange,
                                modifier = Modifier.size(15.dp),
                            )
                            val countText = when {
                                !isSignedIn -> "Sign in for live pros"
                                markers.isEmpty() -> "0 pros in view"
                                markers.size == 1 -> "1 nearby pro"
                                else -> "${markers.size} nearby pros"
                            }
                            Text(
                                text = countText,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("map_marker_count_text"),
                            )
                        }
                    }
                }

                val nearMeInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = {
                        if (AddressGeocoder.hasCoarseLocationPermission(context)) {
                            val resolved = AddressGeocoder.fetchLastKnownCoarseLocation(context)
                            if (resolved != null) {
                                userLocation = resolved
                                locationNotice = null
                                mapView.controller.animateTo(GeoPoint(resolved.first, resolved.second), 13.5, 450L)
                            } else {
                                locationNotice = "Location permission granted; using ${GeographyCatalog.getCountryLabel(selectedCountryCode.ifBlank { "ZW" })} center until GPS fixes."
                            }
                        } else {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    },
                    interactionSource = nearMeInteraction,
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .trolleySpringPress(nearMeInteraction)
                        .minimumInteractiveComponentSize()
                        .testTag("map_near_me_button"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MyLocation,
                            contentDescription = stringResource(R.string.map_near_me),
                            tint = if (userLocation != null) TrolleyTheme.colors.limeAccent else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.map_near_me),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            if (locationNotice != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                ) {
                    Text(
                        text = locationNotice!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = TrolleyTheme.colors.mutedInk,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }

        // Right-edge Zoom controls
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                shadowElevation = 4.dp,
            ) {
                Column {
                    IconButton(
                        onClick = { mapView.controller.zoomIn() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("map_zoom_in_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Zoom in map",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    HorizontalDivider(
                        color = TrolleyTheme.colors.hairline,
                        modifier = Modifier.width(36.dp).align(Alignment.CenterHorizontally),
                    )
                    IconButton(
                        onClick = { mapView.controller.zoomOut() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("map_zoom_out_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "Zoom out map",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // Bottom Floating Area: Sign-In Prompt, Empty State Hint, or Selected Provider Profile Card
        val bottomInset = contentPadding.calculateBottomPadding() + 14.dp
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = bottomInset),
        ) {
            if (selectedMarker == null) {
                when {
                    !isSignedIn -> {
                        MapStatusBannerCard(
                            title = "Explore nearby service pros",
                            body = stringResource(R.string.map_sign_in_prompt),
                            actionLabel = stringResource(R.string.map_sign_in_action),
                            onAction = onSignInClick,
                            testTag = "map_sign_in_banner",
                        )
                    }
                    providersState is UiState.Error -> {
                        MapStatusBannerCard(
                            title = "Could not load nearby pros",
                            body = providersState.message,
                            actionLabel = null,
                            onAction = {},
                            testTag = "map_error_banner",
                        )
                    }
                    providersState is UiState.Success && markers.isEmpty() -> {
                        MapStatusBannerCard(
                            title = stringResource(R.string.map_empty_title),
                            body = stringResource(R.string.map_empty_body),
                            actionLabel = stringResource(R.string.list_your_business),
                            onAction = onEditOwnListingClick,
                            testTag = "map_empty_banner",
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = selectedMarker != null,
                enter = slideInVertically { it / 2 } + fadeIn(),
                exit = slideOutVertically { it / 2 } + fadeOut(),
            ) {
                selectedMarker?.let { marker ->
                    val summary = snapshot?.ratingSummaries?.get(marker.profile.id)
                    ProviderMarkerProfileCard(
                        marker = marker,
                        ratingSummary = summary,
                        onClose = { selectedProviderId = null },
                        onRequestBooking = { onRequestBooking(marker.profile) },
                        onViewProfile = { onViewProviderProfile(marker.profile) },
                        onOpenDirections = {
                            val label = Uri.encode("${marker.profile.businessName}, ${marker.profile.formattedLocation}")
                            val geoUri = Uri.parse("geo:${marker.latitude},${marker.longitude}?q=${marker.latitude},${marker.longitude}($label)")
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, geoUri))
                            }
                        },
                        onContactOrEdit = {
                            if (marker.isOwnPreview) {
                                onEditOwnListingClick()
                            } else {
                                onMessageProvider(marker.profile)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MapStatusBannerCard(
    title: String,
    body: String,
    actionLabel: String?,
    onAction: () -> Unit,
    testTag: String,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
            if (!actionLabel.isNullOrBlank()) {
                val actionInteraction = remember { MutableInteractionSource() }
                Button(
                    onClick = onAction,
                    interactionSource = actionInteraction,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .trolleySpringPress(actionInteraction)
                        .minimumInteractiveComponentSize(),
                ) {
                    Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ProviderMarkerProfileCard(
    marker: MapProviderMarker,
    ratingSummary: ProviderRatingSummary?,
    onClose: () -> Unit,
    onRequestBooking: () -> Unit,
    onViewProfile: () -> Unit,
    onOpenDirections: () -> Unit,
    onContactOrEdit: () -> Unit,
) {
    val profile = marker.profile
    val countryLabel = remember(profile.countryCode) {
        GeographyCatalog.getCountryLabel(profile.countryCode)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
        shadowElevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("map_provider_profile_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (marker.isOwnPreview) TrolleyTheme.colors.orangeSoft else TrolleyTheme.colors.limeSoft,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Storefront,
                        contentDescription = null,
                        tint = if (marker.isOwnPreview) TrolleyTheme.colors.orange else TrolleyTheme.colors.limeAccent,
                        modifier = Modifier.padding(10.dp),
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
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (marker.isOwnPreview) TrolleyTheme.colors.orangeSoft else TrolleyTheme.colors.lime,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VerifiedUser,
                                    contentDescription = null,
                                    tint = if (marker.isOwnPreview) TrolleyTheme.colors.orange else TrolleyTheme.colors.limeDeep,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = if (marker.isOwnPreview) {
                                        "Your ${profile.listingStatus.replace('_', ' ')} (only visible to you)"
                                    } else {
                                        "Published pro"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (marker.isOwnPreview) TrolleyTheme.colors.orange else TrolleyTheme.colors.limeDeep,
                                )
                            }
                        }
                    }

                    Text(
                        text = profile.businessName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("map_card_business_name"),
                    )

                    Text(
                        text = "${profile.categoryName} · ${marker.familyName}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TrolleyTheme.colors.limeAccent,
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("close_provider_card_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close provider card",
                        tint = TrolleyTheme.colors.mutedInk,
                    )
                }
            }

            // Business Address & Distance Row
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = TrolleyTheme.colors.orange,
                        modifier = Modifier.size(16.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        val addressLine = profile.businessAddress?.takeIf { it.isNotBlank() }
                        Text(
                            text = if (addressLine != null) {
                                "$addressLine, ${profile.city}, $countryLabel"
                            } else {
                                "${profile.city}, $countryLabel (city center)"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("map_card_address"),
                        )
                        marker.distanceKm?.let { km ->
                            Text(
                                text = AddressGeocoder.formatDistanceKm(km),
                                style = MaterialTheme.typography.bodySmall,
                                color = TrolleyTheme.colors.mutedInk,
                            )
                        }
                    }
                }
            }

            if (!profile.description.isNullOrBlank()) {
                Text(
                    text = profile.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TrolleyTheme.colors.mutedInk,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            StarRatingBadge(summary = ratingSummary)

            val profileInteraction = remember { MutableInteractionSource() }
            OutlinedButton(
                onClick = onViewProfile,
                interactionSource = profileInteraction,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                modifier = Modifier
                    .fillMaxWidth()
                    .trolleySpringPress(profileInteraction)
                    .minimumInteractiveComponentSize()
                    .testTag("map_card_profile_button"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.VerifiedUser,
                    contentDescription = null,
                    tint = TrolleyTheme.colors.orange,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.view_provider_profile))
            }

            val bookInteraction = remember { MutableInteractionSource() }
            Button(
                onClick = onRequestBooking,
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
                    .testTag("map_card_book_button"),
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
                val directionsInteraction = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = onOpenDirections,
                    interactionSource = directionsInteraction,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                    modifier = Modifier
                        .weight(1f)
                        .trolleySpringPress(directionsInteraction)
                        .minimumInteractiveComponentSize()
                        .testTag("map_card_directions_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Directions,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.map_open_directions))
                }

                val primaryInteraction = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = onContactOrEdit,
                    interactionSource = primaryInteraction,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                    modifier = Modifier
                        .weight(1f)
                        .trolleySpringPress(primaryInteraction)
                        .minimumInteractiveComponentSize()
                        .testTag("map_card_primary_action_button"),
                ) {
                    Icon(
                        imageVector = if (marker.isOwnPreview) Icons.Rounded.Edit else Icons.Rounded.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (marker.isOwnPreview) {
                            stringResource(R.string.map_edit_listing)
                        } else {
                            "Message"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun createPinDrawable(
    context: Context,
    fillColor: Int,
    borderColor: Int,
    innerDotColor: Int,
    selected: Boolean,
): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val widthPx = ((if (selected) 40 else 34) * density).toInt()
    val heightPx = ((if (selected) 50 else 42) * density).toInt()
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cx = widthPx / 2f
    val circleRadius = widthPx * 0.38f
    val cy = circleRadius + 3f * density

    val pinPath = Path().apply {
        addCircle(cx, cy, circleRadius, Path.Direction.CW)
        moveTo(cx - circleRadius * 0.65f, cy + circleRadius * 0.55f)
        lineTo(cx, heightPx - 2f * density)
        lineTo(cx + circleRadius * 0.65f, cy + circleRadius * 0.55f)
        close()
    }

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.4f * density
        color = borderColor
    }
    val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = innerDotColor
    }

    canvas.drawPath(pinPath, fillPaint)
    canvas.drawCircle(cx, cy, circleRadius, strokePaint)
    canvas.drawCircle(cx, cy, circleRadius * 0.34f, innerPaint)

    return BitmapDrawable(context.resources, bitmap)
}

private fun createUserLocationDrawable(
    context: Context,
    fillColor: Int,
    borderColor: Int,
): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val sizePx = (24 * density).toInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val c = sizePx / 2f

    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
        alpha = 60
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = borderColor
    }
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }

    canvas.drawCircle(c, c, sizePx * 0.48f, haloPaint)
    canvas.drawCircle(c, c, sizePx * 0.32f, borderPaint)
    canvas.drawCircle(c, c, sizePx * 0.22f, dotPaint)

    return BitmapDrawable(context.resources, bitmap)
}
