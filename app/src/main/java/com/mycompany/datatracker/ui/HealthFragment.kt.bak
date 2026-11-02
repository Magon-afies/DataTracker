package com.mycompany.datatracker.ui

import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mycompany.datatracker.R
import com.mycompany.datatracker.autopsy.CacheCleanerService
import com.mycompany.datatracker.usage.AppHealth
import com.mycompany.datatracker.usage.AppHealthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HealthFragment : Fragment() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var adapter: HealthAdapter? = null
    private var currentItems = emptyList<AppHealth>()
    private var filteredItems = emptyList<AppHealth>()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_health, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val app = requireContext().applicationContext
        val rv = view.findViewById<RecyclerView>(R.id.health_list)
        val empty = view.findViewById<TextView>(R.id.health_empty)
        val loading = view.findViewById<ProgressBar>(R.id.health_loading)
        val btnCleanAll = view.findViewById<Button>(R.id.btn_clean_all)
        val search = view.findViewById<EditText>(R.id.health_search)
        
        rv.layoutManager = LinearLayoutManager(requireContext())
        
        // THE FIX: onShowTimeline is now passed correctly
        val ad = HealthAdapter(
            onOpenSettings = { pkg -> openAppSettings(pkg) },
            onKill = { pkg -> kill(pkg) },
            onClearCache = { pkg -> clearSingleCache(pkg) },
            onShowTimeline = { pkg, name, hours -> AppTimelineDialog.show(requireContext(), name, hours) }
        )
        rv.adapter = ad
        adapter = ad

        btnCleanAll.setOnClickListener {
            if (filteredItems.isEmpty()) return@setOnClickListener
            if (!CacheCleanerService.isEnabled(app)) {
                Toast.makeText(app, "Enable Accessibility first", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }
            val top10 = filteredItems.take(10).filter { it.cacheBytes > 0 }.map { it.packageName }
            if (top10.isNotEmpty()) {
                CacheCleanerService.startCleaning(app, top10)
                Toast.makeText(app, "Robot starting...", Toast.LENGTH_LONG).show()
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterList(s.toString(), ad, empty)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        loading.visibility = View.VISIBLE
        rv.visibility = View.GONE

        scope.launch {
            val items = withContext(Dispatchers.IO) {
                runCatching { AppHealthRepository(app).snapshot() }.getOrNull() ?: emptyList()
            }
            if (isAdded) {
                loading.visibility = View.GONE
                rv.visibility = View.VISIBLE
                currentItems = items
                filterList(search.text.toString(), ad, empty)
            }
        }
    }

    private fun filterList(query: String, ad: HealthAdapter, empty: TextView) {
        filteredItems = if (query.isBlank()) {
            currentItems
        } else {
            currentItems.filter { it.label.contains(query, ignoreCase = true) }
        }
        ad.submit(filteredItems)
        empty.visibility = if (filteredItems.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openAppSettings(pkg: String) {
        try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$pkg"))) } catch (_: Exception) {}
    }

    private fun kill(pkg: String) {
        try {
            requireContext().applicationContext.getSystemService(ActivityManager::class.java)?.killBackgroundProcesses(pkg)
            Toast.makeText(requireContext(), "Killed $pkg", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    private fun clearSingleCache(pkg: String) {
        val ctx = requireContext()
        if (!CacheCleanerService.isEnabled(ctx)) {
            Toast.makeText(ctx, "Enable Accessibility first", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        CacheCleanerService.startCleaning(ctx, listOf(pkg))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter = null
    }
}
