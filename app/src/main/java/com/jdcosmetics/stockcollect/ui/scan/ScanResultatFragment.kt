package com.jdcosmetics.stockcollect.ui.scan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.jdcosmetics.stockcollect.R
import com.jdcosmetics.stockcollect.databinding.FragmentScanResultatBinding
import com.jdcosmetics.stockcollect.domain.service.ResolutionResult
import com.jdcosmetics.stockcollect.ui.session.SaisieViewModel
import com.jdcosmetics.stockcollect.util.FormatUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ScanResultatFragment : Fragment() {

    private var _binding: FragmentScanResultatBinding? = null
    private val binding get() = _binding!!

    private val scanViewModel: ScanViewModel by viewModels()
    private val saisieViewModel: SaisieViewModel by activityViewModels()
    private val args: ScanResultatFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanResultatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scanViewModel.onCodeBarreDetecte(args.codeBarre)
        observeViewModel()
        setupListeners()
    }

    private fun observeViewModel() {
        scanViewModel.uiState.observe(viewLifecycleOwner) { event ->
            when (val state = event.peek()) {
                is ScanUiState.Resolu -> afficherSucces(state.result)
                is ScanUiState.NonTrouve -> afficherEchec(state.codeBarre)
                is ScanUiState.Loading -> binding.progressResolution.isVisible = true
                else -> {}
            }
        }
    }

    private fun setupListeners() {
        // Pas de TextWatcher : le champ est la source de vérité, lu au moment du clic.
        // Le suivre frappe par frappe faisait retomber la quantité à 0 sur une saisie
        // intermédiaire (« 1. », champ vidé pour être retapé), sans que rien ne l'indique.

        binding.btnMoins.setOnClickListener {
            ecrireQuantite(maxOf(0.0, (quantiteSaisie() ?: 0.0) - 1))
        }

        binding.btnPlus.setOnClickListener {
            ecrireQuantite((quantiteSaisie() ?: 0.0) + 1)
        }

        binding.btnAjouter.setOnClickListener {
            val quantite = quantiteSaisie()
            if (quantite == null) {
                binding.etQuantite.error = "Quantité invalide"
                return@setOnClickListener
            }
            val state = scanViewModel.uiState.value?.peek()
            if (state is ScanUiState.Resolu) {
                saisieViewModel.ajouterLigne(
                    article = state.result.article,
                    codeBarreScanne = state.result.codeBarre,
                    quantite = quantite
                )
                findNavController().navigateUp()
            }
        }

        binding.btnScannerSuivant.setOnClickListener {
            findNavController().navigateUp()
            scanViewModel.reset()
        }

        binding.btnRescanner.setOnClickListener {
            findNavController().navigateUp()
            scanViewModel.reset()
        }
    }

    private fun afficherSucces(result: ResolutionResult.Trouve) {
        binding.progressResolution.isVisible = false
        binding.groupSucces.isVisible = true
        binding.groupEchec.isVisible = false

        binding.tvNomArticle.text = result.article.nomProduit
        binding.tvCodeProduit.text = "Code : ${result.article.codeProduit}"
        binding.tvCodeBarreScanne.text = "CB scanné : ${result.codeBarre}"
        binding.tvViaTableCb.isVisible = result.viaTableCB

        ecrireQuantite(QUANTITE_PAR_DEFAUT)
    }

    private fun afficherEchec(codeBarre: String) {
        binding.progressResolution.isVisible = false
        binding.groupSucces.isVisible = false
        binding.groupEchec.isVisible = true
        binding.tvCodeBarreInconnu.text = codeBarre
    }

    /** Quantité saisie, ou null si le champ ne contient pas un nombre positif exploitable. */
    private fun quantiteSaisie(): Double? =
        binding.etQuantite.text?.toString()?.trim()?.toDoubleOrNull()?.takeIf { it >= 0 }

    private fun ecrireQuantite(quantite: Double) {
        binding.etQuantite.error = null
        binding.etQuantite.setText(FormatUtils.formatQuantite(quantite))
        binding.etQuantite.setSelection(binding.etQuantite.text?.length ?: 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        /** Un scan vaut une unité tant que l'opérateur n'en décide autrement. */
        const val QUANTITE_PAR_DEFAUT = 1.0
    }
}
