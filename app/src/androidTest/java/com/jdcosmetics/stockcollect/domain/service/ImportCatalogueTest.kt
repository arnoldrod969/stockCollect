package com.jdcosmetics.stockcollect.domain.service

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jdcosmetics.stockcollect.data.db.StockCollectDatabase
import com.jdcosmetics.stockcollect.data.db.entity.ArtCodebarreEntity
import com.jdcosmetics.stockcollect.data.db.entity.ArticleEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Import du catalogue ([CsvImportService.analyserCatalogue] puis
 * [CsvImportService.appliquerCatalogue]) sur une base Room en mémoire et de vrais fichiers CSV.
 *
 * Couvre : import simple et mise à jour, les trois issues de l'arbitrage des conflits de
 * codes-barres, la priorité du catalogue sur `art_codebarre` (TASK-11), le rejet des caractères
 * que Nirgescom refuse (TASK-15), le seuil des 10 %, et qu'un refus n'écrit rien.
 */
@RunWith(AndroidJUnit4::class)
class ImportCatalogueTest {

    private lateinit var context: Context
    private lateinit var db: StockCollectDatabase
    private lateinit var service: CsvImportService
    private val fichiers = mutableListOf<File>()

    @Before
    fun preparer() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, StockCollectDatabase::class.java).build()
        service = CsvImportService(context, db, db.articleDao(), db.artCodebarreDao())
    }

    @After
    fun nettoyer() {
        db.close()
        fichiers.forEach { it.delete() }
    }

    private fun fichierCsv(vararg lignes: String): Uri {
        val fichier = File.createTempFile("catalogue", ".csv", context.cacheDir)
        fichier.writeText(lignes.joinToString("\r\n"), Charsets.UTF_8)
        fichiers += fichier
        return Uri.fromFile(fichier)
    }

    /** Ligne catalogue à 6 colonnes : code, code-barre, (ignorée), nom, quantité, prix. */
    private fun ligne(code: String, codeBarre: String, nom: String, qte: String = "5", prix: String = "1000") =
        "$code,$codeBarre,X,$nom,$qte,$prix"

    /** Neuf lignes saines `P01`..`P09`, code-barre `CB01`..`CB09`. */
    private fun lignesSaines(): Array<String> =
        (1..9).map { i -> ligne("P%02d".format(i), "CB%02d".format(i), "ARTICLE $i") }.toTypedArray()

    private suspend fun analyserPret(uri: Uri): AnalyseCatalogue =
        when (val r = service.analyserCatalogue(uri)) {
            is AnalyseResult.Pret -> r.analyse
            is AnalyseResult.Echec -> { fail("Analyse refusée : ${r.message} ${r.erreurs}"); error("") }
        }

    private suspend fun catalogueExistant(vararg articles: Pair<String, String?>) {
        db.articleDao().insertOrReplace(
            articles.map { (code, cb) ->
                ArticleEntity(codeProduit = code, codeBarrePrincipal = cb, nomProduit = "ANCIEN $code",
                    dateImport = "2026-09-01T08:00:00")
            }
        )
    }

    // ------------------------------------------------------------------
    // Import simple
    // ------------------------------------------------------------------

    @Test
    fun importSimple_insereEtMetAJour() = runBlocking {
        catalogueExistant("P01" to "CB01")

        val analyse = analyserPret(fichierCsv(
            ligne("P01", "CB01", "CREME MAINS", "12", "2500"),
            ligne("P02", "", "SAVON", "", ""),
            "P03,CB03,X,AL-NUAIM WHITE ORCHID 9,9ML,4,7500"
        ))
        assertFalse(analyse.aDesConflits)
        assertTrue(analyse.correspondancesRetirees.isEmpty())
        assertEquals(1, analyse.lignesRecollees.size)

        val resultat = service.appliquerCatalogue(analyse, ResolutionConflit.IMPORTER_SANS_CODE_BARRE)

        assertTrue(resultat.success)
        assertEquals(2, resultat.nbImportes)
        assertEquals(1, resultat.nbMisAJour)
        assertEquals(0, resultat.nbErreurs)
        assertEquals(3, db.articleDao().count())
        db.articleDao().findByCodeProduit("P01")!!.let {
            assertEquals("CREME MAINS", it.nomProduit)
            assertEquals(12.0, it.quantiteRef, 0.0)
            assertEquals(2500.0, it.prix, 0.0)
        }
        db.articleDao().findByCodeProduit("P02")!!.let {
            assertNull("Code-barre vide → null", it.codeBarrePrincipal)
            assertEquals(0.0, it.quantiteRef, 0.0)
        }
        assertEquals("AL-NUAIM WHITE ORCHID 9,9ML", db.articleDao().findByCodeProduit("P03")!!.nomProduit)
    }

    // ------------------------------------------------------------------
    // Conflits de codes-barres dans le fichier : les trois issues
    // ------------------------------------------------------------------

    /** P10 revendique CB01, déjà pris par P01 plus haut dans le fichier. */
    private fun fichierAvecConflit() = fichierCsv(*lignesSaines(), ligne("P10", "CB01", "AUTRE PRODUIT"))

    @Test
    fun conflit_detecteSansRienEcrire_puisAnnuler() = runBlocking {
        catalogueExistant("P50" to "CB50")

        val analyse = analyserPret(fichierAvecConflit())

        assertTrue(analyse.aDesConflits)
        val conflit = analyse.conflits.single()
        assertEquals("CB01", conflit.codeBarre)
        assertEquals("P01", conflit.gagnant.codeProduit)
        assertEquals(listOf("P10"), conflit.perdants.map { it.codeProduit })
        assertFalse(conflit.memeProduit)

        // « Annuler l'import » = ne pas appeler appliquerCatalogue : l'analyse n'a rien écrit.
        assertEquals(1, db.articleDao().count())
        assertNull(db.articleDao().findByCodeProduit("P01"))
    }

    @Test
    fun conflit_importerSansCodeBarre() = runBlocking {
        val analyse = analyserPret(fichierAvecConflit())

        val resultat = service.appliquerCatalogue(analyse, ResolutionConflit.IMPORTER_SANS_CODE_BARRE)

        assertTrue(resultat.success)
        assertEquals(10, resultat.nbImportes)
        assertEquals(0, resultat.nbIgnores)
        assertEquals("CB01", db.articleDao().findByCodeProduit("P01")!!.codeBarrePrincipal)
        val perdant = db.articleDao().findByCodeProduit("P10")
        assertNotNull("Le perdant entre quand même", perdant)
        assertNull("… mais sans code-barre", perdant!!.codeBarrePrincipal)
    }

    @Test
    fun conflit_ignorerArticles() = runBlocking {
        val analyse = analyserPret(fichierAvecConflit())

        val resultat = service.appliquerCatalogue(analyse, ResolutionConflit.IGNORER_ARTICLES)

        assertTrue(resultat.success)
        assertEquals(9, resultat.nbImportes)
        assertEquals(1, resultat.nbIgnores)
        assertNull(db.articleDao().findByCodeProduit("P10"))
        assertEquals("CB01", db.articleDao().findByCodeProduit("P01")!!.codeBarrePrincipal)
    }

    @Test
    fun codeBarreReattribueDUnArticleAUnAutre_passeGraceALaLiberation() = runBlocking {
        // En base, CB01 est à P01 ; le nouveau catalogue le donne à P02 et CB02 à P01.
        catalogueExistant("P01" to "CB01", "P02" to "CB02")

        val analyse = analyserPret(fichierCsv(
            ligne("P01", "CB02", "ARTICLE 1"),
            ligne("P02", "CB01", "ARTICLE 2")
        ))
        val resultat = service.appliquerCatalogue(analyse, ResolutionConflit.IMPORTER_SANS_CODE_BARRE)

        assertTrue(resultat.messageErreur, resultat.success)
        assertEquals("CB02", db.articleDao().findByCodeProduit("P01")!!.codeBarrePrincipal)
        assertEquals("CB01", db.articleDao().findByCodeProduit("P02")!!.codeBarrePrincipal)
    }

    // ------------------------------------------------------------------
    // TASK-11 : le catalogue l'emporte sur art_codebarre
    // ------------------------------------------------------------------

    @Test
    fun codePrincipalDejaCorrespondanceDUnAutreArticle_detecteAvantEcriture_puisRetire() = runBlocking {
        catalogueExistant("P01" to "CB01", "P02" to "CB02", "P03" to "CB03")
        db.artCodebarreDao().insertOrReplace(listOf(
            ArtCodebarreEntity("SEC", "P02", "2026-09-01T08:00:00"),    // sera donné à P01
            ArtCodebarreEntity("SEC3", "P03", "2026-09-01T08:00:00"),   // redondant, même article
            ArtCodebarreEntity("AUTRE", "P02", "2026-09-01T08:00:00")   // hors sujet
        ))

        val analyse = analyserPret(fichierCsv(
            ligne("P01", "SEC", "ARTICLE 1"),
            ligne("P02", "CB02", "ARTICLE 2"),
            ligne("P03", "SEC3", "ARTICLE 3")
        ))

        // Détecté à l'analyse…
        assertEquals(
            listOf(CorrespondanceRetiree("SEC", codeProduitRetire = "P02", codeProduitCatalogue = "P01")),
            analyse.correspondancesRetirees
        )
        assertFalse("Pas d'arbitrage : la règle est fixée", analyse.aDesConflits)
        assertNotNull(analyse.resumeCorrespondancesRetirees())
        // … sans rien écrire.
        assertEquals("P02", db.artCodebarreDao().findByCodeBarre("SEC")?.codeProduit)
        assertEquals("CB01", db.articleDao().findByCodeProduit("P01")!!.codeBarrePrincipal)

        val resultat = service.appliquerCatalogue(analyse, ResolutionConflit.IMPORTER_SANS_CODE_BARRE)

        assertTrue(resultat.messageErreur, resultat.success)
        assertEquals(1, resultat.nbCorrespondancesRetirees)
        assertEquals(0, resultat.nbErreurs)
        val info = resultat.erreurs.first()
        assertTrue(info, "SEC" in info && "P02" in info && "P01" in info)
        assertTrue(resultat.toResume(), "1 codes-barres secondaires retirés" in resultat.toResume())

        assertNull("Le catalogue l'emporte", db.artCodebarreDao().findByCodeBarre("SEC"))
        assertEquals("SEC", db.articleDao().findByCodeProduit("P01")!!.codeBarrePrincipal)
        assertEquals("P03", db.artCodebarreDao().findByCodeBarre("SEC3")?.codeProduit)
        assertEquals("P02", db.artCodebarreDao().findByCodeBarre("AUTRE")?.codeProduit)
        assertEquals(2, db.artCodebarreDao().count())
    }

    @Test
    fun correspondanceRetiree_avecArbitrage_memeRetraitQuelleQueSoitLaResolution() = runBlocking {
        catalogueExistant("P01" to null, "P02" to null)
        db.artCodebarreDao().insertOrReplace(listOf(ArtCodebarreEntity("SEC", "P02", "2026-09-01T08:00:00")))

        // P01 et P10 revendiquent SEC dans le fichier (arbitrage) ; SEC est en plus rattaché à
        // P02 dans art_codebarre. Le porteur final est P01, gagnant du conflit.
        val analyse = analyserPret(fichierCsv(
            *lignesSaines().drop(1).toTypedArray(),
            ligne("P01", "SEC", "ARTICLE 1"),
            ligne("P10", "SEC", "AUTRE PRODUIT")
        ))
        assertTrue(analyse.aDesConflits)
        assertEquals(listOf(CorrespondanceRetiree("SEC", "P02", "P01")), analyse.correspondancesRetirees)

        val resultat = service.appliquerCatalogue(analyse, ResolutionConflit.IGNORER_ARTICLES)

        assertTrue(resultat.success)
        assertEquals(1, resultat.nbCorrespondancesRetirees)
        assertNull(db.artCodebarreDao().findByCodeBarre("SEC"))
        assertEquals("SEC", db.articleDao().findByCodeProduit("P01")!!.codeBarrePrincipal)
    }

    // ------------------------------------------------------------------
    // TASK-15 : caractères que Nirgescom refuse → erreur de ligne
    // ------------------------------------------------------------------

    @Test
    fun tabulationDansLeNom_ligneRejeteeEtNonEcrite() = runBlocking {
        // 1 erreur sur 10 = 10 %, pas au-delà du seuil : l'import passe sans elle.
        val analyse = analyserPret(fichierCsv(*lignesSaines(), ligne("P10", "CB10", "CREME\tMAINS")))

        assertEquals(1, analyse.erreurs.size)
        val erreur = analyse.erreurs.single()
        assertTrue(erreur, erreur.startsWith("Ligne 10 : le nom de l'article contient un caractère de contrôle"))

        val resultat = service.appliquerCatalogue(analyse, ResolutionConflit.IMPORTER_SANS_CODE_BARRE)
        assertTrue(resultat.success)
        assertEquals(9, resultat.nbImportes)
        assertEquals(1, resultat.nbErreurs)
        assertNull(db.articleDao().findByCodeProduit("P10"))
    }

    @Test
    fun emojiDansLeNom_ligneRejetee() = runBlocking {
        val analyse = analyserPret(fichierCsv(*lignesSaines(), ligne("P10", "CB10", "PARFUM 🌸")))

        val erreur = analyse.erreurs.single()
        assertTrue(erreur, erreur.startsWith("Ligne 10 : le nom de l'article contient un emoji"))
        assertTrue(analyse.articles.none { it.codeProduit == "P10" })
    }

    @Test
    fun caractereDeControleDansUnCode_ligneRejetee() = runBlocking {
        // 18 lignes saines + 2 fautives = 10 %, à la limite du seuil sans le dépasser.
        val saines = (21..38).map { i -> ligne("P$i", "CB$i", "ARTICLE $i") }
        val analyse = analyserPret(fichierCsv(
            *saines.toTypedArray(),
            ligne("P10", "CB\u000110", "ARTICLE 10"),
            ligne("P\u000711", "CB11", "ARTICLE 11")
        ))

        assertEquals(2, analyse.erreurs.size)
        assertTrue(analyse.erreurs[0], analyse.erreurs[0].startsWith("Ligne 19 : le code-barres contient un caractère de contrôle"))
        assertTrue(analyse.erreurs[1], analyse.erreurs[1].startsWith("Ligne 20 : le code produit contient un caractère de contrôle"))
        assertEquals(18, analyse.articles.size)
    }

    @Test
    fun espacesAutourDesChamps_toujoursRognesEtAcceptes() = runBlocking {
        // CsvParser rogne chaque champ : un espace en bordure n'est pas une erreur.
        val analyse = analyserPret(fichierCsv(" P01 , CB01 ,X, CREME MAINS ,5,100"))

        assertTrue(analyse.erreurs.isEmpty())
        assertEquals("P01", analyse.articles.single().codeProduit)
        assertEquals("CB01", analyse.articles.single().codeBarrePrincipal)
        assertEquals("CREME MAINS", analyse.articles.single().nomProduit)
    }

    // ------------------------------------------------------------------
    // Seuil des 10 % : un refus n'écrit rien
    // ------------------------------------------------------------------

    @Test
    fun auDelaDuSeuil_importRefuseEtRienNEstEcrit() = runBlocking {
        catalogueExistant("P01" to "CB01")
        db.artCodebarreDao().insertOrReplace(listOf(ArtCodebarreEntity("SEC", "P01", "2026-09-01T08:00:00")))

        // 2 erreurs sur 10 = 20 % : une tabulation (TASK-15) et un prix illisible.
        // La ligne 1 donnerait en plus SEC à P02 : rien ne doit être retiré pour autant.
        val resultat = service.analyserCatalogue(fichierCsv(
            ligne("P02", "SEC", "ARTICLE 2"),
            *lignesSaines().drop(2).toTypedArray(),
            ligne("P10", "CB10", "CREME\tMAINS"),
            ligne("P11", "CB11", "ARTICLE 11", prix = "12FCFA")
        ))

        assertTrue(resultat is AnalyseResult.Echec)
        resultat as AnalyseResult.Echec
        assertEquals(2, resultat.erreurs.size)
        assertTrue(resultat.message, resultat.message.startsWith("2 lignes illisibles sur 10"))

        assertEquals(1, db.articleDao().count())
        assertEquals("ANCIEN P01", db.articleDao().findByCodeProduit("P01")!!.nomProduit)
        assertEquals("P01", db.artCodebarreDao().findByCodeBarre("SEC")?.codeProduit)
    }

    @Test
    fun fichierVide_refuse() = runBlocking {
        assertTrue(service.analyserCatalogue(fichierCsv("")) is AnalyseResult.Echec)
    }

    // ------------------------------------------------------------------
    // Lignes déjà décodées (préparation de l'import depuis l'API)
    // ------------------------------------------------------------------

    @Test
    fun lignesDecodees_memesControlesQueLeCsv() = runBlocking {
        val lignes = (1..9).map { i ->
            LigneCatalogue("P%02d".format(i), "CB%02d".format(i), "ARTICLE $i", "5", "100", recollee = false)
        } + LigneCatalogue("P10", "CB01", "AUTRE\tPRODUIT", "5", "100", recollee = false)

        val analyse = (service.analyserCatalogue(lignes) as AnalyseResult.Pret).analyse

        assertEquals(9, analyse.articles.size)
        assertTrue(analyse.erreurs.single().startsWith("Ligne 10 : le nom de l'article"))
        assertTrue(service.analyserCatalogue(emptyList()) is AnalyseResult.Echec)
    }
}
