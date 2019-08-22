package com.example.gRPCTest

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.item_result.view.*
import java.util.*

class ResultAdapter(private var mResults: ArrayList<String>, val viewModel: MainViewModel) :
    androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {

    fun addResult(result: String) {
        mResults.add(0, result)
        notifyItemInserted(0)
    }

    fun upDateResultList(stringArray: ArrayList<String>) {
        mResults = stringArray
        notifyDataSetChanged()
    }
    override fun getItemCount(): Int = mResults.size
    fun getResults(): ArrayList<String> = mResults

    override fun onBindViewHolder(
        holder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
        position: Int
    ) {
        holder.itemView.rowText.text = mResults[position]
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): androidx.recyclerview.widget.RecyclerView.ViewHolder {
        val rowView = LayoutInflater.from(parent.context).inflate(R.layout.item_result, parent, false)
        return ViewHolderOfCell(rowView)
    }

    class ViewHolderOfCell(rowView: View) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(rowView)
}