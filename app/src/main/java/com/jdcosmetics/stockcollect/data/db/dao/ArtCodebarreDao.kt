package com.jdcosmetics.stockcollect.data.db.dao

import androidx.room.*
import com.jdcosmetics.stockcollect.data.db.entity.ArtCodebarreEntity

@Dao
interface ArtCodebarreDao {

    /**
     * Étape 2a de la résolution :
     * Chercher un code-barres secondaire dans la table de correspondance.
     * Retourne le code_produit associé.
     */
    @Query("SELECT * FROM art_codebarre WHERE code_barre = :codeBarre LIMIT 1")
    suspend fun findByCodeBarre(codeBarre: String): ArtCodebarreEntity?

    /**
     * Toute la table (quelques centaines de lignes). Sert à l'analyse du catalogue : un code
     * principal entrant déjà rattaché à un autre article ici en sera retiré (le catalogue gagne).
     */
    @Query("SELECT * FROM art_codebarre")
    suspend fun getAll(): List<ArtCodebarreEntity>

    /** Nombre d'entrées dans la table correspondance */
    @Query("SELECT COUNT(*) FROM art_codebarre")
    suspend fun count(): Int

    /** Insert ou remplace (lors de l'import CSV) */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entries: List<ArtCodebarreEntity>)

    /**
     * Retire des correspondances précises. Appelé par l'import du catalogue quand un code-barre
     * devient le code principal d'un autre article : le catalogue l'emporte.
     */
    @Query("DELETE FROM art_codebarre WHERE code_barre IN (:codesBarres)")
    suspend fun supprimerCodesBarres(codesBarres: List<String>): Int

    /** Supprime toutes les correspondances (avant réimport) */
    @Query("DELETE FROM art_codebarre")
    suspend fun deleteAll()
}
