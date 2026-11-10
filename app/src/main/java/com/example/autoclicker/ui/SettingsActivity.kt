package com.example.autoclicker.ui

import android.os.Bundle
import android.widget.RadioGroup
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.example.autoclicker.R
import com.example.autoclicker.databinding.ActivitySettingsBinding
import com.example.autoclicker.util.ConfigStore

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val state = ConfigStore.loadBarState(this)
        binding.seekScale.progress = ((state.scale - 0.5f) * 100).toInt().coerceIn(0, 100)
        binding.seekOpacity.progress = (state.opacity * 100).toInt().coerceIn(0, 100)

        when (state.orientation) {
            ConfigStore.Orientation.HORIZONTAL -> binding.radioHorizontal.isChecked = true
            ConfigStore.Orientation.VERTICAL -> binding.radioVertical.isChecked = true
            ConfigStore.Orientation.MINIMAL -> binding.radioMinimal.isChecked = true
        }

        val cfg = ConfigStore.loadConfig(this)
        binding.switchScreenOff.isChecked = cfg.stopOnScreenOff
        binding.switchRecord.isChecked = cfg.recordWhileRunning

        binding.radioGroup.setOnCheckedChangeListener { _: RadioGroup, _: Int -> persist() }
        binding.seekScale.setOnSeekBarChangeListener(SimpleSeekListener { persist() })
        binding.seekOpacity.setOnSeekBarChangeListener(SimpleSeekListener { persist() })
        binding.switchScreenOff.setOnCheckedChangeListener { _, _ -> persist() }
        binding.switchRecord.setOnCheckedChangeListener { _, _ -> persist() }
    }

    private fun persist() {
        val orientation = when (binding.radioGroup.checkedRadioButtonId) {
            R.id.radioVertical -> ConfigStore.Orientation.VERTICAL
            R.id.radioMinimal -> ConfigStore.Orientation.MINIMAL
            else -> ConfigStore.Orientation.HORIZONTAL
        }
        val scale = 0.5f + binding.seekScale.progress / 100f // 0.5x ~ 1.5x
        val opacity = binding.seekOpacity.progress / 100f

        val current = ConfigStore.loadBarState(this)
        ConfigStore.saveBarState(this, current.copy(orientation = orientation, scale = scale, opacity = opacity))

        val cfg = ConfigStore.loadConfig(this)
        ConfigStore.saveConfig(
            this,
            cfg.copy(
                stopOnScreenOff = binding.switchScreenOff.isChecked,
                recordWhileRunning = binding.switchRecord.isChecked
            )
        )
        OverlayController.refresh()
    }

    private class SimpleSeekListener(private val onStop: () -> Unit) : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) = onStop()
    }
}
