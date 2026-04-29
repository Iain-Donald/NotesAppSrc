package com.example.iainnotes

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.iainnotes.databinding.ItemNoteBinding
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.ItemTouchHelper
import com.example.iainnotes.databinding.ItemNotePinHeaderBinding

class NoteAdapter(
    private val onTap: (Note) -> Unit,
    private val onPin: (Note) -> Unit,
    private val onNotifyToggle: (Note, Boolean) -> Unit,
    private val onDelete: (Note) -> Unit
) : ListAdapter<NoteAdapter.Item, RecyclerView.ViewHolder>(DiffCallback()) {

    sealed class Item {
        data class Header(val label: String) : Item()
        data class NoteItem(val note: Note) : Item()
    }

    private var alarms: List<Alarm> = emptyList()

    fun updateAlarms(newAlarms: List<Alarm>) {
        alarms = newAlarms
        notifyDataSetChanged()
    }

    fun submitNotes(notes: List<Note>) {
        val items = mutableListOf<Item>()
        val pinned = notes.filter { it.pinned }
        val unpinned = notes.filter { !it.pinned }
        if (pinned.isNotEmpty()) {
            items.add(Item.Header("Pins"))
            items.addAll(pinned.map { Item.NoteItem(it) })
        }
        items.addAll(unpinned.map { Item.NoteItem(it) })
        submitList(items)
    }

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is Item.Header -> 0
        is Item.NoteItem -> 1
    }

    inner class HeaderViewHolder(val binding: ItemNotePinHeaderBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class NoteViewHolder(val binding: ItemNoteBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        when (viewType) {
            0 -> HeaderViewHolder(
                ItemNotePinHeaderBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
            else -> NoteViewHolder(
                ItemNoteBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is Item.Header -> (holder as HeaderViewHolder).binding.tvPinHeader.text = item.label
            is Item.NoteItem -> bindNote(holder as NoteViewHolder, item.note)
        }
    }

    private fun bindNote(holder: NoteViewHolder, note: Note) {
        holder.binding.tvNoteTitle.text = note.title
        holder.binding.tvNotePreview.text = note.content.take(80).ifBlank { "No content" }

        if (note.pinned) {
            holder.binding.btnPin.setImageResource(R.drawable.baseline_push_pin_24)
            holder.binding.btnPin.imageTintList = androidx.core.content.ContextCompat.getColorStateList(
                holder.binding.btnPin.context, R.color.icon_accent
            )
        } else {
            holder.binding.btnPin.setImageResource(R.drawable.outline_push_pin_24)
            holder.binding.btnPin.imageTintList = androidx.core.content.ContextCompat.getColorStateList(
                holder.binding.btnPin.context, R.color.appGrey1Lighter
            )
        }
        holder.binding.btnPin.setOnClickListener { onPin(note) }

        val noteAlarms = alarms.filter { it.noteId == note.id }
        if (noteAlarms.isEmpty()) {
            holder.binding.tvAlarmBadge.visibility = View.GONE
        } else {
            holder.binding.tvAlarmBadge.visibility = View.VISIBLE
            val count = noteAlarms.size
            holder.binding.tvAlarmBadge.text = if (count > 9) "9+" else count.toString()
            val anyActive = noteAlarms.any { it.isActive }
            val badgeColor = if (anyActive) "#FF5252" else "#888888"
            (holder.binding.tvAlarmBadge.background as? GradientDrawable)
                ?.setColor(android.graphics.Color.parseColor(badgeColor))
        }

        holder.binding.btnNotify.setImageResource(
            if (note.notifyEnabled) R.drawable.baseline_notifications_24
            else R.drawable.outline_notifications_off_24
        )
        holder.binding.btnNotify.setOnClickListener { onNotifyToggle(note, !note.notifyEnabled) }
        holder.binding.btnDeleteNote.setOnClickListener { onDelete(note) }
        holder.binding.root.setOnClickListener { onTap(note) }
        holder.binding.root.setOnLongClickListener {
            onDelete(note)
            true
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(a: Item, b: Item) = when {
            a is Item.Header && b is Item.Header -> a.label == b.label
            a is Item.NoteItem && b is Item.NoteItem -> a.note.id == b.note.id
            else -> false
        }
        override fun areContentsTheSame(a: Item, b: Item) = a == b
    }
}