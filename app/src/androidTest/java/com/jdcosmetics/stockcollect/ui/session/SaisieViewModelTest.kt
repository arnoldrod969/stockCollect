package com.jdcosmetics.stockcollect.ui.session

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jdcosmetics.stockcollect.EnregistreurLiveData
import com.jdcosmetics.stockcollect.attendre
import com.jdcosmetics.stockcollect.data.db.StockCollectDatabase
import com.jdcosmetics.stockcollect.data.db.entity.ArticleEntity
import com.jdcosmetics.stockcollect.data.db.entity.LigneCollecteEntity
import com.jdcosmetics.stockcollect.data.db.entity.SessionEntity
import com.jdcosmetics.stockcollect.data.db.entity.StatutSession
import com.jdcosmetics.stockcollect.data.db.entity.TypeOperation
import com.jdcosmetics.stockcollect.data.prefs.ParametresSync
import com.jdcosmetics.stockcollect.data.repository.SessionRepository
import com.jdcosmetics.stockcollect.surThreadPrincipal
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TASK-5 #1 et #2, sur une base Room en mémoire et les vrais dispatchers.
 *
 * #1 : un changement de session ne laisse pas l'ancien collecteur de lignes publier.
 * #2 : `creerSession` ne reprend un brouillon que du type demandé ; sinon il rend la main.
 */
@RunWith(AndroidJUnit4::class)
class SaisieViewModelTest {

    private lateinit var db: StockCollectDatabase
    private lateinit var store: ViewModelStore
    private lateinit var vm: SaisieViewModel

    @Before
    fun preparer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, StockCollectDatabase::class.java).build()
        val repository = SessionRepository(db.sessionDao(), db.ligneCollecteDao(), db.articleDao())
        val parametres = ParametresSync(context)
        store = ViewModelStore()
        vm = surThreadPrincipal {
            ViewModelProvider(
                store,
                viewModelFactory { initializer { SaisieViewModel(repository, parametres) } }
            )[SaisieViewModel::class.java]
        }
        runBlocking {
            db.articleDao().insertOrReplace(listOf(article("P1"), article("P2")))
        }
    }

    @After
    fun nettoyer() {
        surThreadPrincipal { store.clear() }   // annule viewModelScope, donc les collecteurs
        db.close()
    }

    // ---- #1 : observerLignes ----

    @Test
    fun changerDeSession_lAncienCollecteurNePubliePlus() {
        val a = session(TypeOperation.INVENTAIRE, "2026-09-10T08:00:00")
        val b = session(TypeOperation.INVENTAIRE, "2026-09-11T08:00:00")
        ligne(a, "P1", 1.0)
        ligne(b, "P1", 2.0)

        surThreadPrincipal { vm.chargerSessionExistante(a) }
        vm.lignes.attendre { it.isNotEmpty() && it.all { l -> l.idSession == a } }
        surThreadPrincipal { vm.chargerSessionExistante(b) }
        vm.lignes.attendre { it.isNotEmpty() && it.all { l -> l.idSession == b } }

        val publiees = EnregistreurLiveData(vm.lignes)
        // Une écriture dans A invalide la table : un collecteur de A resté vivant republierait
        // la liste de A par-dessus celle de B. C'était le cas avant l'annulation du Job.
        ligne(a, "P2", 5.0)
        ligne(b, "P2", 7.0)
        vm.lignes.attendre { it.size == 2 && it.all { l -> l.idSession == b } }
        Thread.sleep(500)   // laisse à une éventuelle émission tardive de A le temps d'arriver
        publiees.arreter()

        assertTrue(
            "Une liste de la session A a été publiée alors que B est ouverte : ${publiees.valeurs}",
            publiees.valeurs.none { liste -> liste.any { it.idSession == a } }
        )
    }

    @Test
    fun deuxChargementsRapproches_leDernierLEmporte() {
        val a = session(TypeOperation.INVENTAIRE, "2026-09-10T08:00:00")
        val b = session(TypeOperation.INVENTAIRE, "2026-09-11T08:00:00")
        ligne(a, "P1", 1.0)
        ligne(b, "P1", 2.0)

        // Dans le même passage du thread principal : aucune lecture n'a encore pu revenir.
        surThreadPrincipal {
            vm.chargerSessionExistante(a)
            vm.chargerSessionExistante(b)
        }
        vm.lignes.attendre { it.isNotEmpty() && it.all { l -> l.idSession == b } }
        vm.sessionCourante.attendre { it?.idSession == b }
        Thread.sleep(500)

        assertEquals(b, vm.getIdSessionCourante())
        assertEquals(b, surThreadPrincipal { vm.sessionCourante.value }?.idSession)
        assertTrue(surThreadPrincipal { vm.lignes.value }!!.all { it.idSession == b })
    }

    // ---- #2 : creerSession et le type du brouillon ----

    @Test
    fun brouillonDUnAutreType_nEstPasRepris() {
        val entree = session(TypeOperation.ENTREE, "2026-09-10T08:00:00")

        surThreadPrincipal { vm.creerSession(TypeOperation.INVENTAIRE, "comptage rayon A") }
        val etat = vm.uiState.attendre { it.estFinal() }

        assertEquals(
            SaisieUiState.BrouillonAutreType(
                idSession = entree,
                typeBrouillon = TypeOperation.ENTREE,
                typeDemande = TypeOperation.INVENTAIRE
            ),
            etat
        )
        // Ni reprise ni création : c'est à l'utilisateur de choisir.
        assertEquals(1, runBlocking { db.sessionDao().count() })
        assertEquals(-1L, vm.getIdSessionCourante())
        assertNull(surThreadPrincipal { vm.sessionCourante.value })
    }

    @Test
    fun brouillonDuMemeType_estRepris_memeMasqueParUnAutreTypePlusRecent() {
        val inventaire = session(TypeOperation.INVENTAIRE, "2026-09-10T08:00:00")
        session(TypeOperation.ENTREE, "2026-09-20T08:00:00")   // plus récent, autre type

        surThreadPrincipal { vm.creerSession(TypeOperation.INVENTAIRE, null) }
        val etat = vm.uiState.attendre { it.estFinal() }

        assertEquals(SaisieUiState.SessionCreee(inventaire), etat)
        assertEquals(2, runBlocking { db.sessionDao().count() })
        assertEquals(inventaire, vm.getIdSessionCourante())
    }

    @Test
    fun aucunBrouillon_creeUneSessionDuTypeDemande() {
        // Une session close du même type ne compte pas comme brouillon.
        session(TypeOperation.INVENTAIRE, "2026-09-10T08:00:00", StatutSession.CLOTUREE)

        surThreadPrincipal { vm.creerSession(TypeOperation.INVENTAIRE, "obs") }
        val etat = vm.uiState.attendre { it.estFinal() }

        assertTrue("État inattendu : $etat", etat is SaisieUiState.SessionCreee)
        val creee = runBlocking { db.sessionDao().getById((etat as SaisieUiState.SessionCreee).idSession) }
        assertNotNull(creee)
        assertEquals(TypeOperation.INVENTAIRE, creee!!.typeOperation)
        assertEquals(StatutSession.BROUILLON, creee.statut)
        assertEquals("obs", creee.observations)
        assertNotNull("L'UUID est posé à la création", creee.uuidSession)
        assertEquals(2, runBlocking { db.sessionDao().count() })
    }

    @Test
    fun reprendreBrouillon_ouvreLeBrouillonChoisiParLUtilisateur() {
        val entree = session(TypeOperation.ENTREE, "2026-09-10T08:00:00")
        surThreadPrincipal { vm.creerSession(TypeOperation.INVENTAIRE, null) }
        vm.uiState.attendre { it is SaisieUiState.BrouillonAutreType }

        surThreadPrincipal { vm.reprendreBrouillon(entree) }
        val etat = vm.uiState.attendre { it is SaisieUiState.SessionCreee }

        assertEquals(SaisieUiState.SessionCreee(entree), etat)
        assertEquals(
            TypeOperation.ENTREE,
            vm.sessionCourante.attendre { it != null }!!.typeOperation
        )
        assertEquals(1, runBlocking { db.sessionDao().count() })
    }

    // ---- Outils ----

    private fun SaisieUiState.estFinal() =
        this !is SaisieUiState.Idle && this !is SaisieUiState.Loading

    private fun article(code: String) = ArticleEntity(
        codeProduit = code,
        nomProduit = "Article $code",
        dateImport = "2026-09-01T00:00:00"
    )

    private fun session(
        type: String,
        debut: String,
        statut: String = StatutSession.BROUILLON
    ): Long = runBlocking {
        db.sessionDao().insert(
            SessionEntity(typeOperation = type, dateHeureDebut = debut, statut = statut)
        )
    }

    private fun ligne(idSession: Long, codeProduit: String, quantite: Double): Long = runBlocking {
        db.ligneCollecteDao().insert(
            LigneCollecteEntity(
                idSession = idSession,
                codeProduit = codeProduit,
                nomProduitSnap = "Article $codeProduit",
                quantite = quantite,
                dateSaisie = "2026-09-10T08:00:00"
            )
        )
    }
}
