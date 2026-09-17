package com.jdcosmetics.stockcollect.ui.import_catalogue

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdcosmetics.stockcollect.data.db.dao.ArtCodebarreDao
import com.jdcosmetics.stockcollect.data.db.dao.ArticleDao
import com.jdcosmetics.stockcollect.domain.service.AnalyseCatalogue
import com.jdcosmetics.stockcollect.domain.service.AnalyseResult
import com.jdcosmetics.stockcollect.domain.service.CsvImportService
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

    data class Success(val result: ImportResult) : ImportUiState()
    data class Error(val message: String) : ImportUiState()
}

@HiltViewModel
class ImportCatalogueViewModel @Inject constructor(
    private val importService: CsvImportService,
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

    /**
     * Analyse retenue entre l'affichage du dialogue de conflits et le choix de l'utilisateur.
     * Le fichier n'est pas relu : l'URI SAF peut n'être valable que le temps d'un aller-retour.
     */
    private var analyseEnAttente: AnalyseCatalogue? = null

    fun importerCatalogue(uri: Uri) {
        _catalogueState.value = ImportUiState.Loading
        viewModelScope.launch {
            when (val analyse = importService.analyserCatalogue(uri)) {
                is AnalyseResult.Echec -> {
                    analyseEnAttente = null
                    _catalogueState.value = ImportUiState.Error(analyse.message)
                }
                is AnalyseResult.Pret -> {
                    if (analyse.analyse.aDesConflits) {
                        analyseEnAttente = analyse.analyse
                        _catalogueState.value = ImportUiState.ConflitsDetectes(analyse.analyse)
                    } else {
                        // Sans conflit, la résolution n'a aucun effet.
                        appliquer(analyse.analyse, ResolutionConflit.IMPORTER_SANS_CODE_BARRE)
                    }
                }
            }
        }
    }

    /** Réponse de l'utilisateur au dialogue de conflits. */
    fun resoudreConflits(resolution: ResolutionConflit) {
        val analyse = analyseEnAttente ?: return
        analyseEnAttente = null
        _catalogueState.value = ImportUiState.Loading
        viewModelScope.launch { appliquer(analyse, resolution) }
    }

    /** L'utilisateur renonce : rien n'a été écrit, il n'y a donc rien à défaire. */
    fun annulerImport() {
        analyseEnAttente = null
        _catalogueState.value = ImportUiState.Idle
    }

    private suspend fun appliquer(analyse: AnalyseCatalogue, resolution: ResolutionConflit) {
        val result = importService.appliquerCatalogue(analyse, resolution)
        _catalogueState.value = if (result.success) {
            chargerStatsCatalogue()
            ImportUiState.Success(result)
        } else {
            ImportUiState.Error(result.messageErreur ?: "Erreur inconnue")
        }
    }

    fun importerCorrespondance(uri: Uri) {
        _correspondanceState.value = ImportUiState.Loading
        viewModelScope.launch {
            val result = importService.importCorrespondance(uri)
            _correspondanceState.value = if (result.success) {
                chargerStatsCatalogue()
                ImportUiState.Success(result)
            } else {
                ImportUiState.Error(result.messageErreur ?: "Erreur inconnue")
            }
        }
    }

    fun resetCatalogueState() { _catalogueState.value = ImportUiState.Idle }
    fun resetCorrespondanceState() { _correspondanceState.value = ImportUiState.Idle }
}
