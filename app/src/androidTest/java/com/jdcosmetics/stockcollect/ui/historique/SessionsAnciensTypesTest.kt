package com.jdcosmetics.stockcollect.ui.historique

import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jdcosmetics.stockcollect.attendre
import com.jdcosmetics.stockcollect.data.db.StockCollectDatabase
import com.jdcosmetics.stockcollect.data.db.entity.ArticleEntity
import com.jdcosmetics.stockcollect.data.db.entity.LigneCollecteEntity
import com.jdcosmetics.stockcollect.data.db.entity.SessionEntity
import com.jdcosmetics.stockcollect.data.db.entity.StatutSession
import com.jdcosmetics.stockcollect.data.db.entity.TypeOperation
import com.jdcosmetics.stockcollect.data.repository.SessionRepository
import com.jdcosmetics.stockcollect.domain.service.CsvExportService
import com.jdcosmetics.stockcollect.domain.service.ExportResult
import com.jdcosmetics.stockcollect.surThreadPrincipal
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * TASK-5 #6 : ENTREE et SORTIE ne sont plus créables, mais les sessions de ces types déjà
 * présentes sur une tablette restent trouvables (puces de l'Historique), lisibles et exportables.
 */
@RunWith(AndroidJUnit4::class)
class SessionsAnciensTypesTest {

    private lateinit var db: StockCollectDatabase
    private lateinit var store: ViewModelStore

    private var inventaire = 0L
    private var entreeExportee = 0L
    private var entreeBrouillon = 0L
    private var sortieCloturee = 0L

    @Before
    fun preparer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, StockCollectDatabase::class.java).build()
        store = ViewModelStore()
        runBlocking {
            db.articleDao().insertOrReplace(
                listOf(
                    ArticleEntity(
                        codeProduit = "P1", codeBarrePrincipal = "6970000000011",
                        nomProduit = "CREME, 50ML", dateImport = "2026-09-01T00:00:00"
                    )
                )
            )
            val dao = db.sessionDao()
            inventaire = dao.insert(session(TypeOperation.INVENTAIRE, "2026-09-01T08:00:00", StatutSession.EXPORTEE))
            entreeExportee = dao.insert(session(TypeOperation.ENTREE, "2026-09-02T08:00:00", StatutSession.EXPORTEE))
            entreeBrouillon = dao.insert(session(TypeOperation.ENTREE, "2026-09-03T08:00:00", StatutSession.BROUILLON))
            sortieCloturee = dao.insert(session(TypeOperation.SORTIE, "2026-09-04T08:00:00", StatutSession.CLOTUREE))
            db.ligneCollecteDao().insert(
                LigneCollecteEntity(
                    idSession = sortieCloturee, codeProduit = "P1", nomProduitSnap = "CREME, 50ML",
                    quantite = 4.0, dateSaisie = "2026-09-04T08:05:00"
                )
            )
        }
    }

    @After
    fun nettoyer() {
        surThreadPrincipal { store.clear() }
        db.close()
    }

    @Test
    fun puces_retrouventLesSessionsEntreeEtSortie() {
        val vm = surThreadPrincipal {
            ViewModelProvider(store, viewModelFactory { initializer { HistoriqueViewModel(db.sessionDao()) } })[HistoriqueViewModel::class.java]
        }

        // « Tous » : les quatre, les plus récentes d'abord.
        val toutes = vm.sessions.attendre { it.size == 4 }
        assertEquals(listOf(sortieCloturee, entreeBrouillon, entreeExportee, inventaire), toutes.map { it.idSession })

        // Mêmes constantes que celles passées par HistoriqueFragment aux puces.
        surThreadPrincipal { vm.filtrer(TypeOperation.ENTREE) }
        val entrees = vm.sessions.attendre { liste -> liste.all { it.typeOperation == TypeOperation.ENTREE } }
        assertEquals(setOf(entreeExportee, entreeBrouillon), entrees.map { it.idSession }.toSet())
        // Le libellé affiché par HistoriqueAdapter.bind pour ces sessions.
        assertTrue(entrees.all { TypeOperation.label(it.typeOperation) == "Entrée de stock" })

        surThreadPrincipal { vm.filtrer(TypeOperation.SORTIE) }
        val sorties = vm.sessions.attendre { liste -> liste.all { it.typeOperation == TypeOperation.SORTIE } }
        assertEquals(listOf(sortieCloturee), sorties.map { it.idSession })
        assertEquals("Sortie de stock", TypeOperation.label(sorties.single().typeOperation))

        surThreadPrincipal { vm.filtrer(TypeOperation.INVENTAIRE) }
        val inventaires = vm.sessions.attendre { liste -> liste.all { it.typeOperation == TypeOperation.INVENTAIRE } }
        assertEquals(listOf(inventaire), inventaires.map { it.idSession })

        surThreadPrincipal { vm.filtrer(null) }
        vm.sessions.attendre { it.size == 4 }

        // Une SORTIE clôturée compte pour le bouton d'export de l'Historique.
        assertTrue(vm.aClotureeExportable.attendre { it })
    }

    @Test
    fun sessionSortieCloturee_sExporteEnCsv() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = CsvExportService(
            context,
            SessionRepository(db.sessionDao(), db.ligneCollecteDao(), db.articleDao()),
            db.articleDao(),
            db.sessionDao(),
            db.exportDao()
        )
        val fichier = File(context.cacheDir, "export-sortie-test.csv").apply { delete() }

        val resultat = runBlocking { service.exporter(sortieCloturee, Uri.fromFile(fichier)) }

        assertTrue("Export refusé : $resultat", resultat is ExportResult.Succes)
        assertEquals(
            "Code Barre,Code Produit,Nom Produit,Quantité\r\n" +
                "6970000000011,P1,CREME  50ML,4.0\r\n",
            fichier.readText(Charsets.UTF_8)
        )
        assertEquals(
            StatutSession.EXPORTEE,
            runBlocking { db.sessionDao().getById(sortieCloturee) }!!.statut
        )
        fichier.delete()
    }

    private fun session(type: String, debut: String, statut: String) =
        SessionEntity(typeOperation = type, dateHeureDebut = debut, statut = statut, nbLignes = 0)
}
