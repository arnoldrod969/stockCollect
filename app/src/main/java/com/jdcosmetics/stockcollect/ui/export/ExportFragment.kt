package com.jdcosmetics.stockcollect.ui.export

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import com.jdcosmetics.stockcollect.data.db.entity.StatutSession
import com.jdcosmetics.stockcollect.data.db.entity.TypeOperation
import com.jdcosmetics.stockcollect.databinding.FragmentExportBinding
import com.jdcosmetics.stockcollect.util.DateUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ExportFragment : Fragment() {

    private var _binding: FragmentExportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ExportViewModel by viewModels()

    private val createFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            viewModel.exporter(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnExporter.setOnClickListener {
            lancerCreationFichier()
        }
    }

    private fun observeViewModel() {
        viewModel.sessionExportable.observe(viewLifecycleOwner) { session ->
            if (session != null) {
                binding.groupSession.isVisible = true
                binding.tvAucuneSession.isVisible = false
                binding.btnExporter.isEnabled = true

                binding.tvTypeOperation.text = TypeOperation.label(session.typeOperation)
                binding.tvDateHeure.text = DateUtils.toDisplay(session.dateHeureDebut)
                binding.tvNbLignes.text = "${session.nbLignes} ligne${if (session.nbLignes > 1) "s" else ""}"
                val statutTexte = when (session.statut) {
                    StatutSession.CLOTUREE -> "Clôturée"
                    StatutSession.EXPORTEE -> "Exportée"
                    else -> session.statut
                }
                binding.tvStatut.text = statutTexte
            } else {
                binding.groupSession.isVisible = false
                binding.tvAucuneSession.isVisible = true
                binding.btnExporter.isEnabled = false
            }
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ExportUiState.Loading -> {
                    binding.progressExport.isVisible = true
                    binding.btnExporter.isEnabled = false
                }
                is ExportUiState.Succes -> {
                    binding.progressExport.isVisible = false
                    binding.btnExporter.isEnabled = true
                    Snackbar.make(
                        binding.root,
                        "\u2713 ${state.nomFichier} (${state.nbLignes} lignes) \u2014 enregistré dans Téléchargements",
                        Snackbar.LENGTH_LONG
                    ).show()
                    viewModel.resetState()
                }
                is ExportUiState.Erreur -> {
                    binding.progressExport.isVisible = false
                    binding.btnExporter.isEnabled = true
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                    viewModel.resetState()
                }
                else -> {
                    binding.progressExport.isVisible = false
                }
            }
        }
    }

    private fun lancerCreationFichier() {
        val nomFichier = viewModel.getNomFichier()
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, nomFichier)
        }
        createFileLauncher.launch(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
