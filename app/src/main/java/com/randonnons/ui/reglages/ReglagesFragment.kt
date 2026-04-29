package com.randonnons.ui.reglages

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.recyclerview.widget.*
import com.randonnons.data.repository.RandonnonsRepository
import com.randonnons.databinding.*
import com.randonnons.model.CarteMBTiles
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

class ReglagesViewModel(private val repo: RandonnonsRepository) : ViewModel() {
    val cartes: LiveData<List<CarteMBTiles>> = repo.getAllCartes()
    fun toggleCarte(carte: CarteMBTiles) = viewModelScope.launch {
        repo.updateCarte(carte.copy(active = !carte.active))
    }
    fun supprimerCarte(carte: CarteMBTiles) = viewModelScope.launch {
        repo.deleteCarte(carte)
    }
    suspend fun ajouterCarte(carte: CarteMBTiles) = repo.ajouterCarte(carte)
}

class ReglagesViewModelFactory(private val repo: RandonnonsRepository)
    : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST") return ReglagesViewModel(repo) as T
    }
}

class ReglagesFragment : Fragment() {
    private var _binding: FragmentReglagesBinding? = null
    private val binding get() = _binding!!
    private val vm: ReglagesViewModel by viewModels {
        ReglagesViewModelFactory(RandonnonsRepository.getInstance(requireContext()))
    }

    // Picker de fichier MBTiles
    private val mbtilesPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { importerMBTiles(it) } }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, b: Bundle?): View {
        _binding = FragmentReglagesBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = CartesAdapter(
            onToggle  = { carte -> vm.toggleCarte(carte) },
            onDelete  = { carte -> confirmerSuppression(carte) }
        )
        binding.rvCartes.apply {
            this.adapter = adapter
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }
        vm.cartes.observe(viewLifecycleOwner) { adapter.submitList(it) }

        binding.btnAjouterCarte.setOnClickListener {
            mbtilesPicker.launch("*/*")
        }

        // Préférences unités
        val prefs = requireContext().getSharedPreferences("randonnons", android.content.Context.MODE_PRIVATE)
        binding.switchMetrique.isChecked = prefs.getBoolean("unites_metriques", true)
        binding.switchMetrique.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("unites_metriques", checked).apply()
        }
        binding.switchGarderEcran.isChecked = prefs.getBoolean("garder_ecran", false)
        binding.switchGarderEcran.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("garder_ecran", checked).apply()
            if (checked) requireActivity().window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else requireActivity().window.clearFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun importerMBTiles(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cr   = requireContext().contentResolver
                val nom  = cr.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    if (idx >= 0) cursor.getString(idx) else "carte.mbtiles"
                } ?: "carte.mbtiles"

                // Copier dans le répertoire privé de l'app
                val dest = File(requireContext().getExternalFilesDir("mbtiles"), nom)
                    .also { it.parentFile?.mkdirs() }
                cr.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }

                val taille = dest.length()
                val carte  = CarteMBTiles(
                    nom           = nom.removeSuffix(".mbtiles"),
                    cheminFichier = dest.absolutePath,
                    tailleOctets  = taille
                )
                vm.ajouterCarte(carte)

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(),
                        "Carte ajoutée : ${carte.nom}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(),
                        "Erreur import carte : ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun confirmerSuppression(carte: CarteMBTiles) {
        AlertDialog.Builder(requireContext())
            .setTitle("Supprimer la carte « ${carte.nom} » ?")
            .setMessage("Le fichier MBTiles sera supprimé du stockage.")
            .setPositiveButton("Supprimer") { _, _ ->
                File(carte.cheminFichier).delete()
                vm.supprimerCarte(carte)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}

// ── Adapter cartes MBTiles ────────────────────────────────────────────────────
class CartesAdapter(
    private val onToggle: (CarteMBTiles) -> Unit,
    private val onDelete: (CarteMBTiles) -> Unit
) : ListAdapter<CarteMBTiles, CartesAdapter.VH>(DIFF) {

    inner class VH(val b: ItemCarteMbtilesBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCarteMbtilesBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val carte = getItem(position)
        holder.b.apply {
            tvNom.text     = carte.nom
            tvTaille.text  = formatTaille(carte.tailleOctets)
            tvZoom.text    = "Zoom ${carte.zoomMin}–${carte.zoomMax}"
            switchActive.isChecked = carte.active
            switchActive.setOnCheckedChangeListener { _, _ -> onToggle(carte) }
            btnSupprimer.setOnClickListener { onDelete(carte) }
        }
    }

    private fun formatTaille(octets: Long): String = when {
        octets >= 1_073_741_824 -> "%.1f Go".format(octets / 1_073_741_824.0)
        octets >= 1_048_576     -> "%.1f Mo".format(octets / 1_048_576.0)
        else                    -> "%.0f Ko".format(octets / 1_024.0)
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<CarteMBTiles>() {
            override fun areItemsTheSame(a: CarteMBTiles, b: CarteMBTiles) = a.id == b.id
            override fun areContentsTheSame(a: CarteMBTiles, b: CarteMBTiles) = a == b
        }
    }
}
