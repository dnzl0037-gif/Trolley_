package com.example.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.catalog.GeographyCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class GeocodeResult(
    val latitude: Double,
    val longitude: Double,
    val isExactAddress: Boolean,
)

object AddressGeocoder {

    suspend fun geocodeBusinessAddress(
        context: Context,
        businessAddress: String,
        city: String,
        countryCode: String,
    ): GeocodeResult {
        val cleanAddress = businessAddress.trim()
        val cleanCity = city.trim()
        val cleanCountry = countryCode.trim().uppercase(Locale.ENGLISH)
        val countryLabel = GeographyCatalog.getCountryLabel(cleanCountry)

        val queries = buildList {
            if (cleanAddress.isNotBlank()) {
                add(listOf(cleanAddress, cleanCity, countryLabel).filter { it.isNotBlank() }.joinToString(", "))
            }
            if (cleanCity.isNotBlank()) {
                add(listOf(cleanCity, countryLabel).filter { it.isNotBlank() }.joinToString(", "))
            }
        }

        if (Geocoder.isPresent()) {
            for ((index, query) in queries.withIndex()) {
                val resolved = geocodeQuery(context, query)
                if (resolved != null) {
                    return GeocodeResult(
                        latitude = resolved.first,
                        longitude = resolved.second,
                        isExactAddress = index == 0 && cleanAddress.isNotBlank(),
                    )
                }
            }
        }

        val fallback = GeographyCatalog.approximateCoordinates(cleanCountry, cleanCity)
        return GeocodeResult(
            latitude = fallback.first,
            longitude = fallback.second,
            isExactAddress = false,
        )
    }

    private suspend fun geocodeQuery(
        context: Context,
        query: String,
    ): Pair<Double, Double>? {
        val geocoder = Geocoder(context.applicationContext, Locale.ENGLISH)
        return withTimeoutOrNull(5_000L) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    runCatching {
                        geocoder.getFromLocationName(
                            query,
                            1,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    val first = addresses.firstOrNull()
                                    if (continuation.isActive) {
                                        continuation.resume(first?.let { it.latitude to it.longitude })
                                    }
                                }

                                override fun onError(errorMessage: String?) {
                                    if (continuation.isActive) {
                                        continuation.resume(null)
                                    }
                                }
                            },
                        )
                    }.onFailure {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    runCatching {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocationName(query, 1)?.firstOrNull()?.let { it.latitude to it.longitude }
                    }.getOrNull()
                }
            }
        }
    }

    fun hasCoarseLocationPermission(context: Context): Boolean {
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return coarse || fine
    }

    @SuppressLint("MissingPermission")
    fun fetchLastKnownCoarseLocation(context: Context): Pair<Double, Double>? {
        if (!hasCoarseLocationPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
            LocationManager.GPS_PROVIDER,
        )
        var best: Location? = null
        for (provider in providers) {
            val loc = runCatching { manager.getLastKnownLocation(provider) }.getOrNull() ?: continue
            if (best == null || loc.time > best.time) {
                best = loc
            }
        }
        return best?.let { it.latitude to it.longitude }
    }

    fun haversineDistanceKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * earthRadiusKm * asin(sqrt(a))
    }

    fun formatDistanceKm(distanceKm: Double): String {
        return if (distanceKm < 1.0) {
            "${(distanceKm * 1000).toInt().coerceAtLeast(50)} m away"
        } else if (distanceKm < 10.0) {
            String.format(Locale.US, "%.1f km away", distanceKm)
        } else {
            "${distanceKm.toInt()} km away"
        }
    }
}
