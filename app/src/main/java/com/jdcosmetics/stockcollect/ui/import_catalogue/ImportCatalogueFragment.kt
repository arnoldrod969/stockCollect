package com.jdcosmetics.stockcollect.ui.import_catalogue

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.jdcosmetics.stockcollect.R
import com.jdcosmetics.stockcollect.databinding.FragmentImportCatalogueBinding
import com.jdcosmetics.stockcollect.util.DateUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ImportCatalogueFragment : Fragment() {

    private var _binding: FragmentImportCatalogueBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ImportCatalogueViewModel by viewModels()

    private val pickCatalogueLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            confirmerEtImporterCatalogue(uri)
        }
    }

    private val pickCorrespondanceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            viewModel.importerCorrespondance(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImportCatalogueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnChoisirCatalogue.setOnClickListener {
            ouvrirSelecteurFichier(pickCatalogueLauncher)
        }
        binding.btnChoisirCorrespondance.setOnClickListener {
            ouvrirSelecteurFichier(pickCorrespondanceLauncher)
        }
    }

    private fun observeViewModel() {
        viewModel.nbArticles.observe(viewLifecycleOwner) { nb ->
            binding.tvNbArticles.text = nb.toString()
        }
        viewModel.nbCorrespondances.observe(viewLifecycleOwner) { nb ->
            binding.tvNbCorrespondances.text = nb.toString()
        }
        viewModel.dernierImport.observe(viewLifecycleOwner) { date ->
            binding.tvDernierImport.text = if (date != null) DateUtils.toDisplayDate(date)
            else "\u2014"
        }

        viewModel.catalogueState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ImportUiState.Loading -> {
                    binding.progressCatalogue.isVisible = true
                    binding.btnChoisirCatalogue.isEnabled = false
                }
                is ImportUiState.Success -> {
                    binding.progressCatalogue.isVisible = false
                    binding.btnChoisirCatalogue.isEnabled = true
                    afficherDialogResultat("Import catalogue", state.result.toResume(), state.result.erreurs)
                    viewModel.resetCatalogueState()
                }
                is ImportUiState.Error -> {
                    binding.progressCatalogue.isVisible = false
                    binding.btnChoisirCatalogue.isEnabled = true
                    afficherErreur(state.message)
                    viewModel.resetCatalogueState()
                }
                else -> {
                    binding.progressCatalogue.isVisible = false
                    binding.btnChoisirCatalogue.isEnabled = true
                }
            }
        }

        viewModel.correspondanceState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ImportUiState.Loading -> {
                    binding.progressCorrespondance.isVisible = true
                    binding.btnChoisirCorrespondance.isEnabled = false
                }
                is ImportUiState.Success -> {
                    binding.progressCorrespondance.isVisible = false
                    binding.btnChoisirCorrespondance.isEnabled = true
                    afficherDialogResultat("Import correspondance CB", state.result.toResume(), state.result.erreurs)
                    viewModel.resetCorrespondanceState()
                }
                is ImportUiState.Error -> {
                    binding.progressCorrespondance.isVisible = false
                    binding.btnChoisirCorrespondance.isEnabled = true
                    afficherErreur(state.message)
                    viewModel.resetCorrespondanceState()
                }
                else -> {
                    binding.progressCorrespondance.isVisible = false
                    binding.btnChoisirCorrespondance.isEnabled = true
                }
            }
        }
    }

    private fun confirmerEtImporterCatalogue(uri: Uri) {
        val nbActuels = viewModel.nbArticles.value ?: 0
        if (nbActuels > 0) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Remplacer le catalogue ?")
                .setMessage("Le catalogue actuel ($nbActuels articles) sera remplac\u00e9. Cette action est irr\u00e9versible.")
                .setPositiveButton("Remplacer") { _, _ -> viewModel.importerCatalogue(uri) }
                .setNegativeButton("Annuler", null)
                .show()
        } else {
            viewModel.importerCatalogue(uri)
        }
    }

    private fun ouvrirSelecteurFichier(launcher: ActivityResultLauncher<Intent>) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        launcher.launch(intent)
    }

    private fun afficherDialogResultat(titre: String, message: String, erreurs: List<String>) {
        val detail = if (erreurs.isNotEmpty()) {
            "$message\n\nD\u00e9tails erreurs :\n${erreurs.take(5).joinToString("\n")}" +
            if (erreurs.size > 5) "\n... et ${erreurs.size - 5} autres" else ""
        } else message

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titre)
            .setMessage(detail)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun afficherErreur(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
