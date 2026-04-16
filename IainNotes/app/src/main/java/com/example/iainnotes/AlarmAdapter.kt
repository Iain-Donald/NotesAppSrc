package com.example.iainnotes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.iainnotes.databinding.ItemAlarmBinding

//json
class AlarmAdapter(
    private val onToggle: (Alarm, Boolean) -> Unit,
    private val onEdit: (Alarm) -> Unit,
    private val onDelete: (Alarm) -> Unit
) : ListAdapter<Alarm, AlarmAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(val binding: ItemAlarmBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(
            ItemAlarmBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val alarm = getItem(position)
        holder.binding.tvAlarmTime.text =
            String.format("%02d:%02d", alarm.timeHour, alarm.timeMinute)
        holder.binding.tvAlarmName.text = alarm.name
        holder.binding.tvAlarmRepeat.text =
            if (alarm.repeatDays.isEmpty()) "Once" else alarm.repeatDays.joinToString(", ")
        holder.binding.tvAlarmActive.text = if (alarm.isActive) "Active" else "Inactive"

        holder.binding.switchAlarmActive.setOnCheckedChangeListener(null)
        holder.binding.switchAlarmActive.isChecked = alarm.isActive
        holder.binding.switchAlarmActive.setOnCheckedChangeListener { _, checked ->
            onToggle(alarm, checked)
        }

        holder.binding.btnEditAlarm.setOnClickListener { onEdit(alarm) }
        holder.binding.btnDeleteAlarm.setOnClickListener { onDelete(alarm) }
    }

    class DiffCallback : DiffUtil.ItemCallback<Alarm>() {
        override fun areItemsTheSame(a: Alarm, b: Alarm) = a.id == b.id
        override fun areContentsTheSame(a: Alarm, b: Alarm) = a == b
    }
}