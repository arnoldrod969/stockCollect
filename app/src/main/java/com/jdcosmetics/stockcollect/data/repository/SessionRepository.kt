package com.jdcosmetics.stockcollect.data.repository

import com.jdcosmetics.stockcollect.data.db.dao.ArticleDao
import com.jdcosmetics.stockcollect.data.db.dao.LigneCollecteDao
import com.jdcosmetics.stockcollect.data.db.dao.SessionDao
import com.jdcosmetics.stockcollect.data.db.entity.ArticleEntity
import com.jdcosmetics.stockcollect.data.db.entity.LigneCollecteEntity
import com.jdcosmetics.stockcollect.data.db.entity.SessionEntity
import com.jdcosmetics.stockcollect.data.db.entity.StatutSession
import com.jdcosmetics.stockcollect.util.DateUtils
import com.jdcosmetics.stockcollect.util.FormatUtils
import kotlinx.coroutines.flow.Flow
import java.util.UUID
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

    /**
     * L'UUID est posé **à la création**, pas à la première synchronisation : l'API calcule
     * `hash_ligne` à partir de lui, et un renvoi après coupure réseau doit produire exactement les
     * mêmes hash, sinon la session est insérée deux fois côté Nirgescom. Le générer plus tard
     * ouvrirait une fenêtre où deux envois concurrents porteraient deux identifiants différents.
     */
    suspend fun creerSession(typeOperation: String, lieu: String?, observations: String?): Long {
        val session = SessionEntity(
            typeOperation = typeOperation,
            dateHeureDebut = DateUtils.nowIso(),
            statut = StatutSession.BROUILLON,
            lieu = lieu,
            observations = observations,
            uuidSession = UUID.randomUUID().toString()
        )
        return sessionDao.insert(session)
    }

    suspend fun cloturer(idSession: Long): Boolean {
        val nb = sessionDao.cloturer(idSession, DateUtils.nowIso())
        return nb > 0
    }

    suspend fun getLastBrouillon(): SessionEntity? = sessionDao.getLastBrouillon()

    suspend fun getLastBrouillonDuType(typeOperation: String): SessionEntity? =
        sessionDao.getLastBrouillonDuType(typeOperation)

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
            // La somme de deux Double dérive (1.1 + 2.2 = 3.3000000000000003) : normalisée, sinon
            // la session deviendrait inenvoyable après sa clôture.
            val nouvelleQuantite = FormatUtils.normaliserQuantite(ligneExistante.quantite + quantite)
            ligneDao.update(ligneExistante.copy(quantite = nouvelleQuantite))
            return ligneExistante.idLigne
        }
        val ligne = LigneCollecteEntity(
            idSession = idSession,
            codeProduit = article.codeProduit,
            codeBarreScanne = codeBarreScanne,
            nomProduitSnap = article.nomProduit,
            quantite = FormatUtils.normaliserQuantite(quantite),
            dateSaisie = DateUtils.nowIso()
        )
        val id = ligneDao.insert(ligne)
        val nb = ligneDao.countBySession(idSession)
        sessionDao.updateNbLignes(idSession, nb)
        return id
    }

    /** Saisie au clavier ou boutons ± de la liste : même normalisation que [ajouterLigne]. */
    suspend fun mettreAJourQuantite(ligne: LigneCollecteEntity, nouvelleQuantite: Double) {
        ligneDao.update(ligne.copy(quantite = FormatUtils.normaliserQuantite(nouvelleQuantite)))
    }

    suspend fun supprimerLigne(ligne: LigneCollecteEntity) {
        ligneDao.delete(ligne)
        val nb = ligneDao.countBySession(ligne.idSession)
        sessionDao.updateNbLignes(ligne.idSession, nb)
    }

    suspend fun searchArticles(query: String): List<ArticleEntity> =
        articleDao.searchAllSync(query)

    suspend fun getNbArticles(): Int = articleDao.count()
}
