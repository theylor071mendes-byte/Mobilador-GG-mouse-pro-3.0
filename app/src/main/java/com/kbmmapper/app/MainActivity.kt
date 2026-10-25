package com.kbmmapper.app

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.appcompat.app.AppCompatActivity
import com.kbmmapper.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(
            KbmAccessibilityService.PREFS_NAME,
            Context.MODE_PRIVATE
        )

        binding.btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        setupSwitch(binding.switchBack, KbmAccessibilityService.KEY_BACK)
        setupSwitch(binding.switchHome, KbmAccessibilityService.KEY_HOME)
        setupSwitch(binding.switchRecents, KbmAccessibilityService.KEY_RECENTS)
        setupSwitch(binding.switchNotifications, KbmAccessibilityService.KEY_NOTIFICATIONS)
        setupSwitch(binding.switchQuickSettings, KbmAccessibilityService.KEY_QUICK_SETTINGS)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun setupSwitch(switch: android.widget.CompoundButton, prefKey: String) {
        switch.isChecked = prefs.getBoolean(prefKey, true)
        switch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(prefKey, isChecked).apply()
        }
    }

    private fun updateStatus() {
        val enabled = isAccessibilityServiceEnabled()
        if (enabled) {
            binding.tvStatus.text = getString(R.string.status_enabled)
            binding.tvStatus.setTextColor(getColor(R.color.status_enabled))
            binding.btnOpenAccessibility.text = getString(R.string.btn_manage_accessibility)
        } else {
            binding.tvStatus.text = getString(R.string.status_disabled)
            binding.tvStatus.setTextColor(getColor(R.color.status_disabled))
            binding.btnOpenAccessibility.text = getString(R.string.btn_enable_accessibility)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, KbmAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            val componentName = ComponentName.unflattenFromString(splitter.next())
            if (componentName != null && componentName == expected) {
                return true
            }
        }
        return false
    }
}
