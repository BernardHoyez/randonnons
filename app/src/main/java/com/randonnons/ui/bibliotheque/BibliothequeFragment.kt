package com.randonnons.ui.bibliotheque

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.recyclerview.widget.*
import com.randonnons.R
import com.randonnons.data.repository.RandonnonsRepository
import com.randonnons.databinding.*
import com.randonnons.model.*
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ── ViewModel ─────────────────────────────────────────────────────────────────
class BibliothequeViewModel(private val repo: RandonnonsRepository) : ViewModel() {

    val traces: LiveData<List<Trace>> = repo.getAllTraces()
    val routes: LiveData<List<Route>> = repo.getAllRoutes()

    fun supprimerTrace(trace: Trace) = viewModelScope.launch { repo.deleteTrace(trace) }
    fun supprimerRoute(route: Route) = viewModelScope.launch { repo.deleteRoute(route) }

    suspend fun exporterGpx(traceId: Long)  = repo.exporterGpx(traceId)
    suspend fun exporterKml(traceId: Long)  = repo.exporterKml(traceId)

    suspend fun importerFichier(uri: Uri, ctx: android.content.Context): Long {
        val stream = ctx.contentResolver.openInputStream(uri)!!
        val path = uri.path ?: ""
        return if (path.endsWith(".kml", true))
            repo.importerKml(stream)
        else
            repo.importerGpx(stream)
    }
}

class BibliothequeViewModelFactory(private val repo: RandonnonsRepository)
    : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return BibliothequeViewModel(repo) as T
    }
}

// ── Fragment ──────────────────────────────────────────────────────────────────
class BibliothequeFragment : Fragment() {

    private var _binding: FragmentBibliothequeBinding? = null
    private val binding get() = _binding!!
    private val vm: BibliothequeViewModel by viewModels {
        BibliothequeViewModelFactory(RandonnonsRepository.getInstance(requireContext()))
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { vm.importerFichier(uri, requireContext()) }
                Toast.makeText(requireContext(), "Fichier importé", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erreur : ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, b: Bundle?): View {
        _binding = FragmentBibliothequeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tracesAdapter = TraceAdapter(
            onExport = { trace, format -> exporter(trace, format) },
            onDelete  = { trace -> confirmerSuppressionTrace(trace) }
        )
        val routesAdapter = RouteAdapter(
            onDelete = { route -> confirmerSuppressionRoute(route) }
        )

        binding.rvTraces.apply {
            adapter = tracesAdapter
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }
        binding.rvRoutes.apply {
            adapter = routesAdapter
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }

        vm.traces.observe(viewLifecycleOwner) { tracesAdapter.submitList(it) }
        vm.routes.observe(viewLifecycleOwner) { routesAdapter.submitList(it) }

        binding.fabImporter.setOnClickListener {
            importLauncher.launch("*/*")
        }

        // Onglets Traces / Routes
        binding.tabLayout.addOnTabSelectedListener(object :
            com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                binding.rvTraces.visibility = if (tab.position == 0) View.VISIBLE else View.GONE
                binding.rvRoutes.visibility = if (tab.position == 1) View.VISIBLE else View.GONE
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })
    }

    private fun exporter(trace: Trace, format: String) {
        lifecycleScope.launch {
            try {
                val file = if (format == "gpx")
                    vm.exporterGpx(trace.id)
                else
                    vm.exporterKml(trace.id)
                file ?: return@launch
                partagerFichier(file)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erreur export : ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun partagerFichier(file: File) {
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (file.name.endsWith(".gpx")) "application/gpx+xml"
                   else "application/vnd.google-earth.kml+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Partager ${file.name}"))
    }

    private fun confirmerSuppressionTrace(trace: Trace) {
        AlertDialog.Builder(requireContext())
            .setTitle("Supprimer la randonnée")
            .setMessage("Supprimer « ${trace.nom} » ? Cette action est irréversible.")
            .setPositiveButton("Supprimer") { _, _ -> vm.supprimerTrace(trace) }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun confirmerSuppressionRoute(route: Route) {
        AlertDialog.Builder(requireContext())
            .setTitle("Supprimer la route")
            .setMessage("Supprimer « ${route.nom} » ?")
            .setPositiveButton("Supprimer") { _, _ -> vm.supprimerRoute(route) }
            .setNegativeButton("Annuler", null)
            .show()
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}

// ── Adapter Traces ────────────────────────────────────────────────────────────
class TraceAdapter(
    private val onExport: (Trace, String) -> Unit,
    private val onDelete:  (Trace) -> Unit
) : ListAdapter<Trace, TraceAdapter.VH>(DIFF) {

    private val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FR)

    inner class VH(val b: ItemTraceBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemTraceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val trace = getItem(position)
        holder.b.apply {
            tvNom.text      = trace.nom
            tvDate.text     = fmt.format(Date(trace.dateDebut))
            tvDistance.text = "%.1f km".format(trace.distanceM / 1000)
            tvDuree.text    = formatDuree(trace.dureeMs)
            tvDp.text       = "+${trace.denivelePositif.toInt()} m"
            tvStatut.text   = when (trace.statut) {
                TraceStatut.EN_COURS  -> "En cours"
                TraceStatut.TERMINEE  -> "Terminée"
                TraceStatut.IMPORTEE  -> "Importée"
            }
            btnExportGpx.setOnClickListener { onExport(trace, "gpx") }
            btnExportKml.setOnClickListener { onExport(trace, "kml") }
            btnSupprimer.setOnClickListener { onDelete(trace) }
        }
    }

    private fun formatDuree(ms: Long): String {
        val s = ms / 1000
        return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Trace>() {
            override fun areItemsTheSame(a: Trace, b: Trace) = a.id == b.id
            override fun areContentsTheSame(a: Trace, b: Trace) = a == b
        }
    }
}

// ── Adapter Routes ────────────────────────────────────────────────────────────
class RouteAdapter(
    private val onDelete: (Route) -> Unit
) : ListAdapter<Route, RouteAdapter.VH>(DIFF) {

    inner class VH(val b: ItemRouteBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemRouteBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val route = getItem(position)
        holder.b.apply {
            tvNom.text      = route.nom
            tvSource.text   = route.source.ifEmpty { "Import" }
            tvDistance.text = "%.1f km".format(route.distanceM / 1000)
            tvDp.text       = "+${route.denivelePositif.toInt()} m"
            btnSupprimer.setOnClickListener { onDelete(route) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Route>() {
            override fun areItemsTheSame(a: Route, b: Route) = a.id == b.id
            override fun areContentsTheSame(a: Route, b: Route) = a == b
        }
    }
}
