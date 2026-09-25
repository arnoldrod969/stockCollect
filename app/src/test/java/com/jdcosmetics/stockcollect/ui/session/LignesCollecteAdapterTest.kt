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
        assertEquals(25.0, quantiteAEcrire("25", 1.0)!!, 0.0)
        assertEquals(2.5, quantiteAEcrire(" 2.5 ", 1.0)!!, 0.0)
    }

    @Test
    fun `zero est une quantite valide en inventaire`() {
        assertEquals(0.0, quantiteAEcrire("0", 4.0)!!, 0.0)
    }

    @Test
    fun `le texte affiche tel quel n ecrit rien`() {
        assertNull(quantiteAEcrire("13.0", 13.0))
    }

    @Test
    fun `le simple passage du focus ne reecrit pas une quantite arrondie a l affichage`() {
        // 1.25 s'affiche « 1.3 » : relire ce texte ne doit pas remplacer la valeur en base.
        assertNull(quantiteAEcrire("1.3", 1.25))
    }

    @Test
    fun `une saisie illisible ou intermediaire n ecrit rien`() {
        assertNull(quantiteAEcrire("", 3.0))
        assertNull(quantiteAEcrire(null, 3.0))
        assertNull(quantiteAEcrire("abc", 3.0))
        assertNull(quantiteAEcrire("2,5", 3.0))   // virgule décimale : non lue, jamais transformée en 0
    }

    @Test
    fun `une quantite negative est refusee`() {
        assertNull(quantiteAEcrire("-1", 3.0))
    }
}
