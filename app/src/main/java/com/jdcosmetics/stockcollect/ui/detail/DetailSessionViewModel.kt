package com.jdcosmetics.stockcollect.ui.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdcosmetics.stockcollect.data.db.dao.SessionDao
import com.jdcosmetics.stockcollect.data.db.dao.LigneCollecteDao
import com.jdcosmetics.stockcollect.data.db.entity.LigneCollecteEntity
import com.jdcosmetics.stockcollect.data.db.entity.SessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailSessionViewModel @Inject constructor(
    private val sessionDao: SessionDao,
    private val ligneDao: LigneCollecteDao
) : ViewModel() {

    private val _session = MutableLiveData<SessionEntity?>()
    val session: LiveData<SessionEntity?> = _session

    private val _lignes = MutableLiveData<List<LigneCollecteEntity>>(emptyList())
    val lignes: LiveData<List<LigneCollecteEntity>> = _lignes

    fun charger(idSession: Long) {
        viewModelScope.launch {
            _session.value = sessionDao.getById(idSession)
            ligneDao.getLignesBySession(idSession).collect { liste ->
                _lignes.value = liste
            }
        }
    }
}
