package com.jdcosmetics.stockcollect.domain.service

data class ImportResult(
    val success: Boolean,
    val nbImportes: Int = 0,
    val nbMisAJour: Int = 0,
    val nbIgnores: Int = 0,
    val nbErreurs: Int = 0,
    val erreurs: List<String> = emptyList(),
    val messageErreur: String? = null,
    /**
     * Import du catalogue seulement : correspondances secondaires retirées parce que le catalogue
     * donne leur code-barre comme code principal à un autre article (le catalogue l'emporte).
     * Leur détail est en tête de [erreurs], faute d'un canal distinct — comme les lignes
     * recollées, elles n'entrent ni dans [nbErreurs] ni dans le seuil des 10 %.
     */
    val nbCorrespondancesRetirees: Int = 0
) {
    val total: Int get() = nbImportes + nbMisAJour + nbIgnores + nbErreurs

    fun toResume(): String {
        return if (success) {
            buildString {
                append("Import terminé.\n")
                append("• $nbImportes nouveaux\n")
                if (nbMisAJour > 0) append("• $nbMisAJour mis à jour\n")
                if (nbIgnores > 0) append("• $nbIgnores écartés\n")
                if (nbCorrespondancesRetirees > 0) {
                    append("• $nbCorrespondancesRetirees codes-barres secondaires retirés : ")
                    append("le catalogue les donne à un autre article\n")
                }
                if (nbErreurs > 0) append("• $nbErreurs lignes refusées, à corriger à la source (fichier ou Nirgescom)")
            }
        } else {
            "Rien n'a été modifié. ${messageErreur.orEmpty()}"
        }
    }
}
