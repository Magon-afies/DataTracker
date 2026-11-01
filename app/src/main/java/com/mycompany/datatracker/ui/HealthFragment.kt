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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mycompany.datatracker.R
import com.mycompany.datatracker.usage.AppHealthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HealthFragment : Fragment() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var adapter: HealthAdapter? = null

    override fun onCreateView(
        i: LayoutInflater, c: ViewGroup?, s: Bundle?
    ): View = i.inflate(R.layout.fragment_health, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val app = requireContext().applicationContext
        val rv = view.findViewById<RecyclerView>(R.id.health_list)
        rv.layoutManager = LinearLayoutManager(requireContext())
        val ad = HealthAdapter(
            onOpenSettings = { pkg ->
                try {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:$pkg"))
                    )
                } catch (e: Exception) {
                    Toast.makeText(app, e.message ?: "Cannot open", Toast.LENGTH_SHORT).show()
                }
            },
            onKill = { pkg ->
                try {
                    app.getSystemService(ActivityManager::class.java)
                        ?.killBackgroundProcesses(pkg)
                    Toast.makeText(app, "Killed $pkg", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(app, e.message ?: "Cannot kill", Toast.LENGTH_SHORT).show()
                }
            }
        )
        rv.adapter = ad
        adapter = ad

        scope.launch {
            val items = withContext(Dispatchers.IO) {
                runCatching { AppHealthRepository(app).snapshot() }
                    .getOrNull() ?: emptyList()
            }
            if (isAdded) ad.submit(items)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter = null
    }
}
