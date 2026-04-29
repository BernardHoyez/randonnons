package com.randonnons.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.randonnons.R
import com.randonnons.data.db.RandonnonsDatabase
import com.randonnons.model.*
import com.randonnons.ui.MainActivity
import com.randonnons.util.GeoUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/**
 * GpsTrackingService — Foreground Service Android
 *
 * Garantit l'enregistrement continu de la trace GPS même lorsque :
 *  - l'écran du smartphone est éteint
 *  - l'application est en arrière-plan
 *  - le système tente de tuer les processus
 *
 * L'utilisation d'un Foreground Service avec foregroundServiceType="location"
 * est la seule approche fiable pour maintenir la localisation active.
 */
class GpsTrackingService : LifecycleService(), LocationListener {

    companion object {
        const val CHANNEL_ID = "randonnons_gps_channel"
        const val NOTIF_ID = 1001
        const val ACTION_START = "com.randonnons.GPS_START"
        const val ACTION_STOP  = "com.randonnons.GPS_STOP"
        const val ACTION_PAUSE = "com.randonnons.GPS_PAUSE"
        const val EXTRA_TRACE_ID = "trace_id"

        // Intervalle minimal entre deux points (ms) et distance minimale (m)
        const val GPS_INTERVAL_MS = 3000L
        const val GPS_MIN_DISTANCE_M = 3f
        // Précision GPS minimale acceptée (m)
        const val GPS_ACCURACY_THRESHOLD_M = 30f
        // Seuil altitude pour filtrer le bruit altimétrique
        const val ALTITUDE_NOISE_THRESHOLD_M = 3.0

        // StateFlow partagé pour l'UI (accessible via singleton)
        private val _statsFlow = MutableStateFlow(StatsEnCours())
        val statsFlow: StateFlow<StatsEnCours> = _statsFlow

        private val _enregistrement = MutableStateFlow(false)
        val enregistrement: StateFlow<Boolean> = _enregistrement
    }

    private val db by lazy { RandonnonsDatabase.getInstance(this) }
    private val locationManager by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }
    private val wakeLock by lazy {
        (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Randonnons::GpsWakeLock")
    }

    private var traceId: Long = -1
    private var dernierPoint: GpsPoint? = null
    private var pauseDebutMs: Long = 0
    private var totalPauseMs: Long = 0
    private var debutMs: Long = 0
    private var enPause: Boolean = false

    // Accumulation des stats
    private var distanceTotaleM: Double = 0.0
    private var dpTotalM: Double = 0.0
    private var dnTotalM: Double = 0.0
    private var altitudeMin: Double = Double.MAX_VALUE
    private var altitudeMax: Double = Double.MIN_VALUE
    private var derniereAltitude: Double = 0.0

    // Buffer pour flush périodique en base
    private val pointsBuffer = mutableListOf<PointTrace>()
    private var flushJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        creerCanalNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> {
                traceId = intent.getLongExtra(EXTRA_TRACE_ID, -1)
                if (traceId != -1L) demarrer()
            }
            ACTION_STOP  -> arreter()
            ACTION_PAUSE -> togglePause()
        }
        return START_STICKY   // Le système restartera le service si tué
    }

    // ── Démarrage ─────────────────────────────────────────────────────────────
    private fun demarrer() {
        debutMs = System.currentTimeMillis()
        _enregistrement.value = true

        // Acquérir le wake lock pour maintenir le CPU actif
        if (!wakeLock.isHeld) wakeLock.acquire(12 * 60 * 60 * 1000L) // max 12h

        // Démarrer en premier plan avec notification persistante
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification("Localisation en cours…"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, buildNotification("Localisation en cours…"))
        }

        // S'abonner au GPS
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                GPS_INTERVAL_MS,
                GPS_MIN_DISTANCE_M,
                this,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            arreter()
            return
        }

        // Flush en base toutes les 10 secondes
        flushJob = lifecycleScope.launch {
            while (isActive) {
                delay(10_000)
                flushBuffer()
            }
        }
    }

    // ── Réception point GPS ───────────────────────────────────────────────────
    override fun onLocationChanged(location: Location) {
        if (enPause || traceId == -1L) return

        // Filtrer les points imprécis
        if (location.accuracy > GPS_ACCURACY_THRESHOLD_M) return

        val point = GpsPoint(
            latitude  = location.latitude,
            longitude = location.longitude,
            altitude  = location.altitude,
            accuracy  = location.accuracy,
            timestamp = location.time,
            speed     = location.speed
        )

        calculerStats(point)
        dernierPoint = point

        // Ajouter au buffer
        synchronized(pointsBuffer) {
            pointsBuffer.add(PointTrace(
                traceId   = traceId,
                latitude  = point.latitude,
                longitude = point.longitude,
                altitude  = point.altitude,
                timestamp = point.timestamp,
                vitesse   = point.speed,
                precision = point.accuracy
            ))
        }

        // Mettre à jour la notification
        updateNotification()
    }

    // ── Calcul des statistiques ───────────────────────────────────────────────
    private fun calculerStats(point: GpsPoint) {
        val prev = dernierPoint

        if (prev != null) {
            // Distance 2D
            val d = GeoUtils.distance(prev.latitude, prev.longitude,
                                      point.latitude, point.longitude)
            distanceTotaleM += d

            // Dénivelé (avec filtrage du bruit)
            val deltaAlt = point.altitude - prev.altitude
            if (abs(deltaAlt) > ALTITUDE_NOISE_THRESHOLD_M) {
                if (deltaAlt > 0) dpTotalM += deltaAlt
                else dnTotalM += abs(deltaAlt)
                derniereAltitude = point.altitude
            }
        } else {
            derniereAltitude = point.altitude
        }

        // Min/Max altitude
        if (point.altitude < altitudeMin) altitudeMin = point.altitude
        if (point.altitude > altitudeMax) altitudeMax = point.altitude

        // Durée effective (hors pauses)
        val dureeEffective = System.currentTimeMillis() - debutMs - totalPauseMs
        val vitesseMoyKmh  = if (dureeEffective > 0)
            (distanceTotaleM / (dureeEffective / 1000.0) * 3.6).toFloat()
        else 0f

        _statsFlow.value = StatsEnCours(
            distanceM        = distanceTotaleM,
            dureeMs          = dureeEffective,
            vitesseMs        = point.speed,
            altitudeActuelle = point.altitude,
            denivelePositif  = dpTotalM,
            deniveleNegatif  = dnTotalM,
            nbPoints         = pointsBuffer.size,
            precisionGps     = point.accuracy
        )

        // Mettre à jour les stats en base (moins fréquemment)
        lifecycleScope.launch(Dispatchers.IO) {
            db.traceDao().updateStats(traceId, distanceTotaleM, dureeEffective,
                dpTotalM, dnTotalM, altitudeMin, altitudeMax, vitesseMoyKmh)
        }
    }

    // ── Flush buffer → base de données ───────────────────────────────────────
    private suspend fun flushBuffer() {
        val batch: List<PointTrace>
        synchronized(pointsBuffer) {
            if (pointsBuffer.isEmpty()) return
            batch = pointsBuffer.toList()
            pointsBuffer.clear()
        }
        withContext(Dispatchers.IO) {
            db.pointTraceDao().insertAll(batch)
        }
    }

    // ── Pause / Reprise ───────────────────────────────────────────────────────
    private fun togglePause() {
        enPause = !enPause
        if (enPause) {
            pauseDebutMs = System.currentTimeMillis()
            updateNotification("En pause")
        } else {
            totalPauseMs += System.currentTimeMillis() - pauseDebutMs
            updateNotification("Enregistrement en cours")
        }
    }

    // ── Arrêt ─────────────────────────────────────────────────────────────────
    private fun arreter() {
        _enregistrement.value = false
        locationManager.removeUpdates(this)
        flushJob?.cancel()

        lifecycleScope.launch {
            flushBuffer()
            if (traceId != -1L) {
                db.traceDao().terminer(traceId, TraceStatut.TERMINEE,
                    System.currentTimeMillis())
            }
            if (wakeLock.isHeld) wakeLock.release()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // ── Notification persistante ──────────────────────────────────────────────
    private fun creerCanalNotification() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Suivi GPS Randonnons",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification active pendant l'enregistrement d'une randonnée"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(texte: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, GpsTrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stats = _statsFlow.value
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Randonnons — Enregistrement actif")
            .setContentText(texte)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("📍 ${stats.distanceKm.format(2)} km  " +
                         "⏱ ${stats.dureeFormate}  " +
                         "↑ ${stats.denivelePositif.toInt()} m"))
            .setSmallIcon(R.drawable.ic_hiking)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_stop, "Arrêter", stopPi)
            .build()
    }

    private fun updateNotification(texte: String? = null) {
        val stats = _statsFlow.value
        val msg = texte ?: "${stats.distanceKm.format(2)} km · ${stats.dureeFormate}"
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(msg))
    }

    override fun onDestroy() {
        if (wakeLock.isHeld) wakeLock.release()
        locationManager.removeUpdates(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent) = super.onBind(intent)
}

private fun Double.format(decimals: Int) = "%.${decimals}f".format(this)
