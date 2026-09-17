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
    val totalLignes: Int
) {
    val nbFichesDupliquees: Int get() = conflits.count { it.memeProduit }
    val nbVraisConflits: Int get() = conflits.count { !it.memeProduit }
    val nbArticlesPerdants: Int get() = conflits.sumOf { it.perdants.size }
    val aDesConflits: Boolean get() = conflits.isNotEmpty()

    /** Message du dialogue de choix. Donne le décompte par famille : aucune option n'est bonne
     *  dans les deux cas à la fois, l'utilisateur doit voir de quoi il s'agit. */
    fun resumeConflits(): String = buildString {
        append("$nbArticlesPerdants article(s) revendiquent un code-barre déjà attribué.\n\n")
        if (nbFichesDupliquees > 0) {
            append("• $nbFichesDupliquees fiche(s) en double : même nom de produit, ")
            append("le catalogue Nirgescom contient deux fois le même article.\n")
        }
        if (nbVraisConflits > 0) {
            append("• $nbVraisConflits conflit(s) réel(s) : des produits différents partagent ")
            append("un code-barre. À corriger dans Nirgescom.\n")
        }
    }

    /** Détail ligne à ligne, pour le bouton « Voir le détail ». */
    fun detailConflits(): List<String> = conflits.map { conflit ->
        val nature = if (conflit.memeProduit) "fiche en double" else "CONFLIT RÉEL"
        val perdants = conflit.perdants.joinToString(", ") { "${it.codeProduit} (${it.nomProduit})" }
        "${conflit.codeBarre} [$nature] → gardé par ${conflit.gagnant.codeProduit} " +
            "(${conflit.gagnant.nomProduit}) ; écarté : $perdants"
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
