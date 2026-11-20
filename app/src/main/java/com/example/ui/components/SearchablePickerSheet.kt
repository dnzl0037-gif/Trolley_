package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.catalog.GeographyCatalog
import com.example.ui.theme.TrolleyTheme

data class PickerOption(
    val value: String,
    val label: String,
    val hint: String = "",
    val keywords: String = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchablePickerField(
    label: String,
    value: String,
    options: List<PickerOption>,
    onSelect: (String) -> Unit,
    placeholder: String,
    searchPlaceholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
    required: Boolean = false,
    testTag: String = "picker_field",
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val selected = remember(value, options) { options.firstOrNull { it.value == value } }

    Column(modifier = modifier) {
        if (!compact && label.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (required) {
                    Text(
                        text = " *",
                        style = MaterialTheme.typography.labelMedium,
                        color = TrolleyTheme.colors.orange,
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag(testTag)
                .clickable(enabled = enabled) { sheetOpen = true },
            shape = RoundedCornerShape(9.dp),
            color = if (compact) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (selected != null) {
                        if (selected.hint.isNotBlank()) {
                            Text(
                                text = selected.hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = selected.label,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    if (sheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var searchQuery by remember { mutableStateOf("") }

        val filtered = remember(searchQuery, options) {
            val tokens = searchQuery.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokens.isEmpty()) {
                options.take(120)
            } else {
                options.filter { opt ->
                    val haystack = "${opt.label} ${opt.hint} ${opt.keywords} ${opt.value}".lowercase()
                    tokens.all { haystack.contains(it) }
                }.take(120)
            }
        }

        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = label.ifBlank { placeholder },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = { sheetOpen = false }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("${testTag}_search"),
                    placeholder = { Text(searchPlaceholder) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TrolleyTheme.colors.limeAccent,
                        unfocusedBorderColor = TrolleyTheme.colors.hairline,
                    ),
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (filtered.isEmpty()) {
                    Text(
                        text = "No matching options found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                    ) {
                        items(filtered, key = { it.value.ifBlank { "__empty__" } }) { option ->
                            val isSelected = option.value == value
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .clickable {
                                        onSelect(option.value)
                                        sheetOpen = false
                                    }
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        ),
                                        color = if (isSelected) TrolleyTheme.colors.limeAccent else MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (option.hint.isNotBlank()) {
                                        Text(
                                            text = option.hint,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = TrolleyTheme.colors.limeAccent,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            HorizontalDivider(color = TrolleyTheme.colors.hairline.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RegionCountryFields(
    countryCode: String,
    onCountryChange: (String) -> Unit,
    enabled: Boolean = true,
    required: Boolean = true,
    testTagPrefix: String = "provider",
) {
    val derivedRegion = remember(countryCode) {
        if (countryCode.isNotBlank()) GeographyCatalog.getRegionForCountry(countryCode) else ""
    }
    var manualRegion by remember { mutableStateOf(derivedRegion) }
    val shownRegion = if (countryCode.isNotBlank()) derivedRegion else manualRegion

    val regionOptions = remember {
        listOf(PickerOption(value = "", label = "All regions")) +
            GeographyCatalog.regions.map { PickerOption(value = it, label = it) }
    }

    val countryOptions = remember(shownRegion) {
        GeographyCatalog.countries
            .filter { shownRegion.isBlank() || it.region == shownRegion }
            .map { PickerOption(value = it.code, label = it.label, keywords = it.region) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SearchablePickerField(
            label = "Region",
            value = shownRegion,
            options = regionOptions,
            onSelect = { nextRegion ->
                manualRegion = nextRegion
                if (countryCode.isNotBlank() && nextRegion.isNotBlank() &&
                    GeographyCatalog.getRegionForCountry(countryCode) != nextRegion
                ) {
                    onCountryChange("")
                }
            },
            placeholder = "All regions",
            searchPlaceholder = "Search continents",
            enabled = enabled,
            testTag = "${testTagPrefix}_region",
        )

        SearchablePickerField(
            label = "Country",
            value = countryCode,
            options = countryOptions,
            onSelect = onCountryChange,
            placeholder = if (shownRegion.isNotBlank()) "Choose a country" else "Choose a country or territory",
            searchPlaceholder = "Search country",
            enabled = enabled,
            required = required,
            testTag = "${testTagPrefix}_country",
        )
    }
}
