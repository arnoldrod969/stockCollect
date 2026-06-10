package com.jdcosmetics.stockcollect.ui.historique

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdcosmetics.stockcollect.data.db.dao.SessionDao
import com.jdcosmetics.stockcollect.data.db.entity.SessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoriqueViewModel @Inject constructor(
    private val sessionDao: SessionDao
) : ViewModel() {

    private val _sessions = MutableLiveData<List<SessionEntity>>()
    val sessions: LiveData<List<SessionEntity>> = _sessions

    private val _aClotureeExportable = MutableLiveData(false)
    val aClotureeExportable: LiveData<Boolean> = _aClotureeExportable

    private var filtre: String? = null
    private var touteSessions: List<SessionEntity> = emptyList()

    init { charger() }

    private fun charger() {
        viewModelScope.launch {
            sessionDao.getAllSessions().collect { liste ->
                touteSessions = liste
                appliquerFiltre()
            }
        }
        viewModelScope.launch {
            sessionDao.getSessionsByStatut("CLOTUREE").collect { sessions ->
                _aClotureeExportable.value = sessions.isNotEmpty()
            }
        }
    }

    fun filtrer(type: String?) {
        filtre = type
        appliquerFiltre()
    }

    private fun appliquerFiltre() {
        _sessions.value = if (filtre == null) touteSessions
        else touteSessions.filter { it.typeOperation == filtre }
    }
}
