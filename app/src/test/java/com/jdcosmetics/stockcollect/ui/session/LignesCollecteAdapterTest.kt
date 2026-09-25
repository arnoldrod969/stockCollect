package com.jdcosmetics.stockcollect.ui.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Décision d'écriture du champ quantité de la liste (TASK-5 #3).
 *
 * Le recyclage lui-même — quelle ligne reçoit l'écriture — ne se teste pas sans RecyclerView
 * attaché à une fenêtre : il est couvert par le scénario émulateur consigné dans TASK-5.
 * Ici, ce qui est écrit, et surtout ce qui ne l'est pas.
 */
class LignesCollecteAdapterTest {

    @Test
    fun `une quantite tapee differente est ecrite`() {
        assertEquals(25.0, quantiteAEcrire("25", 1.0, modifiee = true)!!, 0.0)
        assertEquals(2.5, quantiteAEcrire(" 2.5 ", 1.0, modifiee = true)!!, 0.0)
    }

    @Test
    fun `zero est une quantite valide en inventaire`() {
        assertEquals(0.0, quantiteAEcrire("0", 4.0, modifiee = true)!!, 0.0)
    }

    @Test
    fun `une valeur egale a celle en base n ecrit rien`() {
        assertNull(quantiteAEcrire("13.0", 13.0, modifiee = true))
    }

    @Test
    fun `le simple passage du focus ne reecrit pas une quantite arrondie a l affichage`() {
        // Une valeur ancienne 1.2999999999999998 s'affiche « 1.3 » : relire ce texte sans l'avoir
        // touché ne remplace rien.
        assertNull(quantiteAEcrire("1.3", 2.3 - 1, modifiee = false))
    }

    @Test
    fun `retaper le texte affiche corrige une quantite flottante`() {
        // 2.3 puis « − » donne 1.2999999999999998, affiché « 1.3 » : le retaper doit l'écrire,
        // sinon la ligne reste non envoyable (plus de 3 décimales) après la clôture.
        assertEquals(1.3, quantiteAEcrire("1.3", 2.3 - 1, modifiee = true)!!, 0.0)
    }

    @Test
    fun `une saisie a plus de 3 decimales est ecrite arrondie`() {
        assertEquals(1.235, quantiteAEcrire("1.2345", 2.0, modifiee = true)!!, 0.0)
        // Arrondie, elle égale la valeur en base : rien à écrire.
        assertNull(quantiteAEcrire("1.2345", 1.235, modifiee = true))
    }

    @Test
    fun `une saisie illisible ou intermediaire n ecrit rien`() {
        assertNull(quantiteAEcrire("", 3.0, modifiee = true))
        assertNull(quantiteAEcrire(null, 3.0, modifiee = true))
        assertNull(quantiteAEcrire("abc", 3.0, modifiee = true))
        assertNull(quantiteAEcrire("2,5", 3.0, modifiee = true))   // virgule décimale : non lue, jamais transformée en 0
    }

    @Test
    fun `une quantite negative est refusee`() {
        assertNull(quantiteAEcrire("-1", 3.0, modifiee = true))
    }
}
