package com.lily.rxandroidble.ui.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lily.rxandroidble.R
import com.polidea.rxandroidble2.scan.ScanResult

class BleListAdapter
    : RecyclerView.Adapter<BleListAdapter.BleViewHolder>(){

    lateinit var mContext: Context
    private var items: ArrayList<ScanResult>? = ArrayList()
    private lateinit var itemClickListner: ItemClickListener
    lateinit var itemView: View


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BleViewHolder {
        mContext = parent.context
        itemView = LayoutInflater.from(mContext).inflate(R.layout.rv_ble_item, parent, false)
        return BleViewHolder(itemView)

    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: BleViewHolder, position: Int) {
        holder.bind(items?.get(position))
        holder.itemView.setOnClickListener {
            itemClickListner.onClick(it, items?.get(position))
        }

    }
    override fun getItemCount(): Int {
        return items?.size?:0
    }
    fun setItem(item: ArrayList<ScanResult>?){

        if(item==null) return
        items = item
        notifyDataSetChanged()
    }


    inner class BleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {


        fun bind(scanResult: ScanResult?) {
            val bleName = itemView.findViewById<TextView>(R.id.ble_name)
            bleName.text = scanResult?.bleDevice?.name
            val bleAddress = itemView.findViewById<TextView>(R.id.ble_address)
            bleAddress.text = scanResult?.bleDevice?.macAddress

        }
    }
    interface ItemClickListener {
        fun onClick(view: View, scanResult: ScanResult?)
    }
    fun setItemClickListener(itemClickListener: ItemClickListener) {
        this.itemClickListner = itemClickListener
    }

}