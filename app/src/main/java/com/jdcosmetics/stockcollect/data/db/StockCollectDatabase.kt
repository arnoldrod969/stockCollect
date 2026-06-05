package com.jdcosmetics.stockcollect.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jdcosmetics.stockcollect.data.db.dao.*
import com.jdcosmetics.stockcollect.data.db.entity.*

/**
 * Base de données Room — StockCollect Mobile
 *
 * Version 1 : schéma initial avec 5 tables.
 * Incrémenter la version + ajouter une migration à chaque changement de schéma.
 */
@Database(
    entities = [
        ArticleEntity::class,
        ArtCodebarreEntity::class,
        SessionEntity::class,
        LigneCollecteEntity::class,
        ExportEntity::class
    ],
    version = 1,
    exportSchema = true   // Exporte le schéma JSON pour audit (dans app/schemas/)
)
abstract class StockCollectDatabase : RoomDatabase() {

    abstract fun articleDao(): ArticleDao
    abstract fun artCodebarreDao(): ArtCodebarreDao
    abstract fun sessionDao(): SessionDao
    abstract fun ligneCollecteDao(): LigneCollecteDao
    abstract fun exportDao(): ExportDao
}
