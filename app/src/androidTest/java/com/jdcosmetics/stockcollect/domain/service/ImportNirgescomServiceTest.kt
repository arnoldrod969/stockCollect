package com.jdcosmetics.stockcollect.domain.service

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jdcosmetics.stockcollect.data.db.StockCollectDatabase
import com.jdcosmetics.stockcollect.data.db.entity.ArtCodebarreEntity
import com.jdcosmetics.stockcollect.data.db.entity.ArticleEntity
import com.jdcosmetics.stockcollect.data.prefs.ParametresSync
import com.jdcosmetics.stockcollect.data.remote.ArticleDistant
import com.jdcosmetics.stockcollect.data.remote.CodeBarreDistant
import com.jdcosmetics.stockcollect.data.remote.NirgescomClient
import com.jdcosmetics.stockcollect.data.remote.ResultatReferentiel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mise à jour depuis Nirgescom (TASK-16) sur une base Room en mémoire, **sans réseau** : les
 * réponses sont fabriquées déjà décodées et passées à [ImportNirgescomService.analyserReponse] /
 * [ImportNirgescomService.importerReponse]. Le client est construit mais jamais appelé.
 *
 * Couvre : quantité de référence conservée, arbitrage avant toute écriture, `304` et catalogue
 * vide sans écriture, liste de codes-barres vide qui ne vide rien, règle « un code-barre = un
 * article » appliquée à l'API, et la tenue des `ETag`.
 *
 * Les `ETag` vivent dans les préférences de l'app sous test : leurs valeurs sont sauvées avant
 * chaque test et remises après.
 */
@RunWith(AndroidJUnit4::class)
class ImportNirgescomServiceTest {

    private lateinit var context: Context
    private lateinit var db: StockCollectDatabase
    private lateinit var parametres: ParametresSync
    private lateinit var service: ImportNirgescomService
    private var etagCatalogueAvant = ""
    private var etagCodesBarresAvant = ""

    @Before
    fun preparer() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, StockCollectDatabase::class.java).build()
        parametres = ParametresSync(context)
        etagCatalogueAvant = parametres.etagCatalogue
        etagCodesBarresAvant = parametres.etagCodesBarres
        parametres.etagCatalogue = ""
        parametres.etagCodesBarres = "\"cb-ancien\""

        val csv = CsvImportService(context, db, db.articleDao(), db.artCodebarreDao())
        service = ImportNirgescomService(
            NirgescomClient(parametres), csv, parametres, db.articleDao(), db.artCodebarreDao()
        )

        // Catalogue venu d'un CSV : P01..P09, quantités de référence non nulles.
        db.articleDao().insertOrReplace(
            (1..9).map { i ->
                ArticleEntity(
                    codeProduit = "P%02d".format(i),
                    codeBarrePrincipal = "CB%02d".format(i),
                    nomProduit = "ARTICLE $i",
                    quantiteRef = 10.0 + i,
                    prix = 100.0,
                    dateImport = "2026-09-25T10:00:00"
                )
            }
        )
        db.artCodebarreDao().insertOrReplace(
            listOf(
                ArtCodebarreEntity("SEC1", "P01", "2026-09-25T10:00:00"),
                ArtCodebarreEntity("SEC2", "P02", "2026-09-25T10:00:00")
            )
        )
    }

    @After
    fun nettoyer() {
        parametres.etagCatalogue = etagCatalogueAvant
        parametres.etagCodesBarres = etagCodesBarresAvant
        db.close()
    }

    private fun article(code: String, codeBarre: String?, nom: String, prix: Double? = 250.0) =
        ArticleDistant(code, codeBarre, nom, prix)

    /** Le même catalogue qu'en base, prix changés, plus un article nouveau P10. */
    private fun catalogueDistant(): List<ArticleDistant> =
        (1..9).map { i -> article("P%02d".format(i), "CB%02d".format(i), "ARTICLE $i") } +
            article("P10", "CB10", "NOUVEL ARTICLE")

    private suspend fun analysePrete(reponse: ResultatReferentiel<List<ArticleDistant>>): AnalyseNirgescom.Prete =
        when (val r = service.analyserReponse(reponse)) {
            is AnalyseNirgescom.Prete -> r
            else -> { fail("analyse attendue prête, obtenu $r"); throw AssertionError() }
        }

    // ------------------------------------------------------------------
    // Catalogue
    // ------------------------------------------------------------------

    @Test
    fun catalogue_quantiteRefConservee_prixMisAJour_etagEnregistre() = runBlocking {
        val prete = analysePrete(ResultatReferentiel.Ok(catalogueDistant(), "\"cat-1\""))
        // Rien n'est retenu tant que rien n'est écrit.
        assertEquals("", parametres.etagCatalogue)

        val resultat = service.appliquerCatalogue(
            prete.analyse, ResolutionConflit.IMPORTER_SANS_CODE_BARRE, prete.etag
        )

        assertTrue(resultat.messageErreur, resultat.success)
        assertEquals(1, resultat.nbImportes)
        assertEquals(9, resultat.nbMisAJour)
        db.articleDao().findByCodeProduit("P03")!!.let {
            assertEquals(13.0, it.quantiteRef, 0.0)
            assertEquals(250.0, it.prix, 0.0)
        }
        assertEquals(0.0, db.articleDao().findByCodeProduit("P10")!!.quantiteRef, 0.0)
        assertEquals("\"cat-1\"", parametres.etagCatalogue)
        // Le catalogue a bougé : la correspondance devra être redemandée en entier.
        assertEquals("", parametres.etagCodesBarres)
    }

    @Test
    fun catalogue_conflitDeCodesBarres_arbitrageAvantTouteEcriture() = runBlocking {
        val distant = catalogueDistant() + article("P11", "CB01", "AUTRE PRODUIT")
        val prete = analysePrete(ResultatReferentiel.Ok(distant, "\"cat-2\""))

        assertTrue(prete.analyse.aDesConflits)
        assertEquals("P11", prete.analyse.conflits.single().perdants.single().codeProduit)
        // Analysé mais pas écrit.
        assertEquals(9, db.articleDao().count())
        assertEquals(100.0, db.articleDao().findByCodeProduit("P01")!!.prix, 0.0)
        assertEquals("", parametres.etagCatalogue)

        val resultat = service.appliquerCatalogue(
            prete.analyse, ResolutionConflit.IGNORER_ARTICLES, prete.etag
        )
        assertTrue(resultat.success)
        assertEquals(1, resultat.nbIgnores)
        assertEquals(null, db.articleDao().findByCodeProduit("P11"))
    }

    @Test
    fun catalogue_304_ni_ecriture_ni_etag() = runBlocking {
        parametres.etagCatalogue = "\"cat-0\""
        assertEquals(AnalyseNirgescom.DejaAJour, service.analyserReponse(ResultatReferentiel.Inchange))
        assertEquals(100.0, db.articleDao().findByCodeProduit("P01")!!.prix, 0.0)
        assertEquals("\"cat-0\"", parametres.etagCatalogue)
    }

    @Test
    fun catalogue_vide_refuse_catalogueIntact() = runBlocking {
        val r = service.analyserReponse(ResultatReferentiel.Ok(emptyList(), "\"vide\""))
        assertEquals(AnalyseNirgescom.Echec(ImportNirgescom.MESSAGE_CATALOGUE_VIDE), r)
        assertEquals(9, db.articleDao().count())
        assertEquals("", parametres.etagCatalogue)
    }

    @Test
    fun catalogue_erreurServeur_rienEcrit() = runBlocking {
        val r = service.analyserReponse(
            ResultatReferentiel.ConfigurationServeur("Configuration de la cle incorrecte")
        )
        assertTrue(r is AnalyseNirgescom.Echec)
        assertTrue((r as AnalyseNirgescom.Echec).message.contains("mal configuré"))
        assertEquals(9, db.articleDao().count())
    }

    @Test
    fun catalogue_memesControlesQueLeCsv_seuilDes10Pourcent() = runBlocking {
        // 2 lignes fautives sur 10 : au-delà du seuil, rien n'est écrit.
        val distant = (1..8).map { i -> article("P%02d".format(i), "CB%02d".format(i), "ARTICLE $i") } +
            article("P20", "CB20", "NOM\tAVEC TABULATION") +
            article("", "CB21", "SANS CODE")
        val r = service.analyserReponse(ResultatReferentiel.Ok(distant, "\"cat-3\""))
        assertTrue(r is AnalyseNirgescom.Echec)
        r as AnalyseNirgescom.Echec
        assertTrue(r.message.contains("Nirgescom"))
        assertEquals(2, r.erreurs.size)
        assertEquals(100.0, db.articleDao().findByCodeProduit("P01")!!.prix, 0.0)
    }

    @Test
    fun importFichierDuCatalogue_oublieLesEtag() {
        parametres.etagCatalogue = "\"cat-1\""
        service.catalogueImporteDepuisFichier()
        assertEquals("", parametres.etagCatalogue)
        assertEquals("", parametres.etagCodesBarres)
    }

    // ------------------------------------------------------------------
    // Codes-barres secondaires
    // ------------------------------------------------------------------

    @Test
    fun codesBarres_listeVide_correspondanceConservee() = runBlocking {
        val r = service.importerReponse(ResultatReferentiel.Ok(emptyList(), "\"vide\""))

        assertEquals(ImportCodesBarresNirgescom.Conserve, r)
        assertEquals(2, db.artCodebarreDao().count())
        // Pas d'ETag retenu pour une liste vide : l'information sera redonnée à chaque fois.
        assertEquals("\"cb-ancien\"", parametres.etagCodesBarres)
    }

    @Test
    fun codesBarres_remplacementComplet_etRegleUnCodeBarreUnArticle() = runBlocking {
        val couples = (3..9).map { i -> CodeBarreDistant("SEC$i", "P%02d".format(i)) } +
            listOf(
                CodeBarreDistant("SEC10", "P01"),
                CodeBarreDistant("SEC11", "P02"),
                // Code principal de P02 : refusé, un code-barre ne désigne qu'un article.
                CodeBarreDistant("CB02", "P03")
            )
        val r = service.importerReponse(ResultatReferentiel.Ok(couples, "\"cb-2\""))

        assertTrue("$r", r is ImportCodesBarresNirgescom.Importe)
        val resultat = (r as ImportCodesBarresNirgescom.Importe).resultat
        assertEquals(9, resultat.nbImportes)
        assertEquals(1, resultat.nbErreurs)
        assertTrue(resultat.erreurs.single().contains("appartient déjà à l'article « P02 »"))
        // Remplacement complet : SEC1 et SEC2, absents de la réponse, sont partis.
        assertEquals(null, db.artCodebarreDao().findByCodeBarre("SEC1"))
        assertEquals(null, db.artCodebarreDao().findByCodeBarre("CB02"))
        assertEquals("P01", db.artCodebarreDao().findByCodeBarre("SEC10")!!.codeProduit)
        assertEquals("\"cb-2\"", parametres.etagCodesBarres)
    }

    @Test
    fun codesBarres_articlesDAutresDepots_ecartesSansFaireTomberLImport() = runBlocking {
        // /codes-barres couvre tous les dépôts, /catalog seulement celui de la clé : 30 couples
        // sur 39 visent des articles absents de la tablette, ce n'est pas 77 % d'erreurs.
        val couples = (1..9).map { i -> CodeBarreDistant("SEC$i", "P%02d".format(i)) } +
            (1..30).map { i -> CodeBarreDistant("AILLEURS$i", "AUTRE-DEPOT-$i") }
        val r = service.importerReponse(ResultatReferentiel.Ok(couples, "\"cb-4\""))

        assertTrue("$r", r is ImportCodesBarresNirgescom.Importe)
        val resultat = (r as ImportCodesBarresNirgescom.Importe).resultat
        assertEquals(9, resultat.nbImportes)
        assertEquals(30, resultat.nbIgnores)
        assertEquals(0, resultat.nbErreurs)
        assertEquals(9, db.artCodebarreDao().count())
    }

    @Test
    fun codesBarres_toutHorsCatalogue_correspondanceConservee() = runBlocking {
        val couples = (1..5).map { i -> CodeBarreDistant("AILLEURS$i", "AUTRE-DEPOT-$i") }
        val r = service.importerReponse(ResultatReferentiel.Ok(couples, "\"cb-5\""))

        assertTrue(r is ImportCodesBarresNirgescom.Echec)
        assertEquals(2, db.artCodebarreDao().count())
        assertEquals("\"cb-ancien\"", parametres.etagCodesBarres)
    }

    @Test
    fun codesBarres_tropDErreurs_rienNEstRemplace() = runBlocking {
        val couples = listOf(
            CodeBarreDistant("SEC5", "P05"),
            CodeBarreDistant("SEC6", "P06\tX"),
            CodeBarreDistant(null, "P07")
        )
        val r = service.importerReponse(ResultatReferentiel.Ok(couples, "\"cb-3\""))

        assertTrue(r is ImportCodesBarresNirgescom.Echec)
        assertEquals(2, (r as ImportCodesBarresNirgescom.Echec).erreurs.size)
        assertTrue(r.message.contains("Nirgescom"))
        assertEquals(2, db.artCodebarreDao().count())
        assertFalse(parametres.etagCodesBarres == "\"cb-3\"")
    }

    @Test
    fun codesBarres_304_rienNEstTouche() = runBlocking {
        assertEquals(
            ImportCodesBarresNirgescom.DejaAJour,
            service.importerReponse(ResultatReferentiel.Inchange)
        )
        assertEquals(2, db.artCodebarreDao().count())
    }
}
