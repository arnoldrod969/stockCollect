package com.jdcosmetics.stockcollect.domain.service

import com.jdcosmetics.stockcollect.data.db.dao.LigneCollecteDao
import com.jdcosmetics.stockcollect.data.db.dao.SessionDao
import com.jdcosmetics.stockcollect.data.db.entity.LigneCollecteEntity
import com.jdcosmetics.stockcollect.data.db.entity.SessionEntity
import com.jdcosmetics.stockcollect.data.db.entity.StatutSession
import com.jdcosmetics.stockcollect.data.db.entity.TypeOperation
import com.jdcosmetics.stockcollect.data.prefs.ParametresSync
import com.jdcosmetics.stockcollect.data.remote.NirgescomClient
import com.jdcosmetics.stockcollect.data.remote.ResultatEnvoi
import com.jdcosmetics.stockcollect.util.DateUtils
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ce que la synchronisation d'une session a donné, du point de vue de l'écran.
 *
 * [Refuse] et [Reessayable] sont distingués parce qu'ils appellent des gestes opposés : un refus
 * (mauvais magasin, clé invalide, données non conformes, serveur mal configuré — `500`) ne
 * changera pas en réessayant, il faut corriger les Paramètres ou remonter le problème ; une
 * indisponibilité (`503`, réseau), elle, se réessaie. Les deux passent la session en échec de
 * synchro, et l'envoi reste proposé : c'est le message qui dit s'il a une chance d'aboutir.
 */
sealed class ResultatSync {
    data class Ok(val inserees: Int, val ignorees: Int) : ResultatSync()
    data class Refuse(val message: String) : ResultatSync()
    data class Reessayable(val message: String) : ResultatSync()

    /** Rien n'a été tenté : la session ou les réglages ne s'y prêtent pas. */
    data class Impossible(val message: String) : ResultatSync()
}

/**
 * Envoi d'une session clôturée vers Nirgescom (contrat §4.2 et §5).
 *
 * Service et non méthode de repository : il compose deux DAO, les préférences et le client HTTP,
 * comme [CsvExportService] compose DAO et SAF. `SessionRepository` ne connaît pas le réseau et il
 * n'y a pas de raison de l'y faire entrer.
 */
@Singleton
class SyncService @Inject constructor(
    private val sessionDao: SessionDao,
    private val ligneDao: LigneCollecteDao,
    private val client: NirgescomClient,
    private val parametres: ParametresSync
) {

    suspend fun synchroniser(idSession: Long): ResultatSync {
        val session = sessionDao.getById(idSession)
            ?: return ResultatSync.Impossible("Cette session n'existe plus sur la tablette.")

        // L'export CSV reste possible sur une session clôturée quoi qu'il arrive (contrat §5) ;
        // c'est l'envoi qui exige une clôture, parce que le contenu ne doit plus bouger après coup.
        if (session.statut == StatutSession.BROUILLON) {
            return ResultatSync.Impossible("Clôturez la session avant de la synchroniser.")
        }
        if (!parametres.estConfigure) {
            return ResultatSync.Impossible(
                "La tablette n'est pas encore reliée à Nirgescom. Réglez l'adresse du serveur, " +
                    "la clé d'API et le dépôt dans Paramètres."
            )
        }

        // Une session d'avant le réglage du dépôt n'a rien à envoyer sous quel nom que ce soit :
        // inventer le dépôt courant rangerait la collecte là où elle n'a pas eu lieu.
        if (session.lieu.isNullOrBlank()) {
            return ResultatSync.Impossible(
                "Cette session a été collectée avant qu'un dépôt soit réglé : Nirgescom ne " +
                    "saurait pas où la ranger. Exportez-la en CSV."
            )
        }

        // Les sessions ENTREE / SORTIE d'avant la V2 restent lisibles et exportables, mais le
        // contrat n'accepte que INVENTAIRE (et COMMANDE, étape 2) : l'envoi finirait en 422.
        if (session.typeOperation != TypeOperation.INVENTAIRE) {
            return ResultatSync.Impossible(
                "Nirgescom n'accepte que les inventaires : une session " +
                    "« ${TypeOperation.label(session.typeOperation)} » ne peut pas lui être " +
                    "envoyée. Exportez-la en CSV."
            )
        }

        val lignes = ligneDao.getLignesBySessionSync(idSession)
        if (lignes.isEmpty()) {
            return ResultatSync.Impossible(
                "Cette session ne contient aucun article : il n'y a rien à envoyer."
            )
        }
        if (lignes.size > ValidationEnvoi.LIGNES_MAX) {
            return ResultatSync.Impossible(
                "Cette session compte ${lignes.size} articles, et Nirgescom en accepte " +
                    "${ValidationEnvoi.LIGNES_MAX} au plus par envoi. Exportez-la en CSV."
            )
        }

        // Mieux vaut refuser ici, en nommant l'article, qu'envoyer pour recevoir un 422 formulé
        // en « lignes[37].code_barre ». Rien n'est tenté : pas d'échec de synchro à consigner.
        val problemes = ValidationEnvoi.verifier(
            magasin = session.lieu.orEmpty(),
            utilisateur = parametres.identifiantTablette,
            lignes = lignes
        )
        if (problemes.isNotEmpty()) {
            return ResultatSync.Impossible(ValidationEnvoi.message(problemes))
        }

        // Les sessions créées avant la migration 1→2 n'ont pas d'UUID : SQLite ne savait pas en
        // générer. On leur en pose un ici, une seule fois — la garde SQL empêche qu'un second
        // envoi n'en change un déjà écrit, ce qui recalculerait tous les hash côté API.
        val uuid = session.uuidSession ?: UUID.randomUUID().toString().also {
            sessionDao.poserUuidSiAbsent(idSession, it)
        }

        val resultat = client.envoyerDocument(construireCorps(session, uuid, lignes))
        val maintenant = DateUtils.nowIso()

        return when (resultat) {
            is ResultatEnvoi.Ok -> {
                sessionDao.marquerSynchronisee(idSession, maintenant)
                ResultatSync.Ok(resultat.inserees, resultat.ignorees)
            }
            is ResultatEnvoi.MagasinRefuse -> echec(
                idSession, maintenant,
                "Cette session a été collectée pour le dépôt « ${session.lieu.orEmpty()} », qui " +
                    "ne correspond pas à la clé d'API réglée sur la tablette. Corrigez le dépôt " +
                    "ou la clé dans Paramètres, ou appelez le service informatique. L'export CSV " +
                    "reste possible.\n\n${resultat.detail}"
            ) { ResultatSync.Refuse(it) }
            is ResultatEnvoi.SessionAutreMagasin -> echec(
                idSession, maintenant,
                "Nirgescom a déjà reçu cette session sous un autre dépôt : elle a été envoyée " +
                    "une première fois avec la clé d'API d'un autre dépôt. Elle ne peut pas être " +
                    "rangée sous « ${session.lieu.orEmpty()} », et réessayer n'y changera rien. " +
                    "Appelez le service informatique ; l'export CSV reste possible." +
                    "\n\n${resultat.detail}"
            ) { ResultatSync.Refuse(it) }
            is ResultatEnvoi.ConfigurationServeur -> echec(
                idSession, maintenant,
                "Le serveur Nirgescom est mal configuré (clé d'API ou base de données côté " +
                    "serveur). Ni le WiFi ni la tablette ne sont en cause, et réessayer ne " +
                    "servira à rien tant que le serveur n'est pas corrigé : prévenez le service " +
                    "informatique. L'export CSV reste possible.\n\n${resultat.detail}"
            ) { ResultatSync.Refuse(it) }
            is ResultatEnvoi.CleRefusee -> echec(
                idSession, maintenant,
                "Clé d'API refusée par Nirgescom. Vérifiez la clé dans Paramètres ; si elle est " +
                    "correcte, appelez le service informatique.\n\n${resultat.detail}"
            ) { ResultatSync.Refuse(it) }
            is ResultatEnvoi.Invalide -> echec(
                idSession, maintenant,
                "Nirgescom a refusé le contenu de cette session. Réessayer n'y changera rien : " +
                    "signalez-le au service informatique. L'export CSV reste possible." +
                    "\n\n${resultat.detail}"
            ) { ResultatSync.Refuse(it) }
            is ResultatEnvoi.UrlInvalide -> echec(
                idSession, maintenant, resultat.detail
            ) { ResultatSync.Refuse(it) }
            is ResultatEnvoi.Indisponible -> echec(
                idSession, maintenant,
                "Nirgescom ne peut pas enregistrer pour le moment. Réessayez dans quelques " +
                    "minutes ; l'export CSV reste possible.\n\n${resultat.detail}"
            ) { ResultatSync.Reessayable(it) }
            is ResultatEnvoi.Injoignable -> echec(
                idSession, maintenant,
                "Serveur injoignable. Vérifiez que la tablette est sur le WiFi de l'entrepôt, " +
                    "puis réessayez.\n\n${resultat.detail}"
            ) { ResultatSync.Reessayable(it) }
            is ResultatEnvoi.ReponseInattendue -> echec(
                idSession, maintenant,
                "Réponse inattendue du serveur. Réessayez ; si cela se reproduit, prévenez le " +
                    "service informatique.\n\nCode ${resultat.code}. ${resultat.detail}"
            ) { ResultatSync.Reessayable(it) }
        }
    }

    private suspend fun echec(
        idSession: Long,
        date: String,
        message: String,
        emballer: (String) -> ResultatSync
    ): ResultatSync {
        sessionDao.marquerEchecSync(idSession, date, message)
        return emballer(message)
    }

    /**
     * `hash_ligne` est volontairement absent : l'API le calcule et fait foi (contrat §4.2).
     *
     * `num_document`, `nom_client` et `prix_vente` sont `null` et doivent l'être en INVENTAIRE,
     * sinon la validation renvoie `422`. Ils s'ouvriront avec le mode COMMANDE.
     */
    private fun construireCorps(
        session: SessionEntity,
        uuid: String,
        lignes: List<com.jdcosmetics.stockcollect.data.db.entity.LigneCollecteEntity>
    ): JSONObject {
        val tableau = JSONArray()
        lignes.forEach { ligne ->
            tableau.put(
                JSONObject().apply {
                    // put(String, null) retire la clé ; JSONObject.NULL écrit bien `null`, ce que
                    // la validation de l'API attend explicitement pour un inventaire.
                    put("code_barre", ligne.codeBarreScanne ?: JSONObject.NULL)
                    put("code_produit", ligne.codeProduit)
                    put("nom_produit", ligne.nomProduitSnap)
                    put("quantite", ligne.quantite)
                    put("prix_vente", JSONObject.NULL)
                }
            )
        }

        return JSONObject().apply {
            put("session_id", uuid)
            // L'instantané pris à la création, et non le réglage courant. Si la tablette a été
            // reconfigurée entre la collecte et l'envoi, envoyer le réglage courant rangerait la
            // collecte sous un dépôt où elle n'a pas été faite, sans que rien ne le signale ;
            // l'instantané déclenche au contraire un 403, bruyant et corrigeable.
            put("magasin", session.lieu.orEmpty())
            put("type_operation", session.typeOperation)
            put("date_heure_cloture", session.dateHeureCloture ?: DateUtils.nowIso())
            put(
                "utilisateur",
                parametres.identifiantTablette.takeIf { it.isNotBlank() } ?: JSONObject.NULL
            )
            put("num_document", JSONObject.NULL)
            put("nom_client", JSONObject.NULL)
            put("lignes", tableau)
        }
    }
}

/**
 * Contrôles que `POST /documents` ferait en `422` (SPEC §4.2, `validation/champs.py` et
 * `validation/document.py` de nirgescom-api), rejoués avant l'envoi pour nommer l'article fautif.
 *
 * **Rien n'est corrigé**, seulement signalé : l'API refuse plutôt que nettoyer parce qu'une valeur
 * retouchée changerait `hash_ligne`, et un renvoi ultérieur de la même session ne retrouverait
 * plus ses lignes. Retirer un espace ou arrondir une quantité ici aurait le même effet.
 *
 * Les contrôles déjà garantis par construction (UUID, date, `null` en inventaire) ne sont pas
 * rejoués. Le type d'opération et le nombre de lignes sont vérifiés dans `synchroniser`.
 */
object ValidationEnvoi {

    /** Plafond de `quantite` : colonne `decimal(10,4)`, et 3 décimales au plus. */
    val QUANTITE_MAX = BigDecimal("999999.999")
    const val DECIMALES_MAX = 3

    /** `MAX_LIGNES` de validation/document.py. */
    const val LIGNES_MAX = 5000

    // Longueurs maximales de validation/document.py.
    const val LONGUEUR_CODE = 50
    const val LONGUEUR_NOM_PRODUIT = 255
    const val LONGUEUR_MAGASIN = 100
    const val LONGUEUR_UTILISATEUR = 100

    /** Au-delà, le message devient illisible : on en montre quelques-uns et on compte le reste. */
    private const val PROBLEMES_AFFICHES = 8

    /** La liste des problèmes, vide si l'envoi peut partir. */
    fun verifier(
        magasin: String,
        utilisateur: String?,
        lignes: List<LigneCollecteEntity>
    ): List<String> {
        val problemes = mutableListOf<String>()

        problemeTexte(magasin, LONGUEUR_MAGASIN)?.let {
            problemes += "Le dépôt de la session « $magasin » $it."
        }
        if (utilisateur != null) {
            problemeTexte(utilisateur, LONGUEUR_UTILISATEUR)?.let {
                problemes += "L'identifiant de la tablette « $utilisateur » $it : corrigez-le " +
                    "dans Paramètres."
            }
        }

        lignes.forEach { ligne ->
            val article = "Article « ${ligne.codeProduit} » (${ligne.nomProduitSnap})"
            if (ligne.codeProduit.isBlank()) {
                problemes += "Un article sans code produit (${ligne.nomProduitSnap})."
            } else {
                problemeTexte(ligne.codeProduit, LONGUEUR_CODE, code = true)?.let {
                    problemes += "$article : le code produit $it."
                }
            }
            ligne.codeBarreScanne?.let { cb ->
                problemeTexte(cb, LONGUEUR_CODE, code = true)?.let {
                    problemes += "$article : le code-barres « $cb » $it."
                }
            }
            problemeTexte(ligne.nomProduitSnap, LONGUEUR_NOM_PRODUIT)?.let {
                problemes += "$article : le nom du produit $it."
            }
            problemeQuantite(ligne.quantite)?.let {
                problemes += "$article : la quantité $it."
            }
        }
        return problemes
    }

    /** Le message à montrer au magasinier quand [verifier] a trouvé quelque chose. */
    fun message(problemes: List<String>): String {
        val affiches = problemes.take(PROBLEMES_AFFICHES).joinToString("\n") { "• $it" }
        val reste = problemes.size - PROBLEMES_AFFICHES
        val suite = if (reste > 0) "\n… et $reste autre${if (reste > 1) "s" else ""}." else ""
        return "Envoi bloqué : Nirgescom refuserait cette session, et la tablette ne modifie pas " +
            "ce qui a été compté. Rien n'a été envoyé. Signalez-le au service informatique ; " +
            "l'export CSV reste possible.\n\n$affiches$suite"
    }

    /**
     * Même ordre et mêmes règles que `lire_texte` côté API. Un texte vide ou blanc vaut `null`
     * pour l'API et n'est donc pas une erreur.
     *
     * Un caractère hors du plan multilingue de base (emoji…) s'écrit en Kotlin avec deux
     * « surrogates » : c'est ce qu'on cherche, la table Nirgescom en `utf8` sur 3 octets ne
     * saurait pas le stocker.
     */
    fun problemeTexte(valeur: String, longueurMax: Int, code: Boolean = false): String? {
        if (valeur.isBlank()) return null
        val longueur = valeur.codePointCount(0, valeur.length)
        return when {
            longueur > longueurMax -> "dépasse $longueurMax caractères"
            valeur.any { it.isSurrogate() } ->
                "contient un emoji ou un caractère que la base Nirgescom ne sait pas stocker"
            valeur.any { it.code < 0x20 || it.code == 0x7F } ->
                "contient un caractère de contrôle invisible (tabulation, retour à la ligne…)"
            code && valeur != valeur.trim(::estEspacePython) -> "commence ou finit par un espace"
            else -> null
        }
    }

    /**
     * Ce que `str.strip()` retire côté API. `Char.isWhitespace()` en couvre tout sauf U+0085
     * (NEL), que Python compte comme espace — et que le repli ISO-8859-1 du CsvParser produit à
     * partir de l'octet cp1252 0x85 (« … »).
     */
    private fun estEspacePython(c: Char) = c.isWhitespace() || c == '\u0085'

    /**
     * La quantité est jugée **telle qu'elle partira** dans le JSON : `org.json` écrit un nombre
     * entier sans décimale, sinon `Double.toString`. Une somme de rescans en virgule flottante
     * (`0.1 + 0.2` = `0.30000000000000004`) y apparaît donc avec 17 décimales, et l'API la
     * refuserait ; la signaler vaut mieux que de l'arrondir en douce.
     */
    fun problemeQuantite(quantite: Double): String? {
        if (quantite.isNaN() || quantite.isInfinite()) return "est illisible"
        if (quantite < 0) return "est négative (${quantite})"
        // Au-delà de 1e15 le cas est de toute façon hors plafond ; la borne évite l'écrêtage de
        // toLong() sans changer le verdict.
        val envoyee = if (quantite == Math.floor(quantite) && quantite <= 1e15) {
            BigDecimal(quantite.toLong())
        } else {
            BigDecimal(quantite.toString())
        }
        return when {
            envoyee > QUANTITE_MAX -> "dépasse 999999.999 (${envoyee.toPlainString()})"
            envoyee.scale() > DECIMALES_MAX ->
                "a plus de $DECIMALES_MAX décimales (${envoyee.toPlainString()})"
            else -> null
        }
    }
}
