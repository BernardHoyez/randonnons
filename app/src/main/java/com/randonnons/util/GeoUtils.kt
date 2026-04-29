package com.randonnons.util

import android.location.Location
import com.randonnons.model.GpsPoint
import com.randonnons.model.PointTrace
import kotlin.math.*

object GeoUtils {

    private const val EARTH_RADIUS_M = 6_371_000.0

    // ── Distance Haversine entre deux coordonnées ──────────────────────────────
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    // ── Distance totale d'une liste de points ─────────────────────────────────
    fun distanceTotale(points: List<PointTrace>): Double {
        if (points.size < 2) return 0.0
        return points.zipWithNext { a, b ->
            distance(a.latitude, a.longitude, b.latitude, b.longitude)
        }.sum()
    }

    // ── Calcul dénivelé positif et négatif ───────────────────────────────────
    data class Denivele(val positif: Double, val negatif: Double)

    fun calculerDenivele(points: List<PointTrace>, seuilM: Double = 3.0): Denivele {
        var dp = 0.0; var dn = 0.0
        points.zipWithNext { a, b ->
            val delta = b.altitude - a.altitude
            if (abs(delta) > seuilM) {
                if (delta > 0) dp += delta else dn += abs(delta)
            }
        }
        return Denivele(dp, dn)
    }

    // ── Simplification Douglas-Peucker (réduire nb de points pour export) ────
    fun douglasPeucker(points: List<PointTrace>, epsilonM: Double): List<PointTrace> {
        if (points.size <= 2) return points
        var maxDist = 0.0; var maxIdx = 0
        val first = points.first(); val last = points.last()
        for (i in 1 until points.size - 1) {
            val d = perpendicularDistance(points[i], first, last)
            if (d > maxDist) { maxDist = d; maxIdx = i }
        }
        return if (maxDist > epsilonM) {
            val left  = douglasPeucker(points.subList(0, maxIdx + 1), epsilonM)
            val right = douglasPeucker(points.subList(maxIdx, points.size), epsilonM)
            left.dropLast(1) + right
        } else listOf(first, last)
    }

    private fun perpendicularDistance(p: PointTrace, a: PointTrace, b: PointTrace): Double {
        val dx = b.longitude - a.longitude; val dy = b.latitude - a.latitude
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return distance(p.latitude, p.longitude, a.latitude, a.longitude)
        val t = ((p.longitude - a.longitude) * dx + (p.latitude - a.latitude) * dy) / len2
        val tc = t.coerceIn(0.0, 1.0)
        val projLat = a.latitude + tc * dy; val projLon = a.longitude + tc * dx
        return distance(p.latitude, p.longitude, projLat, projLon)
    }

    // ── BBox d'une liste de points ─────────────────────────────────────────────
    data class BoundingBox(val minLat: Double, val maxLat: Double,
                           val minLon: Double, val maxLon: Double) {
        val centerLat get() = (minLat + maxLat) / 2
        val centerLon get() = (minLon + maxLon) / 2
    }

    fun boundingBox(points: List<PointTrace>): BoundingBox? {
        if (points.isEmpty()) return null
        return BoundingBox(
            minLat = points.minOf { it.latitude },
            maxLat = points.maxOf { it.latitude },
            minLon = points.minOf { it.longitude },
            maxLon = points.maxOf { it.longitude }
        )
    }

    // ── Profil altimétrique (liste distance cumulée → altitude) ──────────────
    data class ProfilPoint(val distanceCumuleeM: Double, val altitude: Double)

    fun profilAltimetrique(points: List<PointTrace>): List<ProfilPoint> {
        if (points.isEmpty()) return emptyList()
        var cumul = 0.0
        return points.mapIndexed { i, pt ->
            if (i > 0) {
                cumul += distance(points[i-1].latitude, points[i-1].longitude,
                                  pt.latitude, pt.longitude)
            }
            ProfilPoint(cumul, pt.altitude)
        }
    }
}
