package com.randonnons.ui.carte

import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import com.randonnons.R
import com.randonnons.data.repository.RandonnonsRepository
import com.randonnons.databinding.FragmentCarteBinding
import com.randonnons.model.*
import com.randonnons.service.GpsTrackingService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.tilesource.FileBasedTileSource
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.*
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File

// ── ViewModel ─────────────────────────────────────────────────────────────────
class CarteViewModel(private val repo: RandonnonsRepository) : ViewModel() {

    val cartes: LiveData<List<CarteMBTiles>> = repo.getAllCartes()

    // Trace de navigation chargée sur la carte
    private val _routeAffichee = MutableLiveData<RouteAvecPoints?>()
    val routeAffichee: LiveData<RouteAvecPoints?> = _routeAffichee

    private val _traceAffichee = MutableLiveData<TraceAvecPoints?>()
    val traceAffichee: LiveData<TraceAvecPoints?> = _traceAffichee

    fun chargerTrace(id: Long) = viewModelScope.launch {
        _traceAffichee.value = repo.getTraceAvecPoints(id)
    }

    fun chargerRoute(id: Long) = viewModelScope.launch {
        _routeAffichee.value = repo.getRouteAvecPoints(id)
    }
}

class CarteViewModelFactory(private val repo: RandonnonsRepository)
    : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return CarteViewModel(repo) as T
    }
}

// ── Fragment ──────────────────────────────────────────────────────────────────
class CarteFragment : Fragment() {

    private var _binding: FragmentCarteBinding? = null
    private val binding get() = _binding!!
    private val vm: CarteViewModel by viewModels {
        CarteViewModelFactory(RandonnonsRepository.getInstance(requireContext()))
    }

    private lateinit var map: MapView
    private var locationOverlay: MyLocationNewOverlay? = null
    private var tracePolyline: Polyline? = null
    private var livePolyline: Polyline? = null
    private val waypointMarkers = mutableListOf<Marker>()
    private var liveJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCarteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        map = binding.mapView
        configurerCarte()
        configurerLocalisation()
        observerCartes()
        observerTrace()
        observerTraceEnDirect()

        binding.fabCentrer.setOnClickListener { centrerSurPosition() }
        binding.fabAjouterWaypoint.setOnClickListener { ajouterWaypointAuCentre() }
    }

    // ── Configuration OSMDroid ────────────────────────────────────────────────
    private fun configurerCarte() {
        map.apply {
            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(13.0)
            // Centre par défaut sur la France
            controller.setCenter(GeoPoint(46.5, 2.5))
            minZoomLevel = 4.0
            maxZoomLevel = 19.0
        }
    }

    private fun configurerLocalisation() {
        locationOverlay = MyLocationNewOverlay(
            GpsMyLocationProvider(requireContext()), map
        ).apply {
            enableMyLocation()
            enableFollowLocation()
        }
        map.overlays.add(locationOverlay)
    }

    // ── Chargement des tuiles MBTiles ─────────────────────────────────────────
    private fun observerCartes() {
        vm.cartes.observe(viewLifecycleOwner) { cartes ->
            val cartesActives = cartes.filter { it.active && File(it.cheminFichier).exists() }
            if (cartesActives.isNotEmpty()) {
                chargerMBTiles(cartesActives.map { File(it.cheminFichier) })
            }
        }
    }

    private fun chargerMBTiles(fichiers: List<File>) {
        try {
            val receiver = SimpleRegisterReceiver(requireContext())
            val provider = OfflineTileProvider(receiver, fichiers.toTypedArray())
            map.tileProvider = provider
            map.invalidate()
        } catch (e: Exception) {
            // Fallback sur tuiles OSM en ligne
        }
    }

    // ── Affichage trace/route ─────────────────────────────────────────────────
    private fun observerTrace() {
        vm.traceAffichee.observe(viewLifecycleOwner) { trace ->
            trace ?: return@observe
            afficherTrace(trace)
        }
        vm.routeAffichee.observe(viewLifecycleOwner) { route ->
            route ?: return@observe
            afficherRoute(route)
        }
    }

    private fun afficherTrace(trace: TraceAvecPoints) {
        // Supprimer ancienne trace
        tracePolyline?.let { map.overlays.remove(it) }
        waypointMarkers.forEach { map.overlays.remove(it) }
        waypointMarkers.clear()

        if (trace.points.isEmpty()) return

        // Dessiner la trace
        val pts = trace.points.map { GeoPoint(it.latitude, it.longitude) }
        tracePolyline = Polyline().apply {
            setPoints(pts)
            outlinePaint.color = Color.parseColor("#E8593C")
            outlinePaint.strokeWidth = 5f
            outlinePaint.isAntiAlias = true
        }
        map.overlays.add(tracePolyline)

        // Waypoints
        trace.waypoints.forEach { wp ->
            val marker = Marker(map).apply {
                position = GeoPoint(wp.latitude, wp.longitude)
                title = wp.nom
                snippet = wp.description
            }
            map.overlays.add(marker)
            waypointMarkers.add(marker)
        }

        // Centrer sur la trace
        if (pts.isNotEmpty()) {
            map.controller.animateTo(pts.first())
            map.controller.setZoom(14.0)
        }
        map.invalidate()
    }

    private fun afficherRoute(route: RouteAvecPoints) {
        tracePolyline?.let { map.overlays.remove(it) }
        val pts = route.points.sortedBy { it.ordre }
            .map { GeoPoint(it.latitude, it.longitude) }
        tracePolyline = Polyline().apply {
            setPoints(pts)
            outlinePaint.color = Color.parseColor("#1D9E75")
            outlinePaint.strokeWidth = 4f
            outlinePaint.strokeCap = Paint.Cap.ROUND
        }
        map.overlays.add(tracePolyline)
        if (pts.isNotEmpty()) map.controller.animateTo(pts.first())
        map.invalidate()
    }

    // ── Trace GPS en direct ───────────────────────────────────────────────────
    private fun observerTraceEnDirect() {
        liveJob = lifecycleScope.launch {
            GpsTrackingService.statsFlow.collectLatest { stats ->
                if (GpsTrackingService.enregistrement.value) {
                    // La mise à jour de livePolyline serait ici
                    // On se contente d'invalider la carte pour l'overlay de position
                    map.invalidate()
                }
            }
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────
    private fun centrerSurPosition() {
        locationOverlay?.myLocation?.let {
            map.controller.animateTo(it)
            map.controller.setZoom(16.0)
        }
    }

    private fun ajouterWaypointAuCentre() {
        // Déclenche le dialog de création de waypoint via le fragment Randonnée
        parentFragmentManager.setFragmentResult("ajouterWaypoint", Bundle().apply {
            putDouble("lat", map.mapCenter.latitude)
            putDouble("lon", map.mapCenter.longitude)
        })
    }

    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause()  { super.onPause();  map.onPause() }

    override fun onDestroyView() {
        liveJob?.cancel()
        locationOverlay?.disableMyLocation()
        _binding = null
        super.onDestroyView()
    }
}
