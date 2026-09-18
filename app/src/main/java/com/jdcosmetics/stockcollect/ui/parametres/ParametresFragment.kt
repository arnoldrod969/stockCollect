package com.jdcosmetics.stockcollect.ui.parametres

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.jdcosmetics.stockcollect.R
import com.jdcosmetics.stockcollect.data.db.entity.MagasinEntity
import com.jdcosmetics.stockcollect.databinding.FragmentParametresBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Réglages de connexion à l'API Nirgescom.
 *
 * Le test de connexion et la récupération des dépôts portent sur l'adresse et la clé **telles
 * qu'elles sont tapées**, pas sur celles enregistrées : l'opérateur doit pouvoir essayer une IP
 * avant de la valider.
 *
 * Le magasin n'est pas saisi mais choisi dans la liste fournie par le serveur — l'API compare le
 * libellé envoyé au sien par égalité stricte, et une faute de frappe ne se verrait qu'à l'envoi,
 * après une clôture irréversible.
 */
@AndroidEntryPoint
class ParametresFragment : Fragment() {

    private var _binding: FragmentParametresBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ParametresViewModel by viewModels()

    /** Ce que la liste déroulante affiche, dans le même ordre — l'index sert à retrouver l'entité. */
    private var magasinsAffiches: List<MagasinEntity> = emptyList()

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
        binding.btnRecupererDepots.setOnClickListener {
            viewModel.recupererDepots(
                binding.etUrlApi.text?.toString().orEmpty(),
                binding.etCleApi.text?.toString().orEmpty()
            )
        }
        binding.btnEnregistrer.setOnClickListener {
            viewModel.enregistrer(saisieCourante())
        }
        // Le champ et son icône ouvrent la même liste : le champ n'est pas saisissable, un tap
        // dessus n'aurait sinon aucun effet visible.
        binding.etMagasin.setOnClickListener { afficherListeDepots() }
        binding.tilMagasin.setEndIconOnClickListener { afficherListeDepots() }
    }

    /**
     * Les dépôts sans libellé restent affichés mais refusés : les masquer laisserait l'opérateur
     * chercher un dépôt absent de la liste sans jamais savoir pourquoi.
     */
    private fun afficherListeDepots() {
        if (magasinsAffiches.isEmpty()) {
            Snackbar.make(
                binding.root, "Récupérez d'abord la liste des dépôts.", Snackbar.LENGTH_SHORT
            ).show()
            return
        }
        val libelles = magasinsAffiches.map { it.libelle() }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Choisir le dépôt")
            .setItems(libelles) { _, position ->
                val magasin = magasinsAffiches[position]
                if (magasin.selectionnable) {
                    viewModel.selectionnerMagasin(magasin)
                } else {
                    Snackbar.make(
                        binding.root,
                        "${magasin.codeMagasin} n'a pas de libellé côté Nirgescom : il n'y aurait " +
                            "rien à envoyer au serveur.",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun saisieCourante() = ParametresSaisis(
        urlApi = binding.etUrlApi.text?.toString().orEmpty(),
        cleApi = binding.etCleApi.text?.toString().orEmpty(),
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
            if (binding.etTablette.text?.toString() != valeurs.identifiantTablette) {
                binding.etTablette.setText(valeurs.identifiantTablette)
            }
        }

        viewModel.magasins.observe(viewLifecycleOwner) { magasins ->
            magasinsAffiches = magasins
            // Le champ reste muet plutôt que vide quand le cache l'est : sans cette phrase, l'écran
            // n'explique nulle part pourquoi la liste ne s'ouvre pas.
            binding.tilMagasin.hint =
                if (magasins.isEmpty()) "Récupérez d'abord la liste des dépôts" else "Magasin"
        }

        viewModel.magasinSelectionne.observe(viewLifecycleOwner) { afficherSelection(it) }

        viewModel.testState.observe(viewLifecycleOwner) { state ->
            binding.progressTest.isVisible = state is TestUiState.Loading
            binding.btnTester.isEnabled = state !is TestUiState.Loading

            when (state) {
                is TestUiState.Succes ->
                    afficherResultat(binding.tvResultatTest, state.message, R.color.green_secondary)
                is TestUiState.Echec ->
                    afficherResultat(binding.tvResultatTest, state.message, R.color.red_on_container)
                else -> binding.tvResultatTest.isVisible = false
            }
        }

        viewModel.depotsState.observe(viewLifecycleOwner) { state ->
            binding.progressDepots.isVisible = state is DepotsUiState.Loading
            binding.btnRecupererDepots.isEnabled = state !is DepotsUiState.Loading

            when (state) {
                is DepotsUiState.Succes -> afficherResultat(
                    binding.tvResultatDepots, state.message, R.color.green_secondary
                )
                is DepotsUiState.Echec -> afficherResultat(
                    binding.tvResultatDepots, state.message, R.color.red_on_container
                )
                else -> binding.tvResultatDepots.isVisible = false
            }
        }

        viewModel.enregistrement.observe(viewLifecycleOwner) { state ->
            when (state) {
                is EnregistrementUiState.Ok -> {
                    Snackbar.make(binding.root, "Paramètres enregistrés.", Snackbar.LENGTH_SHORT)
                        .show()
                    viewModel.resetEnregistrement()
                }
                is EnregistrementUiState.Refuse -> {
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                    viewModel.resetEnregistrement()
                }
                else -> Unit
            }
        }
    }

    private fun afficherSelection(magasin: MagasinEntity?) {
        binding.etMagasin.setText(magasin?.libelle().orEmpty())
    }

    /**
     * Le résultat reste affiché tant qu'une autre tentative ne l'a pas remplacé, contrairement à
     * une Snackbar : l'opérateur doit pouvoir le relire en corrigeant l'adresse juste au-dessus.
     */
    private fun afficherResultat(vue: android.widget.TextView, message: String, couleur: Int) {
        vue.apply {
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
