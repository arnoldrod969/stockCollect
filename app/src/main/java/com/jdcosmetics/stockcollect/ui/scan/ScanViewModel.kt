package com.jdcosmetics.stockcollect.ui.scan

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdcosmetics.stockcollect.data.db.dao.ArticleDao
import com.jdcosmetics.stockcollect.data.db.entity.ArticleEntity
import com.jdcosmetics.stockcollect.domain.service.BarcodeScanService
import com.jdcosmetics.stockcollect.domain.service.ResolutionResult
import com.jdcosmetics.stockcollect.util.Constants
import com.jdcosmetics.stockcollect.util.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ScanUiState {
    object Idle : ScanUiState()
    object Scanning : ScanUiState()
    data class Resolu(val result: ResolutionResult.Trouve) : ScanUiState()
    data class NonTrouve(val codeBarre: String) : ScanUiState()
    object Loading : ScanUiState()
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanService: BarcodeScanService,
    private val articleDao: ArticleDao
) : ViewModel() {

    private val _uiState = MutableLiveData<Event<ScanUiState>>(Event(ScanUiState.Idle))
    val uiState: LiveData<Event<ScanUiState>> = _uiState

    private val _searchResults = MutableLiveData<List<ArticleEntity>>(emptyList())
    val searchResults: LiveData<List<ArticleEntity>> = _searchResults

    private var dernierScanMs = 0L
    private var resolutionJob: Job? = null

    fun rechercherArticles(query: String) {
        viewModelScope.launch {
            _searchResults.postValue(articleDao.searchByNomSync(query))
        }
    }

    fun onCodeBarreDetecte(codeBarre: String) {
        val maintenant = System.currentTimeMillis()
        if (maintenant - dernierScanMs < Constants.SCAN_DEBOUNCE_MS) return
        dernierScanMs = maintenant

        resolutionJob?.cancel()
        resolutionJob = viewModelScope.launch {
            _uiState.value = Event(ScanUiState.Loading)
            val result = scanService.resoudre(codeBarre)
            _uiState.value = Event(when (result) {
                is ResolutionResult.Trouve -> ScanUiState.Resolu(result)
                is ResolutionResult.NonTrouve -> ScanUiState.NonTrouve(result.codeBarre)
            })
        }
    }

    fun onSaisieManuelle(codeBarre: String) {
        if (codeBarre.isBlank()) return
        onCodeBarreDetecte(codeBarre)
    }

    fun reset() {
        _uiState.value = Event(ScanUiState.Idle)
        dernierScanMs = 0L
    }
}
