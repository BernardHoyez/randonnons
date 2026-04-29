package com.randonnons.ui.waypoints

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.recyclerview.widget.*
import com.randonnons.data.repository.RandonnonsRepository
import com.randonnons.databinding.*
import com.randonnons.model.Waypoint
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class WaypointsViewModel(private val repo: RandonnonsRepository) : ViewModel() {
    val waypoints: LiveData<List<Waypoint>> = repo.getAllWaypoints()
    fun supprimer(wp: Waypoint) = viewModelScope.launch { repo.deleteWaypoint(wp) }
    fun modifier(wp: Waypoint)  = viewModelScope.launch { repo.updateWaypoint(wp) }
}

class WaypointsViewModelFactory(private val repo: RandonnonsRepository)
    : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST") return WaypointsViewModel(repo) as T
    }
}

class WaypointsFragment : Fragment() {
    private var _binding: FragmentWaypointsBinding? = null
    private val binding get() = _binding!!
    private val vm: WaypointsViewModel by viewModels {
        WaypointsViewModelFactory(RandonnonsRepository.getInstance(requireContext()))
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, b: Bundle?): View {
        _binding = FragmentWaypointsBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = WaypointAdapter(
            onEdit   = { wp -> afficherDialogEdit(wp) },
            onDelete = { wp ->
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Supprimer le waypoint « ${wp.nom} » ?")
                    .setPositiveButton("Supprimer") { _, _ -> vm.supprimer(wp) }
                    .setNegativeButton("Annuler", null)
                    .show()
            }
        )
        binding.rvWaypoints.apply {
            this.adapter = adapter
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }
        vm.waypoints.observe(viewLifecycleOwner) { adapter.submitList(it) }
    }

    private fun afficherDialogEdit(wp: Waypoint) {
        val editNom  = android.widget.EditText(requireContext()).apply { setText(wp.nom) }
        val editDesc = android.widget.EditText(requireContext()).apply { setText(wp.description) }
        val layout   = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
            addView(editNom); addView(editDesc)
        }
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Modifier le waypoint")
            .setView(layout)
            .setPositiveButton("Enregistrer") { _, _ ->
                vm.modifier(wp.copy(
                    nom         = editNom.text.toString().trim().ifEmpty { wp.nom },
                    description = editDesc.text.toString().trim()
                ))
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}

// ── Adapter ───────────────────────────────────────────────────────────────────
class WaypointAdapter(
    private val onEdit:   (Waypoint) -> Unit,
    private val onDelete: (Waypoint) -> Unit
) : ListAdapter<Waypoint, WaypointAdapter.VH>(DIFF) {

    private val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FR)

    inner class VH(val b: ItemWaypointBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemWaypointBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val wp = getItem(position)
        holder.b.apply {
            tvNom.text      = wp.nom
            tvDesc.text     = wp.description.ifEmpty { "Aucune description" }
            tvDate.text     = fmt.format(Date(wp.timestamp))
            tvCoords.text   = "%.5f, %.5f".format(wp.latitude, wp.longitude)
            tvAltitude.text = "${wp.altitude.toInt()} m"
            btnEditer.setOnClickListener   { onEdit(wp) }
            btnSupprimer.setOnClickListener { onDelete(wp) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Waypoint>() {
            override fun areItemsTheSame(a: Waypoint, b: Waypoint) = a.id == b.id
            override fun areContentsTheSame(a: Waypoint, b: Waypoint) = a == b
        }
    }
}
