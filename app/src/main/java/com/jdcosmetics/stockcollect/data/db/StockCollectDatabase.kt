package com.jdcosmetics.stockcollect.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jdcosmetics.stockcollect.data.db.dao.*
import com.jdcosmetics.stockcollect.data.db.entity.*

/**
 * Base de données Room — StockCollect Mobile
 *
 * Version 1 : schéma initial avec 5 tables.
 * Version 2 : colonnes de synchronisation WiFi sur `sessions` (cf. [MIGRATION_1_2]).
 *
 * Incrémenter la version + ajouter une migration dans Migrations.kt à chaque changement de
 * schéma, et committer le JSON généré dans app/schemas/.
 */
@Database(
    entities = [
        ArticleEntity::class,
        ArtCodebarreEntity::class,
        SessionEntity::class,
        LigneCollecteEntity::class,
        ExportEntity::class
    ],
    version = 2,
    exportSchema = true   // Exporte le schéma JSON pour audit (dans app/schemas/)
)
abstract class StockCollectDatabase : RoomDatabase() {

    abstract fun articleDao(): ArticleDao
    abstract fun artCodebarreDao(): ArtCodebarreDao
    abstract fun sessionDao(): SessionDao
    abstract fun ligneCollecteDao(): LigneCollecteDao
    abstract fun exportDao(): ExportDao
}
