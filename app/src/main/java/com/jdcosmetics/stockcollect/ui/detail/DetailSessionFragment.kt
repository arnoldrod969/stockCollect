package com.jdcosmetics.stockcollect.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.jdcosmetics.stockcollect.data.db.entity.StatutSession
import com.jdcosmetics.stockcollect.data.db.entity.TypeOperation
import com.jdcosmetics.stockcollect.databinding.FragmentDetailSessionBinding
import com.jdcosmetics.stockcollect.util.DateUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DetailSessionFragment : Fragment() {

    private var _binding: FragmentDetailSessionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetailSessionViewModel by viewModels()
    private val args: DetailSessionFragmentArgs by navArgs()
    private lateinit var adapter: DetailLigneAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailSessionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = DetailLigneAdapter()
        binding.rvLignes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@DetailSessionFragment.adapter
        }
        observeViewModel()
        viewModel.charger(args.idSession)
    }

    private fun observeViewModel() {
        viewModel.session.observe(viewLifecycleOwner) { session ->
            session ?: return@observe
            binding.tvTypeOperation.text = TypeOperation.label(session.typeOperation)
            binding.tvDateHeure.text = DateUtils.toDisplay(session.dateHeureDebut)
            binding.tvNbLignes.text = "${session.nbLignes} ligne${if (session.nbLignes > 1) "s" else ""}"
            binding.tvLieu.text = session.lieu?.let { "Lieu : $it" } ?: ""
            binding.tvObservations.text = session.observations?.let { "Obs. : $it" } ?: ""
            val statutTexte = when (session.statut) {
                StatutSession.BROUILLON -> "Brouillon"
                StatutSession.CLOTUREE -> "Clôturée"
                StatutSession.EXPORTEE -> "Exportée"
                else -> session.statut
            }
            binding.tvStatut.text = statutTexte
        }

        viewModel.lignes.observe(viewLifecycleOwner) { lignes ->
            adapter.submitList(lignes)
            binding.tvAucuneLigne.isVisible = lignes.isEmpty()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
