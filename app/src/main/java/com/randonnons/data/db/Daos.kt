package com.randonnons.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.randonnons.model.*

// ── DAO Traces ────────────────────────────────────────────────────────────────
@Dao
interface TraceDao {

    @Query("SELECT * FROM traces ORDER BY dateDebut DESC")
    fun getAllTraces(): LiveData<List<Trace>>

    @Query("SELECT * FROM traces WHERE statut = 'EN_COURS' LIMIT 1")
    suspend fun getTraceEnCours(): Trace?

    @Transaction
    @Query("SELECT * FROM traces WHERE id = :id")
    suspend fun getTraceAvecPoints(id: Long): TraceAvecPoints?

    @Query("SELECT * FROM traces WHERE id = :id")
    suspend fun getTrace(id: Long): Trace?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trace: Trace): Long

    @Update
    suspend fun update(trace: Trace)

    @Delete
    suspend fun delete(trace: Trace)

    @Query("DELETE FROM traces WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Mise à jour des stats en temps réel (batch)
    @Query("""UPDATE traces SET distanceM=:dist, dureeMs=:duree,
              denivelePositif=:dp, deniveleNegatif=:dn,
              altitudeMin=:altMin, altitudeMax=:altMax,
              vitesseMoyenne=:vitesse WHERE id=:id""")
    suspend fun updateStats(
        id: Long, dist: Double, duree: Long, dp: Double, dn: Double,
        altMin: Double, altMax: Double, vitesse: Float
    )

    @Query("UPDATE traces SET statut=:statut, dateFin=:fin WHERE id=:id")
    suspend fun terminer(id: Long, statut: TraceStatut, fin: Long)
}

// ── DAO Points de trace ───────────────────────────────────────────────────────
@Dao
interface PointTraceDao {

    @Query("SELECT * FROM points_trace WHERE traceId=:traceId ORDER BY timestamp ASC")
    suspend fun getPointsDeLaTrace(traceId: Long): List<PointTrace>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: PointTrace): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<PointTrace>)

    @Query("DELETE FROM points_trace WHERE traceId=:traceId")
    suspend fun deleteByTrace(traceId: Long)

    @Query("SELECT COUNT(*) FROM points_trace WHERE traceId=:traceId")
    suspend fun countPoints(traceId: Long): Int
}

// ── DAO Waypoints ─────────────────────────────────────────────────────────────
@Dao
interface WaypointDao {

    @Query("SELECT * FROM waypoints ORDER BY timestamp DESC")
    fun getAllWaypoints(): LiveData<List<Waypoint>>

    @Query("SELECT * FROM waypoints WHERE traceId=:traceId")
    fun getWaypointsDeLaTrace(traceId: Long): LiveData<List<Waypoint>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(waypoint: Waypoint): Long

    @Update
    suspend fun update(waypoint: Waypoint)

    @Delete
    suspend fun delete(waypoint: Waypoint)
}

// ── DAO Routes ────────────────────────────────────────────────────────────────
@Dao
interface RouteDao {

    @Query("SELECT * FROM routes ORDER BY dateImport DESC")
    fun getAllRoutes(): LiveData<List<Route>>

    @Transaction
    @Query("SELECT * FROM routes WHERE id=:id")
    suspend fun getRouteAvecPoints(id: Long): RouteAvecPoints?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(route: Route): Long

    @Delete
    suspend fun delete(route: Route)
}

// ── DAO Points de route ───────────────────────────────────────────────────────
@Dao
interface PointRouteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<PointRoute>)

    @Query("DELETE FROM points_route WHERE routeId=:routeId")
    suspend fun deleteByRoute(routeId: Long)
}

// ── DAO Cartes MBTiles ────────────────────────────────────────────────────────
@Dao
interface CarteMBTilesDao {

    @Query("SELECT * FROM cartes_mbtiles ORDER BY dateAjout DESC")
    fun getAllCartes(): LiveData<List<CarteMBTiles>>

    @Query("SELECT * FROM cartes_mbtiles WHERE active=1 ORDER BY dateAjout DESC")
    suspend fun getCartesActives(): List<CarteMBTiles>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(carte: CarteMBTiles): Long

    @Update
    suspend fun update(carte: CarteMBTiles)

    @Delete
    suspend fun delete(carte: CarteMBTiles)
}
