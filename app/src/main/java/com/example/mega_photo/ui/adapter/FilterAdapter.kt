package com.example.mega_photo.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.example.mega_photo.data.FilterItem
import com.example.mega_photo.databinding.ItemFilterBinding

class FilterAdapter(
    private val filters: List<FilterItem>,
    private val onFilterClick: (FilterItem) -> Unit
) : RecyclerView.Adapter<FilterAdapter.FilterViewHolder>() {

    private var selectedPosition = 0

    inner class FilterViewHolder(val binding: ItemFilterBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterViewHolder {
        val binding = ItemFilterBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return FilterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FilterViewHolder, position: Int) {
        val item = filters[position]
        holder.binding.tvName.text = item.name

        // [新增] 使用 Glide 加载 assets 图片
        if (item.previewFileName != null) {
            val assetPath = "file:///android_asset/${item.previewFileName}"
            Glide.with(holder.itemView)
                .load(assetPath)
                .into(holder.binding.ivPreview)
        } else {
            // 原图没有预览图的话，可以用一个默认图或者清除
            holder.binding.ivPreview.setImageDrawable(null)
            holder.binding.ivPreview.setBackgroundColor(Color.DKGRAY)
        }

        // 选中状态处理
        if (position == selectedPosition) {
            holder.binding.tvName.setTextColor(Color.YELLOW)
            holder.binding.vSelection.setBackgroundResource(android.R.drawable.dialog_frame) // 简单的高亮框，或者自定义 drawable
            holder.binding.vSelection.alpha = 0.5f // 简单的选中效果
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
    }

    override fun getItemCount() = filters.size
}