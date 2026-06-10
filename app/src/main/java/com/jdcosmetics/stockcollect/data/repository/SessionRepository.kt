package com.jdcosmetics.stockcollect.data.repository

import com.jdcosmetics.stockcollect.data.db.dao.ArticleDao
import com.jdcosmetics.stockcollect.data.db.dao.LigneCollecteDao
import com.jdcosmetics.stockcollect.data.db.dao.SessionDao
import com.jdcosmetics.stockcollect.data.db.entity.ArticleEntity
import com.jdcosmetics.stockcollect.data.db.entity.LigneCollecteEntity
import com.jdcosmetics.stockcollect.data.db.entity.SessionEntity
import com.jdcosmetics.stockcollect.data.db.entity.StatutSession
import com.jdcosmetics.stockcollect.util.DateUtils
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val ligneDao: LigneCollecteDao,
    private val articleDao: ArticleDao
) {
    fun getAllSessions(): Flow<List<SessionEntity>> = sessionDao.getAllSessions()

    suspend fun getById(id: Long): SessionEntity? = sessionDao.getById(id)

    suspend fun creerSession(typeOperation: String, lieu: String?, observations: String?): Long {
        val session = SessionEntity(
            typeOperation = typeOperation,
            dateHeureDebut = DateUtils.nowIso(),
            statut = StatutSession.BROUILLON,
            lieu = lieu,
            observations = observations
        )
        return sessionDao.insert(session)
    }

    suspend fun cloturer(idSession: Long): Boolean {
        val nb = sessionDao.cloturer(idSession, DateUtils.nowIso())
        return nb > 0
    }

    suspend fun getLastBrouillon(): SessionEntity? = sessionDao.getLastBrouillon()

    fun getLignes(idSession: Long): Flow<List<LigneCollecteEntity>> =
        ligneDao.getLignesBySession(idSession)

    suspend fun getLignesSync(idSession: Long): List<LigneCollecteEntity> =
        ligneDao.getLignesBySessionSync(idSession)

    suspend fun ajouterLigne(
        idSession: Long,
        article: ArticleEntity,
        codeBarreScanne: String?,
        quantite: Double
    ): Long {
        val ligneExistante = ligneDao.getLigneBySessionAndProduit(idSession, article.codeProduit)
        if (ligneExistante != null) {
            val nouvelleQuantite = ligneExistante.quantite + quantite
            ligneDao.update(ligneExistante.copy(quantite = nouvelleQuantite))
            return ligneExistante.idLigne
        }
        val ligne = LigneCollecteEntity(
            idSession = idSession,
            codeProduit = article.codeProduit,
            codeBarreScanne = codeBarreScanne,
            nomProduitSnap = article.nomProduit,
            quantite = quantite,
            dateSaisie = DateUtils.nowIso()
        )
        val id = ligneDao.insert(ligne)
        val nb = ligneDao.countBySession(idSession)
        sessionDao.updateNbLignes(idSession, nb)
        return id
    }

    suspend fun mettreAJourQuantite(ligne: LigneCollecteEntity, nouvelleQuantite: Double) {
        ligneDao.update(ligne.copy(quantite = nouvelleQuantite))
    }

    suspend fun supprimerLigne(ligne: LigneCollecteEntity) {
        ligneDao.delete(ligne)
        val nb = ligneDao.countBySession(ligne.idSession)
        sessionDao.updateNbLignes(ligne.idSession, nb)
    }

    suspend fun searchArticles(query: String): List<ArticleEntity> =
        articleDao.searchByNomSync(query)

    suspend fun getNbArticles(): Int = articleDao.count()
}
