package com.jdcosmetics.stockcollect.di

import android.content.Context
import androidx.room.Room
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
            .fallbackToDestructiveMigration() // À remplacer par des migrations explicites en prod
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
