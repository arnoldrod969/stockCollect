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
            binding.tvQuantite.text = FormatUtils.formatQuantite(ligne.quantite)

            binding.btnMoins.setOnClickListener {
                val nouvelle = maxOf(0.0, ligne.quantite - 1)
                onQuantiteChanged(ligne, nouvelle)
            }

            binding.btnPlus.setOnClickListener {
                onQuantiteChanged(ligne, ligne.quantite + 1)
            }

            binding.btnSupprimer.setOnClickListener {
                onSupprimer(ligne)
            }
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
