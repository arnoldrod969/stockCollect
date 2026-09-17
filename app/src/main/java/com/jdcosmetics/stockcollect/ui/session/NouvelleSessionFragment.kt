package com.jdcosmetics.stockcollect.ui.session

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jdcosmetics.stockcollect.R
import com.jdcosmetics.stockcollect.data.db.entity.TypeOperation
import com.jdcosmetics.stockcollect.databinding.FragmentNouvelleSessionBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NouvelleSessionFragment : Fragment() {

    private var _binding: FragmentNouvelleSessionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SaisieViewModel by activityViewModels()
    // Un seul type reste proposable (cf. fragment_nouvelle_session.xml). La variable subsiste
    // plutôt qu'un littéral en dur : COMMANDE arrivera à l'étape 2 et le choix redeviendra réel.
    private val typeSelectionne = TypeOperation.INVENTAIRE

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
        // Plus de listener : la carte est le seul type disponible, elle est donc toujours
        // sélectionnée. Le liseré reste pour que l'écran garde le même langage visuel quand
        // COMMANDE viendra s'ajouter à côté.
        binding.cardInventaire.strokeWidth = 3
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
                is SaisieUiState.BrouillonAutreType -> {
                    binding.btnCommencerSaisie.isEnabled = true
                    demanderQuoiFaireDuBrouillon(state)
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

    /**
     * Un brouillon d'un autre type est en cours. Le reprendre en silence ferait collecter sous un
     * type que l'utilisateur n'a pas choisi ; la clôture irréversible rend l'erreur coûteuse.
     *
     * Inatteignable tant que seul l'inventaire est proposé, mais le cas s'ouvre avec le mode
     * pré-commande (cf. contrat API, type_operation COMMANDE).
     */
    private fun demanderQuoiFaireDuBrouillon(state: SaisieUiState.BrouillonAutreType) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Une collecte est déjà en cours")
            .setMessage(
                "Un brouillon « ${TypeOperation.label(state.typeBrouillon)} » n'est pas terminé, " +
                    "alors que vous démarrez « ${TypeOperation.label(state.typeDemande)} ».\n\n" +
                    "Une seule collecte peut être ouverte à la fois."
            )
            .setPositiveButton("Reprendre le brouillon") { _, _ ->
                viewModel.reprendreBrouillon(state.idSession)
            }
            .setNegativeButton("Annuler") { _, _ -> viewModel.resetState() }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
