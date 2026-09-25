package com.jdcosmetics.stockcollect.ui.session

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jdcosmetics.stockcollect.databinding.FragmentSaisieBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SaisieFragment : Fragment() {

    private var _binding: FragmentSaisieBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SaisieViewModel by activityViewModels()
    private val args: SaisieFragmentArgs by navArgs()

    private lateinit var lignesAdapter: LignesCollecteAdapter
    private lateinit var rechercheAdapter: ArticleRechercheAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSaisieBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.chargerSessionExistante(args.idSession)
        setupRecyclerViews()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerViews() {
        lignesAdapter = LignesCollecteAdapter(
            onQuantiteChanged = { ligne, q -> viewModel.mettreAJourQuantite(ligne, q) },
            onSupprimer = { ligne -> viewModel.supprimerLigne(ligne) }
        )
        binding.rvLignes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = lignesAdapter
        }

        rechercheAdapter = ArticleRechercheAdapter { article ->
            val input = EditText(requireContext()).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText("1.0")
                setSelectAllOnFocus(true)
                hint = "Quantité comptée"
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Quantité comptée")
                .setMessage("${article.nomProduit}\nCode produit\u00a0: ${article.codeProduit}")
                .setView(input)
                .setPositiveButton("Ajouter à la session") { _, _ ->
                    val q = input.text.toString().toDoubleOrNull() ?: 1.0
                    viewModel.ajouterLigne(article, null, q)
                    binding.etRecherche.setText("")
                    binding.rvRecherche.isVisible = false
                }
                .setNegativeButton("Annuler", null)
                .show()
        }
        binding.rvRecherche.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = rechercheAdapter
        }
    }

    private fun setupListeners() {
        binding.btnScanner.setOnClickListener {
            findNavController().navigate(
                SaisieFragmentDirections.actionSaisieToScan()
            )
        }

        binding.etRecherche.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString() ?: ""
                if (q.length >= 2) {
                    viewModel.rechercherArticles(q)
                    binding.rvRecherche.isVisible = true
                } else {
                    binding.rvRecherche.isVisible = false
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.btnCloturer.setOnClickListener {
            // Une quantité en cours de saisie n'est écrite qu'à la perte de focus, et un bouton
            // ne prend pas le focus en mode tactile : sans ce clearFocus, taper « 25 » puis
            // quitter l'écran emportait l'ancienne valeur. L'écriture part avant la navigation,
            // et le récapitulatif relit la base.
            binding.root.findFocus()?.clearFocus()

            val nbLignes = lignesAdapter.itemCount
            if (nbLignes == 0) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Aucun article dans cette session")
                    .setMessage(
                        "Scannez un code-barre, ou cherchez un article par son nom, pour " +
                            "l'ajouter à la session."
                    )
                    .setPositiveButton("Continuer la saisie", null)
                    .show()
                return@setOnClickListener
            }

            // Pas de confirmation ici : ce bouton ne clôture rien, il mène au récapitulatif, qui
            // porte déjà l'avertissement et le bouton irréversible. Un dialogue intermédiaire
            // ajoutait un tap sur le geste le plus fréquent de l'app sans rien garder.
            findNavController().navigate(
                SaisieFragmentDirections.actionSaisieToRecapitulatif(args.idSession)
            )
        }
    }

    private fun observeViewModel() {
        viewModel.sessionCourante.observe(viewLifecycleOwner) { session ->
            session ?: return@observe
            val nbLignes = session.nbLignes
            binding.tvNbLignes.text = "$nbLignes ligne${if (nbLignes > 1) "s" else ""}"
        }

        viewModel.lignes.observe(viewLifecycleOwner) { lignes ->
            lignesAdapter.submitList(lignes)
            binding.tvAucuneLigne.isVisible = lignes.isEmpty()
        }

        viewModel.searchResults.observe(viewLifecycleOwner) { articles ->
            rechercheAdapter.submitList(articles)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
