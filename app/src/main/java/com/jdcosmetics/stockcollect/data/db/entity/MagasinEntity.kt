package com.jdcosmetics.stockcollect.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Table : magasins
 *
 * Liste des dépôts, récupérée depuis `GET /magasins` et conservée localement. Les Paramètres
 * n'offrent que ces valeurs au choix : l'API compare le libellé envoyé dans `POST /documents` à
 * celui porté par la clé d'API par égalité stricte, et répond `403` au moindre écart de casse ou
 * d'espace. Une liste supprime cette classe d'erreur, qu'une saisie libre rendait invisible
 * jusqu'à l'envoi — c'est-à-dire après la clôture, qui est irréversible.
 *
 * Mise en cache et non interrogée à la volée : on configure une tablette là où on la déballe, pas
 * forcément à portée du WiFi du magasin.
 */
@Entity(tableName = "magasins")
data class MagasinEntity(

    /** Jeton Nirgescom (`tblsite.siCode`), 10 caractères au plus côté base. */
    @PrimaryKey
    @ColumnInfo(name = "code_magasin")
    val codeMagasin: String,

    /**
     * Libellé affiché, et seule valeur acceptée par `POST /documents`. **Nullable** : l'API en
     * renvoie pour de vrai (`tests/test_magasins.py` le vérifie sur MOKOLO). Un dépôt sans libellé
     * ne peut donc pas être choisi — il n'y aurait rien à envoyer.
     */
    @ColumnInfo(name = "nom_magasin")
    val nomMagasin: String? = null,

    /** Horodatage ISO 8601 de la dernière récupération, comme `articles.date_import`. */
    @ColumnInfo(name = "date_import")
    val dateImport: String
) {
    /** Un dépôt n'est proposable que s'il a un libellé à envoyer. */
    val selectionnable: Boolean
        get() = !nomMagasin.isNullOrBlank()

    /** Ce que l'opérateur lit dans la liste. */
    fun libelle(): String =
        if (selectionnable) "$nomMagasin ($codeMagasin)"
        else "$codeMagasin — sans libellé, non sélectionnable"
}
