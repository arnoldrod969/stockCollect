package com.jdcosmetics.stockcollect.ui.parametres

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdcosmetics.stockcollect.data.db.dao.MagasinDao
import com.jdcosmetics.stockcollect.data.db.entity.MagasinEntity
import com.jdcosmetics.stockcollect.data.prefs.ParametresSync
import com.jdcosmetics.stockcollect.data.remote.NirgescomClient
import com.jdcosmetics.stockcollect.data.remote.ResultatMagasins
import com.jdcosmetics.stockcollect.data.remote.ResultatSante
import com.jdcosmetics.stockcollect.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ce que l'écran a à afficher au sujet du test de connexion. */
sealed class TestUiState {
    object Idle : TestUiState()
    object Loading : TestUiState()
    data class Succes(val message: String) : TestUiState()
    data class Echec(val message: String) : TestUiState()
}

/** Ce que l'écran a à afficher au sujet de la récupération des dépôts. */
sealed class DepotsUiState {
    object Idle : DepotsUiState()
    object Loading : DepotsUiState()
    data class Succes(val message: String) : DepotsUiState()
    data class Echec(val message: String) : DepotsUiState()
}

/** Résultat d'un « Enregistrer ». */
sealed class EnregistrementUiState {
    object Idle : EnregistrementUiState()
    object Ok : EnregistrementUiState()
    data class Refuse(val message: String) : EnregistrementUiState()
}

/**
 * Les valeurs texte telles qu'elles sont enregistrées, pour préremplir les champs à l'ouverture.
 *
 * Le magasin n'en fait pas partie : il n'est pas saisi mais choisi, et se suit à part via
 * [ParametresViewModel.magasinSelectionne].
 */
data class ParametresSaisis(
    val urlApi: String,
    val cleApi: String,
    val identifiantTablette: String
)

@HiltViewModel
class ParametresViewModel @Inject constructor(
    private val parametres: ParametresSync,
    private val client: NirgescomClient,
    private val magasinDao: MagasinDao
) : ViewModel() {

    private val _valeurs = MutableLiveData<ParametresSaisis>()
    val valeurs: LiveData<ParametresSaisis> = _valeurs

    /** Le cache local, dans l'ordre où la liste déroulante l'affiche. */
    private val _magasins = MutableLiveData<List<MagasinEntity>>(emptyList())
    val magasins: LiveData<List<MagasinEntity>> = _magasins

    private val _magasinSelectionne = MutableLiveData<MagasinEntity?>()
    val magasinSelectionne: LiveData<MagasinEntity?> = _magasinSelectionne

    private val _testState = MutableLiveData<TestUiState>(TestUiState.Idle)
    val testState: LiveData<TestUiState> = _testState

    private val _depotsState = MutableLiveData<DepotsUiState>(DepotsUiState.Idle)
    val depotsState: LiveData<DepotsUiState> = _depotsState

    private val _enregistrement =
        MutableLiveData<EnregistrementUiState>(EnregistrementUiState.Idle)
    val enregistrement: LiveData<EnregistrementUiState> = _enregistrement

    init {
        chargerValeurs()
        chargerMagasins()
    }

    private fun chargerValeurs() {
        _valeurs.value = ParametresSaisis(
            urlApi = parametres.urlApi,
            cleApi = parametres.cleApi,
            identifiantTablette = parametres.identifiantTablette
        )
    }

    private fun chargerMagasins() {
        viewModelScope.launch {
            val liste = magasinDao.getAll()
            _magasins.value = liste
            // Le magasin enregistré est relu depuis le cache plutôt que depuis les préférences :
            // s'il a disparu de la liste côté Nirgescom, la sélection doit redevenir vide plutôt
            // que d'afficher un dépôt que le serveur refusera.
            _magasinSelectionne.value = liste.firstOrNull { it.codeMagasin == parametres.magasinCode }
        }
    }

    fun selectionnerMagasin(magasin: MagasinEntity) {
        _magasinSelectionne.value = magasin
    }

    fun enregistrer(saisis: ParametresSaisis) {
        val magasin = _magasinSelectionne.value
        if (magasin == null || !magasin.selectionnable) {
            _enregistrement.value = EnregistrementUiState.Refuse(
                if (_magasins.value.isNullOrEmpty())
                    "Récupérez d'abord la liste des dépôts, puis choisissez-en un."
                else "Choisissez un dépôt avant d'enregistrer."
            )
            return
        }
        parametres.urlApi = saisis.urlApi
        parametres.cleApi = saisis.cleApi
        parametres.identifiantTablette = saisis.identifiantTablette
        parametres.magasinCode = magasin.codeMagasin
        parametres.magasinLibelle = magasin.nomMagasin.orEmpty()
        chargerValeurs()
        _enregistrement.value = EnregistrementUiState.Ok
    }

    /**
     * Teste l'adresse telle qu'elle est tapée à l'écran, sans l'enregistrer : on doit pouvoir
     * essayer une IP avant de la valider.
     */
    fun tester(urlSaisie: String) {
        if (urlSaisie.isBlank()) {
            _testState.value = TestUiState.Echec("Renseignez d'abord l'adresse du serveur.")
            return
        }
        _testState.value = TestUiState.Loading
        viewModelScope.launch {
            _testState.value = when (val resultat = client.tester(urlSaisie)) {
                is ResultatSante.Ok ->
                    TestUiState.Succes("Connexion établie. Nirgescom version ${resultat.version}.")
                is ResultatSante.ApiSansBase ->
                    TestUiState.Echec(
                        "Le serveur répond mais n'accède pas à ses données. Prévenez le service " +
                            "informatique.\n\n${resultat.detail}"
                    )
                is ResultatSante.Injoignable ->
                    // Le détail vient de la pile réseau et n'est pas traduit : il va sur une ligne
                    // à part, pour que la consigne utile reste lisible en premier.
                    TestUiState.Echec(
                        "Serveur injoignable. Vérifiez que la tablette est sur le WiFi de " +
                            "l'entrepôt, puis relisez l'adresse ci-dessus.\n\n${resultat.detail}"
                    )
                is ResultatSante.UrlInvalide ->
                    TestUiState.Echec(resultat.detail)
                is ResultatSante.ReponseInattendue ->
                    TestUiState.Echec(
                        "Réponse inattendue du serveur. Prévenez le service informatique." +
                            "\n\nCode ${resultat.code}."
                    )
            }
        }
    }

    /**
     * Récupère la liste des dépôts avec l'adresse et la clé **telles qu'elles sont tapées**, pour
     * la même raison que [tester] : on doit pouvoir essayer avant d'enregistrer.
     *
     * L'ETag n'est rejoué que si l'adresse et la clé sont celles déjà enregistrées. Sinon on
     * interroge un autre serveur, dont l'ETag précédent ne dit rien — un `304` renverrait alors la
     * liste du serveur précédent.
     */
    fun recupererDepots(urlSaisie: String, cleSaisie: String) {
        if (urlSaisie.isBlank() || cleSaisie.isBlank()) {
            _depotsState.value =
                DepotsUiState.Echec("Renseignez l'adresse du serveur et la clé d'API.")
            return
        }
        _depotsState.value = DepotsUiState.Loading
        viewModelScope.launch {
            val memeCible = urlSaisie.trim().trimEnd('/') == parametres.urlApi &&
                cleSaisie.trim() == parametres.cleApi
            val etag = if (memeCible) parametres.etagMagasins else ""

            _depotsState.value = when (val r = client.recupererMagasins(urlSaisie, cleSaisie, etag)) {
                is ResultatMagasins.Ok -> enregistrerDepots(r)
                is ResultatMagasins.Inchangee ->
                    DepotsUiState.Succes("Liste déjà à jour (${_magasins.value?.size ?: 0} dépôts).")
                is ResultatMagasins.CleRefusee ->
                    DepotsUiState.Echec(
                        "Clé d'API refusée. Relisez la clé ci-dessus ; si elle est correcte, " +
                            "demandez-en une au service informatique.\n\n${r.detail}"
                    )
                is ResultatMagasins.ApiSansBase ->
                    DepotsUiState.Echec(
                        "Le serveur ne peut pas lire la liste des dépôts. Prévenez le service " +
                            "informatique.\n\n${r.detail}"
                    )
                is ResultatMagasins.Injoignable ->
                    DepotsUiState.Echec(
                        "Serveur injoignable. Vérifiez que la tablette est sur le WiFi de " +
                            "l'entrepôt, puis relisez l'adresse ci-dessus.\n\n${r.detail}"
                    )
                is ResultatMagasins.UrlInvalide -> DepotsUiState.Echec(r.detail)
                is ResultatMagasins.ReponseInattendue ->
                    DepotsUiState.Echec(
                        "Réponse inattendue du serveur. Prévenez le service informatique." +
                            "\n\nCode ${r.code}. ${r.detail}"
                    )
            }
        }
    }

    /**
     * Une réponse vide n'écrase pas le cache : elle signalerait à tort que le magasin configuré
     * n'existe plus, et laisserait la tablette inutilisable alors que la liste précédente était
     * valable.
     */
    private suspend fun enregistrerDepots(resultat: ResultatMagasins.Ok): DepotsUiState {
        if (resultat.magasins.isEmpty()) {
            return DepotsUiState.Echec(
                "Le serveur ne déclare aucun dépôt. La liste précédente reste utilisable ; " +
                    "prévenez le service informatique."
            )
        }
        val maintenant = DateUtils.nowIso()
        magasinDao.remplacerTout(
            resultat.magasins.map {
                MagasinEntity(
                    codeMagasin = it.codeMagasin,
                    nomMagasin = it.nomMagasin,
                    dateImport = maintenant
                )
            }
        )
        // L'ETag n'est retenu qu'une fois la table réellement écrite : le mémoriser plus tôt ferait
        // répondre 304 au prochain appel alors que le cache n'aurait pas la liste correspondante.
        parametres.etagMagasins = resultat.etag.orEmpty()
        chargerMagasins()

        val sansLibelle = resultat.magasins.count { it.nomMagasin.isNullOrBlank() }
        val message = "${resultat.magasins.size} dépôts récupérés. Choisissez le vôtre ci-dessous." +
            if (sansLibelle > 0) " ($sansLibelle sans nom : ils ne peuvent pas être choisis.)" else ""
        return DepotsUiState.Succes(message)
    }

    fun resetTestState() { _testState.value = TestUiState.Idle }
    fun resetDepotsState() { _depotsState.value = DepotsUiState.Idle }
    fun resetEnregistrement() { _enregistrement.value = EnregistrementUiState.Idle }
}
