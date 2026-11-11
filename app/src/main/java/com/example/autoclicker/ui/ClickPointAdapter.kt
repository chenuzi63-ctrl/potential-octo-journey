package com.example.autoclicker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
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

    /**
     * 本地替换某个点的数据，不触发整列表重绑。
     * 之前每次编辑都经 ConfigBus → refreshPointList → notifyDataSetChanged 全量重绑，
     * 导致正在交互的输入框/开关被重建，出现“按钮无反应、输入被重置”。
     */
    fun replaceItem(updated: ClickPoint) {
        val index = items.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            items = items.toMutableList().also { it[index] = updated }
        }
    }

    /** 本地删除某个点（视图上精准移除一行） */
    fun removeItem(id: String) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) {
            items = items.toMutableList().also { it.removeAt(index) }
            notifyItemRemoved(index)
        }
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

        // 以界面当前值为准生成最新配置；同步更新本地 items，防止重绑后数据回滚
        val apply = {
            val updated = point.copy(
                intervalMs = b.etInterval.text.toString().toLongOrNull()?.coerceAtLeast(10) ?: point.intervalMs,
                durationMs = b.etDuration.text.toString().toLongOrNull()?.coerceAtLeast(0) ?: point.durationMs,
                count = b.etCount.text.toString().toIntOrNull()?.coerceAtLeast(0) ?: point.count,
                type = GestureType.entries.getOrElse(b.spinnerType.selectedItemPosition) { point.type },
                enabled = b.switchEnabled.isChecked
            )
            replaceItem(updated)
            onChanged(updated)
        }

        b.etInterval.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) apply() }
        b.etDuration.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) apply() }
        b.etCount.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) apply() }

        // 键盘“完成/下一步”直接保存，避免收键盘时焦点未变化导致输入丢失
        val editorListener = TextView.OnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                apply()
                true
            } else {
                false
            }
        }
        b.etInterval.setOnEditorActionListener(editorListener)
        b.etDuration.setOnEditorActionListener(editorListener)
        b.etCount.setOnEditorActionListener(editorListener)

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
