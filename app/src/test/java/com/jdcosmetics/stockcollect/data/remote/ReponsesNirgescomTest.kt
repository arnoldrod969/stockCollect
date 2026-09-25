package com.jdcosmetics.stockcollect.data.remote

import org.junit.Assert.*
import org.junit.Test

/**
 * Correspondance « code HTTP + corps décodé → résultat » (SPEC nirgescom-api §4, 23/09).
 *
 * Les corps sont donnés déjà décodés : `org.json` n'est qu'un bouchon dans les tests JVM. Un
 * corps vide ou non JSON se décode en `detail = null`, c'est ce cas qui est rejoué ici.
 */
class ReponsesNirgescomTest {

    private fun texte(t: String) = DetailApi.Texte(t)

    // --- POST /documents ---------------------------------------------------------------------

    @Test
    fun `envoi 201 est un succes avec ses compteurs`() {
        val r = ReponsesNirgescom.envoi(201, null, RecapEnvoi(12, 12, 0))
        assertEquals(ResultatEnvoi.Ok(12, 12, 0), r)
    }

    @Test
    fun `envoi 201 avec toutes les lignes ignorees reste un succes`() {
        val r = ReponsesNirgescom.envoi(201, null, RecapEnvoi(40, 0, 40))
        assertEquals(ResultatEnvoi.Ok(40, 0, 40), r)
    }

    @Test
    fun `envoi 201 au corps illisible reste un succes`() {
        assertEquals(ResultatEnvoi.Ok(-1, -1, -1), ReponsesNirgescom.envoi(201, null, null))
    }

    @Test
    fun `envoi 401 est une cle refusee`() {
        val r = ReponsesNirgescom.envoi(401, texte("Cle d'API absente ou inconnue"), null)
        assertEquals(ResultatEnvoi.CleRefusee("Cle d'API absente ou inconnue"), r)
    }

    @Test
    fun `envoi 403 magasin est un magasin refuse`() {
        val d = texte("Magasin 'DEPOT SUPER MARCHE PREVA' non autorise pour cette cle")
        val r = ReponsesNirgescom.envoi(403, d, null)
        assertTrue(r is ResultatEnvoi.MagasinRefuse)
        assertEquals(d.texte, (r as ResultatEnvoi.MagasinRefuse).detail)
    }

    @Test
    fun `envoi 403 session_id d'un autre magasin est distingue du 403 magasin`() {
        val d = texte(
            "session_id '8f3c2a1e-4b7d-4f6a-9c2e-1d5b7a9e3f01' appartient a un autre magasin"
        )
        val r = ReponsesNirgescom.envoi(403, d, null)
        assertTrue(r is ResultatEnvoi.SessionAutreMagasin)
        assertEquals(d.texte, (r as ResultatEnvoi.SessionAutreMagasin).detail)
    }

    @Test
    fun `envoi 403 num_document reste un refus lie a la cle, pas a la session`() {
        val d = texte(
            "num_document 'CMD-X-T2-20260908-0007' ne correspond pas a cette cle " +
                "(prefixe attendu : CMD-22020104-T2-)"
        )
        assertTrue(ReponsesNirgescom.envoi(403, d, null) is ResultatEnvoi.MagasinRefuse)
    }

    @Test
    fun `envoi 403 sans corps est un magasin refuse avec message par defaut`() {
        val r = ReponsesNirgescom.envoi(403, null, null)
        assertEquals(ResultatEnvoi.MagasinRefuse("Magasin non autorisé pour cette clé."), r)
    }

    @Test
    fun `envoi 422 detail liste met un champ par ligne`() {
        val d = DetailApi.Champs(
            listOf(
                ErreurChamp("lignes[0].code_produit", "ne doit ni commencer ni finir par un espace"),
                ErreurChamp("lignes[3].quantite", "accepte au maximum 3 decimales")
            )
        )
        val r = ReponsesNirgescom.envoi(422, d, null)
        assertEquals(
            ResultatEnvoi.Invalide(
                "lignes[0].code_produit : ne doit ni commencer ni finir par un espace\n" +
                    "lignes[3].quantite : accepte au maximum 3 decimales"
            ),
            r
        )
    }

    @Test
    fun `envoi 422 detail chaine est repris tel quel`() {
        val r = ReponsesNirgescom.envoi(422, texte("JSON invalide"), null)
        assertEquals(ResultatEnvoi.Invalide("JSON invalide"), r)
    }

    @Test
    fun `envoi 422 detail liste vide retombe sur le message par defaut`() {
        val r = ReponsesNirgescom.envoi(422, DetailApi.Champs(emptyList()), null)
        assertEquals(ResultatEnvoi.Invalide("Données refusées par le serveur."), r)
    }

    @Test
    fun `envoi 400 n'est pas reessayable`() {
        assertTrue(ReponsesNirgescom.envoi(400, texte("JSON mal forme"), null) is ResultatEnvoi.Invalide)
    }

    @Test
    fun `envoi 500 est une configuration serveur et non une indisponibilite`() {
        val r = ReponsesNirgescom.envoi(500, texte("Configuration de la cle incorrecte"), null)
        assertEquals(ResultatEnvoi.ConfigurationServeur("Configuration de la cle incorrecte"), r)
    }

    @Test
    fun `envoi 500 sans corps reste une configuration serveur`() {
        assertTrue(ReponsesNirgescom.envoi(500, null, null) is ResultatEnvoi.ConfigurationServeur)
    }

    @Test
    fun `envoi 503 est une indisponibilite`() {
        val r = ReponsesNirgescom.envoi(503, null, null)
        assertEquals(
            ResultatEnvoi.Indisponible("Le serveur ne peut pas enregistrer pour l'instant."), r
        )
    }

    @Test
    fun `envoi code inconnu sans corps est une reponse inattendue au detail vide`() {
        assertEquals(ResultatEnvoi.ReponseInattendue(502, ""), ReponsesNirgescom.envoi(502, null, null))
    }

    // --- GET /documents/{session_id} ---------------------------------------------------------

    private val etat = EtatDocument(2, 0, 1, 1, "2026-09-08T16:43:12", listOf("1001099999 : x"))

    @Test
    fun `etat 200 lisible`() {
        assertEquals(ResultatEtat.Ok(etat), ReponsesNirgescom.etat(200, null, etat))
    }

    @Test
    fun `etat 200 illisible est une reponse inattendue`() {
        assertEquals(
            ResultatEtat.ReponseInattendue(200, "Réponse illisible."),
            ReponsesNirgescom.etat(200, null, null)
        )
    }

    @Test
    fun `etat 404 est une session inconnue`() {
        val d = texte("Aucune ligne pour cette session")
        assertEquals(ResultatEtat.Inconnue, ReponsesNirgescom.etat(404, d, null))
    }

    @Test
    fun `etat 401 est une cle refusee`() {
        assertTrue(ReponsesNirgescom.etat(401, null, null) is ResultatEtat.CleRefusee)
    }

    @Test
    fun `etat 403 est une session non autorisee, pas une cle refusee`() {
        val r = ReponsesNirgescom.etat(403, texte("Session non autorisee pour cette cle"), null)
        assertEquals(ResultatEtat.NonAutorisee("Session non autorisee pour cette cle"), r)
    }

    @Test
    fun `etat 422 est un identifiant invalide`() {
        val d = DetailApi.Champs(listOf(ErreurChamp("session_id", "doit etre un UUID")))
        assertEquals(
            ResultatEtat.IdentifiantInvalide("session_id : doit etre un UUID"),
            ReponsesNirgescom.etat(422, d, null)
        )
    }

    @Test
    fun `etat 500 est une configuration serveur`() {
        assertTrue(ReponsesNirgescom.etat(500, null, null) is ResultatEtat.ConfigurationServeur)
    }

    @Test
    fun `etat 503 est une indisponibilite`() {
        assertTrue(ReponsesNirgescom.etat(503, null, null) is ResultatEtat.Indisponible)
    }

    // --- GET /magasins -----------------------------------------------------------------------

    @Test
    fun `magasins 200 garde les codes tels quels, numeriques ou non`() {
        val liste = listOf(
            MagasinDistant("22020104", "DEPOT SUPER MARCHE PREVA"),
            MagasinDistant("220301A1", null)
        )
        assertEquals(
            ResultatMagasins.Ok(liste, "\"abc\""),
            ReponsesNirgescom.magasins(200, null, liste, "\"abc\"")
        )
    }

    @Test
    fun `magasins 200 illisible n'est pas une liste vide`() {
        assertTrue(
            ReponsesNirgescom.magasins(200, null, null, null) is ResultatMagasins.ReponseInattendue
        )
    }

    @Test
    fun `magasins 304 est un succes`() {
        assertEquals(ResultatMagasins.Inchangee, ReponsesNirgescom.magasins(304, null, null, null))
    }

    @Test
    fun `magasins 500 est une configuration serveur, 503 une base injoignable`() {
        val d = texte("Configuration de la base incorrecte")
        assertEquals(
            ResultatMagasins.ConfigurationServeur("Configuration de la base incorrecte"),
            ReponsesNirgescom.magasins(500, d, null, null)
        )
        assertTrue(ReponsesNirgescom.magasins(503, null, null, null) is ResultatMagasins.ApiSansBase)
    }

    // --- URL ---------------------------------------------------------------------------------

    @Test
    fun `url sans parametre de requete ni double barre`() {
        val u = ReponsesNirgescom.url(
            " http://192.168.1.10:8000/ ", "/api/documents/8f3c2a1e-4b7d-4f6a-9c2e-1d5b7a9e3f01"
        )
        assertEquals("http://192.168.1.10:8000/api/documents/8f3c2a1e-4b7d-4f6a-9c2e-1d5b7a9e3f01", u)
        assertFalse(u.contains('?'))
    }

    @Test
    fun `message d'un detail absent ou blanc est null`() {
        assertNull(ReponsesNirgescom.message(null))
        assertNull(ReponsesNirgescom.message(texte("  ")))
    }
}
