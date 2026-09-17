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

    private companion object {
        /** Pas de numéro de version dans l'URL, et c'est délibéré côté API (SPEC §4). */
        const val CHEMIN_SANTE = "/api/health"

        /** Un LAN qui ne répond pas en 5 s ne répondra pas ; l'opérateur attend devant l'écran. */
        const val DELAI_MS = 5_000
    }
}
