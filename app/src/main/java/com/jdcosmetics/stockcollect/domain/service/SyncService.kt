package com.jdcosmetics.stockcollect.domain.service

import com.jdcosmetics.stockcollect.data.db.dao.LigneCollecteDao
import com.jdcosmetics.stockcollect.data.db.dao.SessionDao
import com.jdcosmetics.stockcollect.data.db.entity.SessionEntity
import com.jdcosmetics.stockcollect.data.db.entity.StatutSession
import com.jdcosmetics.stockcollect.data.prefs.ParametresSync
import com.jdcosmetics.stockcollect.data.remote.NirgescomClient
import com.jdcosmetics.stockcollect.data.remote.ResultatEnvoi
import com.jdcosmetics.stockcollect.util.DateUtils
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ce que la synchronisation d'une session a donné, du point de vue de l'écran.
 *
 * [Refuse] et [Reessayable] sont distingués parce qu'ils appellent des gestes opposés : un refus
 * (mauvais magasin, clé invalide, données non conformes) ne changera pas en réessayant, il faut
 * corriger les Paramètres ou remonter le problème ; une indisponibilité, elle, se réessaie.
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

        val lignes = ligneDao.getLignesBySessionSync(idSession)
        if (lignes.isEmpty()) {
            return ResultatSync.Impossible(
                "Cette session ne contient aucun article : il n'y a rien à envoyer."
            )
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
