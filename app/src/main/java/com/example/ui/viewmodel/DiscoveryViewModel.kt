package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.catalog.ServiceCategory
import com.example.data.catalog.ServiceTaxonomy
import com.example.data.local.AppPreferences
import com.example.data.local.DevicePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DiscoveryViewMode {
    DIRECTORY,
    MAP,
}

data class DiscoveryUiState(
    val query: String = "",
    val activeCategory: String = "All services",
    val showAllTaxonomy: Boolean = false,
    val viewMode: DiscoveryViewMode = DiscoveryViewMode.DIRECTORY,
    val preferences: DevicePreferences = DevicePreferences(),
    val matchingCategories: List<ServiceCategory> = emptyList(),
)

class DiscoveryViewModel(
    private val preferences: AppPreferences,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val activeCategoryFlow = MutableStateFlow("All services")
    private val showAllFlow = MutableStateFlow(false)
    private val viewModeFlow = MutableStateFlow(DiscoveryViewMode.DIRECTORY)

    val uiState: StateFlow<DiscoveryUiState> = combine(
        queryFlow,
        activeCategoryFlow,
        showAllFlow,
        viewModeFlow,
        preferences.preferencesFlow,
    ) { query, activeCategory, showAll, viewMode, prefs ->
        val hasFilter = query.isNotBlank() || activeCategory != "All services"
        val matches = if (hasFilter) {
            ServiceTaxonomy.filterServices(query = query, activeFamily = activeCategory)
        } else {
            emptyList()
        }
        DiscoveryUiState(
            query = query,
            activeCategory = activeCategory,
            showAllTaxonomy = showAll,
            viewMode = viewMode,
            preferences = prefs,
            matchingCategories = matches,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = DiscoveryUiState(),
    )

    fun setViewMode(mode: DiscoveryViewMode) {
        viewModeFlow.value = mode
    }

    fun updateQuery(newQuery: String) {
        queryFlow.value = newQuery
    }

    fun chooseCategory(name: String) {
        activeCategoryFlow.value = name
    }

    fun selectServiceType(serviceName: String, familyName: String? = null) {
        queryFlow.value = serviceName
        activeCategoryFlow.value = familyName ?: "All services"
    }

    fun clearFilters() {
        queryFlow.value = ""
        activeCategoryFlow.value = "All services"
    }

    fun toggleShowAllTaxonomy() {
        showAllFlow.value = !showAllFlow.value
    }

    fun browseAllServices() {
        activeCategoryFlow.value = "All services"
        queryFlow.value = ""
        showAllFlow.value = !showAllFlow.value
    }

    fun setDarkTheme(dark: Boolean) {
        viewModelScope.launch { preferences.setDarkTheme(dark) }
    }

    fun setDiscoveryCountry(countryCode: String) {
        viewModelScope.launch { preferences.setDiscoveryCountry(countryCode) }
    }

    fun setConsentChoice(choice: String) {
        viewModelScope.launch { preferences.setConsentChoice(choice) }
    }
}
