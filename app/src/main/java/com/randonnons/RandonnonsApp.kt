package com.randonnons

import android.app.Application
import com.randonnons.data.db.RandonnonsDatabase
import com.randonnons.data.repository.RandonnonsRepository
import org.osmdroid.config.Configuration
import java.io.File

class RandonnonsApp : Application() {

    val database by lazy { RandonnonsDatabase.getInstance(this) }
    val repository by lazy { RandonnonsRepository.getInstance(this) }

    override fun onCreate() {
        super.onCreate()

        // Configurer OSMDroid (cache tuiles en cas de fallback réseau)
        Configuration.getInstance().apply {
            userAgentValue = "Randonnons/1.0"
            osmdroidBasePath = File(getExternalFilesDir(null), "osmdroid")
            osmdroidTileCache = File(getExternalFilesDir(null), "osmdroid/tiles")
        }
    }
}
