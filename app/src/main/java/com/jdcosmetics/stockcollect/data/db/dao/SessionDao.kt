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

    /**
     * Le brouillon le plus récent **du type demandé**. [getLastBrouillon] seul ne suffit pas à la
     * création : un brouillon plus récent d'un autre type masquerait celui qu'on veut reprendre.
     */
    @Query("""
        SELECT * FROM sessions
        WHERE statut = 'BROUILLON' AND type_operation = :typeOperation
        ORDER BY date_heure_debut DESC LIMIT 1
    """)
    suspend fun getLastBrouillonDuType(typeOperation: String): SessionEntity?

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

    // ---- Synchronisation ----

    /**
     * Pose un UUID sur une session qui n'en a pas.
     *
     * `WHERE uuid_session IS NULL` : une fois écrit, l'identifiant ne doit plus bouger, l'API
     * calcule `hash_ligne` à partir de lui. Ne concerne que les sessions créées avant la
     * migration 1→2, SQLite n'ayant pas pu leur en générer un.
     */
    @Query("UPDATE sessions SET uuid_session = :uuid WHERE id_session = :id AND uuid_session IS NULL")
    suspend fun poserUuidSiAbsent(id: Long, uuid: String): Int

    /**
     * Le message d'erreur est effacé : le garder ferait lire un échec révolu sous une session
     * désormais synchronisée. `nb_tentatives` est en revanche conservé, il documente l'effort.
     */
    @Query("""
        UPDATE sessions
        SET statut_sync = 'SYNCHRONISEE', date_derniere_tentative = :date,
            nb_tentatives = nb_tentatives + 1, message_erreur_sync = NULL
        WHERE id_session = :id
    """)
    suspend fun marquerSynchronisee(id: Long, date: String): Int

    @Query("""
        UPDATE sessions
        SET statut_sync = 'ECHEC_SYNC', date_derniere_tentative = :date,
            nb_tentatives = nb_tentatives + 1, message_erreur_sync = :message
        WHERE id_session = :id
    """)
    suspend fun marquerEchecSync(id: Long, date: String, message: String): Int
}
