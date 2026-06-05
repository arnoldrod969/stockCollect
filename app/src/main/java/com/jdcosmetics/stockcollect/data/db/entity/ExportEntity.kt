package com.jdcosmetics.stockcollect.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Table : exports
 * Historique des fichiers CSV générés.
 */
@Entity(
    tableName = "exports",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id_session"],
            childColumns = ["id_session"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["id_session"])]
)
data class ExportEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id_export")
    val idExport: Long = 0,

    @ColumnInfo(name = "id_session")
    val idSession: Long,

    // Ex. STOCK_23052026_1014.csv
    @ColumnInfo(name = "nom_fichier")
    val nomFichier: String,

    @ColumnInfo(name = "date_export")
    val dateExport: String,

    @ColumnInfo(name = "nb_lignes_exportees")
    val nbLignesExportees: Int,

    // URI Android SAF — peut être null si non tracé
    @ColumnInfo(name = "chemin_fichier")
    val cheminFichier: String? = null
)
