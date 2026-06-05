package com.jdcosmetics.stockcollect.domain.service

data class ImportResult(
    val success: Boolean,
    val nbImportes: Int = 0,
    val nbMisAJour: Int = 0,
    val nbIgnores: Int = 0,
    val nbErreurs: Int = 0,
    val erreurs: List<String> = emptyList(),
    val messageErreur: String? = null
) {
    val total: Int get() = nbImportes + nbMisAJour + nbIgnores + nbErreurs

    fun toResume(): String {
        return if (success) {
            buildString {
                append("Import r\u00e9ussi\n")
                append("\u2022 $nbImportes nouveaux articles\n")
                if (nbMisAJour > 0) append("\u2022 $nbMisAJour mis \u00e0 jour\n")
                if (nbIgnores > 0) append("\u2022 $nbIgnores ignor\u00e9s\n")
                if (nbErreurs > 0) append("\u2022 $nbErreurs erreurs (voir d\u00e9tails)")
            }
        } else {
            "Import annul\u00e9 : ${messageErreur ?: "Erreur inconnue"}"
        }
    }
}
