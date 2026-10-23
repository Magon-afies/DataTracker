package com.mycompany.datatracker.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.mycompany.datatracker.R
import com.mycompany.datatracker.data.Prefs
import com.mycompany.datatracker.work.Alerts

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { updateNotifButton() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val prefs = Prefs(requireContext())
        val enabled = view.findViewById<SwitchCompat>(R.id.alerts_switch)
        val appCap = view.findViewById<EditText>(R.id.cap_app_mb)
        val bgCap = view.findViewById<EditText>(R.id.cap_bg_mb)

        enabled.isChecked = prefs.alertsEnabled
        appCap.setText(prefs.appCapMb.toString())
        bgCap.setText(prefs.bgCapMb.toString())

        view.findViewById<Button>(R.id.btn_save).setOnClickListener {
            prefs.alertsEnabled = enabled.isChecked
            prefs.appCapMb = appCap.text.toString().toIntOrNull() ?: 0
            prefs.bgCapMb = bgCap.text.toString().toIntOrNull() ?: 0
            Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button>(R.id.btn_notif).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        updateNotifButton()
    }

    private fun updateNotifButton() {
        view?.findViewById<Button>(R.id.btn_notif)?.visibility =
            if (Alerts.canNotify(requireContext())) View.GONE else View.VISIBLE
    }
}
