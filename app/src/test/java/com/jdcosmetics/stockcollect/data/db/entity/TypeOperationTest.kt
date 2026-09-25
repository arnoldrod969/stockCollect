package com.jdcosmetics.stockcollect.data.db.entity

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TASK-5 #6 : ENTREE et SORTIE ne sont plus créables, mais des sessions de ces types peuvent
 * exister sur les tablettes déployées. Leurs valeurs en base et leurs libellés doivent rester.
 */
class TypeOperationTest {

    @Test
    fun `les valeurs persistees ne changent pas`() {
        // Ce sont les chaînes écrites dans sessions.type_operation : les renommer rendrait les
        // sessions existantes introuvables par les puces de l'Historique.
        assertEquals("INVENTAIRE", TypeOperation.INVENTAIRE)
        assertEquals("ENTREE", TypeOperation.ENTREE)
        assertEquals("SORTIE", TypeOperation.SORTIE)
    }

    @Test
    fun `les anciens types gardent leur libelle`() {
        assertEquals("Inventaire", TypeOperation.label(TypeOperation.INVENTAIRE))
        assertEquals("Entrée de stock", TypeOperation.label(TypeOperation.ENTREE))
        assertEquals("Sortie de stock", TypeOperation.label(TypeOperation.SORTIE))
    }

    @Test
    fun `un type inconnu s affiche tel quel plutot que de planter`() {
        assertEquals("TYPE_INCONNU", TypeOperation.label("TYPE_INCONNU"))
    }
}
