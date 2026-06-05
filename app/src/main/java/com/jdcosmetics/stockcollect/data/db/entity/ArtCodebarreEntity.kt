package com.jdcosmetics.stockcollect.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Table : art_codebarre
 * Correspondance codes-barres secondaires → articles (266 entrées).
 * Importé depuis art_codebarre_csv.xls
 *
 * Permet de gérer les articles ayant plusieurs codes-barres
 * (emballages différents, lots, variantes).
 */
@Entity(
    tableName = "art_codebarre",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["code_produit"],
            childColumns = ["code_produit"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["code_produit"]),  // Optimise la recherche inverse
        Index(value = ["code_barre"], unique = true)
    ]
)
data class ArtCodebarreEntity(

    @PrimaryKey
    @ColumnInfo(name = "code_barre")
    val codeBarre: String,

    @ColumnInfo(name = "code_produit")
    val codeProduit: String,

    @ColumnInfo(name = "date_import")
    val dateImport: String
)
