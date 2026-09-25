package com.jdcosmetics.stockcollect.domain.service

import android.content.Context
import android.net.Uri
import com.jdcosmetics.stockcollect.data.db.dao.ArticleDao
import com.jdcosmetics.stockcollect.data.db.dao.ExportDao
import com.jdcosmetics.stockcollect.data.db.dao.SessionDao
import com.jdcosmetics.stockcollect.data.db.entity.ExportEntity
import com.jdcosmetics.stockcollect.data.db.entity.StatutSession
import com.jdcosmetics.stockcollect.data.repository.SessionRepository
import com.jdcosmetics.stockcollect.util.Constants
import com.jdcosmetics.stockcollect.util.DateUtils
import com.jdcosmetics.stockcollect.util.FormatUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton

sealed class ExportResult {
    data class Succes(val nomFichier: String, val nbLignes: Int, val uri: Uri) : ExportResult()
    data class Erreur(val message: String) : ExportResult()
}

/**
 * Une session s'exporte une fois clôturée, et **reste** exportable une fois exportée : le fichier
 * a pu être perdu (clé USB, dossier effacé) alors que la collecte n'existe plus que sur la
 * tablette. Un brouillon, dont le contenu bouge encore, ne l'est jamais — ni un statut inconnu.
 */
fun estExportable(statut: String): Boolean =
    statut == StatutSession.CLOTUREE || statut == StatutSession.EXPORTEE

@Singleton
class CsvExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionRepository: SessionRepository,
    private val articleDao: ArticleDao,
    private val sessionDao: SessionDao,
    private val exportDao: ExportDao
) {

    suspend fun exporter(idSession: Long, outputUri: Uri): ExportResult =
        withContext(Dispatchers.IO) {
            val session = sessionDao.getById(idSession)
                ?: return@withContext ExportResult.Erreur(
                    "Cette session n'existe plus sur la tablette."
                )

            if (!estExportable(session.statut)) {
                return@withContext ExportResult.Erreur(
                    "Clôturez la session avant de l'exporter."
                )
            }

            val lignes = sessionRepository.getLignesSync(idSession)
            if (lignes.isEmpty()) {
                return@withContext ExportResult.Erreur(
                    "Cette session ne contient aucun article : il n'y a rien à exporter."
                )
            }

            val nomFichier = DateUtils.toFileName()

            return@withContext try {
                val outputStream = context.contentResolver.openOutputStream(outputUri)
                    ?: return@withContext ExportResult.Erreur(
                        "Impossible d'écrire dans le fichier choisi. Recommencez en choisissant " +
                            "un autre dossier, par exemple Téléchargements."
                    )

                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write("${Constants.EXPORT_COL_CODE_BARRE},${Constants.EXPORT_COL_CODE_PRODUIT},${Constants.EXPORT_COL_NOM_PRODUIT},${Constants.EXPORT_COL_QUANTITE}\r\n")

                    lignes.forEach { ligne ->
                        val codeBarre = ligne.codeBarreScanne
                            ?: articleDao.findByCodeProduit(ligne.codeProduit)?.codeBarrePrincipal
                            ?: ""

                        val nomProduit = ligne.nomProduitSnap
                            .replace(",", " ")

                        val quantite = FormatUtils.formatQuantite(ligne.quantite)

                        writer.write("$codeBarre,${ligne.codeProduit},$nomProduit,$quantite\r\n")
                    }
                }

                exportDao.insert(
                    ExportEntity(
                        idSession = idSession,
                        nomFichier = nomFichier,
                        dateExport = DateUtils.nowIso(),
                        nbLignesExportees = lignes.size,
                        cheminFichier = outputUri.toString()
                    )
                )

                // Gardé sur CLOTUREE : pour un réexport la session est déjà EXPORTEE, aucune ligne
                // n'est touchée et c'est voulu — le cycle de vie ne revient jamais en arrière, et
                // le statut de synchro n'a rien à voir avec l'export. Seul l'audit s'allonge.
                sessionDao.marquerExportee(idSession)

                ExportResult.Succes(nomFichier, lignes.size, outputUri)

            } catch (e: Exception) {
                ExportResult.Erreur(
                    "L'écriture du fichier s'est interrompue. Vérifiez l'espace libre sur la " +
                        "tablette, puis recommencez.\n\n${e.message}"
                )
            }
        }
}
