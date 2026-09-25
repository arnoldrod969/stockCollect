package com.jdcosmetics.stockcollect.data.remote

import com.jdcosmetics.stockcollect.data.prefs.ParametresSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Résultat d'un test de connexion, tel que l'écran Paramètres doit le raconter à l'opérateur.
 *
 * Les cas sont distingués parce qu'ils appellent des gestes différents : une URL fautive se
 * corrige à l'écran, un serveur injoignable se règle côté WiFi, une base injoignable côté serveur
 * n'est pas du ressort du magasinier et doit remonter à l'IT.
 */
sealed class ResultatSante {
    data class Ok(val version: String) : ResultatSante()

    /** L'API répond mais sa base est injoignable — `503`, `"db": "unreachable"` (SPEC §4.1). */
    data class ApiSansBase(val detail: String) : ResultatSante()

    data class Injoignable(val detail: String) : ResultatSante()
    data class UrlInvalide(val detail: String) : ResultatSante()
    data class ReponseInattendue(val code: Int) : ResultatSante()
}

/**
 * Un dépôt tel que `GET /magasins` le renvoie. `nomMagasin` est nullable côté API.
 *
 * `codeMagasin` est le `siCode` Nirgescom, traité comme un texte opaque : les codes réels sont
 * surtout numériques (`22020104`) mais pas tous (`220301A1`). Rien dans l'app ne doit en supposer
 * le format.
 */
data class MagasinDistant(val codeMagasin: String, val nomMagasin: String?)

/**
 * Résultat d'une récupération de la liste des dépôts.
 *
 * [Inchangee] est un **succès** : le serveur a répondu `304`, la liste en cache est à jour. Le
 * distinguer de [Ok] évite de réécrire la base pour rien et permet de le dire à l'opérateur.
 */
sealed class ResultatMagasins {
    data class Ok(val magasins: List<MagasinDistant>, val etag: String?) : ResultatMagasins()
    object Inchangee : ResultatMagasins()

    /** `401` : la clé d'API est refusée. Rien à voir avec le réseau, d'où un cas à part. */
    data class CleRefusee(val detail: String) : ResultatMagasins()

    /** `503` : l'API répond mais ne joint pas sa base. Se réessaie plus tard. */
    data class ApiSansBase(val detail: String) : ResultatMagasins()

    /**
     * `500` : serveur mal configuré — vue absente, `GRANT` incomplet, clé sans `code_magasin`
     * valide (SPEC §4.2 et §4.6). Ni la tablette ni un réessai n'y peuvent rien.
     */
    data class ConfigurationServeur(val detail: String) : ResultatMagasins()

    data class Injoignable(val detail: String) : ResultatMagasins()
    data class UrlInvalide(val detail: String) : ResultatMagasins()
    data class ReponseInattendue(val code: Int, val detail: String) : ResultatMagasins()
}

/**
 * Ce que l'API a fait d'un envoi.
 *
 * [Ok] couvre **tout** `201`, y compris un renvoi intégral où `lignesIgnorees` vaut le total : le
 * contrat §4.2 le dit explicitement, les doublons sont ignorés et non rejetés. Traiter ce cas comme
 * une erreur ferait réessayer indéfiniment une session pourtant arrivée.
 */
sealed class ResultatEnvoi {
    data class Ok(val recues: Int, val inserees: Int, val ignorees: Int) : ResultatEnvoi()

    /** `403` : le magasin envoyé ne correspond pas à celui de la clé. */
    data class MagasinRefuse(val detail: String) : ResultatEnvoi()

    /**
     * `403` : ce `session_id` est déjà rangé sous un autre magasin — la session a été envoyée
     * une première fois avec la clé d'un autre dépôt. Réessayer ne la déplacera pas.
     */
    data class SessionAutreMagasin(val detail: String) : ResultatEnvoi()

    data class CleRefusee(val detail: String) : ResultatEnvoi()

    /** `422` (validation, détail par champ) ou `400`. Inutile de réessayer tel quel. */
    data class Invalide(val detail: String) : ResultatEnvoi()

    /**
     * `500` : clé sans `code_magasin` valide (« Configuration de la cle incorrecte »), base mal
     * configurée ou erreur interne. **Pas** réessayable en l'état : il faut intervenir sur le
     * serveur (SPEC §4.2).
     */
    data class ConfigurationServeur(val detail: String) : ResultatEnvoi()

    /** `503` : base injoignable. Le seul refus serveur qui se réessaie. */
    data class Indisponible(val detail: String) : ResultatEnvoi()

    data class Injoignable(val detail: String) : ResultatEnvoi()
    data class UrlInvalide(val detail: String) : ResultatEnvoi()
    data class ReponseInattendue(val code: Int, val detail: String) : ResultatEnvoi()
}

/** État d'une session côté Nirgescom (`GET /documents/{session_id}`). */
data class EtatDocument(
    val total: Int,
    val enAttente: Int,
    val traite: Int,
    val erreur: Int,
    val dateReception: String?,
    val erreurs: List<String>
)

sealed class ResultatEtat {
    data class Ok(val etat: EtatDocument) : ResultatEtat()

    /** `404` : l'API ne connaît aucune ligne pour cette session. */
    object Inconnue : ResultatEtat()

    /** `401` : clé absente ou inconnue. */
    data class CleRefusee(val detail: String) : ResultatEtat()

    /** `403` : la session appartient à un autre magasin que celui de la clé. */
    data class NonAutorisee(val detail: String) : ResultatEtat()

    /** `422` : l'identifiant de session n'est pas un UUID. */
    data class IdentifiantInvalide(val detail: String) : ResultatEtat()

    /** `500` : serveur mal configuré, voir [ResultatEnvoi.ConfigurationServeur]. */
    data class ConfigurationServeur(val detail: String) : ResultatEtat()

    /** `503` : base injoignable. */
    data class Indisponible(val detail: String) : ResultatEtat()

    data class Injoignable(val detail: String) : ResultatEtat()
    data class ReponseInattendue(val code: Int, val detail: String) : ResultatEtat()
}

/**
 * Client HTTP de l'API Nirgescom.
 *
 * `HttpURLConnection` plutôt qu'OkHttp ou Retrofit : quatre appels, en clair, sur un LAN, sans
 * authentification négociée ni retry automatique à câbler. Une pile HTTP complète grossirait l'APK
 * et demanderait des keep rules R8 supplémentaires pour un bénéfice nul à cette échelle. Le seuil
 * serait un vrai besoin de streaming, de reprise, ou d'intercepteurs.
 *
 * Ce fichier ne fait que les E/S et le décodage JSON ; ce que veut dire un code HTTP est décidé
 * par [ReponsesNirgescom], testable sans réseau ni SDK Android.
 */
@Singleton
class NirgescomClient @Inject constructor(private val parametres: ParametresSync) {

    /**
     * `GET /health` — sans authentification (SPEC §4.1), ce qui en fait justement un bon test :
     * il isole un problème de réseau d'un problème de clé d'API.
     */
    suspend fun tester(urlRacine: String): ResultatSante = withContext(Dispatchers.IO) {
        val url = try {
            URL(ReponsesNirgescom.url(urlRacine, CHEMIN_SANTE))
        } catch (e: MalformedURLException) {
            return@withContext ResultatSante.UrlInvalide(
                "Adresse illisible. Attendu : http://192.168.1.10:8000"
            )
        }

        var connexion: HttpURLConnection? = null
        try {
            connexion = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = DELAI_MS
                readTimeout = DELAI_MS
            }
            val code = connexion.responseCode
            val corps = lireCorps(connexion, code)

            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val version = runCatching { JSONObject(corps).optString("version", "?") }
                        .getOrDefault("?")
                    ResultatSante.Ok(version)
                }
                HttpURLConnection.HTTP_UNAVAILABLE -> {
                    val db = runCatching { JSONObject(corps).optString("db", "?") }.getOrDefault("?")
                    ResultatSante.ApiSansBase("L'API répond mais sa base est « $db ».")
                }
                else -> ResultatSante.ReponseInattendue(code)
            }
        } catch (e: SocketTimeoutException) {
            ResultatSante.Injoignable("Pas de réponse après ${DELAI_MS / 1000} s.")
        } catch (e: IOException) {
            ResultatSante.Injoignable(e.message ?: "Serveur injoignable.")
        } finally {
            connexion?.disconnect()
        }
    }

    /** Teste l'adresse déjà enregistrée. */
    suspend fun tester(): ResultatSante = tester(parametres.urlApi)

    /**
     * `GET /magasins` — authentifié, contrairement à `/health` (SPEC §9).
     *
     * L'`ETag` du dernier appel est rejoué en `If-None-Match` : le serveur répond alors `304` sans
     * corps, ce qui évite de réécrire la table pour une liste identique. L'appelant reçoit
     * [ResultatMagasins.Inchangee] et n'a rien à faire.
     */
    suspend fun recupererMagasins(
        urlRacine: String = parametres.urlApi,
        cleApi: String = parametres.cleApi,
        etag: String = parametres.etagMagasins
    ): ResultatMagasins = withContext(Dispatchers.IO) {
        val url = try {
            URL(ReponsesNirgescom.url(urlRacine, CHEMIN_MAGASINS))
        } catch (e: MalformedURLException) {
            return@withContext ResultatMagasins.UrlInvalide(
                "Adresse illisible. Attendu : http://192.168.1.10:8000"
            )
        }

        var connexion: HttpURLConnection? = null
        try {
            connexion = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = DELAI_MS
                readTimeout = DELAI_MS
                setRequestProperty(EN_TETE_CLE, cleApi)
                if (etag.isNotBlank()) setRequestProperty("If-None-Match", etag)
            }
            val code = connexion.responseCode
            val corps = lireCorps(connexion, code)
            val magasins = if (code == HttpURLConnection.HTTP_OK) lireMagasins(corps) else null

            ReponsesNirgescom.magasins(
                code, lireDetail(corps), magasins, connexion.getHeaderField("ETag")
            )
        } catch (e: SocketTimeoutException) {
            ResultatMagasins.Injoignable("Pas de réponse après ${DELAI_MS / 1000} s.")
        } catch (e: IOException) {
            ResultatMagasins.Injoignable(e.message ?: "Serveur injoignable.")
        } finally {
            connexion?.disconnect()
        }
    }

    /** `null` sur un corps illisible, que [ReponsesNirgescom.magasins] traite en réponse inattendue. */
    private fun lireMagasins(corps: String): List<MagasinDistant>? = try {
        val tableau = JSONObject(corps).getJSONArray("magasins")
        (0 until tableau.length()).map { i ->
            val objet = tableau.getJSONObject(i)
            MagasinDistant(
                codeMagasin = objet.getString("code_magasin"),
                // optString rendrait "null" (la chaîne) sur un null JSON.
                nomMagasin = if (objet.isNull("nom_magasin")) null
                else objet.getString("nom_magasin")
            )
        }
    } catch (e: org.json.JSONException) {
        null
    }

    /**
     * `POST /documents` — envoi d'une session clôturée, session et lignes en un seul appel.
     *
     * `hash_ligne` n'est **pas** envoyé : l'API le calcule et fait foi (SPEC §4.2). L'envoyer
     * n'apporterait rien et ferait diverger les deux implémentations au premier changement.
     */
    suspend fun envoyerDocument(corps: JSONObject): ResultatEnvoi = withContext(Dispatchers.IO) {
        val url = try {
            URL(ReponsesNirgescom.url(parametres.urlApi, CHEMIN_DOCUMENTS))
        } catch (e: MalformedURLException) {
            return@withContext ResultatEnvoi.UrlInvalide(
                "Adresse du serveur illisible. Vérifiez les Paramètres."
            )
        }

        var connexion: HttpURLConnection? = null
        try {
            connexion = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = DELAI_MS
                // Un inventaire peut porter des milliers de lignes : le serveur a besoin de plus
                // de temps pour répondre que pour un simple GET.
                readTimeout = DELAI_ENVOI_MS
                doOutput = true
                setRequestProperty(EN_TETE_CLE, parametres.cleApi)
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            connexion.outputStream.bufferedWriter().use { it.write(corps.toString()) }

            val code = connexion.responseCode
            val texte = lireCorps(connexion, code)
            val recap = if (code == HttpURLConnection.HTTP_CREATED) lireRecapitulatif(texte) else null

            ReponsesNirgescom.envoi(code, lireDetail(texte), recap)
        } catch (e: SocketTimeoutException) {
            ResultatEnvoi.Injoignable("Pas de réponse après ${DELAI_ENVOI_MS / 1000} s.")
        } catch (e: IOException) {
            ResultatEnvoi.Injoignable(e.message ?: "Serveur injoignable.")
        } finally {
            connexion?.disconnect()
        }
    }

    /**
     * `null` sur un corps illisible : [ReponsesNirgescom.envoi] en fait quand même un succès, la
     * session **est** arrivée. Réessayer réenverrait des lignes déjà en base.
     */
    private fun lireRecapitulatif(corps: String): RecapEnvoi? = runCatching {
        val objet = JSONObject(corps)
        RecapEnvoi(
            recues = objet.optInt("lignes_recues", -1),
            inserees = objet.optInt("lignes_inserees", -1),
            ignorees = objet.optInt("lignes_ignorees", -1)
        )
    }.getOrNull()

    /**
     * `GET /documents/{session_id}` — état de traitement côté Nirgescom (SPEC §4.3). Aucun
     * paramètre de requête : la route répond `422` à tout paramètre (SPEC §4.6).
     */
    suspend fun consulterDocument(uuidSession: String): ResultatEtat = withContext(Dispatchers.IO) {
        val url = try {
            URL(ReponsesNirgescom.url(parametres.urlApi, "$CHEMIN_DOCUMENTS/$uuidSession"))
        } catch (e: MalformedURLException) {
            return@withContext ResultatEtat.ReponseInattendue(0, "Adresse du serveur illisible.")
        }

        var connexion: HttpURLConnection? = null
        try {
            connexion = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = DELAI_MS
                readTimeout = DELAI_MS
                setRequestProperty(EN_TETE_CLE, parametres.cleApi)
            }
            val code = connexion.responseCode
            val texte = lireCorps(connexion, code)
            val etat = if (code == HttpURLConnection.HTTP_OK) lireEtat(texte) else null

            ReponsesNirgescom.etat(code, lireDetail(texte), etat)
        } catch (e: SocketTimeoutException) {
            ResultatEtat.Injoignable("Pas de réponse après ${DELAI_MS / 1000} s.")
        } catch (e: IOException) {
            ResultatEtat.Injoignable(e.message ?: "Serveur injoignable.")
        } finally {
            connexion?.disconnect()
        }
    }

    private fun lireEtat(corps: String): EtatDocument? = try {
        val objet = JSONObject(corps)
        val tableau = objet.optJSONArray("erreurs")
        val erreurs = (0 until (tableau?.length() ?: 0)).map { i ->
            val e = tableau!!.getJSONObject(i)
            "${e.optString("code_produit")} : ${e.optString("message_erreur")}"
        }
        EtatDocument(
            total = objet.optInt("total"),
            enAttente = objet.optInt("en_attente"),
            traite = objet.optInt("traite"),
            erreur = objet.optInt("erreur"),
            dateReception = objet.optString("date_reception").takeIf { it.isNotBlank() },
            erreurs = erreurs
        )
    } catch (e: org.json.JSONException) {
        null
    }

    /** Les erreurs (≥ 400) arrivent par `errorStream`, pas par `inputStream`. */
    private fun lireCorps(connexion: HttpURLConnection, code: Int): String =
        if (code < HttpURLConnection.HTTP_BAD_REQUEST) {
            connexion.inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } else {
            connexion.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }

    /**
     * Décode `{"detail": ...}` (SPEC §4) : chaîne, ou liste de `{champ, message}` en `422`.
     * `null` pour un corps vide, non JSON ou sans `detail` exploitable ; `message` et `erreur`
     * sont gardés en repli pour un intermédiaire (proxy) qui répondrait autrement.
     */
    private fun lireDetail(corps: String): DetailApi? = runCatching {
        val objet = JSONObject(corps)
        objet.optJSONArray("detail")?.let { tableau ->
            return@runCatching DetailApi.Champs(
                (0 until tableau.length()).map { i ->
                    val e = tableau.optJSONObject(i)
                    ErreurChamp(e?.optString("champ").orEmpty(), e?.optString("message").orEmpty())
                }
            )
        }
        listOf("detail", "message", "erreur")
            .firstNotNullOfOrNull { cle -> objet.optString(cle).takeIf { it.isNotBlank() } }
            ?.let { DetailApi.Texte(it) }
    }.getOrNull()

    private companion object {
        /** Pas de numéro de version dans l'URL, et c'est délibéré côté API (SPEC §4). */
        const val CHEMIN_SANTE = "/api/health"
        const val CHEMIN_MAGASINS = "/api/magasins"
        const val CHEMIN_DOCUMENTS = "/api/documents"
        const val EN_TETE_CLE = "X-Api-Key"

        /** Un inventaire complet fait des milliers de lignes ; l'insertion prend son temps. */
        const val DELAI_ENVOI_MS = 60_000

        /** Un LAN qui ne répond pas en 5 s ne répondra pas ; l'opérateur attend devant l'écran. */
        const val DELAI_MS = 5_000
    }
}
