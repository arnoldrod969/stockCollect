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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Exerce la règle « un code-barre n'appartient qu'à un seul article » **entre** `articles` et
 * `art_codebarre`, sur le vrai [CsvImportService.importCorrespondance] et une base Room en
 * mémoire. Aucune donnée réelle ne viole la règle : sans ce test, le rejet n'a jamais tourné.
 *
 * Catalogue de départ : dix articles `P01`..`P10`, portant chacun le code principal `CB01`..`CB10`.
 */
@RunWith(AndroidJUnit4::class)
class CorrespondanceCodeBarreTest {

    private lateinit var context: Context
    private lateinit var db: StockCollectDatabase
    private lateinit var service: CsvImportService
    private val fichiers = mutableListOf<File>()

    @Before
    fun preparer() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, StockCollectDatabase::class.java).build()
        service = CsvImportService(context, db, db.articleDao(), db.artCodebarreDao())

        db.articleDao().insertOrReplace(
            (1..10).map { i ->
                ArticleEntity(
                    codeProduit = "P%02d".format(i),
                    codeBarrePrincipal = "CB%02d".format(i),
                    nomProduit = "ARTICLE $i",
                    dateImport = "2026-09-25T10:00:00"
                )
            }
        )
    }

    @After
    fun nettoyer() {
        db.close()
        fichiers.forEach { it.delete() }
    }

    /** Écrit un CSV sans en-tête dans le cache de l'app et le rend lisible par le service. */
    private fun fichierCsv(vararg lignes: String): Uri {
        val fichier = File.createTempFile("correspondance", ".csv", context.cacheDir)
        fichier.writeText(lignes.joinToString("\r\n"), Charsets.UTF_8)
        fichiers += fichier
        return Uri.fromFile(fichier)
    }

    /** Neuf correspondances saines : codes secondaires `SEC1`..`SEC9` vers `P01`..`P09`. */
    private fun lignesSaines(): Array<String> =
        (1..9).map { i -> "SEC$i,P%02d".format(i) }.toTypedArray()

    @Test
    fun codeBarrePrincipalDUnAutreArticle_ligneRejeteeEtNonEcrite() = runBlocking {
        // CB01 est le code principal de P01 : le rattacher à P02 violerait la règle.
        // 1 erreur sur 10 lignes = 10 %, pas au-delà du seuil : l'import passe sans elle.
        val uri = fichierCsv(*lignesSaines(), "CB01,P02")

        val resultat = service.importCorrespondance(uri)

        assertTrue("L'import doit passer, la ligne fautive seule étant écartée", resultat.success)
        assertEquals(9, resultat.nbImportes)
        assertEquals(1, resultat.nbErreurs)
        val erreur = resultat.erreurs.single()
        assertTrue("L'erreur doit nommer la ligne : $erreur", erreur.startsWith("Ligne 10 :"))
        assertTrue("L'erreur doit nommer le code-barre : $erreur", "« CB01 »" in erreur)
        assertTrue("L'erreur doit nommer le porteur : $erreur", "« P01 »" in erreur)

        assertNull(
            "Rien ne doit être écrit dans art_codebarre pour la ligne rejetée",
            db.artCodebarreDao().findByCodeBarre("CB01")
        )
        assertEquals(9, db.artCodebarreDao().count())
        // Le porteur légitime n'est pas touché.
        assertEquals("CB01", db.articleDao().findByCodeProduit("P01")?.codeBarrePrincipal)
    }

    @Test
    fun codeBarrePrincipalDuMemeArticle_accepteEtEcrit() = runBlocking {
        // CB03 est déjà le code principal de P03 : la règle n'est pas violée (même article).
        // Le code l'accepte et l'écrit — redondant mais inoffensif, resoudre() trouvant P03
        // dès l'étape 1 sur `articles`.
        val uri = fichierCsv("CB03,P03", "SEC1,P01")

        val resultat = service.importCorrespondance(uri)

        assertTrue(resultat.success)
        assertEquals(2, resultat.nbImportes)
        assertEquals(0, resultat.nbErreurs)
        assertEquals("P03", db.artCodebarreDao().findByCodeBarre("CB03")?.codeProduit)
    }

    @Test
    fun correspondanceSaine_inseree() = runBlocking {
        val uri = fichierCsv(*lignesSaines())

        val resultat = service.importCorrespondance(uri)

        assertTrue(resultat.success)
        assertEquals(9, resultat.nbImportes)
        assertEquals(0, resultat.nbErreurs)
        assertEquals(9, db.artCodebarreDao().count())
        (1..9).forEach { i ->
            val entree = db.artCodebarreDao().findByCodeBarre("SEC$i")
            assertNotNull("SEC$i doit être inséré", entree)
            assertEquals("P%02d".format(i), entree!!.codeProduit)
        }
    }

    @Test
    fun auDelaDuSeuilDe10Pourcent_importRefuseEtTableInchangee() = runBlocking {
        // Une correspondance déjà en base, issue d'un import précédent.
        db.artCodebarreDao().insertOrReplace(
            listOf(ArtCodebarreEntity("ANCIEN", "P05", "2026-09-01T08:00:00"))
        )

        // 2 conflits sur 10 lignes = 20 % > 10 %.
        val uri = fichierCsv(*lignesSaines().take(8).toTypedArray(), "CB01,P02", "CB02,P03")

        val resultat = service.importCorrespondance(uri)

        assertFalse("Au-delà de 10 % d'erreurs, l'import doit être refusé", resultat.success)
        assertEquals(2, resultat.nbErreurs)
        assertNotNull(resultat.messageErreur)
        assertTrue(resultat.erreurs.all { "un code-barre ne peut désigner qu'un seul article" in it })

        // L'existant est intact : ni vidé par le deleteAll, ni complété par les lignes saines.
        assertEquals(1, db.artCodebarreDao().count())
        assertEquals("P05", db.artCodebarreDao().findByCodeBarre("ANCIEN")?.codeProduit)
        assertNull(db.artCodebarreDao().findByCodeBarre("SEC1"))
    }
}
