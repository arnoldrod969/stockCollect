package com.jdcosmetics.stockcollect.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.Date
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * TASK-14 : le format ISO stocké en base et envoyé à Nirgescom (`date_heure_cloture`), et le nom
 * du fichier d'export, sortent en chiffres latins quelle que soit la locale de la tablette.
 */
class DateUtilsTest {

    private lateinit var localeInitiale: Locale

    @Before
    fun poserLocaleArabe() {
        localeInitiale = Locale.getDefault()
        // Arabe d'Égypte : chiffres arabo-indiens (٠١٢…) par défaut.
        Locale.setDefault(LOCALE_AR)
    }

    @After
    fun restaurerLocale() = Locale.setDefault(localeInitiale)

    @Test
    fun `la locale de test produit bien des chiffres non latins avec l'ancien formateur`() {
        // Témoin : sans lui, un JVM qui ne localise pas les chiffres rendrait le test suivant
        // vide de sens. L'ancien code faisait exactement ceci.
        val ancien = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
        assumeFalse("Ce JVM ne localise pas les chiffres en ar-EG", ISO.matches(ancien))
        assertTrue(ancien.any { it in '٠'..'٩' })
    }

    @Test
    fun `nowIso reste en chiffres latins sous une locale arabe`() {
        val iso = DateUtils.nowIso()
        assertTrue("Format ISO attendu, obtenu « $iso »", ISO.matches(iso))
    }

    @Test
    fun `le format ISO produit ne change pas`() {
        assertEquals("2026-05-23T10:14:00", DateUtils.toIso(LocalDateTime.of(2026, 5, 23, 10, 14, 0)))
        assertEquals("2026-01-02T03:04:05", DateUtils.toIso(LocalDateTime.of(2026, 1, 2, 3, 4, 5)))
    }

    @Test
    fun `le nom du fichier d'export ne change pas`() {
        assertEquals(
            "STOCK_23052026_1014.csv",
            DateUtils.toFileName(LocalDateTime.of(2026, 5, 23, 10, 14, 59))
        )
        val nom = DateUtils.toFileName()
        assertTrue("Nom de fichier inattendu : « $nom »", Regex("STOCK_\\d{8}_\\d{4}\\.csv").matches(nom))
        assertTrue(nom.all { it.code < 128 })
    }

    @Test
    fun `une date stockee se relit pour l'affichage`() {
        assertEquals("23/05/2026 10:14", DateUtils.toDisplay("2026-05-23T10:14:00"))
        assertEquals("23/05/2026", DateUtils.toDisplayDate("2026-05-23T10:14:00"))
        // Comme l'ancien SimpleDateFormat.parse, une suite après les secondes est ignorée.
        assertEquals("23/05/2026 10:14", DateUtils.toDisplay("2026-05-23T10:14:00.123"))
        // Relire ce que nowIso vient d'écrire, sous la même locale, doit marcher.
        assertTrue(Regex("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}").matches(DateUtils.toDisplay(DateUtils.nowIso())))
    }

    @Test
    fun `une date illisible est affichee telle quelle`() {
        assertEquals("", DateUtils.toDisplay(""))
        assertEquals("pas une date", DateUtils.toDisplay("pas une date"))
        assertEquals("2026-13-45T10:14:00", DateUtils.toDisplayDate("2026-13-45T10:14:00"))
    }

    @Test
    fun `formatage et relecture concurrents ne se corrompent pas`() {
        // Un SimpleDateFormat partagé sort des dates mêlées sous ce régime.
        val pool = Executors.newFixedThreadPool(8)
        try {
            val taches = (0 until 2_000).map { i ->
                Callable {
                    val date = LocalDateTime.of(2000 + i % 50, 1 + i % 12, 1 + i % 28, i % 24, i % 60, i % 60)
                    val iso = DateUtils.toIso(date)
                    val attendu = "%02d/%02d/%04d %02d:%02d".format(
                        Locale.US, date.dayOfMonth, date.monthValue, date.year, date.hour, date.minute
                    )
                    Triple(iso, DateUtils.toDisplay(iso), attendu) to date
                }
            }
            pool.invokeAll(taches).forEach { f ->
                val (resultat, date) = f.get()
                val (iso, affiche, attendu) = resultat
                assertTrue(ISO.matches(iso))
                assertEquals(date, LocalDateTime.parse(iso))
                assertEquals(attendu, affiche)
            }
        } finally {
            pool.shutdown()
            pool.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private companion object {
        val LOCALE_AR: Locale = Locale.forLanguageTag("ar-EG")
        val ISO = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}")
    }
}
