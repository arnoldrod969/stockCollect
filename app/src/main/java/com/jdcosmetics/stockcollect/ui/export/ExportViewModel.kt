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
import com.jdcosmetics.stockcollect.domain.service.estExportable
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

/**
 * Valeur par défaut de l'argument `idSession` de `exportFragment` dans nav_graph.xml (`-1L`) :
 * aucune session désignée, l'écran prend la plus récente clôturée.
 */
const val EXPORT_SESSION_PLUS_RECENTE = -1L

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val exportService: CsvExportService,
    private val sessionDao: SessionDao
) : ViewModel() {

    private val _uiState = MutableLiveData<ExportUiState>(ExportUiState.Idle)
    val uiState: LiveData<ExportUiState> = _uiState

    private val _sessionExportable = MutableLiveData<SessionEntity?>()
    val sessionExportable: LiveData<SessionEntity?> = _sessionExportable

    /** L'argument reçu de la navigation ; null tant que le fragment ne l'a pas transmis. */
    private var idDemande: Long? = null

    /**
     * Appelé par le fragment avec l'argument Safe Args `idSession`. Ignoré s'il ne change pas :
     * après une rotation, le ViewModel a déjà la session et un rechargement n'apporterait rien.
     */
    fun charger(idSession: Long) {
        if (idDemande == idSession) return
        idDemande = idSession
        chargerSessionExportable()
    }

    private fun chargerSessionExportable() {
        val id = idDemande ?: return
        viewModelScope.launch {
            _sessionExportable.value = trouverSession(id)
        }
    }

    /**
     * Sans session désignée (bouton « dernière clôturée » de l'Historique), la plus récente
     * clôturée, comme avant. Avec une session désignée, **celle-là** et aucune autre — y compris
     * déjà exportée, pour réécrire un fichier perdu —, sauf si elle n'est pas exportable (un
     * brouillon) : l'écran dit alors qu'il n'y a rien à exporter plutôt que d'en montrer une autre.
     */
    private suspend fun trouverSession(id: Long): SessionEntity? =
        if (id == EXPORT_SESSION_PLUS_RECENTE) sessionDao.getMostRecentCloturee()
        else sessionDao.getById(id)?.takeIf { estExportable(it.statut) }

    fun exporter(outputUri: Uri) {
        val session = _sessionExportable.value ?: return
        _uiState.value = ExportUiState.Loading
        viewModelScope.launch {
            val result = exportService.exporter(session.idSession, outputUri)
            _uiState.value = when (result) {
                is ExportResult.Succes -> ExportUiState.Succes(result.nomFichier, result.nbLignes)
                is ExportResult.Erreur -> ExportUiState.Erreur(result.message)
            }
            // Session désignée : on la relit, elle s'affiche désormais « Exportée » et reste
            // réexportable. Sans désignation : la suivante des clôturées, comme avant.
            chargerSessionExportable()
        }
    }

    fun getNomFichier() = DateUtils.toFileName()

    fun resetState() { _uiState.value = ExportUiState.Idle }
}
