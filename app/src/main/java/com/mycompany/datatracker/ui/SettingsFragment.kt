package com.crestpoint.datatracker.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.widget.Spinner
import android.widget.LinearLayout
import android.widget.TextView
import android.content.Intent
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.data.Prefs
import com.crestpoint.datatracker.data.PlanStore
import com.crestpoint.datatracker.util.FormatBytes
import com.crestpoint.datatracker.util.PhoneStorage
import com.crestpoint.datatracker.work.Alerts
import com.crestpoint.datatracker.work.SnapshotWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { updateNotifButton() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val prefs = Prefs(requireContext())
        val enabled = view.findViewById<SwitchCompat>(R.id.alerts_switch)
        val appCap = view.findViewById<EditText>(R.id.cap_app_mb)
        val bgCap = view.findViewById<EditText>(R.id.cap_bg_mb)
        val debug = view.findViewById<EditText>(R.id.debug_usage_percent)

        enabled.isChecked = prefs.alertsEnabled
        appCap.setText(prefs.appCapMb.toString())
        bgCap.setText(prefs.bgCapMb.toString())
        debug.setText(if (prefs.debugUsagePercent == 0) "" else prefs.debugUsagePercent.toString())

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = Calendar.getInstance()
        val expiry = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 30) }
        view.findViewById<EditText>(R.id.plan_start).setText(dateFormat.format(today.time))
        view.findViewById<EditText>(R.id.plan_expiry).setText(dateFormat.format(expiry.time))

        view.findViewById<Button>(R.id.btn_save_plan).setOnClickListener {
            val size = view.findViewById<EditText>(R.id.plan_size).text.toString().toDoubleOrNull()
            val carrier = view.findViewById<EditText>(R.id.plan_carrier).text.toString().trim()
            val price = view.findViewById<EditText>(R.id.plan_price).text.toString()
                .replace(",", "").trim().toDoubleOrNull() ?: 0.0
            val start = parseDate(view.findViewById<EditText>(R.id.plan_start).text.toString(), false)
            val end = parseDate(view.findViewById<EditText>(R.id.plan_expiry).text.toString(), true)
            if (size == null || size <= 0 || carrier.isBlank() || start == null || end == null || end < start) {
                Toast.makeText(requireContext(), "Enter a valid plan, carrier and dates", Toast.LENGTH_SHORT).show()
            } else {
                val multiplier = if (view.findViewById<Spinner>(R.id.plan_unit).selectedItemPosition == 1) 1024.0 else 1.0
                PlanStore(requireContext()).create(
                    (size * multiplier * 1024.0 * 1024.0).toLong(), carrier, start, end, price.coerceAtLeast(0.0)
                )
                SnapshotWorker.schedule(requireContext())
                Toast.makeText(requireContext(), R.string.plan_saved, Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<Button>(R.id.btn_save).setOnClickListener {
            prefs.alertsEnabled = enabled.isChecked
            prefs.appCapMb = appCap.text.toString().toIntOrNull() ?: 0
            prefs.bgCapMb = bgCap.text.toString().toIntOrNull() ?: 0
            prefs.debugUsagePercent = debug.text.toString().toIntOrNull() ?: 0
            Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show()
        }

        refreshStorage(view)
        view.findViewById<Button>(R.id.btn_clear_cache).setOnClickListener {
            PhoneStorage.clearAppCache(requireContext())
            Toast.makeText(requireContext(), R.string.storage_cleared, Toast.LENGTH_SHORT).show()
            refreshStorage(view)
        }
        view.findViewById<Button>(R.id.btn_storage_settings).setOnClickListener {
            val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), R.string.storage_settings_unavailable, Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<SwitchCompat>(R.id.alerts_switch).setOnLongClickListener {
            view.findViewById<LinearLayout>(R.id.debug_panel).visibility = View.VISIBLE
            true
        }
        view.findViewById<Button>(R.id.btn_clear_debug).setOnClickListener {
            debug.setText("")
            prefs.debugUsagePercent = 0
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

    private fun refreshStorage(view: View) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val snapshot = PhoneStorage.snapshot(requireContext())
            withContext(Dispatchers.Main) {
                view.findViewById<TextView>(R.id.storage_summary).text = getString(
                    R.string.storage_summary,
                    FormatBytes.human(snapshot.usedBytes),
                    FormatBytes.human(snapshot.totalBytes),
                    FormatBytes.human(snapshot.freeBytes)
                )
                view.findViewById<TextView>(R.id.storage_cache).text = getString(
                    R.string.storage_cache, FormatBytes.human(snapshot.cacheBytes)
                )
            }
        }
    }

    private fun parseDate(value: String, endOfDay: Boolean): Long? = try {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value) ?: return null
        Calendar.getInstance().apply {
            time = date
            if (endOfDay) {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            } else {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }.timeInMillis
    } catch (_: Exception) { null }
}
