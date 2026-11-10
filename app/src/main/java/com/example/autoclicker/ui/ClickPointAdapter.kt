package com.example.autoclicker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.autoclicker.R
import com.example.autoclicker.databinding.ItemClickPointBinding
import com.example.autoclicker.model.ClickPoint
import com.example.autoclicker.model.GestureType

class ClickPointAdapter(
    private val onChanged: (ClickPoint) -> Unit,
    private val onDelete: (ClickPoint) -> Unit
) : RecyclerView.Adapter<ClickPointAdapter.VH>() {

    private var items: List<ClickPoint> = emptyList()

    fun submit(list: List<ClickPoint>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(val binding: ItemClickPointBinding) : RecyclerView.ViewHolder(binding.root) {
        var suppress: Boolean = false
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemClickPointBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val point = items[position]
        val b = holder.binding
        val ctx = holder.itemView.context

        b.tvLabel.text = point.label.ifEmpty { "点 ${position + 1}" }
        b.etInterval.setText(point.intervalMs.toString())
        b.etDuration.setText(point.durationMs.toString())
        b.etCount.setText(point.count.toString())

        val typeAdapter = ArrayAdapter(
            ctx,
            android.R.layout.simple_spinner_item,
            GestureType.entries.map { it.name }
        )
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        b.spinnerType.adapter = typeAdapter

        holder.suppress = true
        b.spinnerType.setSelection(GestureType.entries.indexOf(point.type).coerceAtLeast(0))
        b.switchEnabled.isChecked = point.enabled
        holder.suppress = false

        val apply = {
            val updated = point.copy(
                intervalMs = b.etInterval.text.toString().toLongOrNull()?.coerceAtLeast(10) ?: point.intervalMs,
                durationMs = b.etDuration.text.toString().toLongOrNull()?.coerceAtLeast(0) ?: point.durationMs,
                count = b.etCount.text.toString().toIntOrNull()?.coerceAtLeast(0) ?: point.count,
                type = GestureType.entries.getOrElse(b.spinnerType.selectedItemPosition) { point.type },
                enabled = b.switchEnabled.isChecked
            )
            onChanged(updated)
        }

        b.etInterval.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) apply() }
        b.etDuration.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) apply() }
        b.etCount.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) apply() }
        b.spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!holder.suppress) apply()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        b.switchEnabled.setOnCheckedChangeListener { _, _ -> if (!holder.suppress) apply() }
        b.btnDelete.setOnClickListener { onDelete(point) }
    }

    override fun getItemCount(): Int = items.size
}
