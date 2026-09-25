package com.jdcosmetics.stockcollect

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.fail
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/*
 * Outils des tests instrumentés de ViewModel.
 *
 * Pas de InstantTaskExecutorRule ni de kotlinx-coroutines-test : ce sont les vrais dispatchers qui
 * tournent — viewModelScope sur le thread principal, Room sur ses propres exécuteurs. C'est ce qui
 * rend visibles les courses entre lectures, et c'est aussi pourquoi on attend au lieu de lire.
 */

/** Exécute [bloc] sur le thread principal et renvoie son résultat. */
fun <T> surThreadPrincipal(bloc: () -> T): T {
    var resultat: Result<T>? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync { resultat = runCatching(bloc) }
    return resultat!!.getOrThrow()
}

/** Attend que la LiveData porte une valeur qui satisfait [condition] ; échoue après [delaiMs]. */
fun <T> LiveData<T>.attendre(delaiMs: Long = 5_000, condition: (T) -> Boolean): T {
    val latch = CountDownLatch(1)
    val trouvee = CopyOnWriteArrayList<Result<T>>()
    val observer = Observer<T> { valeur ->
        if (latch.count > 0 && condition(valeur)) {
            trouvee.add(Result.success(valeur))
            latch.countDown()
        }
    }
    surThreadPrincipal { observeForever(observer) }
    try {
        if (!latch.await(delaiMs, TimeUnit.MILLISECONDS)) {
            fail("Condition jamais remplie en $delaiMs ms. Dernière valeur : ${surThreadPrincipal { value }}")
        }
    } finally {
        surThreadPrincipal { removeObserver(observer) }
    }
    return trouvee.first().getOrThrow()
}

/** Garde toutes les valeurs publiées, pour vérifier après coup qu'aucune n'était fausse. */
class EnregistreurLiveData<T>(private val source: LiveData<T>) {
    val valeurs: MutableList<T> = CopyOnWriteArrayList()
    private val observer = Observer<T> { valeurs.add(it) }

    init { surThreadPrincipal { source.observeForever(observer) } }

    fun arreter() = surThreadPrincipal { source.removeObserver(observer) }
}
