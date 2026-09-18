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
     * Tout est relu à chaque retour sur l'accueil.
     *
     * Le ViewModel survit à l'aller-retour vers un autre écran — il n'est vidé qu'au dépilement
     * réel de l'accueil — donc un `init` seul laissait l'écran figé sur l'état d'avant : le
     * catalogue fraîchement importé n'était pas compté, et surtout le raccourci « Reprendre »
     * ne montrait pas le brouillon qu'on venait de créer. Celui qui sort d'une collecte perdait
     * le chemin d'un tap pour y revenir.
     *
     * Le compteur de sessions, lui, vient d'un Flow et se met à jour seul.
     */
    fun rafraichir() {
        _magasinConfigure.value = parametres.magasinLibelle.takeIf { parametres.estConfigure }
        viewModelScope.launch {
            _nbArticles.value = articleDao.count()
            _dernierImport.value = articleDao.getLastImportDate()
            _lastBrouillon.value = sessionDao.getLastBrouillon()
        }
    }

    private fun charger() {
        rafraichir()
        viewModelScope.launch {
            _nbSessions.value = sessionDao.count()
        }
        viewModelScope.launch {
            sessionDao.getAllSessions().collect { sessions ->
                _nbSessions.value = sessions.size
            }
        }
    }
}
