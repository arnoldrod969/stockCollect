package com.jdcosmetics.stockcollect.ui.session

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdcosmetics.stockcollect.data.db.entity.ArticleEntity
import com.jdcosmetics.stockcollect.data.db.entity.LigneCollecteEntity
import com.jdcosmetics.stockcollect.data.db.entity.SessionEntity
import com.jdcosmetics.stockcollect.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SaisieUiState {
    object Idle : SaisieUiState()
    object Loading : SaisieUiState()
    data class SessionCreee(val idSession: Long) : SaisieUiState()
    object SessionCloturee : SaisieUiState()
    data class Erreur(val message: String) : SaisieUiState()
}

@HiltViewModel
class SaisieViewModel @Inject constructor(
    private val repository: SessionRepository
) : ViewModel() {

    private val _uiState = MutableLiveData<SaisieUiState>(SaisieUiState.Idle)
    val uiState: LiveData<SaisieUiState> = _uiState

    private val _sessionCourante = MutableLiveData<SessionEntity?>()
    val sessionCourante: LiveData<SessionEntity?> = _sessionCourante

    private var _idSessionCourante: Long = -1L

    private var _lignes = MutableLiveData<List<LigneCollecteEntity>>(emptyList())
    val lignes: LiveData<List<LigneCollecteEntity>> = _lignes

    private val _searchResults = MutableLiveData<List<ArticleEntity>>(emptyList())
    val searchResults: LiveData<List<ArticleEntity>> = _searchResults

    fun creerSession(typeOperation: String, lieu: String?, observations: String?) {
        _uiState.value = SaisieUiState.Loading
        viewModelScope.launch {
            try {
                val id = repository.creerSession(typeOperation, lieu, observations)
                _idSessionCourante = id
                chargerSession(id)
                observerLignes(id)
                _uiState.value = SaisieUiState.SessionCreee(id)
            } catch (e: Exception) {
                _uiState.value = SaisieUiState.Erreur(e.message ?: "Erreur création session")
            }
        }
    }

    fun chargerSessionExistante(idSession: Long) {
        _idSessionCourante = idSession
        viewModelScope.launch {
            chargerSession(idSession)
            observerLignes(idSession)
        }
    }

    private suspend fun chargerSession(id: Long) {
        _sessionCourante.value = repository.getById(id)
    }

    private fun observerLignes(idSession: Long) {
        viewModelScope.launch {
            repository.getLignes(idSession).collect { liste ->
                _lignes.value = liste
            }
        }
    }

    fun ajouterLigne(article: ArticleEntity, codeBarreScanne: String?, quantite: Double) {
        val idSession = _idSessionCourante
        if (idSession < 0) return
        viewModelScope.launch {
            repository.ajouterLigne(idSession, article, codeBarreScanne, quantite)
            chargerSession(idSession)
        }
    }

    fun mettreAJourQuantite(ligne: LigneCollecteEntity, nouvelleQuantite: Double) {
        viewModelScope.launch {
            repository.mettreAJourQuantite(ligne, nouvelleQuantite)
        }
    }

    fun supprimerLigne(ligne: LigneCollecteEntity) {
        viewModelScope.launch {
            repository.supprimerLigne(ligne)
            chargerSession(_idSessionCourante)
        }
    }

    fun rechercherArticles(query: String) {
        if (query.length < 2) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _searchResults.value = repository.searchArticles(query)
        }
    }

    fun cloturer() {
        val id = _idSessionCourante
        if (id < 0) return
        viewModelScope.launch {
            val success = repository.cloturer(id)
            if (success) {
                _uiState.value = SaisieUiState.SessionCloturee
            } else {
                _uiState.value = SaisieUiState.Erreur("Impossible de clôturer la session.")
            }
        }
    }

    fun getIdSessionCourante(): Long = _idSessionCourante

    fun resetState() { _uiState.value = SaisieUiState.Idle }
}
