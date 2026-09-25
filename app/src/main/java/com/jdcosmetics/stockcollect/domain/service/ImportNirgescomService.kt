package com.jdcosmetics.stockcollect.domain.service

import androidx.annotation.VisibleForTesting
import com.jdcosmetics.stockcollect.data.db.dao.ArtCodebarreDao
import com.jdcosmetics.stockcollect.data.db.dao.ArticleDao
import com.jdcosmetics.stockcollect.data.prefs.ParametresSync
import com.jdcosmetics.stockcollect.data.remote.ArticleDistant
import com.jdcosmetics.stockcollect.data.remote.CodeBarreDistant
import com.jdcosmetics.stockcollect.data.remote.NirgescomClient
import com.jdcosmetics.stockcollect.data.remote.ResultatReferentiel
import javax.inject.Inject
import javax.inject.Singleton

/** Première moitié d'une mise à jour du catalogue depuis Nirgescom : rien n'est encore écrit. */
sealed class AnalyseNirgescom {
    /**
     * Même objet que pour un fichier : l'écran passe par le même arbitrage. [etag] est à rendre à
     * [ImportNirgescomService.appliquerCatalogue], qui ne l'enregistre qu'après écriture.
     */
    data class Prete(val analyse: AnalyseCatalogue, val etag: String?) : AnalyseNirgescom()

    /** `304` : la tablette a déjà cette version du catalogue. Un succès. */
    object DejaAJour : AnalyseNirgescom()

    data class Echec(val message: String, val erreurs: List<String> = emptyList()) : AnalyseNirgescom()
}

sealed class ImportCodesBarresNirgescom {
    data class Importe(val resultat: ImportResult) : ImportCodesBarresNirgescom()
    object DejaAJour : ImportCodesBarresNirgescom()

    /** Nirgescom n'en fournit aucun : la correspondance existante est gardée telle quelle. */
    object Conserve : ImportCodesBarresNirgescom()

    data class Echec(val message: String, val erreurs: List<String> = emptyList()) :
        ImportCodesBarresNirgescom()
}

/**
 * Mise à jour du catalogue et des codes-barres secondaires depuis l'API Nirgescom (TASK-16).
 *
 * N'ajoute **aucune règle d'import** : le contenu reçu passe par [CsvImportService], exactement
 * comme un fichier — mêmes contrôles, même seuil, même arbitrage des conflits, même règle « un
 * code-barre = un article ». Ce service ne fait que relier le client, les décisions pures de
 * [ImportNirgescom] et les `ETag` de [ParametresSync].
 *
 * Règle des `ETag` : un `304` doit vouloir dire « la tablette a déjà exactement cette version ».
 * D'où trois points :
 * - l'`ETag` n'est enregistré qu'**après** une écriture réussie (un arbitrage annulé n'en laisse
 *   pas) ;
 * - il n'est rejoué que si la table locale n'est pas vide ;
 * - toute écriture du catalogue par une autre voie l'efface ([catalogueImporteDepuisFichier]), et
 *   toute écriture du catalogue efface celui des codes-barres, dont l'acceptation dépend des
 *   articles présents.
 */
@Singleton
class ImportNirgescomService @Inject constructor(
    private val client: NirgescomClient,
    private val importService: CsvImportService,
    private val parametres: ParametresSync,
    private val articleDao: ArticleDao,
    private val artCodebarreDao: ArtCodebarreDao
) {

    /** Les deux mises à jour exigent la même configuration que l'envoi d'une session. */
    val estConfigure: Boolean get() = parametres.estConfigure

    /** Libellé du dépôt réglé, pour dire à l'opérateur quel assortiment il va recevoir. */
    val magasin: String get() = parametres.magasinLibelle

    suspend fun analyserCatalogue(): AnalyseNirgescom {
        val etag = if (articleDao.count() > 0) parametres.etagCatalogue else ""
        return analyserReponse(client.recupererCatalogue(etag))
    }

    /**
     * La suite de [analyserCatalogue], une fois la réponse obtenue. Séparée de l'appel réseau
     * pour être éprouvée sur une base Room en mémoire avec des réponses fabriquées.
     */
    @VisibleForTesting
    internal suspend fun analyserReponse(
        reponse: ResultatReferentiel<List<ArticleDistant>>
    ): AnalyseNirgescom {
        return when (val suite = ImportNirgescom.suiteCatalogue(reponse)) {
            SuiteCatalogue.DejaAJour -> AnalyseNirgescom.DejaAJour
            is SuiteCatalogue.Echec -> AnalyseNirgescom.Echec(suite.message)
            is SuiteCatalogue.Analyser ->
                when (val analyse = importService.analyserCatalogue(suite.lignes)) {
                    is AnalyseResult.Pret -> AnalyseNirgescom.Prete(analyse.analyse, suite.etag)
                    is AnalyseResult.Echec -> AnalyseNirgescom.Echec(analyse.message, analyse.erreurs)
                }
        }
    }

    /** L'API ne porte pas de quantité de référence : celle des articles existants est gardée. */
    suspend fun appliquerCatalogue(
        analyse: AnalyseCatalogue,
        resolution: ResolutionConflit,
        etag: String?
    ): ImportResult {
        val resultat = importService.appliquerCatalogue(
            analyse, resolution, conserverQuantitesRef = true
        )
        if (resultat.success) {
            parametres.etagCatalogue = etag.orEmpty()
            parametres.etagCodesBarres = ""
        }
        return resultat
    }

    suspend fun importerCodesBarres(): ImportCodesBarresNirgescom {
        val etag = if (artCodebarreDao.count() > 0) parametres.etagCodesBarres else ""
        return importerReponse(client.recupererCodesBarres(etag))
    }

    /** La suite de [importerCodesBarres], une fois la réponse obtenue (testable sans réseau). */
    @VisibleForTesting
    internal suspend fun importerReponse(
        reponse: ResultatReferentiel<List<CodeBarreDistant>>
    ): ImportCodesBarresNirgescom {
        return when (val suite = ImportNirgescom.suiteCodesBarres(reponse)) {
            SuiteCodesBarres.DejaAJour -> ImportCodesBarresNirgescom.DejaAJour
            // Pas d'ETag retenu : le prochain appel redira que Nirgescom n'en fournit aucun.
            SuiteCodesBarres.Conserver -> ImportCodesBarresNirgescom.Conserve
            is SuiteCodesBarres.Echec -> ImportCodesBarresNirgescom.Echec(suite.message)
            is SuiteCodesBarres.Remplacer -> {
                val resultat = importService.importCorrespondance(suite.couples)
                if (resultat.success) {
                    parametres.etagCodesBarres = suite.etag.orEmpty()
                    ImportCodesBarresNirgescom.Importe(resultat)
                } else {
                    ImportCodesBarresNirgescom.Echec(
                        resultat.messageErreur ?: "Les codes-barres n'ont pas été modifiés.",
                        resultat.erreurs
                    )
                }
            }
        }
    }

    /** À appeler après un import CSV réussi du catalogue : la copie locale n'est plus celle de l'API. */
    fun catalogueImporteDepuisFichier() {
        parametres.etagCatalogue = ""
        parametres.etagCodesBarres = ""
    }

    /** À appeler après un import CSV réussi des codes-barres. */
    fun codesBarresImportesDepuisFichier() {
        parametres.etagCodesBarres = ""
    }
}
