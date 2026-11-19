package com.crestpoint.datatracker.plan

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.crestpoint.datatracker.R
import com.crestpoint.datatracker.data.PlanStore
import com.crestpoint.datatracker.work.SnapshotWorker
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Makes the data-plan form editable: it shows the plan that is running, saving changes it in place
 * (instead of adding a second plan that the app might ignore), and a Reset button starts the countdown fresh.
 */
object PlanFormHelper {
    private val fmt get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun num(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.2f", v)

    private fun parse(value: String, endOfDay: Boolean): Long? = try {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value.trim())
        if (d == null) null else Calendar.getInstance().apply {
            time = d
            set(Calendar.HOUR_OF_DAY, if (endOfDay) 23 else 0)
            set(Calendar.MINUTE, if (endOfDay) 59 else 0)
            set(Calendar.SECOND, if (endOfDay) 59 else 0)
            set(Calendar.MILLISECOND, if (endOfDay) 999 else 0)
        }.timeInMillis
    } catch (_: Exception) { null }

    fun attach(fragment: Fragment, root: View) {
        val ctx = fragment.requireContext()
        val size = root.findViewById<EditText>(R.id.plan_size) ?: return
        val unit = root.findViewById<Spinner>(R.id.plan_unit) ?: return
        val price = root.findViewById<EditText>(R.id.plan_price) ?: return
        val carrier = root.findViewById<EditText>(R.id.plan_carrier) ?: return
        val start = root.findViewById<EditText>(R.id.plan_start) ?: return
        val expiry = root.findViewById<EditText>(R.id.plan_expiry) ?: return
        val save = root.findViewById<Button>(R.id.btn_save_plan) ?: return
        val originalLabel = save.text.toString()
        val store = PlanStore(ctx)

        fun fill() {
            val p = store.active()
            if (p != null) {
                val mb = p.sizeBytes / 1048576.0
                if (mb >= 1024.0) { size.setText(num(mb / 1024.0)); unit.setSelection(1) } else { size.setText(num(mb)); unit.setSelection(0) }
                carrier.setText(p.carrier)
                price.setText(if (p.priceNaira > 0) num(p.priceNaira) else "")
                start.setText(fmt.format(p.startMs))
                expiry.setText(fmt.format(p.expiryMs))
                save.text = "Update this plan"
            } else {
                size.setText(""); carrier.setText(""); price.setText(""); unit.setSelection(0)
                start.setText(fmt.format(Calendar.getInstance().time))
                expiry.setText(fmt.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 30) }.time))
                save.text = originalLabel
            }
        }
        fill()

        save.setOnClickListener {
            val s = size.text.toString().trim().toDoubleOrNull()
            val c = carrier.text.toString().trim()
            val pr = price.text.toString().replace(",", "").trim().toDoubleOrNull() ?: 0.0
            val st = parse(start.text.toString(), false)
            val en = parse(expiry.text.toString(), true)
            if (s == null || s <= 0 || c.isBlank() || st == null || en == null || en < st) {
                Toast.makeText(ctx, "Enter a valid plan, carrier and dates (yyyy-mm-dd)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val mult = if (unit.selectedItemPosition == 1) 1024.0 else 1.0
            val bytes = (s * mult * 1024.0 * 1024.0).toLong()
            val cur = store.active()
            if (cur != null) {
                store.update(cur.id, bytes, c, st, en, pr.coerceAtLeast(0.0))
                store.allActive().filter { it.id != cur.id }.forEach { store.delete(it.id) }
            } else {
                store.create(bytes, c, st, en, pr.coerceAtLeast(0.0))
            }
            try { SnapshotWorker.schedule(ctx) } catch (_: Exception) { }
            Toast.makeText(ctx, if (cur != null) "Plan updated" else ctx.getString(R.string.plan_saved), Toast.LENGTH_SHORT).show()
            fill()
        }

        val parent = save.parent as? ViewGroup ?: return
        val reset = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        reset.text = "Reset plan (start fresh)"
        reset.isAllCaps = false
        reset.setOnClickListener {
            if (store.allActive().isEmpty()) {
                Toast.makeText(ctx, "There is no active plan to reset", Toast.LENGTH_SHORT).show()
            } else {
                MaterialAlertDialogBuilder(ctx).setTitle("Reset data plan?")
                    .setMessage("This removes your current plan and its usage count so the countdown can start again. Your app usage history is not deleted.")
                    .setPositiveButton("Reset") { _, _ ->
                        store.allActive().forEach { store.delete(it.id) }
                        fill()
                        Toast.makeText(ctx, "Plan reset. Enter your new plan and save.", Toast.LENGTH_LONG).show()
                    }.setNegativeButton("Cancel", null).show()
            }
        }
        val idx = parent.indexOfChild(save) + 1
        if (parent is LinearLayout) parent.addView(reset, idx, LinearLayout.LayoutParams(-1, -2)) else parent.addView(reset, idx)
    }
}
