package com.jdcosmetics.stockcollect.ui.import_catalogue

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdcosmetics.stockcollect.data.db.dao.ArtCodebarreDao
import com.jdcosmetics.stockcollect.data.db.dao.ArticleDao
import com.jdcosmetics.stockcollect.domain.service.CsvImportService
import com.jdcosmetics.stockcollect.domain.service.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ImportUiState {
    object Idle : ImportUiState()
    object Loading : ImportUiState()
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

    fun importerCatalogue(uri: Uri) {
        _catalogueState.value = ImportUiState.Loading
        viewModelScope.launch {
            val result = importService.importCatalogue(uri)
            _catalogueState.value = if (result.success) {
                chargerStatsCatalogue()
                ImportUiState.Success(result)
            } else {
                ImportUiState.Error(result.messageErreur ?: "Erreur inconnue")
            }
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
