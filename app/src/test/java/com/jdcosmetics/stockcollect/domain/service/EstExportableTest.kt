package com.jdcosmetics.stockcollect.domain.service

import com.jdcosmetics.stockcollect.data.db.entity.StatutSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TASK-9 : la règle partagée par CsvExportService, l'écran Export, les boutons de l'Historique
 * et du Détail session.
 */
class EstExportableTest {

    @Test
    fun `une session cloturee est exportable`() {
        assertTrue(estExportable(StatutSession.CLOTUREE))
    }

    @Test
    fun `une session deja exportee le reste, pour reecrire un fichier perdu`() {
        assertTrue(estExportable(StatutSession.EXPORTEE))
    }

    @Test
    fun `un brouillon ne l'est jamais`() {
        assertFalse(estExportable(StatutSession.BROUILLON))
    }

    @Test
    fun `un statut inconnu non plus`() {
        assertFalse(estExportable(""))
        assertFalse(estExportable("cloturee"))
    }
}
