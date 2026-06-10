package com.jdcosmetics.stockcollect.data.db.dao

import androidx.room.*
import com.jdcosmetics.stockcollect.data.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    // ---- Lecture ----

    @Query("SELECT * FROM sessions ORDER BY date_heure_debut DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE statut = :statut ORDER BY date_heure_debut DESC")
    fun getSessionsByStatut(statut: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id_session = :id LIMIT 1")
    suspend fun getById(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE statut = 'BROUILLON' ORDER BY date_heure_debut DESC LIMIT 1")
    suspend fun getLastBrouillon(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE statut = 'CLOTUREE' ORDER BY date_heure_debut DESC LIMIT 1")
    suspend fun getMostRecentCloturee(): SessionEntity?

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun count(): Int

    // ---- Écriture ----

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    /**
     * Clôture une session : met à jour statut + date de clôture.
     * La clôture est irréversible par design — pas de retour à BROUILLON possible.
     */
    @Query("""
        UPDATE sessions 
        SET statut = 'CLOTUREE', date_heure_cloture = :dateCloture
        WHERE id_session = :id AND statut = 'BROUILLON'
    """)
    suspend fun cloturer(id: Long, dateCloture: String): Int

    /** Marque une session comme exportée */
    @Query("UPDATE sessions SET statut = 'EXPORTEE' WHERE id_session = :id AND statut = 'CLOTUREE'")
    suspend fun marquerExportee(id: Long): Int

    /** Met à jour le compteur de lignes */
    @Query("UPDATE sessions SET nb_lignes = :nbLignes WHERE id_session = :id")
    suspend fun updateNbLignes(id: Long, nbLignes: Int)
}
