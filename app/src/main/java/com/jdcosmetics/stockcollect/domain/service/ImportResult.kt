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
                append("Import termin\u00e9.\n")
                append("\u2022 $nbImportes nouveaux\n")
                if (nbMisAJour > 0) append("\u2022 $nbMisAJour mis \u00e0 jour\n")
                if (nbIgnores > 0) append("\u2022 $nbIgnores \u00e9cart\u00e9s\n")
                if (nbErreurs > 0) append("\u2022 $nbErreurs lignes refus\u00e9es, \u00e0 corriger dans le fichier")
            }
        } else {
            "Rien n'a \u00e9t\u00e9 modifi\u00e9. ${messageErreur.orEmpty()}"
        }
    }
}
