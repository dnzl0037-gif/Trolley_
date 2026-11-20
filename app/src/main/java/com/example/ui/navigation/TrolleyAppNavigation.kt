package com.example.ui.navigation

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.data.firebase.AuthManager
import com.example.data.firebase.TrolleyRepository
import com.example.data.firebase.authStateFlow
import com.example.data.local.AppPreferences
import com.example.data.model.BookingRequest
import com.example.data.model.ProviderProfile
import com.example.data.model.UiState
import com.example.ui.components.BookingRequestSheet
import com.example.ui.components.ConsentBannerCard
import com.example.ui.components.PolicyModalDialog
import com.example.ui.components.PolicyModalType
import com.example.ui.components.TrolleySplashOverlay
import com.example.ui.components.TrolleyTopBar
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BookingsScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.HowAndTrustScreen
import com.example.ui.screens.ProviderDetailScreen
import com.example.ui.screens.ProviderScreen
import com.example.ui.screens.UserProfileScreen
import com.example.ui.theme.TrolleyTheme
import com.example.ui.viewmodel.AccountProviderViewModel
import com.example.ui.viewmodel.BookingsViewModel
import com.example.ui.viewmodel.DiscoveryViewModel
import com.example.ui.viewmodel.NearbyProvidersSnapshot
import com.example.ui.viewmodel.NearbyProvidersViewModel
import com.example.ui.viewmodel.ProviderDetailViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.serialization.Serializable

@Serializable
data object DiscoverRoute

@Serializable
data object BookingsRoute

@Serializable
data class ProviderDetailRoute(val providerId: String)

@Serializable
data object HowTrustRoute

@Serializable
data object MyBusinessRoute

@Serializable
data object ProfileRoute

private data class NavTabItem<T : Any>(
    val route: T,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String,
)

@Composable
fun TrolleyAppNavigation() {
    val context = LocalContext.current
    val app = context.applicationContext as Application

    val discoveryViewModel: DiscoveryViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val appInstance = checkNotNull(this[APPLICATION_KEY])
                DiscoveryViewModel(AppPreferences(appInstance))
            }
        },
    )

    val discoveryState by discoveryViewModel.uiState.collectAsStateWithLifecycle()
    val currentUser by remember { Firebase.auth.authStateFlow() }
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    LaunchedEffect(Unit) {
        AuthManager.attemptAutoSignIn(context)
    }

    var activePolicyModal by remember { mutableStateOf<PolicyModalType?>(null) }
    var showSplash by rememberSaveable { mutableStateOf(true) }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val tabs = remember {
        listOf(
            NavTabItem(DiscoverRoute, R.string.nav_discover, Icons.Rounded.Explore, Icons.Outlined.Explore, "nav_discover"),
            NavTabItem(BookingsRoute, R.string.nav_bookings, Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth, "nav_bookings"),
            NavTabItem(HowTrustRoute, R.string.nav_how_trust, Icons.Rounded.Shield, Icons.Outlined.Shield, "nav_how_trust"),
            NavTabItem(MyBusinessRoute, R.string.nav_my_business, Icons.Rounded.Storefront, Icons.Outlined.Storefront, "nav_my_business"),
            NavTabItem(ProfileRoute, R.string.nav_profile, Icons.Rounded.Person, Icons.Outlined.Person, "nav_profile"),
        )
    }

    var activeBookingSheetTarget by remember { mutableStateOf<BookingSheetState?>(null) }

    fun navigateToTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openContactEmail() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:gmrboy2009@gmail.com")
            putExtra(Intent.EXTRA_SUBJECT, "Trolley App Inquiry")
        }
        runCatching { context.startActivity(intent) }
    }

    TrolleyTheme(darkTheme = discoveryState.preferences.darkTheme) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TrolleyTopBar(
                        darkTheme = discoveryState.preferences.darkTheme,
                        isSignedIn = currentUser != null,
                        onToggleTheme = {
                            discoveryViewModel.setDarkTheme(!discoveryState.preferences.darkTheme)
                        },
                        onOpenAccountOrBusiness = { navigateToTab(MyBusinessRoute) },
                        onOpenProfile = { navigateToTab(ProfileRoute) },
                    )
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                        ) {
                            tabs.forEach { tab ->
                                val selected = currentDestination?.hasRoute(tab.route::class) == true
                                val label = stringResource(tab.labelRes)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = label,
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = TrolleyTheme.colors.limeDeep,
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        indicatorColor = TrolleyTheme.colors.lime,
                                        unselectedIconColor = TrolleyTheme.colors.mutedInk,
                                        unselectedTextColor = TrolleyTheme.colors.mutedInk,
                                    ),
                                    modifier = Modifier.testTag(tab.tag),
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding()),
                ) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ) {
                            tabs.forEach { tab ->
                                val selected = currentDestination?.hasRoute(tab.route::class) == true
                                val label = stringResource(tab.labelRes)
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = label,
                                        )
                                    },
                                    label = { Text(label) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = TrolleyTheme.colors.limeDeep,
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        indicatorColor = TrolleyTheme.colors.lime,
                                        unselectedIconColor = TrolleyTheme.colors.mutedInk,
                                        unselectedTextColor = TrolleyTheme.colors.mutedInk,
                                    ),
                                    modifier = Modifier.testTag(tab.tag),
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = DiscoverRoute,
                            enterTransition = {
                                fadeIn(animationSpec = tween(220)) +
                                    slideInHorizontally(animationSpec = tween(220)) { it / 16 }
                            },
                            exitTransition = {
                                fadeOut(animationSpec = tween(180)) +
                                    slideOutHorizontally(animationSpec = tween(180)) { -it / 16 }
                            },
                            popEnterTransition = {
                                fadeIn(animationSpec = tween(220)) +
                                    slideInHorizontally(animationSpec = tween(220)) { -it / 16 }
                            },
                            popExitTransition = {
                                fadeOut(animationSpec = tween(180)) +
                                    slideOutHorizontally(animationSpec = tween(180)) { it / 16 }
                            },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            composable<DiscoverRoute> {
                                val user = currentUser
                                val nearbyProvidersState: UiState<NearbyProvidersSnapshot>? = if (user != null) {
                                    key(user.uid) {
                                        val nearbyViewModel: NearbyProvidersViewModel = viewModel(
                                            key = "nearby_${user.uid}",
                                            factory = viewModelFactory {
                                                initializer {
                                                    val appInstance = checkNotNull(this[APPLICATION_KEY])
                                                    val databaseId = appInstance.getString(R.string.firestore_database_id)
                                                    val db = FirebaseFirestore.getInstance(databaseId)
                                                    NearbyProvidersViewModel(
                                                        repository = TrolleyRepository(db),
                                                        currentUserId = user.uid,
                                                    )
                                                }
                                            },
                                        )
                                        val state by nearbyViewModel.providersState.collectAsStateWithLifecycle()
                                        state
                                    }
                                } else {
                                    null
                                }

                                DiscoverScreen(
                                    uiState = discoveryState,
                                    isSignedIn = user != null,
                                    nearbyProvidersState = nearbyProvidersState,
                                    onQueryChange = discoveryViewModel::updateQuery,
                                    onDiscoveryCountryChange = discoveryViewModel::setDiscoveryCountry,
                                    onChooseCategory = discoveryViewModel::chooseCategory,
                                    onSelectServiceType = discoveryViewModel::selectServiceType,
                                    onClearFilters = discoveryViewModel::clearFilters,
                                    onViewModeChange = discoveryViewModel::setViewMode,
                                    onToggleShowAll = discoveryViewModel::toggleShowAllTaxonomy,
                                    onBrowseAll = discoveryViewModel::browseAllServices,
                                    onListBusinessClick = { navigateToTab(MyBusinessRoute) },
                                    onSignInClick = { navigateToTab(ProfileRoute) },
                                    onRequestBooking = { provider ->
                                        if (user == null) {
                                            navigateToTab(ProfileRoute)
                                        } else {
                                            activeBookingSheetTarget = BookingSheetState(initialProvider = provider)
                                        }
                                    },
                                    onViewProviderProfile = { provider ->
                                        if (user == null) {
                                            navigateToTab(ProfileRoute)
                                        } else {
                                            navController.navigate(ProviderDetailRoute(provider.id))
                                        }
                                    },
                                    onContactClick = ::openContactEmail,
                                    contentPadding = innerPadding,
                                )
                            }

                            composable<ProviderDetailRoute> { entry ->
                                val route = entry.toRoute<ProviderDetailRoute>()
                                val user = currentUser
                                if (user == null) {
                                    AuthScreen(
                                        onAuthSuccess = {},
                                        onOpenPrivacy = { activePolicyModal = PolicyModalType.PRIVACY },
                                        onOpenTerms = { activePolicyModal = PolicyModalType.TERMS },
                                        onContactClick = ::openContactEmail,
                                        contentPadding = innerPadding,
                                    )
                                } else {
                                    key("${user.uid}_${route.providerId}") {
                                        val detailViewModel: ProviderDetailViewModel = viewModel(
                                            key = "provider_detail_${user.uid}_${route.providerId}",
                                            factory = viewModelFactory {
                                                initializer {
                                                    val appInstance = checkNotNull(this[APPLICATION_KEY])
                                                    val databaseId = appInstance.getString(R.string.firestore_database_id)
                                                    val db = FirebaseFirestore.getInstance(databaseId)
                                                    ProviderDetailViewModel(
                                                        repository = TrolleyRepository(db),
                                                        providerId = route.providerId,
                                                        currentUserId = user.uid,
                                                        reviewerDisplayName = user.displayName.orEmpty(),
                                                    )
                                                }
                                            },
                                        )
                                        ProviderDetailScreen(
                                            viewModel = detailViewModel,
                                            currentUserId = user.uid,
                                            onBackClick = { navController.popBackStack() },
                                            onRequestBooking = { provider ->
                                                activeBookingSheetTarget = BookingSheetState(initialProvider = provider)
                                            },
                                            contentPadding = innerPadding,
                                        )
                                    }
                                }
                            }

                            composable<BookingsRoute> {
                                val user = currentUser
                                if (user == null) {
                                    AuthScreen(
                                        onAuthSuccess = {},
                                        onOpenPrivacy = { activePolicyModal = PolicyModalType.PRIVACY },
                                        onOpenTerms = { activePolicyModal = PolicyModalType.TERMS },
                                        onContactClick = ::openContactEmail,
                                        contentPadding = innerPadding,
                                    )
                                } else {
                                    key(user.uid) {
                                        val bookingsViewModel: BookingsViewModel = viewModel(
                                            key = "bookings_${user.uid}",
                                            factory = viewModelFactory {
                                                initializer {
                                                    val appInstance = checkNotNull(this[APPLICATION_KEY])
                                                    val databaseId = appInstance.getString(R.string.firestore_database_id)
                                                    val db = FirebaseFirestore.getInstance(databaseId)
                                                    BookingsViewModel(
                                                        repository = TrolleyRepository(db),
                                                        currentUserId = user.uid,
                                                        customerDisplayName = user.displayName.orEmpty(),
                                                    )
                                                }
                                            },
                                        )
                                        BookingsScreen(
                                            viewModel = bookingsViewModel,
                                            onOpenNewBookingSheet = {
                                                activeBookingSheetTarget = BookingSheetState()
                                            },
                                            onRescheduleBooking = { booking ->
                                                activeBookingSheetTarget = BookingSheetState(existingBooking = booking)
                                            },
                                            onViewProviderProfile = { providerId ->
                                                navController.navigate(ProviderDetailRoute(providerId))
                                            },
                                            onBrowseProvidersClick = { navigateToTab(DiscoverRoute) },
                                            contentPadding = innerPadding,
                                        )
                                    }
                                }
                            }

                            composable<HowTrustRoute> {
                                HowAndTrustScreen(
                                    onOpenPrivacy = { activePolicyModal = PolicyModalType.PRIVACY },
                                    onOpenTerms = { activePolicyModal = PolicyModalType.TERMS },
                                    onContactClick = ::openContactEmail,
                                    contentPadding = innerPadding,
                                )
                            }

                            composable<MyBusinessRoute> {
                                val user = currentUser
                                if (user == null) {
                                    AuthScreen(
                                        onAuthSuccess = {},
                                        onOpenPrivacy = { activePolicyModal = PolicyModalType.PRIVACY },
                                        onOpenTerms = { activePolicyModal = PolicyModalType.TERMS },
                                        onContactClick = ::openContactEmail,
                                        contentPadding = innerPadding,
                                    )
                                } else {
                                    key(user.uid) {
                                        val providerViewModel: AccountProviderViewModel = viewModel(
                                            key = user.uid,
                                            factory = viewModelFactory {
                                                initializer {
                                                    val appInstance = checkNotNull(this[APPLICATION_KEY])
                                                    val databaseId = appInstance.getString(R.string.firestore_database_id)
                                                    val db = FirebaseFirestore.getInstance(databaseId)
                                                    AccountProviderViewModel(
                                                        repository = TrolleyRepository(db),
                                                        currentUserId = user.uid,
                                                        initialDisplayName = user.displayName.orEmpty(),
                                                        initialEmail = user.email,
                                                        initialPhotoUrl = user.photoUrl?.toString(),
                                                    )
                                                }
                                            },
                                        )
                                        ProviderScreen(
                                            viewModel = providerViewModel,
                                            userDisplayName = user.displayName.orEmpty(),
                                            userEmail = user.email,
                                            onSignedOut = {},
                                            contentPadding = innerPadding,
                                        )
                                    }
                                }
                            }

                            composable<ProfileRoute> {
                                val user = currentUser
                                if (user == null) {
                                    AuthScreen(
                                        onAuthSuccess = {},
                                        onOpenPrivacy = { activePolicyModal = PolicyModalType.PRIVACY },
                                        onOpenTerms = { activePolicyModal = PolicyModalType.TERMS },
                                        onContactClick = ::openContactEmail,
                                        contentPadding = innerPadding,
                                    )
                                } else {
                                    key(user.uid) {
                                        val profileViewModel: AccountProviderViewModel = viewModel(
                                            key = user.uid,
                                            factory = viewModelFactory {
                                                initializer {
                                                    val appInstance = checkNotNull(this[APPLICATION_KEY])
                                                    val databaseId = appInstance.getString(R.string.firestore_database_id)
                                                    val db = FirebaseFirestore.getInstance(databaseId)
                                                    AccountProviderViewModel(
                                                        repository = TrolleyRepository(db),
                                                        currentUserId = user.uid,
                                                        initialDisplayName = user.displayName.orEmpty(),
                                                        initialEmail = user.email,
                                                        initialPhotoUrl = user.photoUrl?.toString(),
                                                    )
                                                }
                                            },
                                        )
                                        UserProfileScreen(
                                            firebaseUser = user,
                                            viewModel = profileViewModel,
                                            onSignedOut = {},
                                            onContactClick = ::openContactEmail,
                                            contentPadding = innerPadding,
                                        )
                                    }
                                }
                            }
                        }

                        if (discoveryState.preferences.consentChoice == null) {
                            ConsentBannerCard(
                                onReject = { discoveryViewModel.setConsentChoice("rejected") },
                                onAccept = { discoveryViewModel.setConsentChoice("accepted") },
                                onLearnMore = { activePolicyModal = PolicyModalType.PRIVACY },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(
                                        start = 14.dp,
                                        end = 14.dp,
                                        bottom = innerPadding.calculateBottomPadding() + 12.dp,
                                    ),
                            )
                        }
                    }
                }
            }

            val signedInUser = currentUser
            val sheetTarget = activeBookingSheetTarget
            if (signedInUser != null && sheetTarget != null) {
                key(signedInUser.uid) {
                    val bookingsViewModel: BookingsViewModel = viewModel(
                        key = "bookings_${signedInUser.uid}",
                        factory = viewModelFactory {
                            initializer {
                                val appInstance = checkNotNull(this[APPLICATION_KEY])
                                val databaseId = appInstance.getString(R.string.firestore_database_id)
                                val db = FirebaseFirestore.getInstance(databaseId)
                                BookingsViewModel(
                                    repository = TrolleyRepository(db),
                                    currentUserId = signedInUser.uid,
                                    customerDisplayName = signedInUser.displayName.orEmpty(),
                                )
                            }
                        },
                    )
                    val bookableState by bookingsViewModel.bookableProvidersState.collectAsStateWithLifecycle()
                    val isBusy by bookingsViewModel.isBusy.collectAsStateWithLifecycle()
                    val notice by bookingsViewModel.notice.collectAsStateWithLifecycle()
                    val bookableProviders = (bookableState as? UiState.Success)?.data.orEmpty()

                    BookingRequestSheet(
                        initialProvider = sheetTarget.initialProvider,
                        existingBooking = sheetTarget.existingBooking,
                        bookableProviders = bookableProviders,
                        isBusy = isBusy,
                        errorMessage = notice?.takeIf { it.isError }?.message,
                        onDismiss = {
                            bookingsViewModel.clearNotice()
                            activeBookingSheetTarget = null
                        },
                        onConfirmNewBooking = { provider, dateIso, time24h, notes ->
                            bookingsViewModel.submitBookingRequest(
                                provider = provider,
                                scheduledDate = dateIso,
                                scheduledTime = time24h,
                                notes = notes,
                                onBooked = {
                                    activeBookingSheetTarget = null
                                    navigateToTab(BookingsRoute)
                                },
                            )
                        },
                        onConfirmReschedule = { booking, dateIso, time24h, notes ->
                            bookingsViewModel.rescheduleBooking(
                                booking = booking,
                                scheduledDate = dateIso,
                                scheduledTime = time24h,
                                notes = notes,
                                onRescheduled = {
                                    activeBookingSheetTarget = null
                                },
                            )
                        },
                    )
                }
            }

            if (activePolicyModal != null) {
                PolicyModalDialog(
                    type = activePolicyModal!!,
                    onDismiss = { activePolicyModal = null },
                )
            }

            if (showSplash) {
                TrolleySplashOverlay(onFinished = { showSplash = false })
            }
        }
    }
}

private data class BookingSheetState(
    val initialProvider: ProviderProfile? = null,
    val existingBooking: BookingRequest? = null,
)
