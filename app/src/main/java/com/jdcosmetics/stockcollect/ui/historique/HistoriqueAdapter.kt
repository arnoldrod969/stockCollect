package com.jdcosmetics.stockcollect.ui.historique

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jdcosmetics.stockcollect.data.db.entity.SessionEntity
import com.jdcosmetics.stockcollect.data.db.entity.StatutSession
import com.jdcosmetics.stockcollect.data.db.entity.TypeOperation
import com.jdcosmetics.stockcollect.databinding.ItemSessionHistoriqueBinding
import com.jdcosmetics.stockcollect.util.DateUtils

class HistoriqueAdapter(
    private val onExporter: (Long) -> Unit,
    private val onItemClick: (SessionEntity) -> Unit = {}
) : ListAdapter<SessionEntity, HistoriqueAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemSessionHistoriqueBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(session: SessionEntity) {
            binding.tvType.text = TypeOperation.label(session.typeOperation)
            binding.tvDate.text = DateUtils.toDisplay(session.dateHeureDebut)
            binding.tvNbLignes.text = "${session.nbLignes} ligne${if (session.nbLignes > 1) "s" else ""}"

            val (texte, couleur) = when (session.statut) {
                StatutSession.BROUILLON -> Pair("Brouillon", android.R.color.holo_orange_light)
                StatutSession.CLOTUREE -> Pair("Clôturée", android.R.color.holo_blue_light)
                StatutSession.EXPORTEE -> Pair("Exportée", android.R.color.holo_green_light)
                else -> Pair(session.statut, android.R.color.darker_gray)
            }
            binding.tvStatut.text = texte

            binding.btnExporter.visibility = if (session.statut == StatutSession.CLOTUREE)
                android.view.View.VISIBLE else android.view.View.GONE

            binding.btnExporter.setOnClickListener { onExporter(session.idSession) }
            binding.root.setOnClickListener { onItemClick(session) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSessionHistoriqueBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object DiffCallback : DiffUtil.ItemCallback<SessionEntity>() {
        override fun areItemsTheSame(a: SessionEntity, b: SessionEntity) =
            a.idSession == b.idSession
        override fun areContentsTheSame(a: SessionEntity, b: SessionEntity) = a == b
    }
}
