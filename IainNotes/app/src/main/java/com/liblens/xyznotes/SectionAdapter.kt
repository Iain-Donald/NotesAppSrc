package com.liblens.xyznotes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.liblens.xyznotes.databinding.ItemSectionBinding

//json
class SectionAdapter(
    private val onClick: (Section) -> Unit,
    private val onRename: (Section) -> Unit,
    private val onDelete: (Section) -> Unit,
    private val onPin: (Section) -> Unit,
    private val onMove: (fromPos: Int, toPos: Int) -> Unit,
    private var categories: List<Category> = emptyList()
) : ListAdapter<Section, SectionAdapter.ViewHolder>(DiffCallback()) {

    fun currentIds(): List<String> = (0 until itemCount).map { getItem(it).id }
    fun moveItem(from: Int, to: Int) {
        val list = currentList.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        submitList(list)
        onMove(from, to)
    }

    fun updateCategories(list: List<Category>) {
        categories = list
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: ItemSectionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(
            ItemSectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val section = getItem(position)
        holder.binding.tvSectionName.text = section.name
        holder.binding.root.setOnClickListener { onClick(section) }
        holder.binding.root.setOnLongClickListener {
            android.util.Log.d("DROPDOWN", "long press on ${section.name}")
            onRename(section)
            android.util.Log.d("DROPDOWN", "onRename returned")
            true
        }
        holder.binding.btnDeleteSection.setOnClickListener { onDelete(section) }

        val colorId = categories.find { it.id == section.categoryId }?.colorId ?: Palette.NONE
        if (colorId == Palette.NONE) {
            holder.binding.colorBar.visibility = View.GONE
        } else {
            holder.binding.colorBar.visibility = View.VISIBLE
            holder.binding.colorBar.setBackgroundColor(Palette.colorOf(colorId))
        }

        if (section.pinned) {
            holder.binding.btnPin.setImageResource(R.drawable.baseline_push_pin_24)
            holder.binding.btnPin.imageTintList = androidx.core.content.ContextCompat.getColorStateList(
                holder.binding.btnPin.context, R.color.icon_accent
            )
        } else {
            holder.binding.btnPin.setImageResource(R.drawable.outline_push_pin_24)
            holder.binding.btnPin.imageTintList = androidx.core.content.ContextCompat.getColorStateList(
                holder.binding.btnPin.context, R.color.buttonL3
            )
        }
        holder.binding.btnPin.setOnClickListener { onPin(section) }
    }

    class DiffCallback : DiffUtil.ItemCallback<Section>() {
        override fun areItemsTheSame(a: Section, b: Section) = a.id == b.id
        override fun areContentsTheSame(a: Section, b: Section) = a == b
    }
}