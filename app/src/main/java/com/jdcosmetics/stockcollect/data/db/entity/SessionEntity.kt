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
    val nbLignes: Int = 0,

    // ---- Synchronisation WiFi (ajouté en version 2) ----

    /**
     * Identifiant transmis à l'API Nirgescom, qui exige un UUID v4 (contrat §4.2).
     *
     * Nullable, et c'est délibéré : SQLite ne sait pas générer d'UUID, il n'existe donc aucun
     * moyen de renseigner les sessions déjà présentes au moment de la migration. Elles le
     * reçoivent à leur première synchronisation.
     *
     * Une fois écrit, il ne change plus : l'idempotence de `hash_ligne` côté API en dépend,
     * un renvoi après coupure réseau doit produire exactement les mêmes hash.
     */
    @ColumnInfo(name = "uuid_session")
    val uuidSession: String? = null,

    /**
     * Statut de synchronisation, **volontairement distinct de `statut`** : l'export CSV reste un
     * repli disponible à tout moment (contrat §5), donc une session peut être EXPORTEE *et*
     * synchronisée. Fondre les deux dans une seule colonne rendrait l'un des deux invisible.
     */
    @ColumnInfo(name = "statut_sync", defaultValue = "'NON_SYNCHRONISEE'")
    val statutSync: String = StatutSync.NON_SYNCHRONISEE,

    @ColumnInfo(name = "date_derniere_tentative")
    val dateDerniereTentative: String? = null,

    @ColumnInfo(name = "nb_tentatives", defaultValue = "0")
    val nbTentatives: Int = 0,

    @ColumnInfo(name = "message_erreur_sync")
    val messageErreurSync: String? = null
)

object StatutSession {
    const val BROUILLON = "BROUILLON"
    const val CLOTUREE = "CLOTUREE"
    const val EXPORTEE = "EXPORTEE"
}

/**
 * Suit l'envoi vers Nirgescom, indépendamment de [StatutSession].
 *
 * Constantes chaîne et non enum, comme les autres valeurs persistées en base.
 */
object StatutSync {
    const val NON_SYNCHRONISEE = "NON_SYNCHRONISEE"
    const val SYNCHRONISEE = "SYNCHRONISEE"
    const val ECHEC_SYNC = "ECHEC_SYNC"

    fun label(statut: String): String = when (statut) {
        NON_SYNCHRONISEE -> "Non synchronisée"
        SYNCHRONISEE -> "Synchronisée"
        ECHEC_SYNC -> "Échec de synchronisation"
        else -> statut
    }
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
