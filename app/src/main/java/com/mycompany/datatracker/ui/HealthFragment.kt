package com.mycompany.datatracker.ui

import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mycompany.datatracker.R
import com.mycompany.datatracker.autopsy.CacheCleanerService
import com.mycompany.datatracker.cleaner.CacheBot
import com.mycompany.datatracker.cleaner.CacheBotActivity
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
            onClearCache = { pkg -> cleanCaches(listOf(pkg)) },
            onRestrict = { pkg -> restrict(pkg) }
        )
        rv.adapter = ad
        adapter = ad

        view.findViewById<Button>(R.id.health_clean_all).setOnClickListener { cleanAll() }

        viewLifecycleOwner.lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                runCatching { AppHealthRepository(app).snapshot() }.getOrNull() ?: emptyList()
            }
            if (isAdded) ad.submit(items)
        }
    }

    private fun accessibilityReady(): Boolean {
        val ctx = requireContext()
        if (CacheCleanerService.isEnabled(ctx)) return true
        Toast.makeText(ctx, R.string.bot_need_access, Toast.LENGTH_LONG).show()
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {
        }
        return false
    }

    private fun cleanCaches(packages: List<String>) {
        if (!accessibilityReady()) return
        CacheBotActivity.start(requireContext(), packages)
    }

    private fun cleanAll() {
        if (!accessibilityReady()) return
        val app = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val targets = withContext(Dispatchers.IO) {
                runCatching { CacheBot.targets(app) }.getOrDefault(emptyList())
            }
            if (!isAdded) return@launch
            if (targets.isEmpty()) {
                Toast.makeText(requireContext(), R.string.bot_nothing, Toast.LENGTH_LONG).show()
            } else {
                CacheBotActivity.start(requireContext(), targets.map { it.packageName })
            }
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

    override fun onDestroyView() {
        super.onDestroyView()
        adapter = null
    }
}
