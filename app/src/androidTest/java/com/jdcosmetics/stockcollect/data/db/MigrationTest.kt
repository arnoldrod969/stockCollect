package com.jdcosmetics.stockcollect.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Rejoue la migration sur une vraie base SQLite.
 *
 * `runMigrationsAndValidate` compare le schéma obtenu à `app/schemas/2.json` : c'est ce qui
 * attrape un `DEFAULT` de migration qui ne correspondrait pas au `@ColumnInfo(defaultValue = ...)`
 * de l'entité. Sans ce test, la divergence ne se manifesterait qu'au premier lancement sur une
 * tablette déjà en version 1 — c'est-à-dire chez le magasinier, en pleine collecte.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val nomBase = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StockCollectDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration1vers2_conserveLesSessionsExistantes() {
        // Une base en version 1, avec une session déjà collectée.
        helper.createDatabase(nomBase, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO sessions
                    (id_session, type_operation, date_heure_debut, date_heure_cloture,
                     statut, lieu, observations, nb_lignes)
                VALUES (1, 'INVENTAIRE', '2026-09-17T10:00:00', NULL,
                        'BROUILLON', 'NGOYA I', 'collecte du matin', 3)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(nomBase, 2, true, MIGRATION_1_2)

        db.query("SELECT * FROM sessions WHERE id_session = 1").use { curseur ->
            assertTrue("La session de la v1 doit survivre à la migration", curseur.moveToFirst())

            // Les données collectées avant la migration sont intactes.
            assertEquals("INVENTAIRE", curseur.getString(curseur.getColumnIndexOrThrow("type_operation")))
            assertEquals("NGOYA I", curseur.getString(curseur.getColumnIndexOrThrow("lieu")))
            assertEquals("collecte du matin", curseur.getString(curseur.getColumnIndexOrThrow("observations")))
            assertEquals(3, curseur.getInt(curseur.getColumnIndexOrThrow("nb_lignes")))

            // Les colonnes de synchro prennent leurs valeurs par défaut.
            assertEquals(
                "NON_SYNCHRONISEE",
                curseur.getString(curseur.getColumnIndexOrThrow("statut_sync"))
            )
            assertEquals(0, curseur.getInt(curseur.getColumnIndexOrThrow("nb_tentatives")))

            // SQLite ne sait pas générer d'UUID : les sessions d'avant la migration n'en ont pas
            // encore, il sera posé à la première synchronisation.
            assertTrue(curseur.isNull(curseur.getColumnIndexOrThrow("uuid_session")))
            assertTrue(curseur.isNull(curseur.getColumnIndexOrThrow("date_derniere_tentative")))
            assertTrue(curseur.isNull(curseur.getColumnIndexOrThrow("message_erreur_sync")))
        }
    }

    @Test
    fun migration1vers2_baseVide() {
        helper.createDatabase(nomBase, 1).close()
        // Valide le schéma seul : aucune ligne à convertir, mais la structure doit correspondre.
        helper.runMigrationsAndValidate(nomBase, 2, true, MIGRATION_1_2)
    }
}
