package com.jdcosmetics.stockcollect.di

import android.content.Context
import com.jdcosmetics.stockcollect.data.db.dao.ArtCodebarreDao
import com.jdcosmetics.stockcollect.data.db.dao.ArticleDao
import com.jdcosmetics.stockcollect.data.db.dao.LigneCollecteDao
import com.jdcosmetics.stockcollect.data.db.dao.SessionDao
import com.jdcosmetics.stockcollect.data.db.dao.ExportDao
import com.jdcosmetics.stockcollect.data.repository.SessionRepository
import com.jdcosmetics.stockcollect.domain.service.BarcodeScanService
import com.jdcosmetics.stockcollect.domain.service.CsvExportService
import com.jdcosmetics.stockcollect.domain.service.CsvImportService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideCsvImportService(
        @ApplicationContext context: Context,
        articleDao: ArticleDao,
        artCodebarreDao: ArtCodebarreDao
    ): CsvImportService = CsvImportService(context, articleDao, artCodebarreDao)

    @Provides
    @Singleton
    fun provideBarcodeScanService(
        articleDao: ArticleDao,
        artCodebarreDao: ArtCodebarreDao
    ): BarcodeScanService = BarcodeScanService(articleDao, artCodebarreDao)

    @Provides
    @Singleton
    fun provideSessionRepository(
        sessionDao: SessionDao,
        ligneCollecteDao: LigneCollecteDao,
        articleDao: ArticleDao
    ): SessionRepository = SessionRepository(sessionDao, ligneCollecteDao, articleDao)

    @Provides
    @Singleton
    fun provideCsvExportService(
        @ApplicationContext context: Context,
        sessionRepository: SessionRepository,
        articleDao: ArticleDao,
        sessionDao: SessionDao,
        exportDao: ExportDao
    ): CsvExportService = CsvExportService(context, sessionRepository, articleDao, sessionDao, exportDao)
}
