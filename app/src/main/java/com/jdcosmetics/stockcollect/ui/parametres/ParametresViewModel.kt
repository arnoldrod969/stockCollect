package com.jdcosmetics.stockcollect.ui.parametres

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jdcosmetics.stockcollect.data.prefs.ParametresSync
import com.jdcosmetics.stockcollect.data.remote.NirgescomClient
import com.jdcosmetics.stockcollect.data.remote.ResultatSante
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

/** Les valeurs telles qu'elles sont enregistrées, pour préremplir les champs à l'ouverture. */
data class ParametresSaisis(
    val urlApi: String,
    val cleApi: String,
    val magasin: String,
    val identifiantTablette: String
)

@HiltViewModel
class ParametresViewModel @Inject constructor(
    private val parametres: ParametresSync,
    private val client: NirgescomClient
) : ViewModel() {

    private val _valeurs = MutableLiveData<ParametresSaisis>()
    val valeurs: LiveData<ParametresSaisis> = _valeurs

    private val _testState = MutableLiveData<TestUiState>(TestUiState.Idle)
    val testState: LiveData<TestUiState> = _testState

    private val _enregistre = MutableLiveData(false)
    val enregistre: LiveData<Boolean> = _enregistre

    init { charger() }

    private fun charger() {
        _valeurs.value = ParametresSaisis(
            urlApi = parametres.urlApi,
            cleApi = parametres.cleApi,
            magasin = parametres.magasin,
            identifiantTablette = parametres.identifiantTablette
        )
    }

    fun enregistrer(saisis: ParametresSaisis) {
        parametres.urlApi = saisis.urlApi
        parametres.cleApi = saisis.cleApi
        parametres.magasin = saisis.magasin
        parametres.identifiantTablette = saisis.identifiantTablette
        charger()
        _enregistre.value = true
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
                    TestUiState.Succes("Connexion établie. API version ${resultat.version}.")
                is ResultatSante.ApiSansBase ->
                    TestUiState.Echec("${resultat.detail} Prévenez le service informatique.")
                is ResultatSante.Injoignable ->
                    // Le détail vient de la pile réseau et n'est pas traduit : il va sur une ligne
                    // à part, pour que la consigne utile reste lisible en premier.
                    TestUiState.Echec(
                        "Serveur injoignable. Vérifiez le WiFi et l'adresse.\n\n${resultat.detail}"
                    )
                is ResultatSante.UrlInvalide ->
                    TestUiState.Echec(resultat.detail)
                is ResultatSante.ReponseInattendue ->
                    TestUiState.Echec("Réponse inattendue du serveur (code ${resultat.code}).")
            }
        }
    }

    fun resetTestState() { _testState.value = TestUiState.Idle }
    fun resetEnregistre() { _enregistre.value = false }
}
