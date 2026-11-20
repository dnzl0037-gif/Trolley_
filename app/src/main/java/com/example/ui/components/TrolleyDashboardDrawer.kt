package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.TrolleyTheme
import com.google.firebase.auth.FirebaseUser

enum class DashboardDestinationKey {
    APP_SETTINGS,
    ACCOUNT_SETTINGS,
    PROFILE_MANAGEMENT,
    MESSAGES_INBOX,
    BILLING_PLANS,
    ABOUT_APP,
    NONE,
}

@Composable
fun TrolleyDashboardDrawer(
    currentUser: FirebaseUser?,
    activeKey: DashboardDestinationKey,
    unreadMessagesCount: Int = 0,
    onSelectAppSettings: () -> Unit,
    onSelectUserAccountSettings: () -> Unit,
    onSelectProfileManagement: () -> Unit,
    onSelectMessagesInbox: () -> Unit,
    onSelectBillingPlans: () -> Unit,
    onSelectAboutApp: () -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .width(308.dp)
            .testTag("dashboard_side_menu"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Drawer Header
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (currentUser?.photoUrl != null) {
                        AsyncImage(
                            model = currentUser.photoUrl,
                            contentDescription = currentUser.displayName ?: "Account avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape),
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.trolley_logo),
                            contentDescription = stringResource(R.string.app_name),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentUser?.displayName?.ifBlank { "Trolley Member" }
                                ?: stringResource(R.string.drawer_header_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = currentUser?.email ?: "Sign in with Google to sync",
                            style = MaterialTheme.typography.bodySmall,
                            color = TrolleyTheme.colors.mutedInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "WORKSPACE & CONTROLS",
                style = MaterialTheme.typography.labelSmall,
                color = TrolleyTheme.colors.orange,
                modifier = Modifier.padding(horizontal = 10.dp),
            )

            val itemColors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = TrolleyTheme.colors.lime,
                selectedIconColor = TrolleyTheme.colors.limeDeep,
                selectedTextColor = TrolleyTheme.colors.limeDeep,
                unselectedIconColor = TrolleyTheme.colors.mutedInk,
                unselectedTextColor = MaterialTheme.colorScheme.onSurface,
            )

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.drawer_app_settings)) },
                selected = activeKey == DashboardDestinationKey.APP_SETTINGS,
                onClick = onSelectAppSettings,
                icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                colors = itemColors,
                modifier = Modifier.testTag("drawer_item_app_settings"),
            )

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.drawer_account_settings)) },
                selected = activeKey == DashboardDestinationKey.ACCOUNT_SETTINGS,
                onClick = onSelectUserAccountSettings,
                icon = { Icon(Icons.Rounded.ManageAccounts, contentDescription = null) },
                colors = itemColors,
                modifier = Modifier.testTag("drawer_item_account_settings"),
            )

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.drawer_profile_management)) },
                selected = activeKey == DashboardDestinationKey.PROFILE_MANAGEMENT,
                onClick = onSelectProfileManagement,
                icon = { Icon(Icons.Rounded.Badge, contentDescription = null) },
                colors = itemColors,
                modifier = Modifier.testTag("drawer_item_profile_management"),
            )

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.drawer_messages_inbox)) },
                selected = activeKey == DashboardDestinationKey.MESSAGES_INBOX,
                onClick = onSelectMessagesInbox,
                icon = { Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null) },
                badge = if (unreadMessagesCount > 0) {
                    {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = TrolleyTheme.colors.orange.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = "$unreadMessagesCount NEW",
                                style = MaterialTheme.typography.labelSmall,
                                color = TrolleyTheme.colors.orange,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                } else null,
                colors = itemColors,
                modifier = Modifier.testTag("drawer_item_messages_inbox"),
            )

            HorizontalDivider(
                color = TrolleyTheme.colors.hairline,
                modifier = Modifier.padding(vertical = 4.dp),
            )

            Text(
                text = "PROVIDER MONETIZATION",
                style = MaterialTheme.typography.labelSmall,
                color = TrolleyTheme.colors.orange,
                modifier = Modifier.padding(horizontal = 10.dp),
            )

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.drawer_billing_plans)) },
                selected = activeKey == DashboardDestinationKey.BILLING_PLANS,
                onClick = onSelectBillingPlans,
                icon = { Icon(Icons.Rounded.CreditCard, contentDescription = null) },
                badge = {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = TrolleyTheme.colors.orange.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = "30% OFF YR",
                            style = MaterialTheme.typography.labelSmall,
                            color = TrolleyTheme.colors.orange,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                },
                colors = itemColors,
                modifier = Modifier.testTag("drawer_item_billing"),
            )

            NavigationDrawerItem(
                label = { Text("My Business Studio") },
                selected = false,
                onClick = onSelectProfileManagement,
                icon = { Icon(Icons.Rounded.Storefront, contentDescription = null) },
                colors = itemColors,
                modifier = Modifier.testTag("drawer_item_my_business"),
            )

            HorizontalDivider(
                color = TrolleyTheme.colors.hairline,
                modifier = Modifier.padding(vertical = 4.dp),
            )

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.drawer_about_app)) },
                selected = activeKey == DashboardDestinationKey.ABOUT_APP,
                onClick = onSelectAboutApp,
                icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                colors = itemColors,
                modifier = Modifier.testTag("drawer_item_about_app"),
            )
        }
    }
}
