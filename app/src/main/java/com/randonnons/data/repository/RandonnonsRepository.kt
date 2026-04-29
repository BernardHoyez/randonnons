package com.randonnons.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.randonnons.data.db.RandonnonsDatabase
import com.randonnons.model.*
import com.randonnons.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*

class RandonnonsRepository(private val context: Context) {

    private val db = RandonnonsDatabase.getInstance(context)
    private val traceDao       = db.traceDao()
    private val pointTraceDao  = db.pointTraceDao()
    private val waypointDao    = db.waypointDao()
    private val routeDao       = db.routeDao()
    private val pointRouteDao  = db.pointRouteDao()
    private val carteDao       = db.carteMBTilesDao()

    // ── Traces ────────────────────────────────────────────────────────────────
    fun getAllTraces(): LiveData<List<Trace>> = traceDao.getAllTraces()

    suspend fun getTraceAvecPoints(id: Long) = traceDao.getTraceAvecPoints(id)

    suspend fun creerTrace(nom: String, description: String = ""): Long =
        traceDao.insert(Trace(nom = nom, description = description))

    suspend fun deleteTrace(trace: Trace) = withContext(Dispatchers.IO) {
        pointTraceDao.deleteByTrace(trace.id)
        traceDao.delete(trace)
    }

    // ── Waypoints ─────────────────────────────────────────────────────────────
    fun getAllWaypoints(): LiveData<List<Waypoint>> = waypointDao.getAllWaypoints()
    fun getWaypointsDeLaTrace(traceId: Long) = waypointDao.getWaypointsDeLaTrace(traceId)

    suspend fun ajouterWaypoint(waypoint: Waypoint): Long =
        waypointDao.insert(waypoint)

    suspend fun updateWaypoint(waypoint: Waypoint) = waypointDao.update(waypoint)
    suspend fun deleteWaypoint(waypoint: Waypoint) = waypointDao.delete(waypoint)

    // ── Routes ────────────────────────────────────────────────────────────────
    fun getAllRoutes(): LiveData<List<Route>> = routeDao.getAllRoutes()

    suspend fun getRouteAvecPoints(id: Long) = routeDao.getRouteAvecPoints(id)

    suspend fun deleteRoute(route: Route) = withContext(Dispatchers.IO) {
        pointRouteDao.deleteByRoute(route.id)
        routeDao.delete(route)
    }

    // ── Cartes MBTiles ────────────────────────────────────────────────────────
    fun getAllCartes(): LiveData<List<CarteMBTiles>> = carteDao.getAllCartes()

    suspend fun ajouterCarte(carte: CarteMBTiles): Long = carteDao.insert(carte)
    suspend fun updateCarte(carte: CarteMBTiles) = carteDao.update(carte)
    suspend fun deleteCarte(carte: CarteMBTiles) = carteDao.delete(carte)
    suspend fun getCartesActives() = carteDao.getCartesActives()

    // ── Export GPX ────────────────────────────────────────────────────────────
    suspend fun exporterGpx(traceId: Long): File? = withContext(Dispatchers.IO) {
        val traceAvecPts = traceDao.getTraceAvecPoints(traceId) ?: return@withContext null
        val dir = File(context.getExternalFilesDir("exports"), "gpx").also { it.mkdirs() }
        val nom = traceAvecPts.trace.nom.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(dir, "${nom}_${System.currentTimeMillis()}.gpx")
        FileOutputStream(file).use { GpxExporter.exporter(traceAvecPts, it) }
        // Mettre à jour le chemin dans la base
        traceDao.update(traceAvecPts.trace.copy(cheminFichier = file.absolutePath))
        file
    }

    suspend fun exporterKml(traceId: Long): File? = withContext(Dispatchers.IO) {
        val traceAvecPts = traceDao.getTraceAvecPoints(traceId) ?: return@withContext null
        val dir = File(context.getExternalFilesDir("exports"), "kml").also { it.mkdirs() }
        val nom = traceAvecPts.trace.nom.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(dir, "${nom}_${System.currentTimeMillis()}.kml")
        FileOutputStream(file).use { KmlExporter.exporter(traceAvecPts, it) }
        file
    }

    // ── Import GPX ────────────────────────────────────────────────────────────
    suspend fun importerGpx(input: InputStream): Long = withContext(Dispatchers.IO) {
        val data = GpxImporter.importer(input)
        val traceId = traceDao.insert(Trace(
            nom = data.nom,
            description = data.description,
            statut = TraceStatut.IMPORTEE,
            distanceM = GeoUtils.distanceTotale(data.points)
        ))
        val points = data.points.map { it.copy(traceId = traceId) }
        pointTraceDao.insertAll(points)
        data.waypoints.forEach { wp -> waypointDao.insert(wp.copy(traceId = traceId)) }

        if (data.routePoints.isNotEmpty()) {
            val routeId = routeDao.insert(Route(nom = data.nom, description = data.description))
            val rpts = data.routePoints.mapIndexed { i, p -> p.copy(routeId = routeId, ordre = i) }
            pointRouteDao.insertAll(rpts)
        }
        traceId
    }

    suspend fun importerKml(input: InputStream): Long = withContext(Dispatchers.IO) {
        val data = KmlImporter.importer(input)
        val traceId = traceDao.insert(Trace(
            nom = data.nom,
            description = data.description,
            statut = TraceStatut.IMPORTEE
        ))
        val points = data.points.map { it.copy(traceId = traceId) }
        pointTraceDao.insertAll(points)
        data.waypoints.forEach { wp -> waypointDao.insert(wp.copy(traceId = traceId)) }
        traceId
    }

    companion object {
        @Volatile private var INSTANCE: RandonnonsRepository? = null
        fun getInstance(context: Context) = INSTANCE ?: synchronized(this) {
            INSTANCE ?: RandonnonsRepository(context.applicationContext).also { INSTANCE = it }
        }
    }
}
