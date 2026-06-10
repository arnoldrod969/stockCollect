package com.jdcosmetics.stockcollect.data.db.dao

import androidx.room.*
import com.jdcosmetics.stockcollect.data.db.entity.ArticleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {

    // ---- Lecture ----

    /** Recherche par code-barres principal (Étape 1 de la résolution) */
    @Query("SELECT * FROM articles WHERE code_barre_principal = :codeBarre LIMIT 1")
    suspend fun findByCodeBarre(codeBarre: String): ArticleEntity?

    /** Recherche par code_produit (Étape 2b de la résolution) */
    @Query("SELECT * FROM articles WHERE code_produit = :codeProduit LIMIT 1")
    suspend fun findByCodeProduit(codeProduit: String): ArticleEntity?

    /** Recherche textuelle par nom (fallback si scan échoue) */
    @Query("SELECT * FROM articles WHERE nom_produit LIKE '%' || :query || '%' ORDER BY nom_produit ASC LIMIT 50")
    fun searchByNom(query: String): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE nom_produit LIKE '%' || :query || '%' ORDER BY nom_produit ASC LIMIT 50")
    suspend fun searchByNomSync(query: String): List<ArticleEntity>

    /** Retourne tous les code_produit existants */
    @Query("SELECT code_produit FROM articles")
    suspend fun getAllCodeProduits(): List<String>

    /** Nombre total d'articles dans le catalogue */
    @Query("SELECT COUNT(*) FROM articles")
    suspend fun count(): Int

    /** Date du dernier import */
    @Query("SELECT MAX(date_import) FROM articles")
    suspend fun getLastImportDate(): String?

    // ---- Écriture ----

    /** Insert ou remplace (utilisé lors de l'import CSV) */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(articles: List<ArticleEntity>)

    /** Met à jour plusieurs articles */
    @Update
    suspend fun updateArticles(articles: List<ArticleEntity>)

    /** Supprime tous les articles (avant un réimport complet) */
    @Query("DELETE FROM articles")
    suspend fun deleteAll()
}
