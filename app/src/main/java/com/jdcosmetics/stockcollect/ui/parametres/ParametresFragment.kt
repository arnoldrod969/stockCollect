package com.jdcosmetics.stockcollect.ui.parametres

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import com.jdcosmetics.stockcollect.R
import com.jdcosmetics.stockcollect.databinding.FragmentParametresBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Réglages de connexion à l'API Nirgescom.
 *
 * Le test de connexion porte sur l'adresse **telle qu'elle est tapée**, pas sur celle enregistrée :
 * l'opérateur doit pouvoir essayer une IP avant de la valider.
 */
@AndroidEntryPoint
class ParametresFragment : Fragment() {

    private var _binding: FragmentParametresBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ParametresViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentParametresBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnTester.setOnClickListener {
            viewModel.tester(binding.etUrlApi.text?.toString().orEmpty())
        }
        binding.btnEnregistrer.setOnClickListener {
            viewModel.enregistrer(saisieCourante())
        }
    }

    private fun saisieCourante() = ParametresSaisis(
        urlApi = binding.etUrlApi.text?.toString().orEmpty(),
        cleApi = binding.etCleApi.text?.toString().orEmpty(),
        magasin = binding.etMagasin.text?.toString().orEmpty(),
        identifiantTablette = binding.etTablette.text?.toString().orEmpty()
    )

    private fun observeViewModel() {
        viewModel.valeurs.observe(viewLifecycleOwner) { valeurs ->
            // Ne réécrit que si la valeur diffère : sans ce garde, l'enregistrement replacerait le
            // curseur au début de chaque champ pendant que l'opérateur y est encore.
            if (binding.etUrlApi.text?.toString() != valeurs.urlApi) {
                binding.etUrlApi.setText(valeurs.urlApi)
            }
            if (binding.etCleApi.text?.toString() != valeurs.cleApi) {
                binding.etCleApi.setText(valeurs.cleApi)
            }
            if (binding.etMagasin.text?.toString() != valeurs.magasin) {
                binding.etMagasin.setText(valeurs.magasin)
            }
            if (binding.etTablette.text?.toString() != valeurs.identifiantTablette) {
                binding.etTablette.setText(valeurs.identifiantTablette)
            }
        }

        viewModel.testState.observe(viewLifecycleOwner) { state ->
            binding.progressTest.isVisible = state is TestUiState.Loading
            binding.btnTester.isEnabled = state !is TestUiState.Loading

            when (state) {
                is TestUiState.Succes -> afficherResultat(state.message, R.color.green_secondary)
                is TestUiState.Echec -> afficherResultat(state.message, R.color.red_on_container)
                else -> binding.tvResultatTest.isVisible = false
            }
        }

        viewModel.enregistre.observe(viewLifecycleOwner) { enregistre ->
            if (!enregistre) return@observe
            Snackbar.make(binding.root, "Paramètres enregistrés.", Snackbar.LENGTH_SHORT).show()
            viewModel.resetEnregistre()
        }
    }

    /**
     * Le résultat reste affiché tant qu'un autre test ne l'a pas remplacé, contrairement à une
     * Snackbar : l'opérateur doit pouvoir le relire en corrigeant l'adresse juste au-dessus.
     */
    private fun afficherResultat(message: String, couleur: Int) {
        binding.tvResultatTest.apply {
            text = message
            setTextColor(ContextCompat.getColor(requireContext(), couleur))
            isVisible = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
