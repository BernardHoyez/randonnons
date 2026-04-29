package com.randonnons.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.randonnons.model.*

@Database(
    entities = [
        Trace::class,
        PointTrace::class,
        Waypoint::class,
        Route::class,
        PointRoute::class,
        CarteMBTiles::class
    ],
    version = 1,
    exportSchema = false
)
abstract class RandonnonsDatabase : RoomDatabase() {

    abstract fun traceDao(): TraceDao
    abstract fun pointTraceDao(): PointTraceDao
    abstract fun waypointDao(): WaypointDao
    abstract fun routeDao(): RouteDao
    abstract fun pointRouteDao(): PointRouteDao
    abstract fun carteMBTilesDao(): CarteMBTilesDao

    companion object {
        @Volatile private var INSTANCE: RandonnonsDatabase? = null

        fun getInstance(context: Context): RandonnonsDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RandonnonsDatabase::class.java,
                    "randonnons.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
