package com.jdcosmetics.stockcollect.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrations de la base StockCollect.
 *
 * Jusqu'ici la base vivait avec `fallbackToDestructiveMigration()` : toute évolution d'entité
 * effaçait les sessions collectées sur la tablette. Ce fallback est retiré — une migration
 * manquante fait désormais échouer l'ouverture de la base, ce qui est très préférable à une
 * perte de données silencieuse au retour d'un magasinier.
 *
 * Règle : toute modification d'entité s'accompagne d'une migration ici et du schéma JSON
 * correspondant dans `app/schemas/`, committé.
 */

/**
 * 1 → 2 : colonnes de synchronisation WiFi sur `sessions` (contrat API §5).
 *
 * Les cinq colonnes sont ajoutées en une seule migration plutôt qu'au fil de l'eau : le parc de
 * tablettes se met à jour rarement, autant ne le faire migrer qu'une fois.
 *
 * Les colonnes NOT NULL portent un DEFAULT parce que SQLite l'exige pour un ALTER TABLE ADD
 * COLUMN sur une table qui contient déjà des lignes. Ces DEFAULT doivent correspondre au caractère
 * près aux `@ColumnInfo(defaultValue = ...)` de SessionEntity, sinon Room rejette la base au
 * démarrage en signalant un schéma divergent.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Nullable : SQLite ne sait pas générer d'UUID, les sessions déjà en base le recevront
        // à leur première synchronisation.
        db.execSQL("ALTER TABLE sessions ADD COLUMN uuid_session TEXT")

        db.execSQL(
            "ALTER TABLE sessions ADD COLUMN statut_sync TEXT NOT NULL DEFAULT 'NON_SYNCHRONISEE'"
        )
        db.execSQL("ALTER TABLE sessions ADD COLUMN date_derniere_tentative TEXT")
        db.execSQL("ALTER TABLE sessions ADD COLUMN nb_tentatives INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sessions ADD COLUMN message_erreur_sync TEXT")
    }
}

/** Toutes les migrations, dans l'ordre. À passer à `Room.databaseBuilder().addMigrations(...)`. */
val MIGRATIONS = arrayOf(MIGRATION_1_2)
