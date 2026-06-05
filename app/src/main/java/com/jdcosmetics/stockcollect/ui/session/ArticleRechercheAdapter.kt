package com.jdcosmetics.stockcollect.ui.session

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jdcosmetics.stockcollect.data.db.entity.ArticleEntity
import com.jdcosmetics.stockcollect.databinding.ItemArticleRechercheBinding

class ArticleRechercheAdapter(
    private val onAjouter: (ArticleEntity) -> Unit
) : ListAdapter<ArticleEntity, ArticleRechercheAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemArticleRechercheBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(article: ArticleEntity) {
            binding.tvNomProduit.text = article.nomProduit
            binding.tvCodeProduit.text = article.codeProduit
            binding.btnAjouter.setOnClickListener { onAjouter(article) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemArticleRechercheBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object DiffCallback : DiffUtil.ItemCallback<ArticleEntity>() {
        override fun areItemsTheSame(a: ArticleEntity, b: ArticleEntity) =
            a.codeProduit == b.codeProduit
        override fun areContentsTheSame(a: ArticleEntity, b: ArticleEntity) = a == b
    }
}
