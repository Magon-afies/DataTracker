package com.mycompany.datatracker.ui

import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mycompany.datatracker.R
import com.mycompany.datatracker.autopsy.CacheCleanerService
import com.mycompany.datatracker.usage.AppHealthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HealthFragment : Fragment() {
    private var adapter: HealthAdapter? = null

    override fun onCreateView(
        i: LayoutInflater, c: ViewGroup?, s: Bundle?
    ): View = i.inflate(R.layout.fragment_health, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val app = requireContext().applicationContext
        val rv = view.findViewById<RecyclerView>(R.id.health_list)
        rv.layoutManager = LinearLayoutManager(requireContext())
        val ad = HealthAdapter(
            onOpenSettings = { pkg -> openAppSettings(pkg) },
            onKill = { pkg -> kill(pkg) },
            onClearCache = { pkg -> clearCache(pkg) },
            onRestrict = { pkg -> restrict(pkg) }
        )
        rv.adapter = ad
        adapter = ad

        viewLifecycleOwner.lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                runCatching { AppHealthRepository(app).snapshot() }.getOrNull() ?: emptyList()
            }
            if (isAdded) ad.submit(items)
        }
    }

    private fun openAppSettings(pkg: String) {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$pkg"))
            )
        } catch (e: Exception) {
            Toast.makeText(requireContext(), e.message ?: "Cannot open", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restrict(pkg: String) {
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
            try {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:$pkg"))
                )
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "Cannot open", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun kill(pkg: String) {
        try {
            requireContext().applicationContext
                .getSystemService(ActivityManager::class.java)
                ?.killBackgroundProcesses(pkg)
            Toast.makeText(requireContext(), "Killed $pkg", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), e.message ?: "Cannot kill", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearCache(pkg: String) {
        val ctx = requireContext()
        if (!CacheCleanerService.isEnabled(ctx)) {
            Toast.makeText(
                ctx,
                "Enable DataTracker Accessibility first, then tap again",
                Toast.LENGTH_LONG
            ).show()
            try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: Exception) {}
            return
        }
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$pkg"))
            )
            Toast.makeText(ctx, "Auto-clearing $pkg …", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(ctx, e.message ?: "Cannot open", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter = null
    }
}
