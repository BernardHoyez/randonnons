package com.randonnons.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.randonnons.R
import com.randonnons.data.repository.RandonnonsRepository
import com.randonnons.databinding.ActivityMainBinding
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Demande de permissions localisation
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val fine = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (!fine) {
            Toast.makeText(this,
                "La localisation GPS est requise pour enregistrer une randonnée",
                Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Navigation component
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController
        binding.bottomNav.setupWithNavController(navController)

        // Demander les permissions au démarrage
        demanderPermissions()

        // Gérer l'intent d'import GPX/KML depuis un gestionnaire de fichiers
        handleImportIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleImportIntent(intent)
    }

    private fun handleImportIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri: Uri = intent.data ?: return

        scope.launch {
            try {
                val repo = RandonnonsRepository.getInstance(applicationContext)
                val stream = contentResolver.openInputStream(uri) ?: return@launch
                val path = uri.path ?: ""
                val traceId = when {
                    path.endsWith(".kml", true) ->
                        withContext(Dispatchers.IO) { repo.importerKml(stream) }
                    else ->
                        withContext(Dispatchers.IO) { repo.importerGpx(stream) }
                }
                stream.close()
                Toast.makeText(this@MainActivity,
                    "Fichier importé avec succès", Toast.LENGTH_SHORT).show()
                // Naviguer vers la bibliothèque
                val navHost = supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                navHost.navController.navigate(R.id.nav_bibliotheque)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity,
                    "Erreur d'import : ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun demanderPermissions() {
        val permsRequises = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permsRequises.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val manquantes = permsRequises.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (manquantes.isNotEmpty()) permissionLauncher.launch(manquantes.toTypedArray())
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
