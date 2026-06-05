package com.jdcosmetics.stockcollect.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Table : sessions
 * Enregistrement des sessions de collecte de stock.
 */
@Entity(
    tableName = "sessions",
    indices = [Index(value = ["statut"]), Index(value = ["date_heure_debut"])]
)
data class SessionEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id_session")
    val idSession: Long = 0,

    // INVENTAIRE | ENTREE | SORTIE
    @ColumnInfo(name = "type_operation")
    val typeOperation: String,

    @ColumnInfo(name = "date_heure_debut")
    val dateHeureDebut: String,

    // Null si brouillon, renseigné à la clôture
    @ColumnInfo(name = "date_heure_cloture")
    val dateHeureCloture: String? = null,

    // BROUILLON | CLOTUREE | EXPORTEE
    @ColumnInfo(name = "statut")
    val statut: String = StatutSession.BROUILLON,

    @ColumnInfo(name = "lieu")
    val lieu: String? = null,

    @ColumnInfo(name = "observations")
    val observations: String? = null,

    // Compteur dénormalisé mis à jour à chaque ajout/suppression de ligne
    @ColumnInfo(name = "nb_lignes")
    val nbLignes: Int = 0
)

object StatutSession {
    const val BROUILLON = "BROUILLON"
    const val CLOTUREE = "CLOTUREE"
    const val EXPORTEE = "EXPORTEE"
}

object TypeOperation {
    const val INVENTAIRE = "INVENTAIRE"
    const val ENTREE = "ENTREE"
    const val SORTIE = "SORTIE"

    fun label(type: String): String = when (type) {
        INVENTAIRE -> "Inventaire"
        ENTREE -> "Entrée de stock"
        SORTIE -> "Sortie de stock"
        else -> type
    }
}
