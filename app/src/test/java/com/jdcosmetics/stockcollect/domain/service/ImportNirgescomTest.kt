package com.jdcosmetics.stockcollect.domain.service

import com.jdcosmetics.stockcollect.data.remote.ArticleDistant
import com.jdcosmetics.stockcollect.data.remote.CodeBarreDistant
import com.jdcosmetics.stockcollect.data.remote.ResultatReferentiel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Décisions d'import depuis `GET /catalog` et `GET /codes-barres` (TASK-16), sur des réponses
 * déjà décodées et classées : aucun réseau, aucun `org.json`.
 */
class ImportNirgescomTest {

    private val article = ArticleDistant("1001010033", "6291106811234", "CREME MAINS", 1500.0)

    // --- Correspondance des champs -------------------------------------------------------------

    @Test
    fun `article vers ligne catalogue, prix = prix_detail, pas de quantite`() {
        val ligne = ImportNirgescom.ligneCatalogue(article)
        assertEquals("1001010033", ligne.codeProduit)
        assertEquals("6291106811234", ligne.codeBarre)
        assertEquals("CREME MAINS", ligne.nomProduit)
        assertEquals(1500.0, ligne.prix!!.toDouble(), 0.0)
        // Absente de l'API : la quantité existante est conservée à l'écriture, pas mise à 0 ici.
        assertNull(ligne.quantite)
        assertFalse(ligne.recollee)
    }

    @Test
    fun `prix se relit sans perte, decimales et grands nombres compris`() {
        listOf(0.0, 1250.5, 12_500_000.0, 0.001).forEach { prix ->
            val ligne = ImportNirgescom.ligneCatalogue(article.copy(prixDetail = prix))
            assertEquals(prix, ligne.prix!!.toDouble(), 0.0)
        }
    }

    @Test
    fun `champs absents restent absents, les controles de l'import en decideront`() {
        val ligne = ImportNirgescom.ligneCatalogue(ArticleDistant(null, null, null, null))
        assertNull(ligne.codeProduit)
        assertNull(ligne.codeBarre)
        assertNull(ligne.nomProduit)
        assertNull(ligne.prix)
    }

    @Test
    fun `code-barres vers couple, un null devient vide pour etre refuse en code absent`() {
        assertEquals("SEC1" to "P01", ImportNirgescom.couple(CodeBarreDistant("SEC1", "P01")))
        assertEquals("" to "P01", ImportNirgescom.couple(CodeBarreDistant(null, "P01")))
        assertEquals("SEC1" to "", ImportNirgescom.couple(CodeBarreDistant("SEC1", null)))
    }

    // --- GET /catalog ---------------------------------------------------------------------------

    @Test
    fun `catalogue recu, a analyser avec son ETag`() {
        val suite = ImportNirgescom.suiteCatalogue(ResultatReferentiel.Ok(listOf(article), "\"v1\""))
        assertTrue(suite is SuiteCatalogue.Analyser)
        suite as SuiteCatalogue.Analyser
        assertEquals("\"v1\"", suite.etag)
        assertEquals(listOf(ImportNirgescom.ligneCatalogue(article)), suite.lignes)
    }

    @Test
    fun `catalogue 304 est deja a jour`() {
        assertEquals(
            SuiteCatalogue.DejaAJour,
            ImportNirgescom.suiteCatalogue(ResultatReferentiel.Inchange)
        )
    }

    @Test
    fun `catalogue vide n'est jamais ecrit, le message renvoie au depot`() {
        val suite = ImportNirgescom.suiteCatalogue(ResultatReferentiel.Ok(emptyList(), "\"v\""))
        assertEquals(SuiteCatalogue.Echec(ImportNirgescom.MESSAGE_CATALOGUE_VIDE), suite)
        assertTrue(ImportNirgescom.MESSAGE_CATALOGUE_VIDE.contains("dépôt"))
    }

    @Test
    fun `catalogue refuse par le serveur, message d'echec`() {
        val suite = ImportNirgescom.suiteCatalogue(ResultatReferentiel.CleRefusee("Cle inconnue"))
        assertTrue(suite is SuiteCatalogue.Echec)
        assertTrue((suite as SuiteCatalogue.Echec).message.contains("Clé d'API refusée"))
        assertTrue(suite.message.contains("Cle inconnue"))
    }

    // --- GET /codes-barres ----------------------------------------------------------------------

    @Test
    fun `codes-barres vides, la correspondance existante est conservee`() {
        assertEquals(
            SuiteCodesBarres.Conserver,
            ImportNirgescom.suiteCodesBarres(ResultatReferentiel.Ok(emptyList(), "\"vide\""))
        )
        assertTrue(ImportNirgescom.MESSAGE_CODES_BARRES_VIDE.contains("conservée"))
    }

    @Test
    fun `codes-barres recus, a remplacer avec leur ETag`() {
        val suite = ImportNirgescom.suiteCodesBarres(
            ResultatReferentiel.Ok(
                listOf(CodeBarreDistant("SEC1", "P01"), CodeBarreDistant("SEC2", "P02")), "\"v2\""
            )
        )
        assertEquals(
            SuiteCodesBarres.Remplacer(listOf("SEC1" to "P01", "SEC2" to "P02"), "\"v2\""),
            suite
        )
    }

    @Test
    fun `codes-barres 304 est deja a jour`() {
        assertEquals(
            SuiteCodesBarres.DejaAJour,
            ImportNirgescom.suiteCodesBarres(ResultatReferentiel.Inchange)
        )
    }

    @Test
    fun `codes-barres 503 est un echec`() {
        val suite = ImportNirgescom.suiteCodesBarres(ResultatReferentiel.Indisponible("Base injoignable"))
        assertTrue(suite is SuiteCodesBarres.Echec)
    }

    // --- Messages d'erreur ----------------------------------------------------------------------

    @Test
    fun `500 renvoie vers l'informatique, pas vers le WiFi, et n'invite pas a reessayer`() {
        val m = ImportNirgescom.messageEchec(
            ResultatReferentiel.ConfigurationServeur("Configuration de la cle incorrecte")
        )
        assertTrue(m.contains("mal configuré"))
        assertTrue(m.contains("service informatique"))
        assertTrue(m.contains("réessayer ne servira à rien"))
        assertTrue(m.contains("Configuration de la cle incorrecte"))
    }

    @Test
    fun `503 se reessaie, injoignable renvoie au WiFi, les deux rappellent le CSV`() {
        val m503 = ImportNirgescom.messageEchec(ResultatReferentiel.Indisponible("Base injoignable"))
        assertTrue(m503.contains("Réessayez"))
        assertTrue(m503.contains("CSV"))

        val reseau = ImportNirgescom.messageEchec(ResultatReferentiel.Injoignable("timeout"))
        assertTrue(reseau.contains("WiFi"))
        assertTrue(reseau.contains("CSV"))
        assertTrue(reseau.contains("timeout"))
    }

    @Test
    fun `401 403 422 ont chacun leur consigne`() {
        assertTrue(
            ImportNirgescom.messageEchec(ResultatReferentiel.CleRefusee("x")).contains("Paramètres")
        )
        assertTrue(
            ImportNirgescom.messageEchec(ResultatReferentiel.NonAutorise("x")).contains("dépôt")
        )
        assertTrue(
            ImportNirgescom.messageEchec(ResultatReferentiel.ParametreRefuse("x")).contains("version")
        )
    }

    @Test
    fun `detail blanc n'ajoute pas de paragraphe vide`() {
        val m = ImportNirgescom.messageEchec(ResultatReferentiel.CleRefusee(""))
        assertFalse(m.endsWith("\n\n"))
        val inattendu = ImportNirgescom.messageEchec(ResultatReferentiel.ReponseInattendue(418, ""))
        assertTrue(inattendu.endsWith("Code 418."))
    }

    @Test
    fun `url invalide garde son propre message`() {
        assertEquals(
            "Adresse illisible",
            ImportNirgescom.messageEchec(ResultatReferentiel.UrlInvalide("Adresse illisible"))
        )
    }
}
