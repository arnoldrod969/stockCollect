package com.jdcosmetics.stockcollect.data.db.dao

import androidx.room.*
import com.jdcosmetics.stockcollect.data.db.entity.LigneCollecteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LigneCollecteDao {

    @Query("SELECT * FROM lignes_collecte WHERE id_session = :idSession ORDER BY date_saisie ASC")
    fun getLignesBySession(idSession: Long): Flow<List<LigneCollecteEntity>>

    @Query("SELECT * FROM lignes_collecte WHERE id_session = :idSession ORDER BY date_saisie ASC")
    suspend fun getLignesBySessionSync(idSession: Long): List<LigneCollecteEntity>

    @Query("SELECT COUNT(*) FROM lignes_collecte WHERE id_session = :idSession")
    suspend fun countBySession(idSession: Long): Int

    @Insert
    suspend fun insert(ligne: LigneCollecteEntity): Long

    @Update
    suspend fun update(ligne: LigneCollecteEntity)

    @Delete
    suspend fun delete(ligne: LigneCollecteEntity)

    @Query("DELETE FROM lignes_collecte WHERE id_ligne = :idLigne")
    suspend fun deleteById(idLigne: Long)

    @Query("DELETE FROM lignes_collecte WHERE id_session = :idSession")
    suspend fun deleteBySession(idSession: Long)
}
