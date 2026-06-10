package com.jdcosmetics.stockcollect.ui.export

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdcosmetics.stockcollect.data.db.dao.SessionDao
import com.jdcosmetics.stockcollect.data.db.entity.SessionEntity
import com.jdcosmetics.stockcollect.domain.service.CsvExportService
import com.jdcosmetics.stockcollect.domain.service.ExportResult
import com.jdcosmetics.stockcollect.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ExportUiState {
    object Idle : ExportUiState()
    object Loading : ExportUiState()
    data class Succes(val nomFichier: String, val nbLignes: Int) : ExportUiState()
    data class Erreur(val message: String) : ExportUiState()
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val exportService: CsvExportService,
    private val sessionDao: SessionDao
) : ViewModel() {

    private val _uiState = MutableLiveData<ExportUiState>(ExportUiState.Idle)
    val uiState: LiveData<ExportUiState> = _uiState

    private val _sessionExportable = MutableLiveData<SessionEntity?>()
    val sessionExportable: LiveData<SessionEntity?> = _sessionExportable

    init { chargerSessionExportable() }

    private fun chargerSessionExportable() {
        viewModelScope.launch {
            _sessionExportable.value = sessionDao.getMostRecentCloturee()
        }
    }

    fun exporter(outputUri: Uri) {
        val session = _sessionExportable.value ?: return
        _uiState.value = ExportUiState.Loading
        viewModelScope.launch {
            val result = exportService.exporter(session.idSession, outputUri)
            _uiState.value = when (result) {
                is ExportResult.Succes -> ExportUiState.Succes(result.nomFichier, result.nbLignes)
                is ExportResult.Erreur -> ExportUiState.Erreur(result.message)
            }
            chargerSessionExportable()
        }
    }

    fun getNomFichier() = DateUtils.toFileName()

    fun resetState() { _uiState.value = ExportUiState.Idle }
}
