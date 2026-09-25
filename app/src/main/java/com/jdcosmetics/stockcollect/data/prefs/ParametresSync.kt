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
     * Jeton du dépôt choisi (`tblsite.siCode`). Servira aux `GET /stock?magasin=` de l'étape 2.
     *
     * Le code et le libellé sont stockés tous les deux plutôt que l'un dérivé de l'autre : le
     * libellé est ce que `POST /documents` exige, le code est ce que les consultations exigent, et
     * retrouver l'un à partir de l'autre supposerait que le cache soit toujours peuplé.
     */
    var magasinCode: String
        get() = prefs.getString(CLE_MAGASIN_CODE, "").orEmpty()
        set(valeur) = prefs.edit().putString(CLE_MAGASIN_CODE, valeur.trim()).apply()

    /**
     * Libellé du dépôt, envoyé tel quel dans `POST /documents`. L'API le compare au libellé porté
     * par la clé d'API par égalité stricte et répond 403 au moindre écart — d'où le choix dans une
     * liste venue du serveur plutôt qu'une saisie libre.
     */
    var magasinLibelle: String
        get() = prefs.getString(CLE_MAGASIN_LIBELLE, "").orEmpty()
        set(valeur) = prefs.edit().putString(CLE_MAGASIN_LIBELLE, valeur.trim()).apply()

    /**
     * `ETag` de la dernière liste de dépôts reçue, rejoué en `If-None-Match`. Un `304` en retour
     * veut dire « rien n'a changé » — la liste en cache reste valable, on ne la retélécharge pas.
     */
    var etagMagasins: String
        get() = prefs.getString(CLE_ETAG_MAGASINS, "").orEmpty()
        set(valeur) = prefs.edit().putString(CLE_ETAG_MAGASINS, valeur).apply()

    /**
     * `ETag` du dernier catalogue reçu de `GET /catalog` **et écrit en base**. Enregistré seulement
     * après une écriture réussie (un import annulé à l'arbitrage n'en laisse pas), et effacé dès
     * que le catalogue local change par une autre voie (import CSV) : un `304` doit vouloir dire
     * « la tablette a déjà exactement cette version », pas « le serveur n'a pas bougé ».
     */
    var etagCatalogue: String
        get() = prefs.getString(CLE_ETAG_CATALOGUE, "").orEmpty()
        set(valeur) = prefs.edit().putString(CLE_ETAG_CATALOGUE, valeur).apply()

    /**
     * `ETag` de la dernière correspondance reçue de `GET /codes-barres` et écrite en base. Même
     * règle que [etagCatalogue] ; effacé aussi à chaque écriture du catalogue, qui peut retirer des
     * correspondances ou en rendre acceptables d'autres jusque-là refusées (article inconnu).
     */
    var etagCodesBarres: String
        get() = prefs.getString(CLE_ETAG_CODES_BARRES, "").orEmpty()
        set(valeur) = prefs.edit().putString(CLE_ETAG_CODES_BARRES, valeur).apply()

    /** Identifie la tablette dans les logs de l'API, pour retrouver qui a envoyé quoi. */
    var identifiantTablette: String
        get() = prefs.getString(CLE_TABLETTE, "").orEmpty()
        set(valeur) = prefs.edit().putString(CLE_TABLETTE, valeur.trim()).apply()

    /** Sans ces trois-là, aucun envoi n'est possible ; l'identifiant tablette reste optionnel. */
    val estConfigure: Boolean
        get() = urlApi.isNotBlank() && cleApi.isNotBlank() && magasinCode.isNotBlank()

    private companion object {
        const val FICHIER = "parametres_sync"
        const val CLE_URL = "url_api"
        const val CLE_API = "cle_api"
        const val CLE_MAGASIN_CODE = "magasin_code"
        const val CLE_MAGASIN_LIBELLE = "magasin_libelle"
        const val CLE_ETAG_MAGASINS = "etag_magasins"
        const val CLE_TABLETTE = "identifiant_tablette"
        const val CLE_ETAG_CATALOGUE = "etag_catalogue"
        const val CLE_ETAG_CODES_BARRES = "etag_codes_barres"
    }
}
