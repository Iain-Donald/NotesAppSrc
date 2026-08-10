package com.liblens.xyznotes

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.liblens.xyznotes.databinding.ItemNoteBinding
import com.liblens.xyznotes.databinding.ItemNotePinHeaderBinding

class NoteAdapter(
    private val onTap: (Note) -> Unit,
    private val onPin: (Note) -> Unit,
    private val onNotifyToggle: (Note, Boolean) -> Unit,
    private val onRename: (Note) -> Unit,
    private val onDelete: (Note) -> Unit
) : ListAdapter<NoteAdapter.Row, NoteAdapter.ViewHolder>(DiffCallback()) {

    /** Everything the row renders, so DiffUtil sees badge changes. */
    data class Row(val note: Note, val alarmCount: Int, val anyAlarmActive: Boolean)

    private var alarms: List<Alarm> = emptyList()
    private var notes: List<Note> = emptyList()

    fun updateAlarms(newAlarms: List<Alarm>) {
        alarms = newAlarms
        rebuild()
    }

    fun submitNotes(newNotes: List<Note>) {
        notes = newNotes
        rebuild()
    }

    private fun rebuild() {
        submitList(notes.map { note ->
            val mine = alarms.filter { it.noteId == note.id }
            Row(note, mine.size, mine.any { it.isActive })
        })
    }

    class ViewHolder(val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = getItem(position)
        val note = row.note
        val b = holder.binding

        b.tvNoteTitle.text = note.title
        b.tvNotePreview.text = note.content.take(80).ifBlank { "No content" }

        b.btnPin.bindPin(note.pinned)
        b.btnPin.setOnClickListener { onPin(note) }

        if (row.alarmCount == 0) {
            b.tvAlarmBadge.visibility = View.GONE
        } else {
            b.tvAlarmBadge.visibility = View.VISIBLE
            b.tvAlarmBadge.text = if (row.alarmCount > 9) "9+" else row.alarmCount.toString()
            (b.tvAlarmBadge.background as? GradientDrawable)?.setColor(
                if (row.anyAlarmActive) 0xFFFF5252.toInt() else 0xFF888888.toInt()
            )
        }

        b.btnNotify.setImageResource(
            if (note.notifyEnabled) R.drawable.baseline_notifications_24
            else R.drawable.outline_notifications_off_24
        )
        b.btnNotify.setOnClickListener { onNotifyToggle(note, !note.notifyEnabled) }
        b.btnDeleteNote.setOnClickListener { onDelete(note) }
        b.root.setOnClickListener { onTap(note) }
        b.root.setOnLongClickListener { onRename(note); true }
    }

    class DiffCallback : DiffUtil.ItemCallback<Row>() {
        override fun areItemsTheSame(a: Row, b: Row) = a.note.id == b.note.id
        override fun areContentsTheSame(a: Row, b: Row) = a == b
    }
}