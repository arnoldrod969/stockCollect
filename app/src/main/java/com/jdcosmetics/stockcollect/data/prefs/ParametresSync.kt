package com.jdcosmetics.stockcollect.data.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Réglages de connexion à l'API Nirgescom, saisis dans l'écran Paramètres.
 *
 * Volontairement en `SharedPreferences` et non en Room : ce sont des réglages d'appareil, pas des
 * données de collecte. Ils ne doivent ni partir dans l'export CSV, ni transiter par la synchro, ni
 * être effacés quand on vide la base. DataStore aurait fait l'affaire mais n'est pas dans le
 * projet, et quatre chaînes ne justifient pas une dépendance de plus.
 *
 * Le fichier est exclu de la sauvegarde automatique comme le reste de l'app
 * (`allowBackup="false"`) : la clé d'API est propre à une tablette, la restaurer sur une autre
 * ferait pointer deux appareils sur le même magasin.
 */
@Singleton
class ParametresSync @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    /** Racine de l'API, sans le `/api` final — il est ajouté par le client. */
    var urlApi: String
        get() = prefs.getString(CLE_URL, "").orEmpty()
        set(valeur) = prefs.edit().putString(CLE_URL, valeur.trim().trimEnd('/')).apply()

    var cleApi: String
        get() = prefs.getString(CLE_API, "").orEmpty()
        set(valeur) = prefs.edit().putString(CLE_API, valeur.trim()).apply()

    /**
     * Doit valoir **exactement** le libellé porté par la clé d'API : l'API compare les deux à
     * l'identique et répond 403 sinon. Ce n'est pas `sessions.lieu`, qui est du texte libre saisi
     * par l'opérateur au moment de la collecte.
     */
    var magasin: String
        get() = prefs.getString(CLE_MAGASIN, "").orEmpty()
        set(valeur) = prefs.edit().putString(CLE_MAGASIN, valeur.trim()).apply()

    /** Identifie la tablette dans les logs de l'API, pour retrouver qui a envoyé quoi. */
    var identifiantTablette: String
        get() = prefs.getString(CLE_TABLETTE, "").orEmpty()
        set(valeur) = prefs.edit().putString(CLE_TABLETTE, valeur.trim()).apply()

    /** Sans ces trois-là, aucun envoi n'est possible ; l'identifiant tablette reste optionnel. */
    val estConfigure: Boolean
        get() = urlApi.isNotBlank() && cleApi.isNotBlank() && magasin.isNotBlank()

    private companion object {
        const val FICHIER = "parametres_sync"
        const val CLE_URL = "url_api"
        const val CLE_API = "cle_api"
        const val CLE_MAGASIN = "magasin"
        const val CLE_TABLETTE = "identifiant_tablette"
    }
}
