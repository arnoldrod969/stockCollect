package com.jdcosmetics.stockcollect.ui.session

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jdcosmetics.stockcollect.data.db.entity.LigneCollecteEntity
import com.jdcosmetics.stockcollect.databinding.ItemLigneCollecteBinding
import com.jdcosmetics.stockcollect.util.FormatUtils

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
                if (hasFocus) return@setOnFocusChangeListener
                val courante = ligneCourante() ?: return@setOnFocusChangeListener
                val q = binding.etQuantite.text?.toString()?.trim()?.toDoubleOrNull()
                    ?: return@setOnFocusChangeListener
                if (q >= 0 && q != courante.quantite) onQuantiteChanged(courante, q)
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
