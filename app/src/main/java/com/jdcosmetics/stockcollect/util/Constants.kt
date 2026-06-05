package com.jdcosmetics.stockcollect.util

object Constants {

    // Import CSV
    const val CSV_SEPARATEUR_VIRGULE = ','
    const val CSV_SEPARATEUR_POINTVIRGULE = ';'
    const val CSV_ENCODAGE_UTF8 = "UTF-8"
    const val CSV_ENCODAGE_LATIN1 = "ISO-8859-1"

    // Import catalogue — indices colonnes (0-based, pas d'en-tête)
    const val COL_CATALOGUE_CODE_PRODUIT = 0
    const val COL_CATALOGUE_CODE_BARRE = 1
    // COL 2 ignorée
    const val COL_CATALOGUE_NOM_PRODUIT = 3
    const val COL_CATALOGUE_QUANTITE = 4
    const val COL_CATALOGUE_PRIX = 5

    // Import correspondance — indices colonnes (0-based, pas d'en-tête)
    const val COL_CB_CODE_BARRE = 0
    const val COL_CB_CODE_PRODUIT = 1

    // Export CSV — noms de colonnes (avec en-tête)
    const val EXPORT_COL_CODE_BARRE = "Code Barre"
    const val EXPORT_COL_CODE_PRODUIT = "Code Produit"
    const val EXPORT_COL_NOM_PRODUIT = "Nom Produit"
    const val EXPORT_COL_QUANTITE = "Quantité"

    // BOM UTF-8 pour compatibilité Excel Windows
    const val UTF8_BOM = "\uFEFF"

    // Seuil d'erreur import : si > 10% des lignes en erreur → rollback
    const val IMPORT_SEUIL_ERREUR_POURCENTAGE = 0.10

    // Scan code-barres
    const val SCAN_DEBOUNCE_MS = 2000L  // Anti-doublon 2 secondes

    // Taille max observations
    const val OBSERVATIONS_MAX_CHARS = 500
}
