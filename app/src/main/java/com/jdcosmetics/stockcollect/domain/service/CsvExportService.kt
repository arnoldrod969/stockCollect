package com.jdcosmetics.stockcollect.domain.service

import android.content.Context
import android.net.Uri
import com.jdcosmetics.stockcollect.data.db.dao.ArticleDao
import com.jdcosmetics.stockcollect.data.db.dao.ExportDao
import com.jdcosmetics.stockcollect.data.db.dao.SessionDao
import com.jdcosmetics.stockcollect.data.db.entity.ExportEntity
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
                ?: return@withContext ExportResult.Erreur("Session introuvable.")

            if (session.statut == "BROUILLON") {
                return@withContext ExportResult.Erreur(
                    "Seules les sessions clôturées peuvent être exportées."
                )
            }

            val lignes = sessionRepository.getLignesSync(idSession)
            if (lignes.isEmpty()) {
                return@withContext ExportResult.Erreur("La session ne contient aucune ligne.")
            }

            val nomFichier = DateUtils.toFileName()

            return@withContext try {
                val outputStream = context.contentResolver.openOutputStream(outputUri)
                    ?: return@withContext ExportResult.Erreur("Impossible d'ouvrir le fichier de destination.")

                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(Constants.UTF8_BOM)
                    writer.write("${Constants.EXPORT_COL_CODE_BARRE},${Constants.EXPORT_COL_CODE_PRODUIT},${Constants.EXPORT_COL_NOM_PRODUIT},${Constants.EXPORT_COL_QUANTITE}\n")

                    lignes.forEach { ligne ->
                        val codeBarre = ligne.codeBarreScanne
                            ?: articleDao.findByCodeProduit(ligne.codeProduit)?.codeBarrePrincipal
                            ?: ""

                        val nomProduit = ligne.nomProduitSnap
                            .replace(",", " ")
                            .replace("\"", "\"\"")

                        val quantite = FormatUtils.formatQuantite(ligne.quantite)

                        writer.write("$codeBarre,${ligne.codeProduit},\"$nomProduit\",$quantite\n")
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

                sessionDao.marquerExportee(idSession)

                ExportResult.Succes(nomFichier, lignes.size, outputUri)

            } catch (e: Exception) {
                ExportResult.Erreur("Erreur lors de l'écriture : ${e.message}")
            }
        }
}
