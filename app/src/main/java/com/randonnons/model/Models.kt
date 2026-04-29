package com.randonnons.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Embedded
import androidx.room.Relation

// ── Point GPS brut ────────────────────────────────────────────────────────────
data class GpsPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val speed: Float = 0f           // m/s
)

// ── Trace enregistrée (table Room) ───────────────────────────────────────────
@Entity(tableName = "traces")
data class Trace(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nom: String,
    val description: String = "",
    val dateDebut: Long = System.currentTimeMillis(),
    val dateFin: Long? = null,
    val dureeMs: Long = 0,
    val distanceM: Double = 0.0,        // mètres
    val denivelePositif: Double = 0.0,  // mètres
    val deniveleNegatif: Double = 0.0,
    val altitudeMin: Double = 0.0,
    val altitudeMax: Double = 0.0,
    val vitesseMoyenne: Float = 0f,     // km/h
    val statut: TraceStatut = TraceStatut.EN_COURS,
    val cheminFichier: String? = null   // chemin GPX exporté
)

enum class TraceStatut { EN_COURS, TERMINEE, IMPORTEE }

// ── Point de trace (table Room) ──────────────────────────────────────────────
@Entity(tableName = "points_trace", indices = [
    androidx.room.Index(value = ["traceId"])
])
data class PointTrace(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val traceId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val timestamp: Long,
    val vitesse: Float,
    val precision: Float
)

// ── Waypoint (table Room) ────────────────────────────────────────────────────
@Entity(tableName = "waypoints")
data class Waypoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val traceId: Long? = null,          // null = waypoint indépendant
    val nom: String,
    val description: String = "",
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val symbole: String = "Flag",       // symbole GPX standard
    val cheminPhoto: String? = null
)

// ── Route importée (table Room) ──────────────────────────────────────────────
@Entity(tableName = "routes")
data class Route(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nom: String,
    val description: String = "",
    val distanceM: Double = 0.0,
    val denivelePositif: Double = 0.0,
    val source: String = "",            // ex: "Visorando", "Komoot", "manuel"
    val dateImport: Long = System.currentTimeMillis(),
    val cheminFichier: String? = null
)

// ── Point de route (table Room) ──────────────────────────────────────────────
@Entity(tableName = "points_route", indices = [
    androidx.room.Index(value = ["routeId"])
])
data class PointRoute(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routeId: Long,
    val ordre: Int,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0
)

// ── Carte MBTiles (table Room) ───────────────────────────────────────────────
@Entity(tableName = "cartes_mbtiles")
data class CarteMBTiles(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nom: String,
    val cheminFichier: String,
    val tailleOctets: Long = 0,
    val zoomMin: Int = 0,
    val zoomMax: Int = 18,
    val bounds: String? = null,         // "minLon,minLat,maxLon,maxLat"
    val active: Boolean = true,
    val dateAjout: Long = System.currentTimeMillis()
)

// ── Relations Room ────────────────────────────────────────────────────────────
data class TraceAvecPoints(
    @Embedded val trace: Trace,
    @Relation(parentColumn = "id", entityColumn = "traceId")
    val points: List<PointTrace>,
    @Relation(parentColumn = "id", entityColumn = "traceId")
    val waypoints: List<Waypoint>
)

data class RouteAvecPoints(
    @Embedded val route: Route,
    @Relation(parentColumn = "id", entityColumn = "routeId")
    val points: List<PointRoute>
)

// ── Stats temps réel (flux LiveData) ─────────────────────────────────────────
data class StatsEnCours(
    val distanceM: Double = 0.0,
    val dureeMs: Long = 0,
    val vitesseMs: Float = 0f,          // m/s
    val altitudeActuelle: Double = 0.0,
    val denivelePositif: Double = 0.0,
    val deniveleNegatif: Double = 0.0,
    val nbPoints: Int = 0,
    val precisionGps: Float = 0f
) {
    val vitesseKmh: Float get() = vitesseMs * 3.6f
    val distanceKm: Double get() = distanceM / 1000.0
    val dureeFormate: String get() {
        val s = dureeMs / 1000
        return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }
}
