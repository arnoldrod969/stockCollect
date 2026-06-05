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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.jdcosmetics.stockcollect.databinding.FragmentExportBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ExportFragment : Fragment() {

    private var _binding: FragmentExportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ExportViewModel by viewModels()
    private lateinit var adapter: SessionExportAdapter

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
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = SessionExportAdapter { idSession ->
            viewModel.toggleSelection(idSession)
            majBoutonExport()
        }
        binding.rvSessions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = this@ExportFragment.adapter
        }
    }

    private fun setupListeners() {
        binding.btnExporter.setOnClickListener {
            lancerCreationFichier()
        }
    }

    private fun observeViewModel() {
        viewModel.sessionsCloturees.observe(viewLifecycleOwner) { sessions ->
            adapter.submitList(sessions)
            binding.tvAucuneSession.isVisible = sessions.isEmpty()
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

    private fun majBoutonExport() {
        binding.btnExporter.isEnabled = adapter.currentList.any {
            viewModel.isSelectionne(it.idSession)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
