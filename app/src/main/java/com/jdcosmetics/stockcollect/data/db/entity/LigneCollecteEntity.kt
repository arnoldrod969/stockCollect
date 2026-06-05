package com.jdcosmetics.stockcollect.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Table : lignes_collecte
 * Détail des articles saisis dans chaque session.
 *
 * nom_produit_snap : copie défensive du nom au moment de la saisie.
 * Protège l'historique en cas de réimport du catalogue.
 */
@Entity(
    tableName = "lignes_collecte",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id_session"],
            childColumns = ["id_session"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["code_produit"],
            childColumns = ["code_produit"],
            onDelete = ForeignKey.RESTRICT  // Ne pas supprimer un article si une ligne le référence
        )
    ],
    indices = [
        Index(value = ["id_session"]),
        Index(value = ["code_produit"])
    ]
)
data class LigneCollecteEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id_ligne")
    val idLigne: Long = 0,

    @ColumnInfo(name = "id_session")
    val idSession: Long,

    @ColumnInfo(name = "code_produit")
    val codeProduit: String,

    // Code-barres utilisé lors du scan — null si ajout par recherche textuelle
    @ColumnInfo(name = "code_barre_scanne")
    val codeBarreScanne: String? = null,

    // Snapshot du nom au moment de la saisie
    @ColumnInfo(name = "nom_produit_snap")
    val nomProduitSnap: String,

    // Doit être >= 0 (0 accepté pour inventaire)
    @ColumnInfo(name = "quantite")
    val quantite: Double,

    @ColumnInfo(name = "date_saisie")
    val dateSaisie: String
)
