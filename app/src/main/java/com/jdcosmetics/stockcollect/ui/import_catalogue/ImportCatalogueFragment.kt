package com.jdcosmetics.stockcollect.ui.import_catalogue

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.jdcosmetics.stockcollect.R
import com.jdcosmetics.stockcollect.databinding.FragmentImportCatalogueBinding
import com.jdcosmetics.stockcollect.domain.service.AnalyseCatalogue
import com.jdcosmetics.stockcollect.domain.service.ResolutionConflit
import com.jdcosmetics.stockcollect.util.DateUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ImportCatalogueFragment : Fragment() {

    private var _binding: FragmentImportCatalogueBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ImportCatalogueViewModel by viewModels()

    private val pickCatalogueLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            confirmerEtImporterCatalogue(uri)
        }
    }

    private val pickCorrespondanceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            viewModel.importerCorrespondance(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImportCatalogueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnChoisirCatalogue.setOnClickListener {
            ouvrirSelecteurFichier(pickCatalogueLauncher)
        }
        binding.btnChoisirCorrespondance.setOnClickListener {
            ouvrirSelecteurFichier(pickCorrespondanceLauncher)
        }
    }

    private fun observeViewModel() {
        viewModel.nbArticles.observe(viewLifecycleOwner) { nb ->
            binding.tvNbArticles.text = nb.toString()
        }
        viewModel.nbCorrespondances.observe(viewLifecycleOwner) { nb ->
            binding.tvNbCorrespondances.text = nb.toString()
        }
        viewModel.dernierImport.observe(viewLifecycleOwner) { date ->
            binding.tvDernierImport.text = if (date != null) DateUtils.toDisplayDate(date)
            else "\u2014"
        }

        viewModel.catalogueState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ImportUiState.Loading -> {
                    binding.progressCatalogue.isVisible = true
                    binding.btnChoisirCatalogue.isEnabled = false
                }
                is ImportUiState.ConflitsDetectes -> {
                    binding.progressCatalogue.isVisible = false
                    binding.btnChoisirCatalogue.isEnabled = true
                    afficherDialogConflits(state.analyse)
                }
                is ImportUiState.Success -> {
                    binding.progressCatalogue.isVisible = false
                    binding.btnChoisirCatalogue.isEnabled = true
                    afficherDialogResultat("Import catalogue", state.result.toResume(), state.result.erreurs)
                    viewModel.resetCatalogueState()
                }
                is ImportUiState.Error -> {
                    binding.progressCatalogue.isVisible = false
                    binding.btnChoisirCatalogue.isEnabled = true
                    afficherErreur(state.message)
                    viewModel.resetCatalogueState()
                }
                else -> {
                    binding.progressCatalogue.isVisible = false
                    binding.btnChoisirCatalogue.isEnabled = true
                }
            }
        }

        viewModel.correspondanceState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ImportUiState.Loading -> {
                    binding.progressCorrespondance.isVisible = true
                    binding.btnChoisirCorrespondance.isEnabled = false
                }
                is ImportUiState.Success -> {
                    binding.progressCorrespondance.isVisible = false
                    binding.btnChoisirCorrespondance.isEnabled = true
                    afficherDialogResultat("Import correspondance CB", state.result.toResume(), state.result.erreurs)
                    viewModel.resetCorrespondanceState()
                }
                is ImportUiState.Error -> {
                    binding.progressCorrespondance.isVisible = false
                    binding.btnChoisirCorrespondance.isEnabled = true
                    afficherErreur(state.message)
                    viewModel.resetCorrespondanceState()
                }
                else -> {
                    binding.progressCorrespondance.isVisible = false
                    binding.btnChoisirCorrespondance.isEnabled = true
                }
            }
        }
    }

    private fun confirmerEtImporterCatalogue(uri: Uri) {
        val nbActuels = viewModel.nbArticles.value ?: 0
        if (nbActuels > 0) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Remplacer le catalogue ?")
                .setMessage("Le catalogue actuel ($nbActuels articles) sera remplac\u00e9. Cette action est irr\u00e9versible.")
                .setPositiveButton("Remplacer") { _, _ -> viewModel.importerCatalogue(uri) }
                .setNegativeButton("Annuler", null)
                .show()
        } else {
            viewModel.importerCatalogue(uri)
        }
    }

    private fun ouvrirSelecteurFichier(launcher: ActivityResultLauncher<Intent>) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        launcher.launch(intent)
    }

    /**
     * Règle de gestion : un code-barre n'appartient qu'à un seul article. Quand le fichier la
     * viole, rien n'a encore été écrit — c'est à l'utilisateur de dire quoi faire.
     *
     * Les décomptes par famille sont affichés parce qu'aucune option n'est bonne dans les deux cas :
     * « ignorer » convient à une fiche dupliquée, mais ferait disparaître un produit réel quand
     * deux articles distincts se disputent un code-barre.
     */
    private fun afficherDialogConflits(analyse: AnalyseCatalogue) {
        // Seuls les vrais conflits sont détaillés, et au plus quelques-uns. AlertDialog n'accorde
        // aux boutons que la place laissée par le contenu : avec les 9 conflits du catalogue en
        // entier, les trois boutons s'empilaient puis passaient sous le bord de l'écran, et
        // « Ignorer ces articles » devenait inatteignable. Le rapport complet est affiché après
        // l'import, où il n'y a plus de décision à prendre.
        val decisifs = analyse.detailDecisif()
        val vue = layoutInflater.inflate(R.layout.dialog_conflits_codes_barres, null)
        vue.findViewById<TextView>(R.id.tv_conflits).text = buildString {
            append(analyse.resumeConflits())
            if (decisifs.isNotEmpty()) {
                append("\n")
                append(decisifs.take(MAX_CONFLITS_AFFICHES).joinToString("\n\n"))
                if (decisifs.size > MAX_CONFLITS_AFFICHES) {
                    append("\n\n… et ${decisifs.size - MAX_CONFLITS_AFFICHES} autre(s), ")
                    append("listé(s) dans le rapport d'import.")
                }
            }
            append("\n\nQue faire ?")
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Codes-barres en conflit")
            .setView(vue)
            .setCancelable(false)
            .setPositiveButton("Importer sans code-barres") { _, _ ->
                // Les articles écartés restent trouvables par recherche et comptables :
                // ils perdent seulement la possibilité d'être scannés.
                viewModel.resoudreConflits(ResolutionConflit.IMPORTER_SANS_CODE_BARRE)
            }
            .setNeutralButton("Ignorer ces articles") { _, _ ->
                viewModel.resoudreConflits(ResolutionConflit.IGNORER_ARTICLES)
            }
            .setNegativeButton("Annuler l'import") { _, _ ->
                viewModel.annulerImport()
                Snackbar.make(binding.root, "Import annulé, rien n'a été modifié.", Snackbar.LENGTH_LONG).show()
            }
            .create()

        // La hauteur fixe du ScrollView est posée ici, pas dans le XML : inflate() sans parent ne
        // génère aucun LayoutParams pour la racine, donc le layout_height du fichier est perdu et
        // la vue reprend un wrap_content qui chasse les boutons de l'écran. À l'affichage, la vue
        // est attachée et ses LayoutParams sont ceux du conteneur du dialogue.
        dialog.setOnShowListener {
            vue.layoutParams = vue.layoutParams.apply {
                height = (HAUTEUR_DETAIL_DP * resources.displayMetrics.density).toInt()
            }
            vue.requestLayout()
        }
        dialog.show()
    }

    private fun afficherDialogResultat(titre: String, message: String, erreurs: List<String>) {
        val detail = if (erreurs.isNotEmpty()) {
            // \u00ab D\u00e9tails \u00bb et non \u00ab D\u00e9tails erreurs \u00bb : la liste m\u00eale les lignes rejet\u00e9es et les
            // lignes recoll\u00e9es, qui sont au contraire des r\u00e9cup\u00e9rations r\u00e9ussies.
            "$message\n\nD\u00e9tails :\n${erreurs.take(5).joinToString("\n")}" +
            if (erreurs.size > 5) "\n... et ${erreurs.size - 5} autres" else ""
        } else message

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titre)
            .setMessage(detail)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun afficherErreur(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Laisse la place au titre et aux trois boutons empilés sur la plus petite tablette visée. */
        private const val HAUTEUR_DETAIL_DP = 300

        /** Au-delà, le dialogue de décision déborde ; le reste part dans le rapport d'import. */
        private const val MAX_CONFLITS_AFFICHES = 4
    }
}
