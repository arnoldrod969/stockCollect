package com.jdcosmetics.stockcollect.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdcosmetics.stockcollect.data.db.dao.ArticleDao
import com.jdcosmetics.stockcollect.data.db.dao.SessionDao
import com.jdcosmetics.stockcollect.data.db.entity.SessionEntity
import com.jdcosmetics.stockcollect.data.prefs.ParametresSync
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionDao: SessionDao,
    private val articleDao: ArticleDao,
    private val parametres: ParametresSync
) : ViewModel() {

    private val _magasinConfigure = MutableLiveData<String?>(null)
    /** Libellé du magasin réglé, ou `null` tant que la synchro n'est pas paramétrée. */
    val magasinConfigure: LiveData<String?> = _magasinConfigure

    private val _nbSessions = MutableLiveData(0)
    val nbSessions: LiveData<Int> = _nbSessions

    private val _nbArticles = MutableLiveData(0)
    val nbArticles: LiveData<Int> = _nbArticles

    private val _dernierImport = MutableLiveData<String?>(null)
    val dernierImport: LiveData<String?> = _dernierImport

    private val _lastBrouillon = MutableLiveData<SessionEntity?>(null)
    val lastBrouillon: LiveData<SessionEntity?> = _lastBrouillon

    init { charger() }

    /**
     * Relu à chaque retour sur l'accueil : les paramètres changent dans un autre écran, et un
     * `init` seul afficherait encore l'ancien état après un aller-retour.
     */
    fun rafraichirParametres() {
        _magasinConfigure.value = parametres.magasin.takeIf { parametres.estConfigure }
    }

    private fun charger() {
        rafraichirParametres()
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
