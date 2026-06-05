package com.jdcosmetics.stockcollect.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Table : articles
 * Catalogue principal des produits JD Cosmetics (2 718 articles).
 * Importé depuis catalogue17022025_csv.xls
 */
@Entity(
    tableName = "articles",
    indices = [
        Index(value = ["code_barre_principal"], unique = true),
        Index(value = ["nom_produit"])
    ]
)
data class ArticleEntity(

    @PrimaryKey
    @ColumnInfo(name = "code_produit")
    val codeProduit: String,

    // Code-barres principal — peut être null si absent dans le CSV source
    @ColumnInfo(name = "code_barre_principal")
    val codeBarrePrincipal: String? = null,

    @ColumnInfo(name = "nom_produit")
    val nomProduit: String,

    // Quantité de référence issue du catalogue (à titre indicatif uniquement)
    @ColumnInfo(name = "quantite_ref")
    val quantiteRef: Double = 0.0,

    @ColumnInfo(name = "prix")
    val prix: Double = 0.0,

    // Horodatage ISO 8601 du dernier import
    @ColumnInfo(name = "date_import")
    val dateImport: String
)
