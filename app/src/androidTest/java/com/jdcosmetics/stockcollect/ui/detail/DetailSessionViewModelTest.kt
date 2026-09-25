package com.jdcosmetics.stockcollect.ui.detail

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jdcosmetics.stockcollect.attendre
import com.jdcosmetics.stockcollect.data.db.StockCollectDatabase
import com.jdcosmetics.stockcollect.data.db.dao.LigneCollecteDao
import com.jdcosmetics.stockcollect.data.db.entity.ArticleEntity
import com.jdcosmetics.stockcollect.data.db.entity.LigneCollecteEntity
import com.jdcosmetics.stockcollect.data.db.entity.SessionEntity
import com.jdcosmetics.stockcollect.data.db.entity.StatutSession
import com.jdcosmetics.stockcollect.data.db.entity.TypeOperation
import com.jdcosmetics.stockcollect.data.prefs.ParametresSync
import com.jdcosmetics.stockcollect.data.remote.NirgescomClient
import com.jdcosmetics.stockcollect.domain.service.SyncService
import com.jdcosmetics.stockcollect.surThreadPrincipal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * TASK-13, sur une base Room en mémoire et les vrais dispatchers.
 *
 * Le fragment rappelle `charger` à chaque recréation de sa vue, alors que le ViewModel survit.
 * Le DAO des lignes est enveloppé pour compter les collecteurs vivants et noter lequel publie :
 * c'est la preuve directe, là où compter les valeurs de la LiveData dépendrait du rythme de Room.
 */
@RunWith(AndroidJUnit4::class)
class DetailSessionViewModelTest {

    private lateinit var db: StockCollectDatabase
    private lateinit var store: ViewModelStore
    private lateinit var vm: DetailSessionViewModel

    /** Collecteurs du Flow des lignes lancés depuis le début du test. */
    private val lances = AtomicInteger(0)
    /** Collecteurs encore actifs. */
    private val actifs = AtomicInteger(0)
    /** Numéro du collecteur à l'origine de chaque émission. */
    private val publieurs = CopyOnWriteArrayList<Int>()

    @Before
    fun preparer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, StockCollectDatabase::class.java).build()
        val vrai = db.ligneCollecteDao()
        val compteur = object : LigneCollecteDao by vrai {
            override fun getLignesBySession(idSession: Long): Flow<List<LigneCollecteEntity>> {
                val numero = lances.incrementAndGet()
                return vrai.getLignesBySession(idSession)
                    .onStart { actifs.incrementAndGet() }
                    .onEach { publieurs.add(numero) }
                    .onCompletion { actifs.decrementAndGet() }
            }
        }
        val parametres = ParametresSync(context)
        val client = NirgescomClient(parametres)   // jamais appelé ici : aucun réseau
        val sync = SyncService(db.sessionDao(), compteur, client, parametres)
        store = ViewModelStore()
        vm = surThreadPrincipal {
            ViewModelProvider(
                store,
                viewModelFactory {
                    initializer { DetailSessionViewModel(db.sessionDao(), compteur, sync, client) }
                }
            )[DetailSessionViewModel::class.java]
        }
        runBlocking {
            db.articleDao().insertOrReplace(listOf(article("P1"), article("P2")))
        }
    }

    @After
    fun nettoyer() {
        surThreadPrincipal { store.clear() }
        db.close()
    }

    @Test
    fun deuxChargements_unSeulCollecteurPublie() {
        val s = session()
        ligne(s, "P1", 1.0)

        // Première vue, puis recréation de la vue (rotation, retour arrière) : même session.
        surThreadPrincipal { vm.charger(s) }
        vm.lignes.attendre { it.size == 1 }
        surThreadPrincipal { vm.charger(s) }
        attendreQue("le second collecteur démarre") { lances.get() == 2 && actifs.get() >= 1 }
        attendreQue("le premier collecteur s'arrête") { actifs.get() == 1 }

        // Une écriture invalide la table : chaque collecteur vivant republierait la liste.
        publieurs.clear()
        ligne(s, "P2", 2.0)
        vm.lignes.attendre { it.size == 2 }
        Thread.sleep(500)   // laisse à une éventuelle émission d'un collecteur resté vivant le temps d'arriver

        assertEquals("Collecteurs actifs", 1, actifs.get())
        assertEquals(
            "Seul le dernier collecteur doit publier : $publieurs",
            setOf(2), publieurs.toSet()
        )
        assertEquals(s, surThreadPrincipal { vm.session.value }?.idSession)
    }

    // ---- Outils ----

    private fun attendreQue(quoi: String, delaiMs: Long = 5_000, condition: () -> Boolean) {
        val limite = System.currentTimeMillis() + delaiMs
        while (!condition()) {
            if (System.currentTimeMillis() > limite) {
                throw AssertionError(
                    "Jamais vu : $quoi (lancés ${lances.get()}, actifs ${actifs.get()})"
                )
            }
            Thread.sleep(20)
        }
    }

    private fun article(code: String) = ArticleEntity(
        codeProduit = code,
        nomProduit = "Article $code",
        dateImport = "2026-09-01T00:00:00"
    )

    private fun session(): Long = runBlocking {
        db.sessionDao().insert(
            SessionEntity(
                typeOperation = TypeOperation.INVENTAIRE,
                dateHeureDebut = "2026-09-10T08:00:00",
                statut = StatutSession.CLOTUREE
            )
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
