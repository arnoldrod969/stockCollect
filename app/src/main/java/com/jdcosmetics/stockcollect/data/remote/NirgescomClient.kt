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

/** Un dépôt tel que `GET /magasins` le renvoie. `nomMagasin` est nullable côté API. */
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

    /** `503` : l'API répond mais ne peut pas lire la base (vue absente, droits manquants). */
    data class ApiSansBase(val detail: String) : ResultatMagasins()

    data class Injoignable(val detail: String) : ResultatMagasins()
    data class UrlInvalide(val detail: String) : ResultatMagasins()
    data class ReponseInattendue(val code: Int, val detail: String) : ResultatMagasins()
}

/**
 * Client HTTP de l'API Nirgescom.
 *
 * `HttpURLConnection` plutôt qu'OkHttp ou Retrofit : l'app n'a pour l'instant qu'un appel à faire,
 * en clair, sur un LAN. Une pile HTTP complète grossirait l'APK et demanderait des keep rules R8
 * supplémentaires pour un bénéfice nul à cette échelle. À réexaminer quand `POST /documents` et la
 * consultation des dépôts arriveront.
 */
@Singleton
class NirgescomClient @Inject constructor(private val parametres: ParametresSync) {

    /**
     * `GET /health` — sans authentification (SPEC §4.1), ce qui en fait justement un bon test :
     * il isole un problème de réseau d'un problème de clé d'API.
     */
    suspend fun tester(urlRacine: String): ResultatSante = withContext(Dispatchers.IO) {
        val url = try {
            URL("${urlRacine.trim().trimEnd('/')}$CHEMIN_SANTE")
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
            // 503 a un corps utile, mais il arrive par errorStream et non par inputStream.
            val corps = if (code < HttpURLConnection.HTTP_BAD_REQUEST) {
                connexion.inputStream.bufferedReader().use { it.readText() }
            } else {
                connexion.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

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
            URL("${urlRacine.trim().trimEnd('/')}$CHEMIN_MAGASINS")
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
            val corps = if (code < HttpURLConnection.HTTP_BAD_REQUEST) {
                connexion.inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            } else {
                connexion.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            when (code) {
                HttpURLConnection.HTTP_OK -> lireMagasins(corps, connexion.getHeaderField("ETag"))
                HttpURLConnection.HTTP_NOT_MODIFIED -> ResultatMagasins.Inchangee
                HttpURLConnection.HTTP_UNAUTHORIZED ->
                    ResultatMagasins.CleRefusee(messageApi(corps) ?: "Clé d'API refusée.")
                HttpURLConnection.HTTP_UNAVAILABLE ->
                    ResultatMagasins.ApiSansBase(
                        messageApi(corps) ?: "L'API ne peut pas lire la base des dépôts."
                    )
                else -> ResultatMagasins.ReponseInattendue(code, messageApi(corps).orEmpty())
            }
        } catch (e: SocketTimeoutException) {
            ResultatMagasins.Injoignable("Pas de réponse après ${DELAI_MS / 1000} s.")
        } catch (e: IOException) {
            ResultatMagasins.Injoignable(e.message ?: "Serveur injoignable.")
        } finally {
            connexion?.disconnect()
        }
    }

    /**
     * Un corps illisible est traité comme une réponse inattendue plutôt que comme une liste vide :
     * une liste vide bloquerait l'écran Paramètres en prétendant que le magasin n'existe pas.
     */
    private fun lireMagasins(corps: String, etag: String?): ResultatMagasins = try {
        val tableau = JSONObject(corps).getJSONArray("magasins")
        val magasins = (0 until tableau.length()).map { i ->
            val objet = tableau.getJSONObject(i)
            MagasinDistant(
                codeMagasin = objet.getString("code_magasin"),
                // optString rendrait "null" (la chaîne) sur un null JSON.
                nomMagasin = if (objet.isNull("nom_magasin")) null
                else objet.getString("nom_magasin")
            )
        }
        ResultatMagasins.Ok(magasins, etag)
    } catch (e: org.json.JSONException) {
        ResultatMagasins.ReponseInattendue(HttpURLConnection.HTTP_OK, "Réponse illisible.")
    }

    /** L'API renvoie ses erreurs sous `{"erreur": "...", "detail": "..."}` (SPEC §4). */
    private fun messageApi(corps: String): String? = runCatching {
        val objet = JSONObject(corps)
        listOf("detail", "message", "erreur")
            .firstNotNullOfOrNull { cle -> objet.optString(cle).takeIf { it.isNotBlank() } }
    }.getOrNull()

    private companion object {
        /** Pas de numéro de version dans l'URL, et c'est délibéré côté API (SPEC §4). */
        const val CHEMIN_SANTE = "/api/health"
        const val CHEMIN_MAGASINS = "/api/magasins"
        const val EN_TETE_CLE = "X-Api-Key"

        /** Un LAN qui ne répond pas en 5 s ne répondra pas ; l'opérateur attend devant l'écran. */
        const val DELAI_MS = 5_000
    }
}
