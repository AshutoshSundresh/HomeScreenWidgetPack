package com.ashutoshsun.homescreenwidgetpack

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Dynamic colors are applied globally in WidgetPackApplication
        
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupUI()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun setupUI() {
        val statusText = findViewById<TextView>(R.id.permission_status)
        val enableButton = findViewById<Button>(R.id.enable_permission_button)
        val settingsButton = findViewById<Button>(R.id.settings_button)
        val infoButton = findViewById<ImageButton>(R.id.info_button)

        enableButton.setOnClickListener {
            openNotificationListenerSettings()
        }

        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        infoButton.setOnClickListener {
            showAboutDialog()
        }

        updatePermissionStatus()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("About")
            .setMessage("Authored by Ashutosh Sundresh in 2025")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun updatePermissionStatus() {
        val statusText = findViewById<TextView>(R.id.permission_status)
        val enableButton = findViewById<Button>(R.id.enable_permission_button)

        if (isNotificationListenerEnabled()) {
            statusText.text = "✓ Notification Access Enabled\n\nYou can now add the music widget to your home screen!"
            enableButton.text = "Open Notification Access Settings"
        } else {
            statusText.text = "⚠ Notification Access Required\n\nThe music widget needs notification access to display currently playing media."
            enableButton.text = "Enable Notification Access"
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val componentName = ComponentName(this, MediaNotificationListener::class.java)
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        return enabledListeners?.contains(componentName.flattenToString()) == true
    }

    private fun openNotificationListenerSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open settings", Toast.LENGTH_SHORT).show()
        }
    }
}