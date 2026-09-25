package com.jdcosmetics.stockcollect.util

import com.jdcosmetics.stockcollect.domain.service.ValidationEnvoi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Quantités (TASK-12) : ce qui est stocké doit rester envoyable à Nirgescom, et l'écran, le CSV
 * et l'API doivent porter la même valeur.
 */
class FormatQuantiteTest {

    @Test
    fun `les sommes flottantes sont ramenees a leur valeur decimale`() {
        assertEquals(0.3, FormatUtils.normaliserQuantite(0.1 + 0.2), 0.0)
        assertEquals(3.3, FormatUtils.normaliserQuantite(1.1 + 2.2), 0.0)
        assertEquals(1.3, FormatUtils.normaliserQuantite(2.3 - 1), 0.0)
    }

    @Test
    fun `une quantite normalisee passe la validation d'envoi`() {
        // Sans normalisation, ces trois sommes étaient refusées (« plus de 3 décimales »).
        listOf(0.1 + 0.2, 1.1 + 2.2, 2.3 - 1, 1.2345, 999999.9994).forEach {
            assertNull("$it", ValidationEnvoi.problemeQuantite(FormatUtils.normaliserQuantite(it)))
        }
    }

    @Test
    fun `au-dela de 3 decimales on arrondit au plus proche`() {
        assertEquals(1.235, FormatUtils.normaliserQuantite(1.2345), 0.0)
        assertEquals(1.001, FormatUtils.normaliserQuantite(1.0005), 0.0)   // pas 1.000 : arrondi sur la forme décimale
        assertEquals(2.0, FormatUtils.normaliserQuantite(1.9999), 0.0)
    }

    @Test
    fun `les valeurs deja propres ne bougent pas`() {
        listOf(0.0, 1.0, 12.0, 1.25, 0.125, 999999.999).forEach {
            assertEquals(it, FormatUtils.normaliserQuantite(it), 0.0)
        }
    }

    @Test
    fun `l'affichage garde les entiers a une decimale, comme avant`() {
        assertEquals("0.0", FormatUtils.formatQuantite(0.0))
        assertEquals("12.0", FormatUtils.formatQuantite(12.0))
        assertEquals("1000000.0", FormatUtils.formatQuantite(1_000_000.0))   // jamais « 1.0E6 »
    }

    @Test
    fun `l'affichage montre jusqu'a 3 decimales, comme ce qui part chez Nirgescom`() {
        assertEquals("1.25", FormatUtils.formatQuantite(1.25))
        assertEquals("0.125", FormatUtils.formatQuantite(0.125))
        assertEquals("2.5", FormatUtils.formatQuantite(2.5))
    }

    @Test
    fun `une valeur ancienne non normalisee s'affiche proprement`() {
        // Sessions d'avant la correction : la base peut encore porter la dérive.
        assertEquals("1.3", FormatUtils.formatQuantite(2.3 - 1))
        assertEquals("3.3", FormatUtils.formatQuantite(1.1 + 2.2))
    }
}
