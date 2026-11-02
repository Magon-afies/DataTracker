package com.mycompany.datatracker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.util.Size
import androidx.recyclerview.widget.RecyclerView
import com.mycompany.datatracker.R
import com.mycompany.datatracker.media.MediaGroup
import java.util.concurrent.Executors

class MediaGroupAdapter(
    private val onDelete: (MediaGroup) -> Unit,
    private val onOpen: (MediaGroup) -> Unit
) : RecyclerView.Adapter<MediaGroupAdapter.ViewHolder>() {

    private val items = mutableListOf<MediaGroup>()
    private val executor = Executors.newFixedThreadPool(2)

    fun submit(list: List<MediaGroup>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.group_thumb)
        val title: TextView = view.findViewById(R.id.group_title)
        val meta: TextView = view.findViewById(R.id.group_meta)
        val sample: TextView = view.findViewById(R.id.group_sample)
        val snippet: TextView = view.findViewById(R.id.group_snippet)
        val delete: Button = view.findViewById(R.id.group_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_media_group, parent, false)
        )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val group = items[position]
        val context = holder.itemView.context
        holder.title.text = group.title
        holder.meta.text = context.getString(R.string.group_meta, group.count)
        holder.sample.text = group.sampleName
        holder.snippet.text = group.snippet.ifBlank { context.getString(R.string.group_no_text) }

        holder.itemView.setOnClickListener { onOpen(group) }

        if (group.extraIds.isNotEmpty()) {
            holder.delete.visibility = View.VISIBLE
            holder.delete.text = context.getString(R.string.delete_extras, group.extraIds.size)
            holder.delete.setOnClickListener { onDelete(group) }
        } else {
            holder.delete.visibility = View.GONE
        }

        // Thumbnail, loaded off the main thread
        holder.thumb.setImageDrawable(null)
        val uri = group.sampleUri ?: return
        holder.thumb.tag = uri
        val resolver = context.applicationContext.contentResolver
        executor.execute {
            val bmp = try {
                resolver.loadThumbnail(uri, Size(160, 160), null)
            } catch (e: Exception) {
                null
            }
            holder.thumb.post {
                if (bmp != null && holder.thumb.tag == uri) holder.thumb.setImageBitmap(bmp)
            }
        }
    }
}
