package com.jdcosmetics.stockcollect.data.db.dao

import androidx.room.*
import com.jdcosmetics.stockcollect.data.db.entity.ArticleEntity
import kotlinx.coroutines.flow.Flow

/** Couple (code-barre principal, article qui le porte). Voir [ArticleDao.getCodesBarresPrincipaux]. */
data class CodeBarrePrincipal(
    @ColumnInfo(name = "code_barre_principal") val codeBarre: String,
    @ColumnInfo(name = "code_produit") val codeProduit: String
)

@Dao
interface ArticleDao {

    // ---- Lecture ----

    /** Recherche par code-barres principal (Étape 1 de la résolution) */
    @Query("SELECT * FROM articles WHERE code_barre_principal = :codeBarre LIMIT 1")
    suspend fun findByCodeBarre(codeBarre: String): ArticleEntity?

    /** Recherche par code_produit (Étape 2b de la résolution) */
    @Query("SELECT * FROM articles WHERE code_produit = :codeProduit LIMIT 1")
    suspend fun findByCodeProduit(codeProduit: String): ArticleEntity?

    /** Recherche unifiée : nom, code_produit ou code_barre_principal */
    @Query("""
        SELECT * FROM articles
        WHERE nom_produit LIKE '%' || :query || '%'
           OR code_produit LIKE '%' || :query || '%'
           OR code_barre_principal LIKE '%' || :query || '%'
        ORDER BY nom_produit ASC LIMIT 50
    """)
    fun searchAll(query: String): Flow<List<ArticleEntity>>

    @Query("""
        SELECT * FROM articles
        WHERE nom_produit LIKE '%' || :query || '%'
           OR code_produit LIKE '%' || :query || '%'
           OR code_barre_principal LIKE '%' || :query || '%'
        ORDER BY nom_produit ASC LIMIT 50
    """)
    suspend fun searchAllSync(query: String): List<ArticleEntity>

    /** Retourne tous les code_produit existants */
    @Query("SELECT code_produit FROM articles")
    suspend fun getAllCodeProduits(): List<String>

    /**
     * Tous les codes-barres principaux déjà attribués, avec leur article.
     *
     * Sert à faire respecter « un code-barre n'appartient qu'à un seul article » **entre** les
     * tables `articles` et `art_codebarre` : Room ne peut pas l'imposer, aucune contrainte ne
     * relie les deux.
     */
    @Query("SELECT code_barre_principal, code_produit FROM articles WHERE code_barre_principal IS NOT NULL")
    suspend fun getCodesBarresPrincipaux(): List<CodeBarrePrincipal>

    /**
     * Détache un code-barre de l'article qui le porte actuellement.
     *
     * Appelé juste avant d'écrire un import, sur tous les codes-barres du fichier. Sans cela,
     * réattribuer un code-barre d'un article à un autre — correction légitime côté Nirgescom, ou
     * résolution d'un conflit — violerait l'index unique le temps que l'ancien porteur soit mis à
     * jour, et l'ordre des UPDATE déciderait du succès de l'import.
     */
    @Query("UPDATE articles SET code_barre_principal = NULL WHERE code_barre_principal IN (:codesBarres)")
    suspend fun libererCodesBarres(codesBarres: List<String>)

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
