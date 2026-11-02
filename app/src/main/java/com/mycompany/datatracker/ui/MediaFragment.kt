package com.mycompany.datatracker.ui

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mycompany.datatracker.R
import com.mycompany.datatracker.media.MediaCache
import com.mycompany.datatracker.media.MediaGroup
import com.mycompany.datatracker.media.MediaGrouper
import com.mycompany.datatracker.media.PdfScanner
import com.mycompany.datatracker.media.ScanWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaFragment : Fragment(R.layout.fragment_media) {

    private enum class Access { FULL, PARTIAL, NONE }

    private val cache by lazy { MediaCache(requireContext()) }
    private lateinit var adapter: MediaGroupAdapter
    private var wasRunning = false
    private var pendingDeleteIds: List<Long>? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (access() == Access.NONE) {
                view?.findViewById<TextView>(R.id.scan_status)?.text =
                    getString(R.string.photo_access_denied)
            } else {
                startScan()
            }
        }

    private val deleteLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                pendingDeleteIds?.let { cache.deleteIds(it) }
                loadGroups()
            }
            pendingDeleteIds = null
        }

    private val pdfPicker =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) scanPdfs(uris)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list = view.findViewById<RecyclerView>(R.id.groups_list)
        list.layoutManager = LinearLayoutManager(requireContext())
        adapter = MediaGroupAdapter(onDelete = { confirmDelete(it) }, onOpen = { openGroup(it) })
        list.adapter = adapter

        view.findViewById<Button>(R.id.btn_scan).setOnClickListener {
            if (access() == Access.NONE) permissionLauncher.launch(requiredPermissions())
            else startScan()
        }
        view.findViewById<Button>(R.id.btn_pdf).setOnClickListener {
            pdfPicker.launch(arrayOf("application/pdf"))
        }
        view.findViewById<TextView>(R.id.partial_note).setOnClickListener {
            permissionLauncher.launch(requiredPermissions())
        }

        updatePartialNote()
        loadGroups()

        WorkManager.getInstance(requireContext())
            .getWorkInfosForUniqueWorkLiveData(ScanWorker.NAME)
            .observe(viewLifecycleOwner) { infos -> onWorkInfo(infos.firstOrNull()) }
    }

    // ---- permissions (Android 14 "selected photos only" included)

    private fun requiredPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), permission) ==
            PackageManager.PERMISSION_GRANTED

    private fun access(): Access = when {
        Build.VERSION.SDK_INT >= 34 -> when {
            isGranted(Manifest.permission.READ_MEDIA_IMAGES) -> Access.FULL
            isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> Access.PARTIAL
            else -> Access.NONE
        }
        Build.VERSION.SDK_INT >= 33 ->
            if (isGranted(Manifest.permission.READ_MEDIA_IMAGES)) Access.FULL else Access.NONE
        else ->
            if (isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) Access.FULL else Access.NONE
    }

    private fun updatePartialNote() {
        view?.findViewById<TextView>(R.id.partial_note)?.visibility =
            if (access() == Access.PARTIAL) View.VISIBLE else View.GONE
    }

    // ---- scanning

    private fun startScan() {
        updatePartialNote()
        ScanWorker.enqueue(requireContext())
    }

    private fun onWorkInfo(info: WorkInfo?) {
        val root = view ?: return
        val status = root.findViewById<TextView>(R.id.scan_status)
        val bar = root.findViewById<ProgressBar>(R.id.scan_progress)
        val button = root.findViewById<Button>(R.id.btn_scan)

        val running = info != null &&
            (info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED)

        if (running && info != null) {
            val done = info.progress.getInt("done", 0)
            val total = info.progress.getInt("total", 0)
            bar.visibility = View.VISIBLE
            button.isEnabled = false
            if (total > 0) {
                bar.isIndeterminate = false
                bar.max = total
                bar.progress = done
                status.text = getString(R.string.scan_progress, done, total)
            } else {
                bar.isIndeterminate = true
                status.text = getString(R.string.scanning)
            }
        } else {
            bar.visibility = View.GONE
            button.isEnabled = true
            if (wasRunning) loadGroups()
        }
        wasRunning = running
    }

    private fun loadGroups() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                val images = cache.allImages()
                val docs = cache.allDocs()
                Pair(MediaGrouper.build(images, docs), images.size + docs.size)
            }
            val root = view ?: return@launch
            adapter.submit(result.first)
            if (!wasRunning) {
                root.findViewById<TextView>(R.id.scan_status).text =
                    if (result.second == 0) getString(R.string.nothing_indexed)
                    else getString(R.string.scan_summary, result.second, result.first.size)
            }
        }
    }

    private fun scanPdfs(uris: List<Uri>) {
        val status = view?.findViewById<TextView>(R.id.scan_status) ?: return
        status.text = getString(R.string.pdf_scanning, uris.size)
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                PdfScanner(appContext, MediaCache(appContext)).scan(uris)
            }
            loadGroups()
        }
    }

    // ---- group actions

    private fun openGroup(group: MediaGroup) {
        val uri = group.sampleUri ?: return
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, group.mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.cannot_open, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(group: MediaGroup) {
        if (Build.VERSION.SDK_INT < 30) {
            Toast.makeText(requireContext(), R.string.delete_needs_android11, Toast.LENGTH_LONG).show()
            return
        }
        val n = group.extraIds.size
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_title, n))
            .setMessage(getString(R.string.delete_message, group.sampleName))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_confirm) { _, _ -> trash(group.extraIds) }
            .show()
    }

    private fun trash(ids: List<Long>) {
        if (Build.VERSION.SDK_INT < 30) return
        val uris = ids.map {
            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it)
        }
        pendingDeleteIds = ids
        val pending = MediaStore.createTrashRequest(
            requireContext().contentResolver, uris, true
        )
        deleteLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
    }
}
