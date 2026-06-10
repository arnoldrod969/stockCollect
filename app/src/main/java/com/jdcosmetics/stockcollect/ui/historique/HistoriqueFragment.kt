package com.jdcosmetics.stockcollect.ui.historique

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.jdcosmetics.stockcollect.data.db.entity.StatutSession
import com.jdcosmetics.stockcollect.databinding.FragmentHistoriqueBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HistoriqueFragment : Fragment() {

    private var _binding: FragmentHistoriqueBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoriqueViewModel by viewModels()
    private lateinit var adapter: HistoriqueAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoriqueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = HistoriqueAdapter(
            onItemClick = { session ->
                when (session.statut) {
                    StatutSession.BROUILLON -> {
                        val action = HistoriqueFragmentDirections.actionHistoriqueToSaisie(session.idSession)
                        findNavController().navigate(action)
                    }
                    else -> {
                        val action = HistoriqueFragmentDirections.actionHistoriqueToDetail(session.idSession)
                        findNavController().navigate(action)
                    }
                }
            }
        )
        binding.rvSessions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = this@HistoriqueFragment.adapter
        }

        binding.chipTous.setOnClickListener { viewModel.filtrer(null) }
        binding.chipInventaire.setOnClickListener { viewModel.filtrer("INVENTAIRE") }
        binding.chipEntree.setOnClickListener { viewModel.filtrer("ENTREE") }
        binding.chipSortie.setOnClickListener { viewModel.filtrer("SORTIE") }

        binding.btnExporter.setOnClickListener {
            findNavController().navigate(
                HistoriqueFragmentDirections.actionHistoriqueToExport()
            )
        }

        viewModel.sessions.observe(viewLifecycleOwner) { sessions ->
            adapter.submitList(sessions)
            binding.tvAucuneSession.isVisible = sessions.isEmpty()
        }

        viewModel.aClotureeExportable.observe(viewLifecycleOwner) { exportable ->
            binding.btnExporter.isVisible = exportable
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
