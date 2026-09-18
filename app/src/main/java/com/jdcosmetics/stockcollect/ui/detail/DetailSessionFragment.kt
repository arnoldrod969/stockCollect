package com.jdcosmetics.stockcollect.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.jdcosmetics.stockcollect.R
import com.jdcosmetics.stockcollect.data.db.entity.SessionEntity
import com.jdcosmetics.stockcollect.data.db.entity.StatutSession
import com.jdcosmetics.stockcollect.data.db.entity.StatutSync
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
        binding.btnSynchroniser.setOnClickListener { viewModel.synchroniser() }
        binding.btnEtatNirgescom.setOnClickListener { viewModel.consulterEtat() }
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
            afficherBlocSync(session)
        }

        viewModel.lignes.observe(viewLifecycleOwner) { lignes ->
            adapter.submitList(lignes)
            binding.tvAucuneLigne.isVisible = lignes.isEmpty()
        }

        viewModel.syncState.observe(viewLifecycleOwner) { state ->
            binding.progressSync.isVisible = state is SyncUiState.Loading
            binding.btnSynchroniser.isEnabled = state !is SyncUiState.Loading
            binding.btnEtatNirgescom.isEnabled = state !is SyncUiState.Loading

            when (state) {
                is SyncUiState.Succes -> afficherDetailSync(state.message, R.color.green_secondary)
                is SyncUiState.Echec -> afficherDetailSync(state.message, R.color.red_on_container)
                else -> Unit
            }
        }
    }

    /**
     * Le bloc n'apparaît qu'après la clôture : un brouillon n'a rien à envoyer, et son contenu
     * bougerait encore. Le bouton d'envoi reste offert une fois synchronisée — c'est un renvoi
     * idempotent côté API, utile quand on doute que l'envoi soit passé.
     */
    private fun afficherBlocSync(session: SessionEntity) {
        val cloturee = session.statut != StatutSession.BROUILLON
        binding.blocSync.isVisible = cloturee
        if (!cloturee) return

        binding.tvStatutSync.text = StatutSync.label(session.statutSync)
        binding.tvStatutSync.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                when (session.statutSync) {
                    StatutSync.SYNCHRONISEE -> R.color.green_secondary
                    StatutSync.ECHEC_SYNC -> R.color.red_on_container
                    else -> R.color.on_surface
                }
            )
        )
        binding.btnSynchroniser.text =
            if (session.statutSync == StatutSync.SYNCHRONISEE) "Renvoyer" else "Synchroniser"
        binding.btnEtatNirgescom.isVisible = session.statutSync == StatutSync.SYNCHRONISEE

        // L'erreur de la dernière tentative est réaffichée à l'ouverture de l'écran : sans elle, un
        // magasinier revenant le lendemain ne verrait qu'« Échec » sans jamais savoir pourquoi.
        val messageStocke = session.messageErreurSync
        if (viewModel.syncState.value is SyncUiState.Idle && !messageStocke.isNullOrBlank()) {
            afficherDetailSync(messageStocke, R.color.red_on_container)
        }
    }

    private fun afficherDetailSync(message: String, couleur: Int) {
        binding.tvDetailSync.apply {
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
