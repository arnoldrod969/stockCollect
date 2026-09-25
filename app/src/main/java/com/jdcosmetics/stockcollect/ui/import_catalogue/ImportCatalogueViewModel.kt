package com.jdcosmetics.stockcollect.ui.import_catalogue

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdcosmetics.stockcollect.data.db.dao.ArtCodebarreDao
import com.jdcosmetics.stockcollect.data.db.dao.ArticleDao
import com.jdcosmetics.stockcollect.domain.service.AnalyseCatalogue
import com.jdcosmetics.stockcollect.domain.service.AnalyseNirgescom
import com.jdcosmetics.stockcollect.domain.service.AnalyseResult
import com.jdcosmetics.stockcollect.domain.service.CsvImportService
import com.jdcosmetics.stockcollect.domain.service.ImportCodesBarresNirgescom
import com.jdcosmetics.stockcollect.domain.service.ImportNirgescom
import com.jdcosmetics.stockcollect.domain.service.ImportNirgescomService
import com.jdcosmetics.stockcollect.domain.service.ImportResult
import com.jdcosmetics.stockcollect.domain.service.ResolutionConflit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ImportUiState {
    object Idle : ImportUiState()
    object Loading : ImportUiState()

    /** Le fichier a été analysé mais rien n'est écrit : l'utilisateur doit arbitrer. */
    data class ConflitsDetectes(val analyse: AnalyseCatalogue) : ImportUiState()

    /** [depuisNirgescom] ne change que le titre du compte rendu. */
    data class Success(val result: ImportResult, val depuisNirgescom: Boolean = false) : ImportUiState()
    /**
     * [erreurs] porte les lignes fautives. Un import rejeté est justement le cas où l'utilisateur
     * a besoin du détail — il doit corriger le fichier source — alors qu'il n'avait jusqu'ici
     * qu'un message fugace sans aucune indication de ligne.
     */
    data class Error(val message: String, val erreurs: List<String> = emptyList()) : ImportUiState()

    /**
     * Compte rendu d'une mise à jour depuis Nirgescom qui n'a rien écrit : déjà à jour, liste vide
     * conservée, ou refus du serveur. Toujours en dialogue : ces messages disent qui doit agir
     * (WiFi, clé, informatique) et ne tiennent pas dans une Snackbar.
     */
    data class Message(
        val titre: String,
        val message: String,
        val erreurs: List<String> = emptyList()
    ) : ImportUiState()
}

@HiltViewModel
class ImportCatalogueViewModel @Inject constructor(
    private val importService: CsvImportService,
    private val importNirgescom: ImportNirgescomService,
    private val articleDao: ArticleDao,
    private val artCodebarreDao: ArtCodebarreDao
) : ViewModel() {

    private val _catalogueState = MutableLiveData<ImportUiState>(ImportUiState.Idle)
    val catalogueState: LiveData<ImportUiState> = _catalogueState

    private val _correspondanceState = MutableLiveData<ImportUiState>(ImportUiState.Idle)
    val correspondanceState: LiveData<ImportUiState> = _correspondanceState

    private val _nbArticles = MutableLiveData<Int>(0)
    val nbArticles: LiveData<Int> = _nbArticles

    private val _nbCorrespondances = MutableLiveData<Int>(0)
    val nbCorrespondances: LiveData<Int> = _nbCorrespondances

    private val _dernierImport = MutableLiveData<String?>(null)
    val dernierImport: LiveData<String?> = _dernierImport

    /** Connexion Nirgescom réglée (URL, clé, dépôt) : conditionne les deux mises à jour par l'API. */
    private val _nirgescomConfigure = MutableLiveData<Boolean>(importNirgescom.estConfigure)
    val nirgescomConfigure: LiveData<Boolean> = _nirgescomConfigure

    init {
        chargerStatsCatalogue()
    }

    fun chargerStatsCatalogue() {
        viewModelScope.launch {
            _nbArticles.value = articleDao.count()
            _nbCorrespondances.value = artCodebarreDao.count()
            _dernierImport.value = articleDao.getLastImportDate()
        }
    }

    /** Relu au retour sur l'écran : les Paramètres ont pu être réglés entre-temps. */
    fun rafraichirConfiguration() {
        _nirgescomConfigure.value = importNirgescom.estConfigure
    }

    /** Dépôt dont le catalogue va être demandé, pour la confirmation. */
    val magasinConfigure: String get() = importNirgescom.magasin

    /**
     * Analyse retenue entre l'affichage du dialogue de conflits et le choix de l'utilisateur.
     * Le fichier n'est pas relu : l'URI SAF peut n'être valable que le temps d'un aller-retour.
     * L'API non plus n'est pas rappelée : l'utilisateur tranche sur ce qu'il a vu.
     */
    private var analyseEnAttente: AnalyseCatalogue? = null

    /**
     * Origine de [analyseEnAttente]. `null` : fichier. Sinon l'analyse vient de Nirgescom et
     * porte cet `ETag` (éventuellement vide), à n'enregistrer qu'après écriture.
     */
    private var origineNirgescom: OrigineNirgescom? = null

    private class OrigineNirgescom(val etag: String?)

    /**
     * Un seul import à la fois sur tout l'écran, catalogue et codes-barres confondus. Les deux
     * écrivent des tables liées : un import de codes-barres qui lit la liste des articles pendant
     * qu'un catalogue s'écrit écarterait des codes valides puis enregistrerait son ETag — et un
     * 304 masquerait ensuite le manque. Vérifié ici, pas seulement par les boutons : un double tap
     * ou deux dialogues de confirmation passent avant que l'écran ait désactivé quoi que ce soit.
     */
    val importEnCours: Boolean
        get() = _catalogueState.value is ImportUiState.Loading ||
            _correspondanceState.value is ImportUiState.Loading

    fun importerCatalogue(uri: Uri) {
        if (importEnCours) return
        _catalogueState.value = ImportUiState.Loading
        viewModelScope.launch {
            when (val analyse = importService.analyserCatalogue(uri)) {
                is AnalyseResult.Echec -> {
                    oublierAnalyse()
                    _catalogueState.value = ImportUiState.Error(analyse.message, analyse.erreurs)
                }
                is AnalyseResult.Pret -> traiterAnalyse(analyse.analyse, origine = null)
            }
        }
    }

    /**
     * `GET /catalog`, puis **la même analyse et le même arbitrage** qu'un fichier (TASK-16 #2) :
     * rien n'est écrit avant que les conflits éventuels soient tranchés.
     */
    fun importerCatalogueNirgescom() {
        if (importEnCours) return
        _catalogueState.value = ImportUiState.Loading
        viewModelScope.launch {
            when (val analyse = importNirgescom.analyserCatalogue()) {
                is AnalyseNirgescom.Prete ->
                    traiterAnalyse(analyse.analyse, OrigineNirgescom(analyse.etag))
                AnalyseNirgescom.DejaAJour -> {
                    oublierAnalyse()
                    _catalogueState.value = ImportUiState.Message(
                        "Catalogue à jour", ImportNirgescom.MESSAGE_CATALOGUE_A_JOUR
                    )
                }
                is AnalyseNirgescom.Echec -> {
                    oublierAnalyse()
                    _catalogueState.value = ImportUiState.Message(
                        "Catalogue non mis à jour", analyse.message, analyse.erreurs
                    )
                }
            }
        }
    }

    private suspend fun traiterAnalyse(analyse: AnalyseCatalogue, origine: OrigineNirgescom?) {
        if (analyse.aDesConflits) {
            analyseEnAttente = analyse
            origineNirgescom = origine
            _catalogueState.value = ImportUiState.ConflitsDetectes(analyse)
        } else {
            // Sans conflit, la résolution n'a aucun effet.
            appliquer(analyse, ResolutionConflit.IMPORTER_SANS_CODE_BARRE, origine)
        }
    }

    /** Réponse de l'utilisateur au dialogue de conflits. */
    fun resoudreConflits(resolution: ResolutionConflit) {
        val analyse = analyseEnAttente ?: return
        val origine = origineNirgescom
        oublierAnalyse()
        _catalogueState.value = ImportUiState.Loading
        viewModelScope.launch { appliquer(analyse, resolution, origine) }
    }

    /** L'utilisateur renonce : rien n'a été écrit, il n'y a donc rien à défaire. */
    fun annulerImport() {
        oublierAnalyse()
        _catalogueState.value = ImportUiState.Idle
    }

    private fun oublierAnalyse() {
        analyseEnAttente = null
        origineNirgescom = null
    }

    private suspend fun appliquer(
        analyse: AnalyseCatalogue,
        resolution: ResolutionConflit,
        origine: OrigineNirgescom?
    ) {
        val result = if (origine != null) {
            importNirgescom.appliquerCatalogue(analyse, resolution, origine.etag)
        } else {
            importService.appliquerCatalogue(analyse, resolution).also {
                if (it.success) importNirgescom.catalogueImporteDepuisFichier()
            }
        }
        _catalogueState.value = if (result.success) {
            chargerStatsCatalogue()
            ImportUiState.Success(result, depuisNirgescom = origine != null)
        } else {
            ImportUiState.Error(
                result.messageErreur
                    ?: "L'import a échoué sans raison identifiable. Réessayez ; si cela se " +
                    "reproduit, appelez le service informatique."
            )
        }
    }

    fun importerCorrespondance(uri: Uri) {
        if (importEnCours) return
        _correspondanceState.value = ImportUiState.Loading
        viewModelScope.launch {
            val result = importService.importCorrespondance(uri)
            _correspondanceState.value = if (result.success) {
                importNirgescom.codesBarresImportesDepuisFichier()
                chargerStatsCatalogue()
                ImportUiState.Success(result)
            } else {
                ImportUiState.Error(
                    result.messageErreur
                        ?: "L'import a échoué sans raison identifiable. Réessayez ; si cela se " +
                        "reproduit, appelez le service informatique."
                )
            }
        }
    }

    /**
     * `GET /codes-barres`, puis le même import que le fichier. Une liste vide ne remplace rien
     * (TASK-16 #4) : la correspondance en place est gardée et l'utilisateur le sait.
     */
    fun importerCodesBarresNirgescom() {
        if (importEnCours) return
        _correspondanceState.value = ImportUiState.Loading
        viewModelScope.launch {
            _correspondanceState.value = when (val r = importNirgescom.importerCodesBarres()) {
                is ImportCodesBarresNirgescom.Importe -> {
                    chargerStatsCatalogue()
                    ImportUiState.Success(r.resultat, depuisNirgescom = true)
                }
                ImportCodesBarresNirgescom.DejaAJour -> ImportUiState.Message(
                    "Codes-barres à jour", ImportNirgescom.MESSAGE_CODES_BARRES_A_JOUR
                )
                ImportCodesBarresNirgescom.Conserve -> ImportUiState.Message(
                    "Codes-barres conservés", ImportNirgescom.MESSAGE_CODES_BARRES_VIDE
                )
                is ImportCodesBarresNirgescom.Echec -> ImportUiState.Message(
                    "Codes-barres non mis à jour", r.message, r.erreurs
                )
            }
        }
    }

    fun resetCatalogueState() { _catalogueState.value = ImportUiState.Idle }
    fun resetCorrespondanceState() { _correspondanceState.value = ImportUiState.Idle }
}
