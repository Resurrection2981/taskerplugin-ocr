package io.github.sauvio.ocr

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.github.sauvio.ocr.tasker.toToast

class SettingsActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings);
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
    }
}

class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.ocr_preference, rootKey)

        val adaptiveThresholdConfigurationPreference: Preference? =
            findPreference(getString(R.string.key_adaptive_threshold_configuration))
        adaptiveThresholdConfigurationPreference?.onPreferenceClickListener = this
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {

        return true
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        return when(preference.key){
            // Perform click key_adaptive_threshold_configuration
            getString(R.string.key_adaptive_threshold_configuration) -> {
                parentFragmentManager.beginTransaction()
                    .replace(
                        R.id.settings,
                        AdaptiveThresholdSubPreferenceFragment()
                    )
                    .addToBackStack(getString(R.string.key_adaptive_threshold_configuration))
                    .commit()
                true
            }
            else -> true
        }
    }

}

class AdaptiveThresholdSubPreferenceFragment: PreferenceFragmentCompat(),Preference.OnPreferenceChangeListener,
    Preference.OnPreferenceClickListener {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.ocr_preference_adaptive_threshold, rootKey)
        val adaptiveThresholdBlockSizePreference: EditTextPreference? =
        findPreference(getString(R.string.key_adaptive_threshold_block_size))
        val adaptiveThresholdMaxValuePreference:EditTextPreference? =
        findPreference(getString(R.string.key_adaptive_threshold_max_value))

        adaptiveThresholdBlockSizePreference?.onPreferenceClickListener = this
        adaptiveThresholdBlockSizePreference?.onPreferenceChangeListener = this
        adaptiveThresholdMaxValuePreference?.onPreferenceClickListener = this
        adaptiveThresholdMaxValuePreference?.onPreferenceChangeListener = this
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        return when(preference.key){
            getString(R.string.key_adaptive_threshold_max_value) -> {
                val intValue = newValue.toString().toDoubleOrNull()
                if(intValue != null && intValue != 0.0) {
                    true
                } else{
                    getString(R.string.hint_adaptive_threshold_max_value).toToast(requireContext())
                    false
                }
            }
            getString(R.string.key_adaptive_threshold_block_size) -> {
                val intValue = newValue.toString().toIntOrNull()
                if (intValue != null && intValue and 1 == 1 && intValue > 1) {
                    true
                } else {
                    getString(R.string.hint_adaptive_threshold_block_size).toToast(requireContext())
                    false
                }
            }

            else -> true
        }
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        return when(preference.key){
            getString(R.string.key_adaptive_threshold_max_value) -> {
                val editTextPreference = preference as? EditTextPreference
                editTextPreference?.setOnBindEditTextListener {
                    it.hint = getString(R.string.hint_adaptive_threshold_max_value)
                }
                true
            }
            getString(R.string.key_adaptive_threshold_block_size) -> {
                val editTextPreference = preference as? EditTextPreference
                editTextPreference?.setOnBindEditTextListener {
                    it.hint = getString(R.string.hint_adaptive_threshold_block_size)
                }
                true
            }
            else -> true
        }
    }
}