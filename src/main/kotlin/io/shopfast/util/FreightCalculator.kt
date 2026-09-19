package io.shopfast.util

/**
 * Calculo de frete do ShopFast.
 *
 * Existia em duas copias identicas — uma em `CouponService`, outra em
 * `LegacyPricing` — e era o achado de duplicacao (CPD) do projeto. Agora e um
 * lugar so, e as duas chamam daqui.
 */
object FreightCalculator {

    private const val BASE_FEE = 12.50
    private const val HEAVY_SURCHARGE = 25.0
    private const val MEDIUM_SURCHARGE = 12.0
    private const val LIGHT_SURCHARGE = 6.0
    private const val HEAVY_WEIGHT_KG = 30.0
    private const val MEDIUM_WEIGHT_KG = 10.0
    private const val LIGHT_WEIGHT_KG = 5.0

    private const val LONG_DISTANCE_KM = 800.0
    private const val MEDIUM_DISTANCE_KM = 300.0
    private const val LONG_DISTANCE_RATE = 0.18
    private const val MEDIUM_DISTANCE_RATE = 0.12
    private const val SHORT_DISTANCE_RATE = 0.08

    private const val DEFAULT_REGION_FEE = 5.70
    private const val REMOTE_REGION_FEE = 18.90
    private const val MIDWEST_REGION_FEE = 11.40
    private const val INSURANCE_RATE = 0.03

    private val REMOTE_REGIONS = setOf("NORTE", "NORDESTE")
    private const val MIDWEST_REGION = "CENTRO-OESTE"

    /** Componentes do frete: base, distancia, regiao, seguro e total. */
    fun breakdown(weightKg: Double, distanceKm: Double, region: String): Map<String, Double> {
        val base = BASE_FEE + weightSurcharge(weightKg)
        val distanceFee = distanceKm * distanceRate(distanceKm)
        val regionFee = regionFee(region)
        val insurance = (base + distanceFee + regionFee) * INSURANCE_RATE
        return mapOf(
            "base" to base,
            "distancia" to distanceFee,
            "regiao" to regionFee,
            "seguro" to insurance,
            "total" to base + distanceFee + regionFee + insurance,
        )
    }

    private fun weightSurcharge(weightKg: Double): Double = when {
        weightKg > HEAVY_WEIGHT_KG -> HEAVY_SURCHARGE
        weightKg > MEDIUM_WEIGHT_KG -> MEDIUM_SURCHARGE
        weightKg > LIGHT_WEIGHT_KG -> LIGHT_SURCHARGE
        else -> 0.0
    }

    private fun distanceRate(distanceKm: Double): Double = when {
        distanceKm > LONG_DISTANCE_KM -> LONG_DISTANCE_RATE
        distanceKm > MEDIUM_DISTANCE_KM -> MEDIUM_DISTANCE_RATE
        else -> SHORT_DISTANCE_RATE
    }

    private fun regionFee(region: String): Double = when (region) {
        in REMOTE_REGIONS -> REMOTE_REGION_FEE
        MIDWEST_REGION -> MIDWEST_REGION_FEE
        else -> DEFAULT_REGION_FEE
    }
}
