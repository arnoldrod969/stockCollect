package com.jdcosmetics.stockcollect.data.remote

import java.net.HttpURLConnection

/** Une erreur de validation telle que l'API la détaille en `422` : `{"champ", "message"}`. */
data class ErreurChamp(val champ: String, val message: String)

/**
 * Le champ `detail` d'une réponse d'erreur de l'API, déjà décodé.
 *
 * L'API renvoie tantôt une chaîne (`401`, `403`, `404`, `500`), tantôt une liste de
 * `{champ, message}` (`422`). Les deux formes sont gardées distinctes jusqu'à l'affichage.
 * Un corps vide ou qui n'est pas du JSON se décode en `null`.
 */
sealed class DetailApi {
    data class Texte(val texte: String) : DetailApi()
    data class Champs(val erreurs: List<ErreurChamp>) : DetailApi()
}

/** Les compteurs d'un `201` de `POST /documents`. */
data class RecapEnvoi(val recues: Int, val inserees: Int, val ignorees: Int)

/**
 * Correspondance « code HTTP + corps décodé → résultat » des routes Nirgescom.
 *
 * Isolée du client et de toute E/S pour être testée en JVM : `org.json` fait partie du SDK
 * Android et n'est qu'un bouchon dans les tests unitaires. Le client décode le corps, ces
 * fonctions décident de ce qu'il veut dire. Les codes suivent la SPEC de nirgescom-api, §4.
 */
object ReponsesNirgescom {

    private const val HTTP_UNPROCESSABLE = 422

    /**
     * URL d'une route, sans aucun paramètre de requête : l'API répond `422` à tout paramètre
     * qu'elle ne connaît pas (SPEC §4.6), et aucune route utilisée par l'app n'en attend.
     */
    fun url(racine: String, chemin: String): String = "${racine.trim().trimEnd('/')}$chemin"

    /**
     * Le `detail` mis en texte. En `422`, une ligne par champ : sans cela l'écran dirait
     * « données refusées » sans jamais dire lequel des champs pose problème.
     */
    fun message(detail: DetailApi?): String? = when (detail) {
        null -> null
        is DetailApi.Texte -> detail.texte.takeIf { it.isNotBlank() }
        is DetailApi.Champs -> detail.erreurs
            .joinToString("\n") { "${it.champ} : ${it.message}" }
            .takeIf { it.isNotBlank() }
    }

    /**
     * Le `403` de `POST /documents` a trois causes (SPEC §4.2) ; seule celle du `session_id` déjà
     * rangé sous un autre magasin appelle un geste différent. Le texte de l'API est le seul moyen
     * de la reconnaître : `session_id '<uuid>' appartient a un autre magasin`.
     */
    fun estSessionAutreMagasin(detail: DetailApi?): Boolean {
        val texte = message(detail)?.lowercase() ?: return false
        return "session_id" in texte && "autre magasin" in texte
    }

    /**
     * `POST /documents`. Tout `201` est un succès, y compris quand `lignes_ignorees` vaut le
     * total : c'est un renvoi après coupure. Un `201` au corps illisible reste un succès, avec
     * des compteurs à `-1` — la session **est** arrivée.
     */
    fun envoi(code: Int, detail: DetailApi?, recap: RecapEnvoi?): ResultatEnvoi {
        val texte = message(detail)
        return when (code) {
            HttpURLConnection.HTTP_CREATED -> {
                val r = recap ?: RecapEnvoi(-1, -1, -1)
                ResultatEnvoi.Ok(r.recues, r.inserees, r.ignorees)
            }
            HttpURLConnection.HTTP_BAD_REQUEST ->
                ResultatEnvoi.Invalide(texte ?: "Le serveur n'a pas su lire l'envoi.")
            HttpURLConnection.HTTP_UNAUTHORIZED ->
                ResultatEnvoi.CleRefusee(texte ?: "Clé d'API refusée.")
            HttpURLConnection.HTTP_FORBIDDEN ->
                if (estSessionAutreMagasin(detail)) {
                    ResultatEnvoi.SessionAutreMagasin(
                        texte ?: "Session déjà reçue sous un autre magasin."
                    )
                } else {
                    ResultatEnvoi.MagasinRefuse(texte ?: "Magasin non autorisé pour cette clé.")
                }
            HTTP_UNPROCESSABLE ->
                ResultatEnvoi.Invalide(texte ?: "Données refusées par le serveur.")
            HttpURLConnection.HTTP_INTERNAL_ERROR ->
                ResultatEnvoi.ConfigurationServeur(texte ?: "Erreur interne du serveur.")
            HttpURLConnection.HTTP_UNAVAILABLE ->
                ResultatEnvoi.Indisponible(
                    texte ?: "Le serveur ne peut pas enregistrer pour l'instant."
                )
            else -> ResultatEnvoi.ReponseInattendue(code, texte.orEmpty())
        }
    }

    /**
     * `GET /documents/{session_id}`. [etat] est `null` quand un `200` n'a pas pu être lu.
     */
    fun etat(code: Int, detail: DetailApi?, etat: EtatDocument?): ResultatEtat {
        val texte = message(detail)
        return when (code) {
            HttpURLConnection.HTTP_OK ->
                if (etat != null) ResultatEtat.Ok(etat)
                else ResultatEtat.ReponseInattendue(code, "Réponse illisible.")
            HttpURLConnection.HTTP_NOT_FOUND -> ResultatEtat.Inconnue
            HttpURLConnection.HTTP_UNAUTHORIZED ->
                ResultatEtat.CleRefusee(texte ?: "Clé d'API refusée.")
            HttpURLConnection.HTTP_FORBIDDEN ->
                ResultatEtat.NonAutorisee(texte ?: "Session non autorisée pour cette clé.")
            HTTP_UNPROCESSABLE ->
                ResultatEtat.IdentifiantInvalide(texte ?: "Identifiant de session refusé.")
            HttpURLConnection.HTTP_INTERNAL_ERROR ->
                ResultatEtat.ConfigurationServeur(texte ?: "Erreur interne du serveur.")
            HttpURLConnection.HTTP_UNAVAILABLE ->
                ResultatEtat.Indisponible(texte ?: "Serveur indisponible.")
            else -> ResultatEtat.ReponseInattendue(code, texte.orEmpty())
        }
    }

    /**
     * `GET /catalog` et `GET /codes-barres`. [donnees] est `null` quand un `200` n'a pas pu être
     * lu — surtout pas une liste vide, qui passerait pour un référentiel vide.
     *
     * Une liste **réellement** vide reste un `Ok` : décider qu'elle ne remplace rien appartient à
     * l'import (`ImportNirgescom`), pas au classement des codes HTTP.
     */
    fun <T> referentiel(
        code: Int,
        detail: DetailApi?,
        donnees: T?,
        etag: String?
    ): ResultatReferentiel<T> {
        val texte = message(detail)
        return when (code) {
            HttpURLConnection.HTTP_OK ->
                if (donnees != null) ResultatReferentiel.Ok(donnees, etag?.takeIf { it.isNotBlank() })
                else ResultatReferentiel.ReponseInattendue(code, "Réponse illisible.")
            HttpURLConnection.HTTP_NOT_MODIFIED -> ResultatReferentiel.Inchange
            HttpURLConnection.HTTP_UNAUTHORIZED ->
                ResultatReferentiel.CleRefusee(texte ?: "Clé d'API refusée.")
            HttpURLConnection.HTTP_FORBIDDEN ->
                ResultatReferentiel.NonAutorise(texte ?: "Accès refusé pour cette clé.")
            HTTP_UNPROCESSABLE ->
                ResultatReferentiel.ParametreRefuse(texte ?: "Requête refusée par le serveur.")
            HttpURLConnection.HTTP_INTERNAL_ERROR ->
                ResultatReferentiel.ConfigurationServeur(texte ?: "Erreur interne du serveur.")
            HttpURLConnection.HTTP_UNAVAILABLE ->
                ResultatReferentiel.Indisponible(texte ?: "L'API ne joint pas sa base.")
            else -> ResultatReferentiel.ReponseInattendue(code, texte.orEmpty())
        }
    }

    /**
     * `GET /magasins`. [magasins] est `null` quand un `200` n'a pas pu être lu : une liste vide
     * bloquerait l'écran Paramètres en prétendant que le magasin n'existe pas.
     */
    fun magasins(
        code: Int,
        detail: DetailApi?,
        magasins: List<MagasinDistant>?,
        etag: String?
    ): ResultatMagasins {
        val texte = message(detail)
        return when (code) {
            HttpURLConnection.HTTP_OK ->
                if (magasins != null) ResultatMagasins.Ok(magasins, etag)
                else ResultatMagasins.ReponseInattendue(code, "Réponse illisible.")
            HttpURLConnection.HTTP_NOT_MODIFIED -> ResultatMagasins.Inchangee
            HttpURLConnection.HTTP_UNAUTHORIZED ->
                ResultatMagasins.CleRefusee(texte ?: "Clé d'API refusée.")
            HttpURLConnection.HTTP_INTERNAL_ERROR ->
                ResultatMagasins.ConfigurationServeur(texte ?: "Erreur interne du serveur.")
            HttpURLConnection.HTTP_UNAVAILABLE ->
                ResultatMagasins.ApiSansBase(texte ?: "L'API ne joint pas sa base.")
            else -> ResultatMagasins.ReponseInattendue(code, texte.orEmpty())
        }
    }
}
