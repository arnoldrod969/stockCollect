package com.jdcosmetics.stockcollect.domain.service

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.jdcosmetics.stockcollect.data.db.StockCollectDatabase
import com.jdcosmetics.stockcollect.data.db.dao.ArtCodebarreDao
import com.jdcosmetics.stockcollect.data.db.dao.ArticleDao
import com.jdcosmetics.stockcollect.data.db.entity.ArtCodebarreEntity
import com.jdcosmetics.stockcollect.data.db.entity.ArticleEntity
import com.jdcosmetics.stockcollect.util.Constants
import com.jdcosmetics.stockcollect.util.DateUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sortie de la phase d'analyse du catalogue. Aucune écriture n'a eu lieu à ce stade.
 */
sealed class AnalyseResult {
    data class Pret(val analyse: AnalyseCatalogue) : AnalyseResult()
    data class Echec(val message: String, val erreurs: List<String> = emptyList()) : AnalyseResult()
}

@Singleton
class CsvImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: StockCollectDatabase,
    private val articleDao: ArticleDao,
    private val artCodebarreDao: ArtCodebarreDao
) {

    // ------------------------------------------------------------------
    // Catalogue — analyse puis écriture
    // ------------------------------------------------------------------

    /**
     * Lit et contrôle le fichier sans rien écrire.
     *
     * L'import se fait en deux temps pour que les conflits de codes-barres soient arbitrés par
     * l'utilisateur (cf. [ResolutionConflit]). Effet de bord bienvenu : plus aucune fenêtre où la
     * moitié du catalogue serait chargée si la suite échoue.
     */
    suspend fun analyserCatalogue(uri: Uri): AnalyseResult =
        withContext(Dispatchers.IO) {

            val lignes = try {
                CsvParser.lireFichierAvecFallbackEncodage(context, uri).second
            } catch (e: Exception) {
                return@withContext AnalyseResult.Echec(
                    "Impossible de lire ce fichier. Vérifiez qu'il s'agit bien du fichier " +
                        "catalogue exporté depuis Nirgescom.\n\n${e.message}"
                )
            }

            if (lignes.isEmpty()) {
                return@withContext AnalyseResult.Echec(
                    "Ce fichier est vide. Choisissez le fichier catalogue exporté depuis Nirgescom."
                )
            }

            val articles = mutableListOf<ArticleEntity>()
            val erreurs = mutableListOf<String>()
            val recollees = mutableListOf<String>()
            val dateImport = DateUtils.nowIso()

            lignes.forEachIndexed { index, colonnes ->
                val numLigne = index + 1

                if (colonnes.size < 4) {
                    erreurs.add("Ligne $numLigne : il manque des colonnes (${colonnes.size} au lieu de 4 minimum)")
                    return@forEachIndexed
                }

                val ligne = CsvParser.mapperCatalogue(colonnes)

                val codeProduit = ligne.codeProduit?.trim()?.takeIf { it.isNotBlank() }
                if (codeProduit == null) {
                    erreurs.add("Ligne $numLigne : code produit absent")
                    return@forEachIndexed
                }
                if (codeProduit.length > 20) {
                    erreurs.add("Ligne $numLigne : code produit trop long (${codeProduit.length} caractères, 20 au maximum)")
                    return@forEachIndexed
                }

                val nomProduit = ligne.nomProduit?.trim()?.takeIf { it.isNotBlank() }
                if (nomProduit == null) {
                    erreurs.add("Ligne $numLigne : nom de l'article absent")
                    return@forEachIndexed
                }
                if (nomProduit.length > 200) {
                    erreurs.add("Ligne $numLigne : nom de l'article trop long (${nomProduit.length} caractères, 200 au maximum)")
                    return@forEachIndexed
                }

                // Un champ présent mais illisible est une erreur, pas un zéro. L'ancien
                // `?: 0.0` transformait « 9ML » en quantité nulle et un prix décalé en 0 FCFA
                // sans que rien ne le signale.
                val quantite = lireNombre(ligne.quantite, "quantite", numLigne, erreurs)
                    ?: return@forEachIndexed
                val prix = lireNombre(ligne.prix, "prix", numLigne, erreurs)
                    ?: return@forEachIndexed

                if (ligne.recollee) {
                    recollees.add("Ligne $numLigne : virgule dans le nom, colonnes recollées → « $nomProduit »")
                }

                articles.add(
                    ArticleEntity(
                        codeProduit = codeProduit,
                        codeBarrePrincipal = ligne.codeBarre?.trim()?.takeIf { it.isNotBlank() },
                        nomProduit = nomProduit,
                        quantiteRef = quantite,
                        prix = prix,
                        dateImport = dateImport
                    )
                )
            }

            val tauxErreur = erreurs.size.toDouble() / lignes.size
            if (tauxErreur > Constants.IMPORT_SEUIL_ERREUR_POURCENTAGE) {
                return@withContext AnalyseResult.Echec(
                    "${erreurs.size} lignes illisibles sur ${lignes.size} : le catalogue n'a pas été " +
                        "modifié. Vérifiez qu'il s'agit bien du fichier catalogue exporté " +
                        "depuis Nirgescom.",
                    erreurs
                )
            }

            if (articles.isEmpty()) {
                return@withContext AnalyseResult.Echec(
                    "Aucun article lisible dans ce fichier. Le catalogue n'a pas été modifié.",
                    erreurs
                )
            }

            AnalyseResult.Pret(
                AnalyseCatalogue(
                    articles = articles,
                    conflits = detecterConflits(articles),
                    lignesRecollees = recollees,
                    erreurs = erreurs,
                    totalLignes = lignes.size
                )
            )
        }

    /**
     * Applique une analyse en base, selon la résolution choisie par l'utilisateur.
     * Tout se joue dans une transaction : à la moindre erreur, rien n'est écrit.
     */
    suspend fun appliquerCatalogue(
        analyse: AnalyseCatalogue,
        resolution: ResolutionConflit
    ): ImportResult = withContext(Dispatchers.IO) {

        val perdants = analyse.conflits.flatMap { conflit ->
            conflit.perdants.map { it.codeProduit }
        }.toHashSet()

        val aEcrire = when (resolution) {
            ResolutionConflit.IGNORER_ARTICLES ->
                analyse.articles.filter { it.codeProduit !in perdants }

            ResolutionConflit.IMPORTER_SANS_CODE_BARRE ->
                analyse.articles.map { article ->
                    if (article.codeProduit in perdants) article.copy(codeBarrePrincipal = null)
                    else article
                }
        }

        try {
            var inseres = 0
            var misAJour = 0

            db.withTransaction {
                val existants = articleDao.getAllCodeProduits().toHashSet()
                val aInserer = aEcrire.filter { it.codeProduit !in existants }
                val aMettreAJour = aEcrire.filter { it.codeProduit in existants }

                // Détacher d'abord tous les codes-barres du fichier de leurs porteurs actuels.
                // Sans cela, réattribuer un code-barre d'un article à un autre violerait l'index
                // unique selon l'ordre des UPDATE. Voir ArticleDao.libererCodesBarres.
                val codesBarres = aEcrire.mapNotNull { it.codeBarrePrincipal }
                if (codesBarres.isNotEmpty()) {
                    codesBarres.chunked(LOT_SQL).forEach { articleDao.libererCodesBarres(it) }
                }

                if (aMettreAJour.isNotEmpty()) articleDao.updateArticles(aMettreAJour)
                if (aInserer.isNotEmpty()) articleDao.insertOrReplace(aInserer)

                inseres = aInserer.size
                misAJour = aMettreAJour.size
            }

            ImportResult(
                success = true,
                nbImportes = inseres,
                nbMisAJour = misAJour,
                nbIgnores = analyse.articles.size - aEcrire.size,
                nbErreurs = analyse.erreurs.size,
                // Les lignes recollées voyagent avec les erreurs faute d'un canal distinct, mais
                // n'entrent ni dans nbErreurs ni dans le seuil des 10 % : ce sont des lignes
                // rattrapées, pas rejetées. Le dialogue les présente donc sous « Détails », pas
                // sous « Détails erreurs ».
                erreurs = analyse.erreurs + analyse.lignesRecollees
            )
        } catch (e: Exception) {
            ImportResult(
                success = false,
                messageErreur = "L'enregistrement a échoué et rien n'a été modifié. " +
                    "Réessayez ; si cela se reproduit, appelez le service informatique." +
                    "\n\n${e.message}"
            )
        }
    }

    /**
     * Groupe les articles qui revendiquent un même code-barre. Le premier du fichier le garde ;
     * l'ordre vient du catalogue source, l'app n'arbitre pas à sa place.
     */
    private fun detecterConflits(articles: List<ArticleEntity>): List<ConflitCodeBarre> =
        articles
            .filter { it.codeBarrePrincipal != null }
            .groupBy { it.codeBarrePrincipal!! }
            .filterValues { it.size > 1 }
            .map { (codeBarre, groupe) ->
                ConflitCodeBarre(
                    codeBarre = codeBarre,
                    gagnant = groupe.first(),
                    perdants = groupe.drop(1)
                )
            }

    /** Champ absent ou vide → 0.0 (légitime). Champ présent mais non numérique → erreur. */
    private fun lireNombre(
        brut: String?,
        nom: String,
        numLigne: Int,
        erreurs: MutableList<String>
    ): Double? {
        val texte = brut?.trim()
        if (texte.isNullOrBlank()) return 0.0

        val valeur = texte.toDoubleOrNull()
        if (valeur == null) {
            erreurs.add("Ligne $numLigne : $nom illisible (« $texte »)")
            return null
        }
        return valeur.coerceAtLeast(0.0)
    }

    // ------------------------------------------------------------------
    // Table de correspondance
    // ------------------------------------------------------------------

    suspend fun importCorrespondance(uri: Uri): ImportResult =
        withContext(Dispatchers.IO) {

            if (articleDao.count() == 0) {
                return@withContext ImportResult(
                    success = false,
                    messageErreur = "Importez d'abord le catalogue des articles : les codes-barres " +
                        "secondaires s'y rattachent."
                )
            }

            val lignes = try {
                CsvParser.lireFichierAvecFallbackEncodage(context, uri).second
            } catch (e: Exception) {
                return@withContext ImportResult(
                    success = false,
                    messageErreur = "Impossible de lire ce fichier. Vérifiez qu'il s'agit bien " +
                        "du fichier des codes-barres exporté depuis Nirgescom.\n\n${e.message}"
                )
            }

            if (lignes.isEmpty()) {
                return@withContext ImportResult(
                    success = false,
                    messageErreur = "Ce fichier est vide. Choisissez le fichier des " +
                        "codes-barres exporté depuis Nirgescom."
                )
            }

            // Un seul aller-retour en base au lieu d'un findByCodeProduit par ligne.
            val codeProduitsConnus = articleDao.getAllCodeProduits().toHashSet()
            // Codes-barres déjà attribués comme code-barre principal : la règle « un code-barre
            // n'appartient qu'à un seul article » vaut aussi ENTRE les deux tables. Room ne peut
            // pas l'imposer, et BarcodeScanService interrogeant `articles` en premier, une
            // correspondance conflictuelle serait morte sans que personne le sache.
            val codeBarresPrincipaux = articleDao.getCodesBarresPrincipaux()
                .associate { it.codeBarre to it.codeProduit }

            val entites = mutableListOf<ArtCodebarreEntity>()
            val erreurs = mutableListOf<String>()
            val dateImport = DateUtils.nowIso()
            val vus = hashSetOf<String>()

            lignes.forEachIndexed { index, colonnes ->
                val numLigne = index + 1

                if (colonnes.size < 2) {
                    erreurs.add("Ligne $numLigne : il manque des colonnes (${colonnes.size} au lieu de 2)")
                    return@forEachIndexed
                }

                val codeBarre = colonnes.getOrNull(Constants.COL_CB_CODE_BARRE)
                    ?.trim()?.takeIf { it.isNotBlank() }
                if (codeBarre == null) {
                    erreurs.add("Ligne $numLigne : code-barre absent")
                    return@forEachIndexed
                }

                val codeProduit = colonnes.getOrNull(Constants.COL_CB_CODE_PRODUIT)
                    ?.trim()?.takeIf { it.isNotBlank() }
                if (codeProduit == null) {
                    erreurs.add("Ligne $numLigne : code produit absent")
                    return@forEachIndexed
                }

                if (codeProduit !in codeProduitsConnus) {
                    erreurs.add("Ligne $numLigne : article « $codeProduit » absent du catalogue")
                    return@forEachIndexed
                }

                if (!vus.add(codeBarre)) {
                    erreurs.add("Ligne $numLigne : code-barre « $codeBarre » déjà présent plus haut dans le fichier")
                    return@forEachIndexed
                }

                val proprietaire = codeBarresPrincipaux[codeBarre]
                if (proprietaire != null && proprietaire != codeProduit) {
                    erreurs.add(
                        "Ligne $numLigne : le code-barre « $codeBarre » appartient déjà à l'article " +
                            "« $proprietaire » — un code-barre ne peut désigner qu'un seul article"
                    )
                    return@forEachIndexed
                }

                entites.add(
                    ArtCodebarreEntity(
                        codeBarre = codeBarre,
                        codeProduit = codeProduit,
                        dateImport = dateImport
                    )
                )
            }

            val tauxErreur = erreurs.size.toDouble() / lignes.size
            if (tauxErreur > Constants.IMPORT_SEUIL_ERREUR_POURCENTAGE) {
                return@withContext ImportResult(
                    success = false,
                    nbErreurs = erreurs.size,
                    erreurs = erreurs,
                    messageErreur = "${erreurs.size} lignes illisibles sur ${lignes.size} : les " +
                        "codes-barres n'ont pas été modifiés. Vérifiez le fichier."
                )
            }

            return@withContext try {
                // deleteAll + réinsertion dans une transaction : un échec au milieu laissait
                // jusqu'ici la table de correspondance vide.
                db.withTransaction {
                    artCodebarreDao.deleteAll()
                    artCodebarreDao.insertOrReplace(entites)
                }

                ImportResult(
                    success = true,
                    nbImportes = entites.size,
                    nbErreurs = erreurs.size,
                    erreurs = erreurs
                )
            } catch (e: Exception) {
                ImportResult(
                success = false,
                messageErreur = "L'enregistrement a échoué et rien n'a été modifié. " +
                    "Réessayez ; si cela se reproduit, appelez le service informatique." +
                    "\n\n${e.message}"
            )
            }
        }

    private companion object {
        /** SQLite plafonne le nombre de paramètres d'une requête (999 par défaut). */
        const val LOT_SQL = 500
    }
}
