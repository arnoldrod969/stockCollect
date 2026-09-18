package com.jdcosmetics.stockcollect.ui.session

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdcosmetics.stockcollect.data.db.entity.ArticleEntity
import com.jdcosmetics.stockcollect.data.db.entity.LigneCollecteEntity
import com.jdcosmetics.stockcollect.data.db.entity.SessionEntity
import com.jdcosmetics.stockcollect.data.prefs.ParametresSync
import com.jdcosmetics.stockcollect.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SaisieUiState {
    object Idle : SaisieUiState()
    object Loading : SaisieUiState()
    data class SessionCreee(val idSession: Long) : SaisieUiState()
    object SessionCloturee : SaisieUiState()

    /**
     * Un brouillon existe, mais d'un autre type que celui demandé. Reprendre en silence
     * ferait compter un inventaire sous un mouvement de stock : c'est à l'utilisateur de trancher.
     */
    data class BrouillonAutreType(
        val idSession: Long,
        val typeBrouillon: String,
        val typeDemande: String
    ) : SaisieUiState()

    data class Erreur(val message: String) : SaisieUiState()
}

@HiltViewModel
class SaisieViewModel @Inject constructor(
    private val repository: SessionRepository,
    private val parametres: ParametresSync
) : ViewModel() {

    /** Le dépôt réglé, tel qu'il sera inscrit dans la session. Vide si rien n'est configuré. */
    val magasinConfigure: String
        get() = parametres.magasinLibelle

    private val _uiState = MutableLiveData<SaisieUiState>(SaisieUiState.Idle)
    val uiState: LiveData<SaisieUiState> = _uiState

    private val _sessionCourante = MutableLiveData<SessionEntity?>()
    val sessionCourante: LiveData<SessionEntity?> = _sessionCourante

    private var _idSessionCourante: Long = -1L

    private var _lignes = MutableLiveData<List<LigneCollecteEntity>>(emptyList())
    val lignes: LiveData<List<LigneCollecteEntity>> = _lignes

    /** Collecteur du Flow des lignes. Annulé avant toute relance — voir observerLignes. */
    private var lignesJob: Job? = null

    private val _searchResults = MutableLiveData<List<ArticleEntity>>(emptyList())
    val searchResults: LiveData<List<ArticleEntity>> = _searchResults

    /**
     * Le dépôt n'est plus saisi ici : la session hérite du magasin réglé dans les Paramètres, dont
     * elle garde un **instantané** dans `lieu`, sur le même principe que `nom_produit_snap`.
     *
     * Relire le réglage au moment de l'envoi étiquetterait silencieusement la collecte sous le
     * mauvais dépôt si la tablette a été reconfigurée entre-temps. L'instantané fait au contraire
     * échouer l'envoi en 403 — bruyant, donc corrigeable.
     */
    fun creerSession(typeOperation: String, observations: String?) {
        val lieu = parametres.magasinLibelle.takeIf { it.isNotBlank() }
        _uiState.value = SaisieUiState.Loading
        viewModelScope.launch {
            try {
                val brouillonExistant = repository.getLastBrouillon()
                if (brouillonExistant != null) {
                    // Un brouillon d'un autre type ne se reprend pas en silence : les observations
                    // saisies seraient jetées, et la collecte irait grossir une session
                    // que l'utilisateur n'a pas choisie.
                    if (brouillonExistant.typeOperation != typeOperation) {
                        _uiState.value = SaisieUiState.BrouillonAutreType(
                            idSession = brouillonExistant.idSession,
                            typeBrouillon = brouillonExistant.typeOperation,
                            typeDemande = typeOperation
                        )
                        return@launch
                    }
                    reprendre(brouillonExistant.idSession)
                    return@launch
                }
                val id = repository.creerSession(typeOperation, lieu, observations)
                reprendre(id)
            } catch (e: Exception) {
                // Le message d'exception n'est ni français ni actionnable : il passe en détail
                // secondaire, derrière la consigne.
                _uiState.value = SaisieUiState.Erreur(
                    "La session n'a pas pu être créée. Réessayez ; si cela se reproduit, " +
                        "appelez le service informatique." + detailTechnique(e)
                )
            }
        }
    }

    /** L'utilisateur a choisi de reprendre le brouillon existant malgré son type différent. */
    fun reprendreBrouillon(idSession: Long) {
        viewModelScope.launch {
            try {
                reprendre(idSession)
            } catch (e: Exception) {
                _uiState.value = SaisieUiState.Erreur(
                    "Ce brouillon n'a pas pu être rouvert. Retrouvez-le dans l'Historique ; " +
                        "si cela se reproduit, appelez le service informatique." + detailTechnique(e)
                )
            }
        }
    }

    private suspend fun reprendre(idSession: Long) {
        _idSessionCourante = idSession
        chargerSession(idSession)
        observerLignes(idSession)
        _uiState.value = SaisieUiState.SessionCreee(idSession)
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

    /**
     * Le Flow Room ne se termine jamais : sans annulation, chaque appel empilerait un collecteur
     * de plus. Ce ViewModel étant partagé par activityViewModels() sur quatre écrans, les
     * collecteurs survivaient jusqu'à la destruction de l'Activity, à republier la même liste.
     */
    private fun observerLignes(idSession: Long) {
        lignesJob?.cancel()
        lignesJob = viewModelScope.launch {
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
                // Le UPDATE gardé ne matche que BROUILLON : un échec veut dire que la session
                // était déjà clôturée, pas qu'il y a eu une panne.
                _uiState.value = SaisieUiState.Erreur(
                    "Cette session est déjà clôturée. Retrouvez-la dans l'Historique pour " +
                        "l'exporter ou l'envoyer à Nirgescom."
                )
            }
        }
    }

    fun getIdSessionCourante(): Long = _idSessionCourante

    /** Le message d'origine, sur une ligne à part, pour que la consigne reste lisible en premier. */
    private fun detailTechnique(e: Exception): String =
        e.message?.takeIf { it.isNotBlank() }?.let { "\n\n$it" }.orEmpty()

    fun resetState() { _uiState.value = SaisieUiState.Idle }
}
