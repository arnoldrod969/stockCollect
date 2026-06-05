package com.jdcosmetics.stockcollect.ui.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jdcosmetics.stockcollect.data.db.entity.LigneCollecteEntity
import com.jdcosmetics.stockcollect.databinding.ItemDetailLigneBinding

class DetailLigneAdapter : ListAdapter<LigneCollecteEntity, DetailLigneAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemDetailLigneBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(ligne: LigneCollecteEntity) {
            binding.tvNomProduit.text = ligne.nomProduitSnap
            binding.tvCodeProduit.text = ligne.codeProduit
            binding.tvQuantite.text = com.jdcosmetics.stockcollect.util.FormatUtils.formatQuantite(ligne.quantite)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDetailLigneBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    companion object DiffCallback : DiffUtil.ItemCallback<LigneCollecteEntity>() {
        override fun areItemsTheSame(a: LigneCollecteEntity, b: LigneCollecteEntity) =
            a.idLigne == b.idLigne
        override fun areContentsTheSame(a: LigneCollecteEntity, b: LigneCollecteEntity) = a == b
    }
}
