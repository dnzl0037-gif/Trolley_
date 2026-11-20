package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.catalog.GeographyCatalog
import com.example.data.local.DevicePreferences
import com.example.ui.components.PickerOption
import com.example.ui.components.SearchablePickerField
import com.example.ui.theme.TrolleyTheme

@Composable
fun AppSettingsScreen(
    preferences: DevicePreferences,
    isSignedIn: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    onChangeDiscoveryCountry: (String) -> Unit,
    onResetConsent: () -> Unit,
    onOpenAccountSettings: () -> Unit,
    onOpenBilling: () -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    BackHandler(onBack = onBack)

    val countryOptions = remember {
        GeographyCatalog.countries.map {
            PickerOption(
                value = it.code,
                label = it.label,
                hint = it.region,
                keywords = "${it.label} ${it.code} ${it.region}",
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(
                start = 18.dp,
                end = 18.dp,
                top = 18.dp,
                bottom = contentPadding.calculateBottomPadding() + 28.dp,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 580.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("settings_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.settings_eyebrow),
                        style = MaterialTheme.typography.labelSmall,
                        color = TrolleyTheme.colors.orange,
                    )
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            // Appearance Card (Relocated Dark Mode control)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "APPEARANCE",
                        style = MaterialTheme.typography.labelSmall,
                        color = TrolleyTheme.colors.orange,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(42.dp),
                            ) {
                                Icon(
                                    imageVector = if (preferences.darkTheme) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                                    contentDescription = null,
                                    tint = TrolleyTheme.colors.limeAccent,
                                    modifier = Modifier.padding(9.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_dark_mode),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.settings_dark_mode_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TrolleyTheme.colors.mutedInk,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = preferences.darkTheme,
                            onCheckedChange = onToggleDarkTheme,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TrolleyTheme.colors.limeDeep,
                                checkedTrackColor = TrolleyTheme.colors.lime,
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("settings_dark_mode_switch"),
                        )
                    }
                }
            }

            // Discovery Defaults Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Public,
                            contentDescription = null,
                            tint = TrolleyTheme.colors.orange,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "DEFAULT DISCOVERY REGION",
                            style = MaterialTheme.typography.labelSmall,
                            color = TrolleyTheme.colors.orange,
                        )
                    }
                    SearchablePickerField(
                        label = "Default marketplace country",
                        value = preferences.discoveryCountry,
                        options = countryOptions,
                        onSelect = onChangeDiscoveryCountry,
                        placeholder = "Choose discovery country",
                        searchPlaceholder = "Search country or ISO code",
                        enabled = true,
                        testTag = "settings_country_picker",
                    )
                }
            }

            // Privacy & Security Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PrivacyTip,
                            contentDescription = null,
                            tint = TrolleyTheme.colors.orange,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "PRIVACY & SECURITY CONTROLS",
                            style = MaterialTheme.typography.labelSmall,
                            color = TrolleyTheme.colors.orange,
                        )
                    }

                    Text(
                        text = "Storage & consent preference: ${preferences.consentChoice?.replaceFirstChar { it.uppercase() } ?: "Not set"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    OutlinedButton(
                        onClick = onResetConsent,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("settings_reset_consent_button"),
                    ) {
                        Text("Review cookie & storage consent choice")
                    }

                    HorizontalDivider(color = TrolleyTheme.colors.hairline)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = TrolleyTheme.colors.limeAccent,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (isSignedIn) {
                                "Google Sign-In session verified · 10-minute step-up re-verification enforced"
                            } else {
                                "Sign in with Google for end-to-end Firebase Auth & Firestore protection"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TrolleyTheme.colors.mutedInk,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = onOpenAccountSettings,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("settings_open_account_button"),
                        ) {
                            Text("Account Settings")
                        }
                        OutlinedButton(
                            onClick = onOpenBilling,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("settings_open_billing_button"),
                        ) {
                            Text("Billing & Plans")
                        }
                    }
                }
            }
        }
    }
}
