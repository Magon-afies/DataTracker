package com.mycompany.datatracker

import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.mycompany.datatracker.ui.AppsFragment
import com.mycompany.datatracker.ui.DashboardFragment
import com.mycompany.datatracker.ui.MediaFragment
import com.mycompany.datatracker.ui.SettingsFragment
import com.mycompany.datatracker.work.Alerts
import com.mycompany.datatracker.work.SnapshotWorker

class MainActivity : AppCompatActivity() {

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
        val title = findViewById<View>(R.id.intro_title)
        val tagline = findViewById<View>(R.id.intro_tagline)
        val subtitle = findViewById<View>(R.id.intro_subtitle)
        val product = findViewById<View>(R.id.intro_product)
        val credit = findViewById<View>(R.id.intro_credit)
        val textViews = listOf(title, tagline, subtitle, product, credit)

        logo.alpha = 0f
        logo.scaleX = 0.65f
        logo.scaleY = 0.65f
        textViews.forEach { it.alpha = 0f; it.translationY = 22f }
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(650)
            .setInterpolator(DecelerateInterpolator()).start()
        logo.animate().rotationBy(360f).setDuration(1100).setStartDelay(100).start()
        textViews.forEachIndexed { index, view ->
            view.animate().alpha(1f).translationY(0f).setDuration(450)
                .setStartDelay(220L + index * 110L)
                .setInterpolator(DecelerateInterpolator()).start()
        }
        overlay.postDelayed({
            overlay.animate().alpha(0f).setDuration(500).withEndAction {
                overlay.visibility = View.GONE
            }.start()
        }, 2100)
    }
}
