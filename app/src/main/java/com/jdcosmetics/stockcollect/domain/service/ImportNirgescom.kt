package com.jdcosmetics.stockcollect.domain.service

import com.jdcosmetics.stockcollect.data.remote.ArticleDistant
import com.jdcosmetics.stockcollect.data.remote.CodeBarreDistant
import com.jdcosmetics.stockcollect.data.remote.ResultatReferentiel

/** Ce qu'il faut faire d'une réponse de `GET /catalog`. */
sealed class SuiteCatalogue {
    /** `304` : la tablette a déjà cette version. Succès, rien à écrire. */
    object DejaAJour : SuiteCatalogue()

    /** Lignes à passer par la même analyse que le CSV. [etag] n'est à retenir qu'après écriture. */
    data class Analyser(val lignes: List<LigneCatalogue>, val etag: String?) : SuiteCatalogue()

    /** Rien n'est écrit ; [message] est destiné tel quel à l'opérateur. */
    data class Echec(val message: String) : SuiteCatalogue()
}

/** Ce qu'il faut faire d'une réponse de `GET /codes-barres`. */
sealed class SuiteCodesBarres {
    object DejaAJour : SuiteCodesBarres()

    /**
     * Liste vide : **rien n'est effacé**. La vue `codebarre_article` est vide sur la base réelle
     * alors que les tablettes portent les codes-barres secondaires venus du CSV ; remplacer
     * aveuglément les ferait disparaître au premier clic. Même règle que `GET /magasins`.
     */
    object Conserver : SuiteCodesBarres()

    data class Remplacer(val couples: List<Pair<String, String>>, val etag: String?) : SuiteCodesBarres()
    data class Echec(val message: String) : SuiteCodesBarres()
}

/**
 * Traduction des réponses de `GET /catalog` et `GET /codes-barres` en décisions d'import
 * (TASK-16). Fonctions pures, testées en JVM : le client décode, classe les
 * codes HTTP (`ReponsesNirgescom`), ce fichier décide ; `ImportNirgescomService` ne fait qu'exécuter.
 *
 * Les contrôles de contenu (champs absents, caractères refusés, seuil des 10 %, règle « un
 * code-barre = un article ») ne sont **pas** refaits ici : ils restent ceux de `CsvImportService`,
 * communs au fichier et à l'API.
 */
object ImportNirgescom {

    const val MESSAGE_CATALOGUE_A_JOUR =
        "Le catalogue est déjà à jour : rien n'a changé dans Nirgescom depuis la dernière mise à jour."

    const val MESSAGE_CODES_BARRES_A_JOUR =
        "Les codes-barres sont déjà à jour : rien n'a changé dans Nirgescom depuis la dernière " +
            "mise à jour."

    const val MESSAGE_CODES_BARRES_VIDE =
        "Nirgescom ne fournit aucun code-barres secondaire : la correspondance actuelle est conservée."

    /**
     * Cause la plus fréquente d'un catalogue vide selon la SPEC (§9) : le code dépôt de la clé ne
     * correspond à aucun dépôt de Nirgescom. Un catalogue vide n'est jamais écrit.
     */
    const val MESSAGE_CATALOGUE_VIDE =
        "Nirgescom ne renvoie aucun article pour le dépôt de cette tablette : le catalogue " +
            "actuel est conservé. Vérifiez le dépôt réglé dans Paramètres ; s'il est juste, " +
            "prévenez le service informatique."

    /**
     * Un article de l'API vers la ligne qu'analyse `CsvImportService`.
     *
     * - `prix` ← `prix_detail`, le prix rayon (SPEC §8). Absent (`null`) : `0.0`, comme une
     *   colonne prix vide dans le CSV.
     * - `quantite` ← **rien** : l'API n'a pas de quantité de référence. La quantité existante de
     *   l'article est conservée à l'écriture (`appliquerCatalogue(conserverQuantitesRef = true)`),
     *   un article nouveau entre à `0.0`.
     * - `recollee` est propre au CSV (virgule non échappée), toujours faux ici.
     */
    fun ligneCatalogue(article: ArticleDistant): LigneCatalogue = LigneCatalogue(
        codeProduit = article.codeProduit,
        codeBarre = article.codeBarre,
        nomProduit = article.nomProduit,
        quantite = null,
        // Double.toString se relit sans perte par toDouble, notation scientifique comprise.
        prix = article.prixDetail?.toString(),
        recollee = false
    )

    /**
     * Un `null` devient une chaîne vide, que `importCorrespondance` refuse en « code absent » : la
     * ligne est comptée en erreur, elle ne disparaît pas en silence.
     */
    fun couple(codeBarre: CodeBarreDistant): Pair<String, String> =
        codeBarre.codeBarre.orEmpty() to codeBarre.codeProduit.orEmpty()

    fun suiteCatalogue(resultat: ResultatReferentiel<List<ArticleDistant>>): SuiteCatalogue =
        when (resultat) {
            is ResultatReferentiel.Ok ->
                if (resultat.donnees.isEmpty()) SuiteCatalogue.Echec(MESSAGE_CATALOGUE_VIDE)
                else SuiteCatalogue.Analyser(resultat.donnees.map(::ligneCatalogue), resultat.etag)
            ResultatReferentiel.Inchange -> SuiteCatalogue.DejaAJour
            else -> SuiteCatalogue.Echec(messageEchec(resultat))
        }

    fun suiteCodesBarres(resultat: ResultatReferentiel<List<CodeBarreDistant>>): SuiteCodesBarres =
        when (resultat) {
            is ResultatReferentiel.Ok ->
                if (resultat.donnees.isEmpty()) SuiteCodesBarres.Conserver
                else SuiteCodesBarres.Remplacer(resultat.donnees.map(::couple), resultat.etag)
            ResultatReferentiel.Inchange -> SuiteCodesBarres.DejaAJour
            else -> SuiteCodesBarres.Echec(messageEchec(resultat))
        }

    /**
     * Le message d'un refus, sur le modèle de l'envoi (`SyncService`). Chaque cas dit **qui** peut
     * agir : l'opérateur (clé, WiFi), ou l'informatique (serveur). L'import CSV est rappelé comme
     * repli dès que le réseau ou le serveur est en cause.
     */
    fun messageEchec(resultat: ResultatReferentiel<*>): String = when (resultat) {
        is ResultatReferentiel.CleRefusee ->
            "Clé d'API refusée par Nirgescom. Vérifiez la clé dans Paramètres ; si elle est " +
                "correcte, appelez le service informatique." + detail(resultat.detail)
        is ResultatReferentiel.NonAutorise ->
            "Nirgescom refuse cette consultation pour la clé de la tablette. Vérifiez la clé et " +
                "le dépôt dans Paramètres." + detail(resultat.detail)
        is ResultatReferentiel.ParametreRefuse ->
            "Nirgescom ne reconnaît pas la demande de la tablette : l'application et le serveur " +
                "ne sont plus à la même version. Prévenez le service informatique." +
                detail(resultat.detail)
        // Même message que pour l'envoi d'une session : 500 = configuration serveur.
        is ResultatReferentiel.ConfigurationServeur ->
            "Le serveur Nirgescom est mal configuré (clé d'API ou base de données côté " +
                "serveur). Ni le WiFi ni la tablette ne sont en cause, et réessayer ne servira à " +
                "rien tant que le serveur n'est pas corrigé : prévenez le service informatique. " +
                "L'import par fichier CSV reste possible." + detail(resultat.detail)
        is ResultatReferentiel.Indisponible ->
            "Nirgescom ne joint pas sa base de données pour le moment. Réessayez dans quelques " +
                "minutes ; l'import par fichier CSV reste possible." + detail(resultat.detail)
        is ResultatReferentiel.Injoignable ->
            "Serveur injoignable. Vérifiez que la tablette est sur le WiFi de l'entrepôt, puis " +
                "réessayez. L'import par fichier CSV reste possible." + detail(resultat.detail)
        is ResultatReferentiel.UrlInvalide -> resultat.detail
        is ResultatReferentiel.ReponseInattendue ->
            "Réponse inattendue du serveur. Réessayez ; si cela se reproduit, prévenez le " +
                "service informatique.\n\nCode ${resultat.code}." +
                (if (resultat.detail.isNotBlank()) " ${resultat.detail}" else "")
        // Pas des échecs : ne devrait pas être demandé, mais reste lisible si ça l'est.
        is ResultatReferentiel.Ok, ResultatReferentiel.Inchange -> "Aucune erreur."
    }

    private fun detail(texte: String): String = if (texte.isBlank()) "" else "\n\n$texte"
}
