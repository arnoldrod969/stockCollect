package com.jdcosmetics.stockcollect.domain.service

import com.jdcosmetics.stockcollect.data.db.entity.ArticleEntity

/**
 * Règle de gestion : un article peut porter plusieurs codes-barres, mais un code-barre
 * n'appartient qu'à un seul article.
 *
 * Le schéma l'impose déjà (index unique sur articles.code_barre_principal, code_barre en clé
 * primaire de art_codebarre). Un fichier source qui la viole faisait donc échouer l'import en
 * bloc ; on la fait désormais respecter à l'analyse, en laissant l'utilisateur arbitrer.
 *
 * Le premier code_produit rencontré garde le code-barre — ordre du fichier, pas de règle plus
 * savante : le choix appartient au catalogue source, pas à l'app.
 */
data class ConflitCodeBarre(
    val codeBarre: String,
    val gagnant: ArticleEntity,
    val perdants: List<ArticleEntity>
) {
    /**
     * Même libellé partout : la fiche article est dupliquée dans Nirgescom, le code-barre ne
     * désigne bien qu'un seul produit réel. Libellés différents : vraie erreur de saisie, deux
     * produits distincts revendiquent le même code-barre.
     *
     * La distinction change ce que coûte chaque option à l'utilisateur, d'où son affichage.
     */
    val memeProduit: Boolean
        get() = perdants.all { it.nomProduit.equals(gagnant.nomProduit, ignoreCase = true) }
}

/**
 * Un code-barre secondaire (`art_codebarre`) retiré à l'import du catalogue, parce que le catalogue
 * le donne comme code principal à un **autre** article.
 *
 * Règle de priorité (TASK-11) : **le catalogue l'emporte sur la correspondance**. Il n'y a donc rien
 * à choisir ; l'utilisateur est seulement informé de ce qui a été retiré, et de quel article.
 * Sans ce retrait, le code désignerait deux articles et le rattachement secondaire serait mort,
 * `BarcodeScanService.resoudre()` interrogeant `articles` en premier.
 */
data class CorrespondanceRetiree(
    val codeBarre: String,
    /** L'article auquel `art_codebarre` rattachait le code, et qui le perd. */
    val codeProduitRetire: String,
    /** L'article qui porte désormais le code comme code principal. */
    val codeProduitCatalogue: String
) {
    fun libelle(): String =
        "Code-barres $codeBarre retiré de l'article $codeProduitRetire : le catalogue " +
            "le donne à $codeProduitCatalogue"
}

/**
 * Photographie du fichier avant écriture. **Rien n'a encore touché la base** : c'est ce qui permet
 * de proposer un choix à l'utilisateur, et accessoirement ce qui rend l'import atomique — il n'y a
 * plus de fenêtre où la moitié du catalogue serait chargée.
 */
data class AnalyseCatalogue(
    /** Articles valides, code-barre encore intact. La résolution s'applique à l'écriture. */
    val articles: List<ArticleEntity>,
    val conflits: List<ConflitCodeBarre>,
    /** Lignes dont les colonnes ont été recollées (virgule non échappée dans le nom). */
    val lignesRecollees: List<String>,
    val erreurs: List<String>,
    val totalLignes: Int,
    /**
     * Correspondances secondaires que l'écriture retirera (le catalogue l'emporte). Calculées ici
     * pour être annoncées ; `appliquerCatalogue` les recalcule dans sa transaction.
     */
    val correspondancesRetirees: List<CorrespondanceRetiree> = emptyList()
) {
    val nbFichesDupliquees: Int get() = conflits.count { it.memeProduit }
    val nbVraisConflits: Int get() = conflits.count { !it.memeProduit }
    val nbArticlesPerdants: Int get() = conflits.sumOf { it.perdants.size }
    val aDesConflits: Boolean get() = conflits.isNotEmpty()

    /** Message du dialogue de choix. Donne le décompte par famille : aucune option n'est bonne
     *  dans les deux cas à la fois, l'utilisateur doit voir de quoi il s'agit. */
    fun resumeConflits(): String = buildString {
        append("$nbArticlesPerdants articles portent un code-barre déjà pris par un autre ")
        append("article du fichier.\n\n")
        if (nbFichesDupliquees > 0) {
            append("• $nbFichesDupliquees fiches en double : même nom d'article, ")
            append("le catalogue Nirgescom contient deux fois le même produit.\n")
        }
        if (nbVraisConflits > 0) {
            append("• $nbVraisConflits conflits réels : deux articles différents partagent ")
            append("un code-barre. À corriger dans Nirgescom.\n")
        }
    }

    /**
     * Paragraphe d'information pour le dialogue d'arbitrage, `null` s'il n'y a rien à retirer.
     * Ce n'est pas un choix : la règle est fixée, le catalogue l'emporte.
     */
    fun resumeCorrespondancesRetirees(): String? {
        if (correspondancesRetirees.isEmpty()) return null
        val n = correspondancesRetirees.size
        return if (n == 1) {
            "Par ailleurs, 1 code-barres secondaire sera retiré, le catalogue le donnant à un " +
                "autre article :\n${correspondancesRetirees.single().libelle()}."
        } else {
            "Par ailleurs, $n codes-barres secondaires seront retirés, le catalogue les donnant " +
                "à d'autres articles (détail dans le rapport d'import)."
        }
    }

    /**
     * Les seuls conflits que l'utilisateur a besoin de lire pour trancher : deux produits
     * réellement différents qui se disputent un code-barre. Les fiches en double sont comptées
     * dans le résumé mais pas détaillées — elles n'influencent pas le choix, et les afficher
     * toutes allonge le dialogue au point d'en chasser les boutons.
     */
    fun detailDecisif(): List<String> = detailConflits()
        .filterIndexed { index, _ -> !conflits[index].memeProduit }

    /** Détail ligne à ligne, pour le rapport affiché une fois l'import terminé. */
    fun detailConflits(): List<String> = conflits.map { conflit ->
        val nature = if (conflit.memeProduit) "fiche en double" else "conflit réel"
        val perdants = conflit.perdants.joinToString(", ") { "${it.codeProduit} (${it.nomProduit})" }
        "${conflit.codeBarre} — $nature : gardé par ${conflit.gagnant.codeProduit} " +
            "(${conflit.gagnant.nomProduit}), écarté ${perdants}"
    }
}

/**
 * Ce que l'utilisateur décide face aux conflits. Choix transitoire, jamais persisté — d'où un enum
 * ici, là où les statuts stockés en base restent des constantes chaîne.
 */
enum class ResolutionConflit {
    /** Les perdants entrent sans code-barre : trouvables par recherche, comptables, non scannables. */
    IMPORTER_SANS_CODE_BARRE,

    /** Les perdants n'entrent pas du tout. Le reste du catalogue passe. */
    IGNORER_ARTICLES
}
