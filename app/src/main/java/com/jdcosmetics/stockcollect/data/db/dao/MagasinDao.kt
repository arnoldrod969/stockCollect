package com.jdcosmetics.stockcollect.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.jdcosmetics.stockcollect.data.db.entity.MagasinEntity

@Dao
interface MagasinDao {

    // ---- Lecture ----

    @Query("SELECT * FROM magasins ORDER BY code_magasin")
    suspend fun getAll(): List<MagasinEntity>

    @Query("SELECT * FROM magasins WHERE code_magasin = :codeMagasin")
    suspend fun findByCode(codeMagasin: String): MagasinEntity?

    @Query("SELECT COUNT(*) FROM magasins")
    suspend fun count(): Int

    @Query("SELECT MAX(date_import) FROM magasins")
    suspend fun getLastImportDate(): String?

    // ---- Écriture ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(magasins: List<MagasinEntity>)

    @Query("DELETE FROM magasins")
    suspend fun deleteAll()

    /**
     * Remplace la liste entière, en une transaction.
     *
     * Un remplacement et non une fusion : un dépôt retiré côté Nirgescom doit disparaître de la
     * tablette, sinon il resterait proposable et l'envoi finirait en `403`. La transaction évite
     * la fenêtre pendant laquelle la liste serait vide — c'est justement la condition qui bloque
     * l'écran Paramètres.
     */
    @Transaction
    suspend fun remplacerTout(magasins: List<MagasinEntity>) {
        deleteAll()
        if (magasins.isNotEmpty()) insertOrReplace(magasins)
    }
}
