package com.example.historyhub

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

data class DisplayHistoryItem(
    val year: Int,
    val text: String,
    val thumbnailUrl: String?,
    val wikiUrl: String?,
    var isBookmarked: Boolean = false
)

class HistoryAdapter(
    private var items: List<DisplayHistoryItem>,
    private val onItemClick: (DisplayHistoryItem) -> Unit,
    private val onBookmarkClick: (DisplayHistoryItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumbnail: ImageView = view.findViewById(R.id.ivThumbnail)
        val tvYear: TextView = view.findViewById(R.id.tvYear)
        val tvText: TextView = view.findViewById(R.id.tvText)
        val btnBookmark: ImageButton = view.findViewById(R.id.btnBookmark)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvYear.text = if (item.year > 0) item.year.toString() else "History"
        holder.tvText.text = item.text

        if (!item.thumbnailUrl.isNullOrEmpty()) {
            holder.ivThumbnail.visibility = View.VISIBLE
            Glide.with(holder.itemView.context)
                .load(item.thumbnailUrl)
                .centerCrop()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(holder.ivThumbnail)
        } else {
            holder.ivThumbnail.visibility = View.GONE
        }

        val starRes = if (item.isBookmarked) {
            android.R.drawable.btn_star_big_on
        } else {
            android.R.drawable.btn_star_big_off
        }
        holder.btnBookmark.setImageResource(starRes)

        holder.btnBookmark.setOnClickListener {
            onBookmarkClick(item)
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<DisplayHistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}