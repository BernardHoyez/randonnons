package com.randonnons.ui.randonnee

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.randonnons.R
import com.randonnons.data.repository.RandonnonsRepository
import com.randonnons.databinding.FragmentRandonneeBinding
import com.randonnons.model.*
import com.randonnons.service.GpsTrackingService
import com.randonnons.util.GeoUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

// ── ViewModel ─────────────────────────────────────────────────────────────────
class RandonneeViewModel(private val repo: RandonnonsRepository) : ViewModel() {

    private val _traceIdEnCours = MutableLiveData<Long?>()
    val traceIdEnCours: LiveData<Long?> = _traceIdEnCours

    private val _enregistrement = MutableLiveData(false)
    val enregistrement: LiveData<Boolean> = _enregistrement

    fun demarrerTrace(nom: String) = viewModelScope.launch {
        val id = repo.creerTrace(nom)
        _traceIdEnCours.value = id
        _enregistrement.value = true
    }

    fun terminerTrace() {
        _enregistrement.value = false
        _traceIdEnCours.value = null
    }

    fun getTraceId() = _traceIdEnCours.value

    suspend fun chargerProfilAltimetrique(traceId: Long): List<GeoUtils.ProfilPoint> {
        val trace = repo.getTraceAvecPoints(traceId) ?: return emptyList()
        return GeoUtils.profilAltimetrique(trace.points)
    }
}

class RandonneeViewModelFactory(private val repo: RandonnonsRepository)
    : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return RandonneeViewModel(repo) as T
    }
}

// ── Fragment ──────────────────────────────────────────────────────────────────
class RandonneeFragment : Fragment() {

    private var _binding: FragmentRandonneeBinding? = null
    private val binding get() = _binding!!
    private val vm: RandonneeViewModel by viewModels {
        RandonneeViewModelFactory(RandonnonsRepository.getInstance(requireContext()))
    }
    private var statsJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRandonneeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configurerGraphiqueAlt()
        observerStats()
        configurerBoutons()
    }

    // ── Graphique altimétrique ────────────────────────────────────────────────
    private fun configurerGraphiqueAlt() {
        binding.chartAltitude.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
            xAxis.apply {
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(v: Float) = "${(v / 1000).toInt()} km"
                }
            }
            axisRight.isEnabled = false
            axisLeft.apply {
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(v: Float) = "${v.toInt()} m"
                }
            }
        }
    }

    // ── Collecte des stats GPS en temps réel ──────────────────────────────────
    private fun observerStats() {
        statsJob = lifecycleScope.launch {
            GpsTrackingService.statsFlow.collectLatest { stats ->
                afficherStats(stats)
            }
        }
        vm.enregistrement.observe(viewLifecycleOwner) { enCours ->
            binding.btnDemarrer.text = if (enCours) "Pause" else "Démarrer"
            binding.btnTerminer.isEnabled = enCours
            binding.btnTerminer.alpha = if (enCours) 1f else 0.4f
        }
    }

    private fun afficherStats(stats: StatsEnCours) {
        binding.apply {
            tvDistance.text  = "%.2f km".format(stats.distanceKm)
            tvDuree.text     = stats.dureeFormate
            tvVitesse.text   = "%.1f km/h".format(stats.vitesseKmh)
            tvAltitude.text  = "${stats.altitudeActuelle.toInt()} m"
            tvDpositif.text  = "+${stats.denivelePositif.toInt()} m"
            tvDnegatif.text  = "-${stats.deniveleNegatif.toInt()} m"
            tvPrecision.text = "±${stats.precisionGps.toInt()} m"
        }
    }

    // ── Boutons contrôle ──────────────────────────────────────────────────────
    private fun configurerBoutons() {
        binding.btnDemarrer.setOnClickListener {
            if (GpsTrackingService.enregistrement.value) {
                // Pause
                val intent = Intent(requireContext(), GpsTrackingService::class.java)
                    .setAction(GpsTrackingService.ACTION_PAUSE)
                requireContext().startService(intent)
            } else {
                // Démarrer
                afficherDialogNom()
            }
        }

        binding.btnTerminer.setOnClickListener {
            val intent = Intent(requireContext(), GpsTrackingService::class.java)
                .setAction(GpsTrackingService.ACTION_STOP)
            requireContext().startService(intent)
            vm.terminerTrace()
            // Rafraîchir le graphique avec le profil final
            vm.getTraceId()?.let { traceId ->
                lifecycleScope.launch {
                    val profil = vm.chargerProfilAltimetrique(traceId)
                    afficherGraphiqueAlt(profil)
                }
            }
        }

        binding.btnWaypoint.setOnClickListener {
            afficherDialogWaypoint()
        }
    }

    private fun afficherDialogNom() {
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("Nouvelle randonnée")
            .setView(R.layout.dialog_nom_trace)
            .setPositiveButton("Démarrer") { d, _ ->
                val editText = (d as android.app.AlertDialog)
                    .findViewById<android.widget.EditText>(R.id.et_nom_trace)
                val nom = editText?.text?.toString()?.trim()
                    ?.ifEmpty { "Randonnée ${java.text.SimpleDateFormat("dd/MM HH:mm",
                        java.util.Locale.FR).format(java.util.Date())}" }
                    ?: "Randonnée"
                demarrerEnregistrement(nom)
            }
            .setNegativeButton("Annuler", null)
            .create()
        dialog.show()
    }

    private fun demarrerEnregistrement(nom: String) {
        vm.demarrerTrace(nom)
        // Démarrer le Foreground Service après que la trace est créée en base
        vm.traceIdEnCours.observe(viewLifecycleOwner) { traceId ->
            traceId ?: return@observe
            val intent = Intent(requireContext(), GpsTrackingService::class.java).apply {
                action = GpsTrackingService.ACTION_START
                putExtra(GpsTrackingService.EXTRA_TRACE_ID, traceId)
            }
            requireContext().startForegroundService(intent)
        }
    }

    private fun afficherDialogWaypoint() {
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("Ajouter un waypoint")
            .setView(R.layout.dialog_waypoint)
            .setPositiveButton("Ajouter") { d, _ ->
                val dlg = d as android.app.AlertDialog
                val nom  = dlg.findViewById<android.widget.EditText>(R.id.et_wp_nom)
                    ?.text?.toString()?.trim() ?: "Waypoint"
                val desc = dlg.findViewById<android.widget.EditText>(R.id.et_wp_desc)
                    ?.text?.toString()?.trim() ?: ""
                val lastStats = GpsTrackingService.statsFlow.value
                lifecycleScope.launch {
                    val repo = RandonnonsRepository.getInstance(requireContext())
                    // Récupérer la position actuelle depuis le LocationManager
                    // Pour simplifier, on utilise les stats (altitude connue)
                    // Le lat/lon réel sera ajouté via le fragment Carte
                    repo.ajouterWaypoint(Waypoint(
                        traceId = vm.getTraceId(),
                        nom = nom,
                        description = desc,
                        latitude = 0.0,   // rempli par CarteFragment
                        longitude = 0.0,
                        altitude = lastStats.altitudeActuelle
                    ))
                }
            }
            .setNegativeButton("Annuler", null)
            .create()
        dialog.show()
    }

    private fun afficherGraphiqueAlt(profil: List<GeoUtils.ProfilPoint>) {
        if (profil.isEmpty()) return
        val entries = profil.map { Entry(it.distanceCumuleeM.toFloat(), it.altitude.toFloat()) }
        val dataSet = LineDataSet(entries, "Altitude").apply {
            color = android.graphics.Color.parseColor("#E8593C")
            setDrawCircles(false)
            lineWidth = 2f
            setDrawFilled(true)
            fillAlpha = 40
            fillColor = android.graphics.Color.parseColor("#E8593C")
        }
        binding.chartAltitude.data = LineData(dataSet)
        binding.chartAltitude.invalidate()
    }

    override fun onDestroyView() {
        statsJob?.cancel()
        _binding = null
        super.onDestroyView()
    }
}
