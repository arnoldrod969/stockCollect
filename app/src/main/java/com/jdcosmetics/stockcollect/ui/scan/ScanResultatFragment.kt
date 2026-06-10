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

    private var quantite = 1.0

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
        binding.btnMoins.setOnClickListener {
            if (quantite > 0) {
                quantite -= 1
                majAffichageQuantite()
            }
        }

        binding.btnPlus.setOnClickListener {
            quantite += 1
            majAffichageQuantite()
        }

        binding.btnAjouter.setOnClickListener {
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

        majAffichageQuantite()
    }

    private fun afficherEchec(codeBarre: String) {
        binding.progressResolution.isVisible = false
        binding.groupSucces.isVisible = false
        binding.groupEchec.isVisible = true
        binding.tvCodeBarreInconnu.text = codeBarre
    }

    private fun majAffichageQuantite() {
        binding.tvQuantite.text = FormatUtils.formatQuantite(quantite)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
