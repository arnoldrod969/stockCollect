package com.jdcosmetics.stockcollect.domain.service

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jdcosmetics.stockcollect.data.db.StockCollectDatabase
import com.jdcosmetics.stockcollect.data.db.entity.ArtCodebarreEntity
import com.jdcosmetics.stockcollect.data.db.entity.ArticleEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TASK-7 #4 : la résolution en deux temps de [BarcodeScanService.resoudre], sur une base Room en
 * mémoire — code principal dans `articles`, sinon `art_codebarre`, sinon [ResolutionResult.NonTrouve].
 *
 * Catalogue : P1 porte le principal CB1 et le secondaire SEC1 ; P2 n'a pas de principal mais le
 * secondaire SEC2 ; P3 porte le principal CB3.
 */
@RunWith(AndroidJUnit4::class)
class BarcodeScanServiceTest {

    private lateinit var db: StockCollectDatabase
    private lateinit var service: BarcodeScanService

    @Before
    fun preparer() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, StockCollectDatabase::class.java).build()
        service = BarcodeScanService(db.articleDao(), db.artCodebarreDao())

        db.articleDao().insertOrReplace(
            listOf(
                article("P1", "CB1"),
                article("P2", null),
                article("P3", "CB3")
            )
        )
        db.artCodebarreDao().insertOrReplace(
            listOf(
                ArtCodebarreEntity("SEC1", "P1", DATE),
                ArtCodebarreEntity("SEC2", "P2", DATE)
            )
        )
    }

    @After
    fun nettoyer() = db.close()

    @Test
    fun codePrincipal_resoluDirectement() = runBlocking {
        val r = service.resoudre("CB1")
        assertEquals(ResolutionResult.Trouve(article("P1", "CB1"), "CB1", viaTableCB = false), r)
    }

    @Test
    fun codeSecondaire_resoluViaArtCodebarre() = runBlocking {
        val r = service.resoudre("SEC2")
        assertEquals(ResolutionResult.Trouve(article("P2", null), "SEC2", viaTableCB = true), r)
    }

    @Test
    fun codeSecondaireDUnArticleQuiAUnPrincipal_resoluViaArtCodebarre() = runBlocking {
        val r = service.resoudre("SEC1")
        assertEquals(ResolutionResult.Trouve(article("P1", "CB1"), "SEC1", viaTableCB = true), r)
    }

    @Test
    fun codeInconnu_nonTrouve() = runBlocking {
        assertEquals(ResolutionResult.NonTrouve("INCONNU"), service.resoudre("INCONNU"))
    }

    @Test
    fun espacesAutourDuCode_ignores() = runBlocking {
        // Une douchette ou une saisie manuelle peut laisser un espace ou un retour chariot.
        val r = service.resoudre("  CB3\n")
        assertEquals(ResolutionResult.Trouve(article("P3", "CB3"), "CB3", viaTableCB = false), r)
        assertEquals(ResolutionResult.NonTrouve("INCONNU"), service.resoudre(" INCONNU "))
    }

    private fun article(code: String, principal: String?) = ArticleEntity(
        codeProduit = code,
        codeBarrePrincipal = principal,
        nomProduit = "ARTICLE $code",
        dateImport = DATE
    )

    private companion object {
        const val DATE = "2026-09-25T10:00:00"
    }
}
