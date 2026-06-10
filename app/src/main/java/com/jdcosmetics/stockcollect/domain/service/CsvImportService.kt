package com.jdcosmetics.stockcollect.domain.service

import android.content.Context
import android.net.Uri
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

@Singleton
class CsvImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val articleDao: ArticleDao,
    private val artCodebarreDao: ArtCodebarreDao
) {

    suspend fun importCatalogue(uri: Uri): ImportResult =
        withContext(Dispatchers.IO) {

            val (separateur, lignes) = try {
                CsvParser.lireFichierAvecFallbackEncodage(context, uri)
            } catch (e: Exception) {
                return@withContext ImportResult(
                    success = false,
                    messageErreur = "Impossible de lire le fichier : ${e.message}"
                )
            }

            if (lignes.isEmpty()) {
                return@withContext ImportResult(
                    success = false,
                    messageErreur = "Le fichier est vide."
                )
            }

            val articlesValides = mutableListOf<ArticleEntity>()
            val erreurs = mutableListOf<String>()
            val dateImport = DateUtils.nowIso()
            var nbIgnores = 0

            lignes.forEachIndexed { index, colonnes ->
                val numLigne = index + 1

                if (colonnes.size < 4) {
                    erreurs.add("Ligne $numLigne : nombre de colonnes insuffisant (${colonnes.size} < 4)")
                    return@forEachIndexed
                }

                val codeProduit = colonnes.getOrNull(Constants.COL_CATALOGUE_CODE_PRODUIT)
                    ?.trim()?.takeIf { it.isNotBlank() }

                if (codeProduit == null) {
                    erreurs.add("Ligne $numLigne : code_produit vide ou manquant")
                    return@forEachIndexed
                }

                if (codeProduit.length > 20) {
                    erreurs.add("Ligne $numLigne : code_produit trop long (${codeProduit.length} > 20 car.)")
                    return@forEachIndexed
                }

                val codeBarrePrincipal = colonnes.getOrNull(Constants.COL_CATALOGUE_CODE_BARRE)
                    ?.trim()?.takeIf { it.isNotBlank() }

                val nomProduit = colonnes.getOrNull(Constants.COL_CATALOGUE_NOM_PRODUIT)
                    ?.trim()?.takeIf { it.isNotBlank() }

                if (nomProduit == null) {
                    erreurs.add("Ligne $numLigne : nom_produit vide ou manquant")
                    return@forEachIndexed
                }

                if (nomProduit.length > 200) {
                    erreurs.add("Ligne $numLigne : nom_produit trop long (${nomProduit.length} > 200 car.)")
                    return@forEachIndexed
                }

                val quantite = colonnes.getOrNull(Constants.COL_CATALOGUE_QUANTITE)
                    ?.trim()?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

                val prix = colonnes.getOrNull(Constants.COL_CATALOGUE_PRIX)
                    ?.trim()?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

                articlesValides.add(
                    ArticleEntity(
                        codeProduit = codeProduit,
                        codeBarrePrincipal = codeBarrePrincipal,
                        nomProduit = nomProduit,
                        quantiteRef = quantite,
                        prix = prix,
                        dateImport = dateImport
                    )
                )
            }

            val totalLignes = lignes.size
            val tauxErreur = if (totalLignes > 0) erreurs.size.toDouble() / totalLignes else 0.0

            if (tauxErreur > Constants.IMPORT_SEUIL_ERREUR_POURCENTAGE) {
                return@withContext ImportResult(
                    success = false,
                    nbErreurs = erreurs.size,
                    erreurs = erreurs,
                    messageErreur = "Trop d'erreurs (${erreurs.size}/$totalLignes lignes). Import annul\u00e9. V\u00e9rifiez le format du fichier."
                )
            }

            if (articlesValides.isEmpty()) {
                return@withContext ImportResult(
                    success = false,
                    messageErreur = "Aucun article valide trouv\u00e9 dans le fichier."
                )
            }

            try {
                val existants = articleDao.getAllCodeProduits().toHashSet()
                val aInserer = mutableListOf<ArticleEntity>()
                val aMettreAJour = mutableListOf<ArticleEntity>()

                for (article in articlesValides) {
                    if (article.codeProduit in existants) {
                        aMettreAJour.add(article)
                    } else {
                        aInserer.add(article)
                    }
                }

                if (aMettreAJour.isNotEmpty()) {
                    articleDao.updateArticles(aMettreAJour)
                }
                if (aInserer.isNotEmpty()) {
                    articleDao.insertOrReplace(aInserer)
                }

                ImportResult(
                    success = true,
                    nbImportes = aInserer.size,
                    nbMisAJour = aMettreAJour.size,
                    nbIgnores = nbIgnores,
                    nbErreurs = erreurs.size,
                    erreurs = erreurs
                )
            } catch (e: Exception) {
                ImportResult(
                    success = false,
                    messageErreur = "Erreur base de donn\u00e9es : ${e.message}"
                )
            }
        }

    suspend fun importCorrespondance(uri: Uri): ImportResult =
        withContext(Dispatchers.IO) {

            val nbArticles = articleDao.count()
            if (nbArticles == 0) {
                return@withContext ImportResult(
                    success = false,
                    messageErreur = "Le catalogue articles doit \u00eatre import\u00e9 avant la table de correspondance."
                )
            }

            val (separateur, lignes) = try {
                CsvParser.lireFichierAvecFallbackEncodage(context, uri)
            } catch (e: Exception) {
                return@withContext ImportResult(
                    success = false,
                    messageErreur = "Impossible de lire le fichier : ${e.message}"
                )
            }

            if (lignes.isEmpty()) {
                return@withContext ImportResult(
                    success = false,
                    messageErreur = "Le fichier est vide."
                )
            }

            val entitesValides = mutableListOf<ArtCodebarreEntity>()
            val erreurs = mutableListOf<String>()
            val dateImport = DateUtils.nowIso()

            lignes.forEachIndexed { index, colonnes ->
                val numLigne = index + 1

                if (colonnes.size < 2) {
                    erreurs.add("Ligne $numLigne : 2 colonnes requises, ${colonnes.size} trouv\u00e9e(s)")
                    return@forEachIndexed
                }

                val codeBarre = colonnes.getOrNull(Constants.COL_CB_CODE_BARRE)
                    ?.trim()?.takeIf { it.isNotBlank() }

                if (codeBarre == null) {
                    erreurs.add("Ligne $numLigne : code_barre vide")
                    return@forEachIndexed
                }

                val codeProduit = colonnes.getOrNull(Constants.COL_CB_CODE_PRODUIT)
                    ?.trim()?.takeIf { it.isNotBlank() }

                if (codeProduit == null) {
                    erreurs.add("Ligne $numLigne : code_produit vide")
                    return@forEachIndexed
                }

                val articleExiste = articleDao.findByCodeProduit(codeProduit) != null
                if (!articleExiste) {
                    erreurs.add("Ligne $numLigne : code_produit '$codeProduit' introuvable dans le catalogue")
                    return@forEachIndexed
                }

                entitesValides.add(
                    ArtCodebarreEntity(
                        codeBarre = codeBarre,
                        codeProduit = codeProduit,
                        dateImport = dateImport
                    )
                )
            }

            val totalLignes = lignes.size
            val tauxErreur = if (totalLignes > 0) erreurs.size.toDouble() / totalLignes else 0.0

            if (tauxErreur > Constants.IMPORT_SEUIL_ERREUR_POURCENTAGE) {
                return@withContext ImportResult(
                    success = false,
                    nbErreurs = erreurs.size,
                    erreurs = erreurs,
                    messageErreur = "Trop d'erreurs (${erreurs.size}/$totalLignes lignes). Import annul\u00e9."
                )
            }

            return@withContext try {
                artCodebarreDao.deleteAll()
                artCodebarreDao.insertOrReplace(entitesValides)

                ImportResult(
                    success = true,
                    nbImportes = entitesValides.size,
                    nbErreurs = erreurs.size,
                    erreurs = erreurs
                )
            } catch (e: Exception) {
                ImportResult(
                    success = false,
                    messageErreur = "Erreur base de donn\u00e9es : ${e.message}"
                )
            }
        }
}
