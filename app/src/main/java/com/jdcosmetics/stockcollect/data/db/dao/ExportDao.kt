package com.jdcosmetics.stockcollect.data.db.dao

import androidx.room.*
import com.jdcosmetics.stockcollect.data.db.entity.ExportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExportDao {

    @Query("SELECT * FROM exports ORDER BY date_export DESC")
    fun getAllExports(): Flow<List<ExportEntity>>

    @Query("SELECT * FROM exports WHERE id_session = :idSession ORDER BY date_export DESC")
    fun getExportsBySession(idSession: Long): Flow<List<ExportEntity>>

    @Insert
    suspend fun insert(export: ExportEntity): Long
}
