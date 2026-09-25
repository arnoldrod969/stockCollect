package com.jdcosmetics.stockcollect.ui

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.jdcosmetics.stockcollect.R
import com.jdcosmetics.stockcollect.data.db.entity.StatutSession

/**
 * Pastille de statut d'une session (Historique, Détail), sur le fond `bg_chip`.
 *
 * Une couleur par statut : la pastille était bleue pour tous, et Brouillon, Clôturée et Exportée
 * ne se distinguaient qu'en lisant. Le libellé reste là — la couleur aide, elle ne porte pas seule
 * l'information. Couples conteneur / texte de la palette, lisibles (contraste ≥ 4,5:1).
 */
fun TextView.afficherStatutSession(statut: String) {
    val (libelle, fond, texte) = when (statut) {
        StatutSession.BROUILLON ->
            Triple("Brouillon", R.color.amber_container, R.color.amber_on_container)
        StatutSession.CLOTUREE ->
            Triple("Clôturée", R.color.blue_container, R.color.blue_on_container)
        StatutSession.EXPORTEE ->
            Triple("Exportée", R.color.green_container, R.color.green_on_container)
        else -> Triple(statut, R.color.surface_container_high, R.color.on_surface)
    }
    text = libelle
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, fond))
    setTextColor(ContextCompat.getColor(context, texte))
}
