package com.jdcosmetics.stockcollect.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdcosmetics.stockcollect.data.db.dao.ArticleDao
import com.jdcosmetics.stockcollect.data.db.dao.SessionDao
import com.jdcosmetics.stockcollect.data.db.entity.SessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionDao: SessionDao,
    private val articleDao: ArticleDao
) : ViewModel() {

    private val _nbSessions = MutableLiveData(0)
    val nbSessions: LiveData<Int> = _nbSessions

    private val _nbArticles = MutableLiveData(0)
    val nbArticles: LiveData<Int> = _nbArticles

    private val _dernierImport = MutableLiveData<String?>(null)
    val dernierImport: LiveData<String?> = _dernierImport

    private val _lastBrouillon = MutableLiveData<SessionEntity?>(null)
    val lastBrouillon: LiveData<SessionEntity?> = _lastBrouillon

    init { charger() }

    private fun charger() {
        viewModelScope.launch {
            _nbSessions.value = sessionDao.count()
            _nbArticles.value = articleDao.count()
            _dernierImport.value = articleDao.getLastImportDate()
        }
        viewModelScope.launch {
            sessionDao.getAllSessions().collect { sessions ->
                _nbSessions.value = sessions.size
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            _lastBrouillon.postValue(sessionDao.getLastBrouillon())
        }
    }
}
