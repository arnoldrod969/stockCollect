package com.jdcosmetics.stockcollect.ui.session

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.jdcosmetics.stockcollect.databinding.FragmentRecapitulatifBinding
import com.jdcosmetics.stockcollect.util.DateUtils
import com.jdcosmetics.stockcollect.util.FormatUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RecapitulatifFragment : Fragment() {

    private var _binding: FragmentRecapitulatifBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SaisieViewModel by activityViewModels()
    private val args: RecapitulatifFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecapitulatifBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnConfirmerCloture.setOnClickListener {
            viewModel.cloturer()
        }

        binding.btnRetourSaisie.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun observeViewModel() {
        viewModel.sessionCourante.observe(viewLifecycleOwner) { session ->
            session ?: return@observe
            binding.tvTypeOperation.text = com.jdcosmetics.stockcollect.data.db.entity.TypeOperation.label(session.typeOperation)
            binding.tvDateHeure.text = DateUtils.toDisplay(session.dateHeureDebut)
            binding.tvNbLignesTotal.text = "${session.nbLignes} ligne${if (session.nbLignes > 1) "s" else ""}"
        }

        viewModel.lignes.observe(viewLifecycleOwner) { lignes ->
            val apercu = lignes.take(3)
            binding.tvApercuLignes.text = apercu.joinToString("\n") {
                "• ${it.nomProduitSnap}  →  ${FormatUtils.formatQuantite(it.quantite)}"
            }
            val restant = lignes.size - apercu.size
            binding.tvPlusLignes.visibility = if (restant > 0) View.VISIBLE else View.GONE
            binding.tvPlusLignes.text = "+ $restant autre${if (restant > 1) "s" else ""} ligne${if (restant > 1) "s" else ""}..."
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is SaisieUiState.SessionCloturee -> {
                    findNavController().navigate(
                        RecapitulatifFragmentDirections.actionRecapitulatifToHome()
                    )
                    viewModel.resetState()
                }
                is SaisieUiState.Loading -> {
                    binding.btnConfirmerCloture.isEnabled = false
                }
                else -> binding.btnConfirmerCloture.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
