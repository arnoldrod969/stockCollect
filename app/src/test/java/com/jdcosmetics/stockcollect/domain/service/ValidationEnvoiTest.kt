package com.jdcosmetics.stockcollect.domain.service

import com.jdcosmetics.stockcollect.data.db.entity.LigneCollecteEntity
import org.junit.Assert.*
import org.junit.Test

/** Contrôles avant envoi, calqués sur les 422 de `POST /documents` (SPEC §4.2, 23/09). */
class ValidationEnvoiTest {

    private val magasin = "DEPOT SUPER MARCHE PREVA"

    private fun ligne(
        codeProduit: String = "1001010033",
        codeBarre: String? = "9501101370104",
        nom: String = "CREME CAROLIGHT CONGO 300ML",
        quantite: Double = 12.0
    ) = LigneCollecteEntity(
        idSession = 1,
        codeProduit = codeProduit,
        codeBarreScanne = codeBarre,
        nomProduitSnap = nom,
        quantite = quantite,
        dateSaisie = "2026-09-08T16:42:00"
    )

    private fun verifier(vararg lignes: LigneCollecteEntity, utilisateur: String? = "support-it") =
        ValidationEnvoi.verifier(magasin, utilisateur, lignes.toList())

    @Test
    fun `une session conforme passe`() {
        val problemes = verifier(
            ligne(),
            ligne(codeProduit = "1001050003", codeBarre = null, nom = "SAVON 72H GOMMANT 125G",
                quantite = 5.0),
            ligne(codeProduit = "1001000203", nom = "AL-NUAIM WHITE ORCHID 9,9ML", quantite = 0.0),
            ligne(codeProduit = "1001000204", nom = "Crème éclaircissante", quantite = 2.125),
            ligne(codeProduit = "1001000205", quantite = 999999.999)
        )
        assertEquals(emptyList<String>(), problemes)
    }

    @Test
    fun `espace en tete ou fin de code_produit refuse, en nommant l'article`() {
        val p = verifier(ligne(codeProduit = " 1001010033"))
        assertEquals(1, p.size)
        assertTrue(p[0], p[0].contains("1001010033"))
        assertTrue(p[0], p[0].contains("CREME CAROLIGHT CONGO 300ML"))
        assertTrue(p[0], p[0].contains("espace"))
        assertEquals(1, verifier(ligne(codeProduit = "1001010033 ")).size)
    }

    @Test
    fun `espace en tete ou fin de code_barre refuse`() {
        val p = verifier(ligne(codeBarre = "9501101370104 "))
        assertEquals(1, p.size)
        assertTrue(p[0], p[0].contains("code-barres"))
        assertEquals(1, verifier(ligne(codeBarre = " 9501101370104")).size)
    }

    @Test
    fun `code_barre blanc vaut null pour l'API et passe`() {
        assertEquals(emptyList<String>(), verifier(ligne(codeBarre = "   ")))
    }

    @Test
    fun `emoji dans le nom du produit refuse`() {
        val p = verifier(ligne(nom = "CREME 😀 300ML"))
        assertEquals(1, p.size)
        assertTrue(p[0], p[0].contains("emoji"))
    }

    @Test
    fun `caractere de controle dans le nom du produit refuse`() {
        assertEquals(1, verifier(ligne(nom = "CREME\tCAROLIGHT")).size)
        assertEquals(1, verifier(ligne(nom = "CREME\u007FCAROLIGHT")).size)
    }

    @Test
    fun `emoji ou controle dans l'identifiant de tablette refuse`() {
        val p = verifier(ligne(), utilisateur = "rayon 🚀")
        assertEquals(1, p.size)
        assertTrue(p[0], p[0].contains("Paramètres"))
        assertEquals(1, verifier(ligne(), utilisateur = "rayon\n2").size)
    }

    @Test
    fun `emoji dans le magasin refuse`() {
        val p = ValidationEnvoi.verifier("DEPOT 🏪", null, listOf(ligne()))
        assertEquals(1, p.size)
        assertTrue(p[0], p[0].contains("dépôt"))
    }

    @Test
    fun `quantite au-dela de 999999,999 refusee`() {
        val p = verifier(ligne(quantite = 1_000_000.0))
        assertEquals(1, p.size)
        assertTrue(p[0], p[0].contains("quantité"))
    }

    @Test
    fun `quantite a plus de 3 decimales refusee`() {
        assertEquals(1, verifier(ligne(quantite = 1.2345)).size)
    }

    @Test
    fun `somme flottante de rescans signalee, pas arrondie`() {
        val p = verifier(ligne(quantite = 0.1 + 0.2))
        assertEquals(1, p.size)
        assertTrue(p[0], p[0].contains("0.30000000000000004"))
    }

    @Test
    fun `quantite negative refusee`() {
        assertEquals(1, verifier(ligne(quantite = -1.0)).size)
    }

    @Test
    fun `code_produit trop long refuse`() {
        assertEquals(1, verifier(ligne(codeProduit = "1".repeat(51))).size)
        assertEquals(0, verifier(ligne(codeProduit = "1".repeat(50))).size)
    }

    @Test
    fun `plusieurs problemes sont tous listes, le message en montre un nombre borne`() {
        val lignes = (1..12).map { ligne(codeProduit = "$it ") }
        val p = ValidationEnvoi.verifier(magasin, null, lignes)
        assertEquals(12, p.size)
        val message = ValidationEnvoi.message(p)
        assertTrue(message, message.contains("… et 4 autres."))
        assertTrue(message, message.contains("Rien n'a été envoyé"))
    }
}
