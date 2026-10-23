package com.mycompany.datatracker

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.mycompany.datatracker.ui.AppsFragment
import com.mycompany.datatracker.ui.DashboardFragment
import com.mycompany.datatracker.ui.MediaFragment
import com.mycompany.datatracker.ui.SettingsFragment
import com.mycompany.datatracker.work.Alerts
import com.mycompany.datatracker.work.SnapshotWorker
import com.mycompany.datatracker.data.Prefs

class MainActivity : AppCompatActivity() {

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Alerts.ensureChannel(this)
        SnapshotWorker.schedule(this)

        if (savedInstanceState == null) swap(DashboardFragment())
        playIntro()

        findViewById<BottomNavigationView>(R.id.bottom_nav)
            .setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_dashboard -> swap(DashboardFragment())
                    R.id.nav_apps -> swap(AppsFragment())
                    R.id.nav_media -> swap(MediaFragment())
                    R.id.nav_settings -> swap(SettingsFragment())
                }
                true
            }
    }

    private fun swap(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_container, fragment)
            .commit()
    }

    private fun playIntro() {
        val overlay = findViewById<View>(R.id.intro_overlay)
        val logo = findViewById<ImageView>(R.id.intro_logo)
        val title = findViewById<TextView>(R.id.intro_title)
        val tagline = findViewById<View>(R.id.intro_tagline)
        val subtitle = findViewById<View>(R.id.intro_subtitle)
        val product = findViewById<View>(R.id.intro_product)
        val credit = findViewById<View>(R.id.intro_credit)
        val textViews = listOf(title, tagline, subtitle, product, credit)

        logo.alpha = 0f
        logo.scaleX = 0.65f
        logo.scaleY = 0.65f
        title.text = ""
        textViews.forEach { it.alpha = 0f; it.translationY = 22f }
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(650)
            .setInterpolator(DecelerateInterpolator()).start()
        logo.animate().rotationBy(360f).setDuration(1100).setStartDelay(100).start()
        textViews.drop(1).forEachIndexed { index, view ->
            view.animate().alpha(1f).translationY(0f).setDuration(450)
                .setStartDelay(1150L + index * 180L)
                .setInterpolator(DecelerateInterpolator()).start()
        }
        val titleText = getString(R.string.app_name)
        title.animate().alpha(1f).translationY(0f).setDuration(300).setStartDelay(850).start()
        titleText.forEachIndexed { index, char ->
            Handler(Looper.getMainLooper()).postDelayed({
                title.append(char.toString())
            }, 850L + index * 115L)
        }
        overlay.postDelayed({
            overlay.animate().alpha(0f).setDuration(500).withEndAction {
                overlay.visibility = View.GONE
            }.start()
            requestNotificationPermissionIfNeeded()
        }, 4500)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            val prefs = Prefs(this)
            if (!prefs.notificationsRequested &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                prefs.notificationsRequested = true
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
