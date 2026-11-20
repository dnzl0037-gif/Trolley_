package com.example.data.catalog

import java.util.Locale

data class CountryOption(
    val code: String,
    val label: String,
    val region: String,
)

object GeographyCatalog {

    val regions: List<String> = listOf(
        "Africa",
        "Asia",
        "Europe",
        "North America",
        "South America",
        "Oceania",
        "Antarctica",
    )

    private val countryCodesByRegion: Map<String, String> = mapOf(
        "Africa" to "DZ AO BJ BW BF BI CV CM CF TD KM CG CD CI DJ EG GQ ER SZ ET GA GM GH GN GW KE LS LR LY MG MW ML MR MU YT MA MZ NA NE NG RE RW SH ST SN SC SL SO ZA SS SD TZ TG TN UG EH ZM ZW",
        "Asia" to "AF AM AZ BH BD BT BN KH CN CX CC GE HK IN ID IR IQ IL JP JO KZ KP KR KW KG LA LB MO MY MV MN MM NP OM PK PS PH QA SA SG LK SY TW TJ TH TL TR TM AE UZ VN YE IO",
        "Europe" to "AX AL AD AT BY BE BA BG HR CY CZ DK EE FO FI FR DE GI GR GG VA HU IS IE IM IT JE LV LI LT LU MT MD MC ME NL MK NO PL PT RO RU SM RS SK SI ES SJ SE CH UA GB",
        "North America" to "AI AG AW BS BB BZ BM BQ CA KY CR CU CW DM DO SV GL GD GP GT HT HN JM MQ MX MS NI PA PR BL KN LC MF PM VC SX TT TC US VG VI",
        "South America" to "AR BO BR CL CO EC FK GF GY PY PE SR UY VE",
        "Oceania" to "AS AU CK FJ PF GU KI MH FM NR NC NZ NU NF MP PW PG PN WS SB TK TO TV UM VU WF",
        "Antarctica" to "AQ BV TF HM GS",
    )

    val countries: List<CountryOption> = countryCodesByRegion.entries.flatMap { (region, codes) ->
        codes.split(" ").map { code ->
            val display = Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH)
            CountryOption(
                code = code,
                label = display.ifBlank { code },
                region = region,
            )
        }
    }.sortedBy { it.label }

    private val countriesByCode: Map<String, CountryOption> = countries.associateBy { it.code }

    fun getRegionForCountry(code: String): String {
        return countriesByCode[code.trim().uppercase(Locale.ENGLISH)]?.region.orEmpty()
    }

    fun getCountryLabel(code: String): String {
        val normalized = code.trim().uppercase(Locale.ENGLISH)
        return countriesByCode[normalized]?.label ?: normalized
    }

    private val cityCoordinates: Map<String, Pair<Double, Double>> = mapOf(
        "harare" to (-17.8252 to 31.0335),
        "bulawayo" to (-20.1325 to 28.6265),
        "chitungwiza" to (-18.0127 to 31.0756),
        "mutare" to (-18.9707 to 32.6709),
        "gweru" to (-19.4500 to 29.8167),
        "kwekwe" to (-18.9281 to 29.8149),
        "kadoma" to (-18.3333 to 29.9153),
        "masvingo" to (-20.0637 to 30.8277),
        "victoria falls" to (-17.9318 to 25.8307),
        "johannesburg" to (-26.2041 to 28.0473),
        "cape town" to (-33.9249 to 18.4241),
        "pretoria" to (-25.7479 to 28.2293),
        "durban" to (-29.8587 to 31.0218),
        "gaborone" to (-24.6282 to 25.9231),
        "lusaka" to (-15.3875 to 28.3228),
        "nairobi" to (-1.2921 to 36.8219),
        "lagos" to (6.5244 to 3.3792),
        "accra" to (5.6037 to -0.1870),
        "london" to (51.5072 to -0.1276),
        "manchester" to (53.4808 to -2.2426),
        "new york" to (40.7128 to -74.0060),
        "toronto" to (43.6532 to -79.3832),
        "sydney" to (-33.8688 to 151.2093),
        "melbourne" to (-37.8136 to 144.9631),
    )

    private val countryCoordinates: Map<String, Pair<Double, Double>> = mapOf(
        "ZW" to (-17.8252 to 31.0335),
        "ZA" to (-26.2041 to 28.0473),
        "BW" to (-24.6282 to 25.9231),
        "ZM" to (-15.3875 to 28.3228),
        "KE" to (-1.2921 to 36.8219),
        "NG" to (6.5244 to 3.3792),
        "GH" to (5.6037 to -0.1870),
        "TZ" to (-6.7924 to 39.2083),
        "UG" to (0.3476 to 32.5825),
        "GB" to (51.5072 to -0.1276),
        "US" to (39.8283 to -98.5795),
        "CA" to (43.6532 to -79.3832),
        "AU" to (-33.8688 to 151.2093),
        "IN" to (28.6139 to 77.2090),
    )

    fun approximateCoordinates(countryCode: String, city: String = ""): Pair<Double, Double> {
        val cleanCity = city.trim().lowercase(Locale.ENGLISH)
        if (cleanCity.isNotBlank()) {
            cityCoordinates[cleanCity]?.let { return it }
        }
        val cleanCountry = countryCode.trim().uppercase(Locale.ENGLISH)
        countryCoordinates[cleanCountry]?.let { return it }
        return when (getRegionForCountry(cleanCountry)) {
            "Africa" -> -17.8252 to 31.0335
            "Europe" -> 51.5072 to -0.1276
            "North America" -> 40.7128 to -74.0060
            "South America" -> -23.5505 to -46.6333
            "Asia" -> 28.6139 to 77.2090
            "Oceania" -> -33.8688 to 151.2093
            else -> -17.8252 to 31.0335
        }
    }
}
