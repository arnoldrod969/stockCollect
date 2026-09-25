package com.jdcosmetics.stockcollect.ui.export

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
import com.jdcosmetics.stockcollect.data.db.entity.StatutSync
import com.jdcosmetics.stockcollect.data.db.entity.TypeOperation
import com.jdcosmetics.stockcollect.data.repository.SessionRepository
import com.jdcosmetics.stockcollect.domain.service.CsvExportService
import com.jdcosmetics.stockcollect.domain.service.ExportResult
import com.jdcosmetics.stockcollect.surThreadPrincipal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * TASK-9 : exporter une session autre que la dernière clôturée, et réexporter une session déjà
 * exportée sans toucher au cycle de vie ni au statut de synchro.
 */
@RunWith(AndroidJUnit4::class)
class ExportSessionDesigneeTest {

    private lateinit var db: StockCollectDatabase
    private lateinit var store: ViewModelStore
    private lateinit var service: CsvExportService
    private lateinit var fichier: File

    private var ancienneCloturee = 0L
    private var exportee = 0L
    private var recenteCloturee = 0L
    private var brouillon = 0L

    @Before
    fun preparer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, StockCollectDatabase::class.java).build()
        store = ViewModelStore()
        service = CsvExportService(
            context,
            SessionRepository(db.sessionDao(), db.ligneCollecteDao(), db.articleDao()),
            db.articleDao(),
            db.sessionDao(),
            db.exportDao()
        )
        fichier = File(context.cacheDir, "export-designee-test.csv").apply { delete() }
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
            ancienneCloturee = dao.insert(session("2026-09-01T08:00:00", StatutSession.CLOTUREE))
            exportee = dao.insert(
                session("2026-09-02T08:00:00", StatutSession.EXPORTEE, StatutSync.SYNCHRONISEE)
            )
            recenteCloturee = dao.insert(session("2026-09-03T08:00:00", StatutSession.CLOTUREE))
            brouillon = dao.insert(session("2026-09-04T08:00:00", StatutSession.BROUILLON))
            // Chaque session a une ligne : le refus du brouillon doit venir de son statut, pas
            // d'une session vide.
            listOf(ancienneCloturee, exportee, recenteCloturee, brouillon).forEachIndexed { i, id ->
                db.ligneCollecteDao().insert(
                    LigneCollecteEntity(
                        idSession = id, codeProduit = "P1", nomProduitSnap = "CREME, 50ML",
                        quantite = (i + 1).toDouble(), dateSaisie = "2026-09-0${i + 1}T08:05:00"
                    )
                )
            }
        }
    }

    @After
    fun nettoyer() {
        surThreadPrincipal { store.clear() }
        db.close()
        fichier.delete()
    }

    // ---- Service : CLOTUREE → EXPORTEE, EXPORTEE → EXPORTEE, BROUILLON refusé ----

    @Test
    fun sessionClotureeAncienne_sExporteEtPasseEnExportee() {
        val resultat = runBlocking { service.exporter(ancienneCloturee, Uri.fromFile(fichier)) }

        assertTrue("Export refusé : $resultat", resultat is ExportResult.Succes)
        assertEquals(
            "Code Barre,Code Produit,Nom Produit,Quantité\r\n" +
                "6970000000011,P1,CREME  50ML,1.0\r\n",
            fichier.readText(Charsets.UTF_8)
        )
        assertEquals(StatutSession.EXPORTEE, statut(ancienneCloturee))
        assertEquals(1, nbExports(ancienneCloturee))
        // La plus récente, elle, n'a pas bougé.
        assertEquals(StatutSession.CLOTUREE, statut(recenteCloturee))
        assertEquals(0, nbExports(recenteCloturee))
    }

    @Test
    fun sessionDejaExportee_seReexporteSansChangerDeStatutNiDeSynchro() {
        val premier = runBlocking { service.exporter(exportee, Uri.fromFile(fichier)) }
        fichier.delete() // le fichier « perdu »
        val second = runBlocking { service.exporter(exportee, Uri.fromFile(fichier)) }

        assertTrue("Premier réexport refusé : $premier", premier is ExportResult.Succes)
        assertTrue("Second réexport refusé : $second", second is ExportResult.Succes)
        assertEquals(
            "Code Barre,Code Produit,Nom Produit,Quantité\r\n" +
                "6970000000011,P1,CREME  50ML,2.0\r\n",
            fichier.readText(Charsets.UTF_8)
        )
        val apres = runBlocking { db.sessionDao().getById(exportee) }!!
        assertEquals(StatutSession.EXPORTEE, apres.statut)
        assertEquals(StatutSync.SYNCHRONISEE, apres.statutSync)
        // Audit append-only : une ligne par écriture de fichier.
        assertEquals(2, nbExports(exportee))
    }

    @Test
    fun exportClotureeNeTouchePasAuStatutDeSynchro() {
        runBlocking { service.exporter(recenteCloturee, Uri.fromFile(fichier)) }
        val apres = runBlocking { db.sessionDao().getById(recenteCloturee) }!!
        assertEquals(StatutSession.EXPORTEE, apres.statut)
        assertEquals(StatutSync.NON_SYNCHRONISEE, apres.statutSync)
    }

    @Test
    fun brouillon_nEstJamaisExporte() {
        val resultat = runBlocking { service.exporter(brouillon, Uri.fromFile(fichier)) }

        assertTrue(resultat is ExportResult.Erreur)
        assertEquals(StatutSession.BROUILLON, statut(brouillon))
        assertEquals(0, nbExports(brouillon))
        assertTrue(!fichier.exists() || fichier.length() == 0L)
    }

    // ---- Écran : quelle session l'écran Export affiche ----

    @Test
    fun sansSessionDesignee_laPlusRecenteCloturee() {
        val vm = viewModel()
        surThreadPrincipal { vm.charger(EXPORT_SESSION_PLUS_RECENTE) }
        assertEquals(recenteCloturee, vm.sessionExportable.attendre { it != null }!!.idSession)
    }

    @Test
    fun sessionClotureeDesignee_estCelleAffichee() {
        val vm = viewModel()
        surThreadPrincipal { vm.charger(ancienneCloturee) }
        assertEquals(ancienneCloturee, vm.sessionExportable.attendre { it != null }!!.idSession)
    }

    @Test
    fun sessionExporteeDesignee_estAfficheeEtReexportable() {
        val vm = viewModel()
        surThreadPrincipal { vm.charger(exportee) }
        assertEquals(exportee, vm.sessionExportable.attendre { it != null }!!.idSession)

        surThreadPrincipal { vm.exporter(Uri.fromFile(fichier)) }
        vm.uiState.attendre { it is ExportUiState.Succes }
        // Toujours la même session après l'export, pas « la suivante des clôturées ».
        val apres = vm.sessionExportable.attendre { it?.idSession == exportee }!!
        assertEquals(StatutSession.EXPORTEE, apres.statut)
        assertEquals(1, nbExports(exportee))
    }

    @Test
    fun sessionClotureeDesignee_resteAfficheeApresExport() {
        val vm = viewModel()
        surThreadPrincipal { vm.charger(ancienneCloturee) }
        vm.sessionExportable.attendre { it?.idSession == ancienneCloturee }

        surThreadPrincipal { vm.exporter(Uri.fromFile(fichier)) }
        vm.uiState.attendre { it is ExportUiState.Succes }
        vm.sessionExportable.attendre {
            it?.idSession == ancienneCloturee && it.statut == StatutSession.EXPORTEE
        }
    }

    @Test
    fun brouillonDesigne_rienAExporter() {
        val vm = viewModel()
        surThreadPrincipal { vm.charger(brouillon) }
        // null et non une autre session : l'écran dit qu'il n'y a rien à exporter.
        vm.sessionExportable.attendre { it == null }
    }

    private fun viewModel(): ExportViewModel = surThreadPrincipal {
        ViewModelProvider(
            store,
            viewModelFactory { initializer { ExportViewModel(service, db.sessionDao()) } }
        )[ExportViewModel::class.java]
    }

    private fun statut(id: Long) = runBlocking { db.sessionDao().getById(id) }!!.statut

    private fun nbExports(id: Long) = runBlocking { db.exportDao().getExportsBySession(id).first() }.size

    private fun session(debut: String, statut: String, statutSync: String = StatutSync.NON_SYNCHRONISEE) =
        SessionEntity(
            typeOperation = TypeOperation.INVENTAIRE, dateHeureDebut = debut, statut = statut,
            nbLignes = 1, statutSync = statutSync
        )
}
