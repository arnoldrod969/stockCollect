package com.jdcosmetics.stockcollect.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jdcosmetics.stockcollect.data.db.StockCollectDatabase
import com.jdcosmetics.stockcollect.data.db.entity.ArticleEntity
import com.jdcosmetics.stockcollect.data.db.entity.TypeOperation
import com.jdcosmetics.stockcollect.domain.service.ValidationEnvoi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TASK-12 : ce que SessionRepository écrit en base ne porte jamais la dérive des `Double`, et reste
 * donc envoyable à Nirgescom après la clôture.
 */
@RunWith(AndroidJUnit4::class)
class QuantitesNormaliseesTest {

    private lateinit var db: StockCollectDatabase
    private lateinit var repository: SessionRepository
    private var idSession = 0L

    private val article = ArticleEntity(
        codeProduit = "P1", nomProduit = "Article P1", dateImport = "2026-09-01T00:00:00"
    )

    @Before
    fun preparer() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, StockCollectDatabase::class.java).build()
        repository = SessionRepository(db.sessionDao(), db.ligneCollecteDao(), db.articleDao())
        db.articleDao().insertOrReplace(listOf(article))
        idSession = repository.creerSession(TypeOperation.INVENTAIRE, "DEPOT TEST", null)
    }

    @After
    fun nettoyer() = db.close()

    private fun quantiteEnBase(): Double = runBlocking {
        repository.getLignesSync(idSession).single().quantite
    }

    @Test
    fun rescans_1_1_puis_2_2_donnent_3_3() = runBlocking {
        repository.ajouterLigne(idSession, article, null, 1.1)
        repository.ajouterLigne(idSession, article, null, 2.2)
        assertEquals(3.3, quantiteEnBase(), 0.0)   // et non 3.3000000000000003
    }

    @Test
    fun rescans_0_1_puis_0_2_donnent_0_3() = runBlocking {
        repository.ajouterLigne(idSession, article, null, 0.1)
        repository.ajouterLigne(idSession, article, null, 0.2)
        assertEquals(0.3, quantiteEnBase(), 0.0)
    }

    @Test
    fun bouton_moins_sur_2_3_donne_1_3() = runBlocking {
        repository.ajouterLigne(idSession, article, null, 2.3)
        val ligne = repository.getLignesSync(idSession).single()
        repository.mettreAJourQuantite(ligne, ligne.quantite - 1)   // ce que fait le bouton « − »
        assertEquals(1.3, quantiteEnBase(), 0.0)   // et non 1.2999999999999998
    }

    @Test
    fun saisie_a_4_decimales_arrondie_a_3() = runBlocking {
        repository.ajouterLigne(idSession, article, null, 1.2345)
        assertEquals(1.235, quantiteEnBase(), 0.0)
    }

    @Test
    fun la_ligne_ecrite_passe_la_validation_d_envoi() = runBlocking {
        repository.ajouterLigne(idSession, article, null, 1.1)
        repository.ajouterLigne(idSession, article, null, 2.2)
        val ligne = repository.getLignesSync(idSession).single()
        repository.mettreAJourQuantite(ligne, ligne.quantite - 1)
        val problemes = ValidationEnvoi.verifier(
            magasin = "DEPOT TEST", utilisateur = null, lignes = repository.getLignesSync(idSession)
        )
        assertTrue(problemes.toString(), problemes.isEmpty())
    }
}
