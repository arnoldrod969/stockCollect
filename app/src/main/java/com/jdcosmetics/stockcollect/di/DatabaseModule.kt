package com.jdcosmetics.stockcollect.di

import android.content.Context
import androidx.room.Room
import com.jdcosmetics.stockcollect.data.db.MIGRATIONS
import com.jdcosmetics.stockcollect.data.db.StockCollectDatabase
import com.jdcosmetics.stockcollect.data.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): StockCollectDatabase {
        return Room.databaseBuilder(
            context,
            StockCollectDatabase::class.java,
            "stockcollect.db"
        )
            // Pas de fallbackToDestructiveMigration : il effaçait les sessions collectées sur la
            // tablette à chaque évolution du schéma, sans rien dire. Une migration manquante fait
            // maintenant échouer l'ouverture de la base — bruyant, mais récupérable.
            .addMigrations(*MIGRATIONS)
            .build()
    }

    @Provides
    fun provideArticleDao(db: StockCollectDatabase): ArticleDao = db.articleDao()

    @Provides
    fun provideArtCodebarreDao(db: StockCollectDatabase): ArtCodebarreDao = db.artCodebarreDao()

    @Provides
    fun provideSessionDao(db: StockCollectDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideLigneCollecteDao(db: StockCollectDatabase): LigneCollecteDao = db.ligneCollecteDao()

    @Provides
    fun provideExportDao(db: StockCollectDatabase): ExportDao = db.exportDao()
}
