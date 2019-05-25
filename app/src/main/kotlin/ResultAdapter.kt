package com.example.gRPCTest

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.gRPCTest.R
import kotlinx.android.synthetic.main.item_result.view.*
import java.util.ArrayList

class ResultAdapter(private var mResults: ArrayList<String>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

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

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        holder.itemView.rowText.text = mResults[position]
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val rowView = LayoutInflater.from(parent.context).inflate(R.layout.item_result, parent, false)
        return ViewHolderOfCell(rowView)
    }

    class ViewHolderOfCell(rowView: View) : RecyclerView.ViewHolder(rowView)
}