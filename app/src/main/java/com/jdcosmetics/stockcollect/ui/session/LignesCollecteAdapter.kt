package com.jdcosmetics.stockcollect.ui.session

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jdcosmetics.stockcollect.data.db.entity.LigneCollecteEntity
import com.jdcosmetics.stockcollect.databinding.ItemLigneCollecteBinding
import com.jdcosmetics.stockcollect.util.FormatUtils

/**
 * La quantité à écrire pour le texte du champ, ou `null` s'il n'y a rien à écrire.
 *
 * Le champ affiche la quantité arrondie à une décimale : comparer le nombre relu au `Double` en
 * base réécrivait 1.25 en 1.3 au simple passage du focus, sans que rien n'ait été tapé. On compare
 * donc d'abord au texte affiché. Revers assumé : taper exactement ce texte arrondi n'écrit rien.
 *
 * Fonction pure, hors du ViewHolder, pour être testée sans Android (LignesCollecteAdapterTest).
 */
internal fun quantiteAEcrire(saisie: String?, quantiteEnBase: Double): Double? {
    val texte = saisie?.trim().orEmpty()
    if (texte == FormatUtils.formatQuantite(quantiteEnBase)) return null
    val q = texte.toDoubleOrNull() ?: return null
    return q.takeIf { it >= 0 && it != quantiteEnBase }
}

class LignesCollecteAdapter(
    private val onQuantiteChanged: (LigneCollecteEntity, Double) -> Unit,
    private val onSupprimer: (LigneCollecteEntity) -> Unit
) : ListAdapter<LigneCollecteEntity, LignesCollecteAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemLigneCollecteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(ligne: LigneCollecteEntity) {
            binding.tvNomProduit.text = ligne.nomProduitSnap
            binding.tvCodeProduit.text = ligne.codeProduit

            // Détacher le listener AVANT d'écrire la valeur : s'il était encore branché sur la
            // ligne précédente, la perte de focus qui suit le recyclage lui ferait lire la
            // nouvelle quantité et l'écrire sur l'ancien article.
            binding.etQuantite.onFocusChangeListener = null
            binding.etQuantite.setText(FormatUtils.formatQuantite(ligne.quantite))

            binding.etQuantite.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) validerSaisie()
            }

            // La perte de focus ne suffit pas : un MaterialButton ne prend pas le focus en mode
            // tactile, donc taper « Clôturer » juste après avoir saisi une quantité la laissait
            // dans le champ sans jamais l'écrire. Constaté sur l'émulateur : 25 tapé, 1.0 en base.
            // La touche de validation du clavier donne une seconde issue, plus naturelle.
            binding.etQuantite.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                    validerSaisie()
                }
                false   // false : on laisse le clavier se fermer comme d'habitude
            }

            binding.btnMoins.setOnClickListener {
                val courante = ligneCourante() ?: return@setOnClickListener
                onQuantiteChanged(courante, maxOf(0.0, courante.quantite - 1))
            }

            binding.btnPlus.setOnClickListener {
                val courante = ligneCourante() ?: return@setOnClickListener
                onQuantiteChanged(courante, courante.quantite + 1)
            }

            binding.btnSupprimer.setOnClickListener {
                val courante = ligneCourante() ?: return@setOnClickListener
                onSupprimer(courante)
            }
        }

        /** Écrit la quantité tapée, si elle est lisible et différente de celle déjà enregistrée. */
        private fun validerSaisie() {
            val courante = ligneCourante() ?: return
            val q = quantiteAEcrire(binding.etQuantite.text?.toString(), courante.quantite) ?: return
            onQuantiteChanged(courante, q)
        }

        /**
         * Relit la ligne par la position courante du ViewHolder plutôt que par la capture faite
         * au bind : entre les deux, la vue a pu être recyclée sur un autre article.
         *
         * `adapterPosition` et non `bindingAdapterPosition` : recyclerview résout en 1.1.0 ici,
         * où le second n'existe pas encore. Sans ConcatAdapter, les deux sont équivalents.
         */
        private fun ligneCourante(): LigneCollecteEntity? {
            val position = adapterPosition
            if (position == RecyclerView.NO_POSITION || position >= itemCount) return null
            return getItem(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLigneCollecteBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<LigneCollecteEntity>() {
        override fun areItemsTheSame(a: LigneCollecteEntity, b: LigneCollecteEntity) =
            a.idLigne == b.idLigne
        override fun areContentsTheSame(a: LigneCollecteEntity, b: LigneCollecteEntity) =
            a == b
    }
}
