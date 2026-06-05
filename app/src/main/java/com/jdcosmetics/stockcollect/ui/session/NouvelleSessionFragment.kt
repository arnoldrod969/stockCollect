package com.jdcosmetics.stockcollect.ui.session

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.jdcosmetics.stockcollect.R
import com.jdcosmetics.stockcollect.data.db.entity.TypeOperation
import com.jdcosmetics.stockcollect.databinding.FragmentNouvelleSessionBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NouvelleSessionFragment : Fragment() {

    private var _binding: FragmentNouvelleSessionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SaisieViewModel by activityViewModels()
    private var typeSelectionne = TypeOperation.INVENTAIRE

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNouvelleSessionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTypeSelection()
        setupListeners()
        observeViewModel()
    }

    private fun setupTypeSelection() {
        marquerTypeSelectionne(TypeOperation.INVENTAIRE)

        binding.cardInventaire.setOnClickListener {
            typeSelectionne = TypeOperation.INVENTAIRE
            marquerTypeSelectionne(TypeOperation.INVENTAIRE)
        }
        binding.cardEntree.setOnClickListener {
            typeSelectionne = TypeOperation.ENTREE
            marquerTypeSelectionne(TypeOperation.ENTREE)
        }
        binding.cardSortie.setOnClickListener {
            typeSelectionne = TypeOperation.SORTIE
            marquerTypeSelectionne(TypeOperation.SORTIE)
        }
    }

    private fun marquerTypeSelectionne(type: String) {
        binding.cardInventaire.strokeWidth = if (type == TypeOperation.INVENTAIRE) 3 else 0
        binding.cardEntree.strokeWidth = if (type == TypeOperation.ENTREE) 3 else 0
        binding.cardSortie.strokeWidth = if (type == TypeOperation.SORTIE) 3 else 0
    }

    private fun setupListeners() {
        binding.btnCommencerSaisie.setOnClickListener {
            val lieu = binding.etLieu.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            val observations = binding.etObservations.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            viewModel.creerSession(typeSelectionne, lieu, observations)
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is SaisieUiState.SessionCreee -> {
                    val action = NouvelleSessionFragmentDirections
                        .actionNouvelleSessionToSaisie(state.idSession)
                    findNavController().navigate(action)
                    viewModel.resetState()
                }
                is SaisieUiState.Erreur -> {
                    binding.btnCommencerSaisie.isEnabled = true
                }
                is SaisieUiState.Loading -> {
                    binding.btnCommencerSaisie.isEnabled = false
                }
                else -> binding.btnCommencerSaisie.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
