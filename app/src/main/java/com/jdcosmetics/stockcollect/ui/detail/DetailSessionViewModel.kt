package com.jdcosmetics.stockcollect.ui.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdcosmetics.stockcollect.data.db.dao.LigneCollecteDao
import com.jdcosmetics.stockcollect.data.db.dao.SessionDao
import com.jdcosmetics.stockcollect.data.db.entity.LigneCollecteEntity
import com.jdcosmetics.stockcollect.data.db.entity.SessionEntity
import com.jdcosmetics.stockcollect.data.remote.NirgescomClient
import com.jdcosmetics.stockcollect.data.remote.ResultatEtat
import com.jdcosmetics.stockcollect.domain.service.ResultatSync
import com.jdcosmetics.stockcollect.domain.service.SyncService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ce que l'écran a à afficher au sujet de l'envoi vers Nirgescom. */
sealed class SyncUiState {
    object Idle : SyncUiState()
    object Loading : SyncUiState()
    data class Succes(val message: String) : SyncUiState()
    data class Echec(val message: String) : SyncUiState()
}

@HiltViewModel
class DetailSessionViewModel @Inject constructor(
    private val sessionDao: SessionDao,
    private val ligneDao: LigneCollecteDao,
    private val syncService: SyncService,
    private val client: NirgescomClient
) : ViewModel() {

    private val _session = MutableLiveData<SessionEntity?>()
    val session: LiveData<SessionEntity?> = _session

    private val _lignes = MutableLiveData<List<LigneCollecteEntity>>(emptyList())
    val lignes: LiveData<List<LigneCollecteEntity>> = _lignes

    private val _syncState = MutableLiveData<SyncUiState>(SyncUiState.Idle)
    val syncState: LiveData<SyncUiState> = _syncState

    private var idSession: Long = -1L

    fun charger(idSession: Long) {
        this.idSession = idSession
        viewModelScope.launch {
            _session.value = sessionDao.getById(idSession)
            ligneDao.getLignesBySession(idSession).collect { liste ->
                _lignes.value = liste
            }
        }
    }

    /** Relit la session seule — le Flow des lignes tourne déjà, inutile de le relancer. */
    private fun rafraichirSession() {
        viewModelScope.launch { _session.value = sessionDao.getById(idSession) }
    }

    fun synchroniser() {
        _syncState.value = SyncUiState.Loading
        viewModelScope.launch {
            val resultat = syncService.synchroniser(idSession)
            _syncState.value = when (resultat) {
                is ResultatSync.Ok -> SyncUiState.Succes(messageSucces(resultat))
                is ResultatSync.Refuse -> SyncUiState.Echec(resultat.message)
                is ResultatSync.Reessayable -> SyncUiState.Echec(resultat.message)
                is ResultatSync.Impossible -> SyncUiState.Echec(resultat.message)
            }
            rafraichirSession()
        }
    }

    /**
     * Un renvoi après coupure réseau n'insère rien de neuf et c'est **normal** (contrat §4.2) :
     * le dire évite qu'un « 0 ligne insérée » ne passe pour un échec.
     */
    private fun messageSucces(resultat: ResultatSync.Ok): String = when {
        resultat.inserees < 0 -> "Session envoyée à Nirgescom."
        resultat.ignorees > 0 && resultat.inserees == 0 ->
            "Nirgescom avait déjà reçu cette session : ${lignes(resultat.ignorees)} y " +
                "${if (resultat.ignorees > 1) "sont" else "est"} déjà. Rien à refaire."
        resultat.ignorees > 0 ->
            "Session envoyée à Nirgescom : ${lignes(resultat.inserees)} de plus, " +
                "${resultat.ignorees} y étaient déjà."
        else -> "Session envoyée à Nirgescom : ${lignes(resultat.inserees)} en tout."
    }

    /** L'accord au singulier compte : une session d'une seule ligne n'est pas un cas rare ici. */
    private fun lignes(nb: Int) = "$nb ligne${if (nb > 1) "s" else ""}"

    /** Interroge Nirgescom sur le sort des lignes déjà envoyées (contrat §4.3). */
    fun consulterEtat() {
        val uuid = _session.value?.uuidSession
        if (uuid.isNullOrBlank()) {
            _syncState.value = SyncUiState.Echec(
                "Cette session n'a pas encore été envoyée. Touchez « Envoyer » d'abord."
            )
            return
        }
        _syncState.value = SyncUiState.Loading
        viewModelScope.launch {
            _syncState.value = when (val r = client.consulterDocument(uuid)) {
                is ResultatEtat.Ok -> {
                    val e = r.etat
                    val base = "Chez Nirgescom : ${e.traite} lignes traitées, ${e.enAttente} en " +
                        "attente, ${e.erreur} en erreur (sur ${e.total})."
                    if (e.erreurs.isEmpty()) SyncUiState.Succes(base)
                    // Des lignes en erreur ne sont pas un échec d'envoi : la session est bien
                    // arrivée, c'est Nirgescom qui bute sur des produits. D'où le rouge, mais pas
                    // de changement de statut_sync.
                    else SyncUiState.Echec(
                        "$base\n\nCes lignes sont à corriger dans Nirgescom :\n" +
                            e.erreurs.joinToString("\n")
                    )
                }
                is ResultatEtat.Inconnue ->
                    SyncUiState.Echec(
                        "Nirgescom n'a aucune ligne pour cette session. Renvoyez-la."
                    )
                is ResultatEtat.CleRefusee -> SyncUiState.Echec(
                    "Clé d'API refusée par Nirgescom. Vérifiez la clé dans Paramètres ; si elle " +
                        "est correcte, appelez le service informatique.\n\n${r.detail}"
                )
                is ResultatEtat.NonAutorisee -> SyncUiState.Echec(
                    "Nirgescom a rangé cette session sous un autre dépôt que celui de la clé " +
                        "d'API réglée sur la tablette : elle n'est pas consultable avec cette " +
                        "clé. Appelez le service informatique si c'est inattendu.\n\n${r.detail}"
                )
                is ResultatEtat.IdentifiantInvalide -> SyncUiState.Echec(
                    "Nirgescom ne reconnaît pas l'identifiant de cette session. Prévenez le " +
                        "service informatique.\n\n${r.detail}"
                )
                is ResultatEtat.ConfigurationServeur -> SyncUiState.Echec(
                    "Le serveur Nirgescom est mal configuré (clé d'API ou base de données côté " +
                        "serveur). Ni le WiFi ni la tablette ne sont en cause : prévenez le " +
                        "service informatique.\n\n${r.detail}"
                )
                is ResultatEtat.Indisponible -> SyncUiState.Echec(
                    "Nirgescom ne répond pas pour le moment. Réessayez dans quelques minutes." +
                        "\n\n${r.detail}"
                )
                is ResultatEtat.Injoignable ->
                    SyncUiState.Echec(
                        "Serveur injoignable. Vérifiez que la tablette est sur le WiFi de " +
                            "l'entrepôt, puis réessayez.\n\n${r.detail}"
                    )
                is ResultatEtat.ReponseInattendue ->
                    SyncUiState.Echec(
                        "Réponse inattendue du serveur. Réessayez ; si cela se reproduit, " +
                            "prévenez le service informatique.\n\nCode ${r.code}. ${r.detail}"
                    )
            }
        }
    }

    fun resetSyncState() { _syncState.value = SyncUiState.Idle }
}
