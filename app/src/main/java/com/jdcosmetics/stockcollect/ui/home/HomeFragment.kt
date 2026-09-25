package com.jdcosmetics.stockcollect.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.jdcosmetics.stockcollect.R
import com.jdcosmetics.stockcollect.data.db.entity.StatutSession
import com.jdcosmetics.stockcollect.data.db.entity.TypeOperation
import com.jdcosmetics.stockcollect.databinding.FragmentHomeBinding
import com.jdcosmetics.stockcollect.util.DateUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnScanner.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_scan)
        }
        binding.btnNouvelleSession.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_nouvelle_session)
        }
        binding.rowHistorique.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_historique)
        }
        binding.rowImport.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_import)
        }
        binding.rowParametres.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_parametres)
        }
        binding.cardReprendreBrouillon.setOnClickListener {
            val idSession = viewModel.lastBrouillon.value?.idSession ?: return@setOnClickListener
            val action = HomeFragmentDirections.actionHomeToSaisie(idSession)
            findNavController().navigate(action)
        }
        binding.btnReprendreBrouillon.setOnClickListener {
            val idSession = viewModel.lastBrouillon.value?.idSession ?: return@setOnClickListener
            val action = HomeFragmentDirections.actionHomeToSaisie(idSession)
            findNavController().navigate(action)
        }
    }

    private fun observeViewModel() {
        viewModel.nbSessions.observe(viewLifecycleOwner) { nb ->
            binding.tvNbSessions.text = nb.toString()
        }
        viewModel.nbArticles.observe(viewLifecycleOwner) { nb ->
            binding.tvNbArticles.text = nb.toString()
        }
        viewModel.dernierImport.observe(viewLifecycleOwner) { date ->
            binding.tvDernierImport.text =
                if (date != null) "Mis à jour le ${DateUtils.toDisplayDate(date)}"
                // La ligne est cliquable et mène à l'import : le dire vaut mieux que constater
                // le vide.
                else "Aucun catalogue — à importer"
        }
        viewModel.magasinConfigure.observe(viewLifecycleOwner) { magasin ->
            binding.tvEtatParametres.text =
                if (magasin != null) "Dépôt : $magasin" else "Dépôt à régler"
        }
        viewModel.lastBrouillon.observe(viewLifecycleOwner) { brouillon ->
            if (brouillon != null) {
                binding.cardReprendreBrouillon.isVisible = true
                binding.tvBrouillonType.text = TypeOperation.label(brouillon.typeOperation)
                binding.tvBrouillonDate.text = DateUtils.toDisplay(brouillon.dateHeureDebut)
            } else {
                binding.cardReprendreBrouillon.isVisible = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Tout se modifie dans un autre écran — réglages, catalogue, brouillon en cours. Le
        // ViewModel survivant à l'aller-retour, sans cette relecture l'accueil resterait figé sur
        // l'état d'avant.
        viewModel.rafraichir()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
