package com.jdcosmetics.stockcollect.domain.service

import com.jdcosmetics.stockcollect.data.db.dao.ArtCodebarreDao
import com.jdcosmetics.stockcollect.data.db.dao.ArticleDao
import com.jdcosmetics.stockcollect.data.db.entity.ArticleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class ResolutionResult {
    data class Trouve(
        val article: ArticleEntity,
        val codeBarre: String,
        val viaTableCB: Boolean
    ) : ResolutionResult()

    data class NonTrouve(val codeBarre: String) : ResolutionResult()
}

@Singleton
class BarcodeScanService @Inject constructor(
    private val articleDao: ArticleDao,
    private val artCodebarreDao: ArtCodebarreDao
) {

    suspend fun resoudre(codeBarre: String): ResolutionResult =
        withContext(Dispatchers.IO) {
            val cb = codeBarre.trim()

            val articleDirect = articleDao.findByCodeBarre(cb)
            if (articleDirect != null) {
                return@withContext ResolutionResult.Trouve(
                    article = articleDirect,
                    codeBarre = cb,
                    viaTableCB = false
                )
            }

            val correspondance = artCodebarreDao.findByCodeBarre(cb)
            if (correspondance != null) {
                val articleViaCB = articleDao.findByCodeProduit(correspondance.codeProduit)
                if (articleViaCB != null) {
                    return@withContext ResolutionResult.Trouve(
                        article = articleViaCB,
                        codeBarre = cb,
                        viaTableCB = true
                    )
                }
            }

            ResolutionResult.NonTrouve(codeBarre = cb)
        }
}
