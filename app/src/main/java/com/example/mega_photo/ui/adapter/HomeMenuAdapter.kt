package com.example.mega_photo.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.mega_photo.databinding.ItemHomeMenuBinding

data class MenuItem(val title: String, val actionId: Int)

class HomeMenuAdapter(
    private val items: List<MenuItem>,
    // [修改] 回调增加 View 参数：(MenuItem, View) -> Unit
    private val onItemClick: (MenuItem, View) -> Unit
) : RecyclerView.Adapter<HomeMenuAdapter.MenuViewHolder>() {

    inner class MenuViewHolder(val binding: ItemHomeMenuBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val binding = ItemHomeMenuBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MenuViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvTitle.text = item.title

        // [修改] 传递点击的卡片视图 (cardContainer)
        holder.binding.cardContainer.setOnClickListener {
            onItemClick(item, holder.binding.cardContainer)
        }
    }

    override fun getItemCount() = items.size
}