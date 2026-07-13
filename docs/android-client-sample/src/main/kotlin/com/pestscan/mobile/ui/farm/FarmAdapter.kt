package com.pestscan.mobile.ui.farm

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pestscan.mobile.R
import com.pestscan.mobile.domain.model.Farm

class FarmAdapter : ListAdapter<Farm, FarmAdapter.FarmViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FarmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_farm, parent, false)
        return FarmViewHolder(view as TextView)
    }

    override fun onBindViewHolder(holder: FarmViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FarmViewHolder(private val label: TextView) : RecyclerView.ViewHolder(label) {
        fun bind(farm: Farm) {
            label.text = farm.name
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<Farm>() {
        override fun areItemsTheSame(oldItem: Farm, newItem: Farm): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Farm, newItem: Farm): Boolean = oldItem == newItem
    }
}
