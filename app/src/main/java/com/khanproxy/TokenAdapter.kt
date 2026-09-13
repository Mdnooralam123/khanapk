package com.khanproxy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TokenAdapter(val onClick: (TokenItem) -> Unit) : RecyclerView.Adapter<TokenAdapter.VH>() {
    private val items = mutableListOf<TokenItem>()
    private val fmt = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())

    fun submit(list: List<TokenItem>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_token, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = items[position]
        holder.tvName.text = t.name
        holder.tvUid.text = if (t.uid.isNotEmpty()) "UID: ${t.uid}" else t.region
        holder.tvPreview.text = t.jwt.take(90) + if (t.jwt.length > 90) "..." else ""
        holder.tvTime.text = "🕐 " + fmt.format(Date(t.receivedAt))
        holder.itemView.setOnClickListener { onClick(t) }
        holder.btnCopy.setOnClickListener { onClick(t) }
    }

    override fun getItemCount() = items.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvName)
        val tvUid: TextView = v.findViewById(R.id.tvUid)
        val tvPreview: TextView = v.findViewById(R.id.tvPreview)
        val tvTime: TextView = v.findViewById(R.id.tvTime)
        val btnCopy: TextView = v.findViewById(R.id.btnCopy)
    }
}
