package com.example.mega_photo.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.mega_photo.data.FilterItem
import com.example.mega_photo.databinding.ItemFilterBinding
import java.io.File

class FilterAdapter(
    private var filters: List<FilterItem>,
    private val onFilterClick: (FilterItem?) -> Unit,
    // [新增] 长按回调
    private val onFilterLongClick: (FilterItem) -> Unit
) : RecyclerView.Adapter<FilterAdapter.FilterViewHolder>() {

    private var selectedPosition = 0

    fun updateData(newFilters: List<FilterItem>) {
        filters = newFilters
        notifyDataSetChanged()
    }

    // [新增] 用于删除时的局部刷新
    fun notifyItemRemovedInternal(position: Int) {
        notifyItemRemoved(position)
    }

    inner class FilterViewHolder(val binding: ItemFilterBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterViewHolder {
        val binding = ItemFilterBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return FilterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FilterViewHolder, position: Int) {
        if (position == 0) {
            bindAddButton(holder)
            return
        }

        val realPosition = position - 1
        val item = filters[realPosition]

        holder.binding.tvName.text = item.name

        if (item.previewFileName != null) {
            val path = item.previewFileName
            if (path.startsWith("lut_example/")) {
                Glide.with(holder.itemView).load("file:///android_asset/$path").into(holder.binding.ivPreview)
            } else {
                Glide.with(holder.itemView).load(File(path)).into(holder.binding.ivPreview)
            }
        } else {
            holder.binding.ivPreview.setImageDrawable(null)
            holder.binding.ivPreview.setBackgroundColor(Color.DKGRAY)
        }

        if (position == selectedPosition) {
            holder.binding.tvName.setTextColor(Color.YELLOW)
            holder.binding.vSelection.setBackgroundResource(android.R.drawable.dialog_frame)
            holder.binding.vSelection.alpha = 0.5f
        } else {
            holder.binding.tvName.setTextColor(Color.WHITE)
            holder.binding.vSelection.background = null
        }

        holder.itemView.setOnClickListener {
            val previous = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(previous)
            notifyItemChanged(selectedPosition)
            onFilterClick(item)
        }

        // [新增] 绑定长按事件
        holder.itemView.setOnLongClickListener {
            onFilterLongClick(item)
            true // 返回 true 表示事件已消费
        }
    }

    private fun bindAddButton(holder: FilterViewHolder) {
        holder.binding.tvName.text = "导入"
        holder.binding.tvName.setTextColor(Color.WHITE)
        holder.binding.ivPreview.scaleType = ImageView.ScaleType.CENTER
        holder.binding.ivPreview.setImageResource(android.R.drawable.ic_input_add)
        holder.binding.ivPreview.setBackgroundColor(Color.parseColor("#333333"))
        holder.binding.vSelection.background = null

        holder.itemView.setOnClickListener {
            onFilterClick(null)
        }
        // 添加按钮不需要长按事件
        holder.itemView.setOnLongClickListener(null)
    }

    override fun getItemCount() = filters.size + 1
}
