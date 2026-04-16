package com.example.iainnotes

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.iainnotes.databinding.ItemNoteBinding

class NoteAdapter(
    private val onTap: (Note) -> Unit,
    private val onDelete: (Note) -> Unit,
    private var alarms: List<Alarm> = emptyList()
) : ListAdapter<Note, NoteAdapter.ViewHolder>(DiffCallback()) {

    fun updateAlarms(newAlarms: List<Alarm>) {
        alarms = newAlarms
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemNoteBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(
            ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val note = getItem(position)
        holder.binding.tvNoteTitle.text = note.title
        holder.binding.tvNotePreview.text = note.content.take(80).ifBlank { "No content" }

        val noteAlarms = alarms.filter { it.noteId == note.id }
        if (noteAlarms.isEmpty()) {
            holder.binding.tvAlarmBadge.visibility = View.GONE
        } else {
            holder.binding.tvAlarmBadge.visibility = View.VISIBLE
            val count = noteAlarms.size
            holder.binding.tvAlarmBadge.text = if (count > 9) "9+" else count.toString()
            val anyActive = noteAlarms.any { it.isActive }
            val badgeColor = if (anyActive) "#ec727b" else "#888888"
            (holder.binding.tvAlarmBadge.background as? GradientDrawable)
                ?.setColor(android.graphics.Color.parseColor(badgeColor))
        }

        holder.binding.btnDeleteNote.setOnClickListener { onDelete(note) }
        holder.binding.root.setOnClickListener { onTap(note) }
        holder.binding.root.setOnLongClickListener {
            onDelete(note)
            true
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(a: Note, b: Note) = a.id == b.id
        override fun areContentsTheSame(a: Note, b: Note) = a == b
    }
}