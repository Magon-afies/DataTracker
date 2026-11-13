package com.crestpoint.datatracker.databot

enum class Toggle { NOT_FOUND, ALLOWED, BLOCKED }

interface DataBotScreen {
    fun activePackage(): String?
    /** Taps "Mobile data & Wi-Fi" / "Data usage" on the App info page. */
    fun clickDataEntry(): Boolean
    /** Reads the "Background data" switch: ALLOWED = app may use data in the background. */
    fun readToggle(): Toggle
    fun clickToggle(): Boolean
    /** Presses OK on a confirmation dialog if one is showing. */
    fun confirmDialog(): Boolean
    fun back()
    /** Plain-text dump of what is on screen (used for failure reports). */
    fun dump(): String
}

/**
 * Walks Settings for each package: App info -> data page -> flip "Background data".
 * Pure logic (no Android classes) so it can be unit-tested.
 */
class DataBotMachine(
    private val ownPackage: String,
    private val now: () -> Long
) {
    enum class Phase { IDLE, OPENING, DATA_PAGE, VERIFY, RETURNING }

    var screen: DataBotScreen? = null
    var launcher: ((String) -> Unit)? = null
    var onChange: (() -> Unit)? = null
    var onFinished: (() -> Unit)? = null

    var running = false; private set
    var finished = false; private set
    var phase = Phase.IDLE; private set
    var current: String? = null; private set
    var total = 0; private set
    var error: String? = null; private set
    var wantAllowed = false; private set
    var lastDump = ""; private set

    val changed = ArrayList<String>()
    val already = ArrayList<String>()
    val skipped = ArrayList<String>()
    val done: Int get() = changed.size + already.size + skipped.size

    private val queue = ArrayDeque<String>()
    private var phaseSince = 0L
    private var appSince = 0L
    private var lastEntryClick = 0L
    private var entryClicks = 0
    private var toggleClicks = 0
    private var backs = 0
    private var lastBack = 0L
    private var strayStart = 0L

    fun begin(packages: List<String>, allow: Boolean) {
        queue.clear(); queue.addAll(packages)
        total = packages.size
        changed.clear(); already.clear(); skipped.clear()
        wantAllowed = allow
        error = null; finished = false; phase = Phase.IDLE; current = null; strayStart = 0L
        running = packages.isNotEmpty()
        if (!running) finished = true
        notifyChange()
    }

    fun cancel() {
        running = false; finished = true; phase = Phase.IDLE; current = null; queue.clear()
        notifyChange(); notifyFinished()
    }

    private fun isSettings(pkg: String?): Boolean =
        pkg != null && pkg != ownPackage &&
            (pkg.contains("settings", ignoreCase = true) || pkg == "com.miui.securitycenter" || pkg == "com.android.systemui")

    fun launchNextIfIdle() {
        while (running && phase == Phase.IDLE) {
            val next = queue.removeFirstOrNull()
            if (next == null) { finish(); return }
            val l = launcher
            if (l == null) { fail("The bot screen was closed"); return }
            current = next
            phase = Phase.OPENING
            phaseSince = now(); appSince = phaseSince
            lastEntryClick = 0L; entryClicks = 0; toggleClicks = 0; strayStart = 0L
            notifyChange()
            val ok = try { l(next); true } catch (_: Exception) { false }
            if (ok) return
            skipped.add(next); phase = Phase.IDLE; current = null
        }
    }

    fun tick() {
        if (!running) return
        val s = screen ?: return
        val t = now()
        val pkg = s.activePackage()

        if (phase == Phase.IDLE) {
            if (pkg == ownPackage) launchNextIfIdle()
            return
        }

        if (pkg != null && pkg != ownPackage && !isSettings(pkg)) {
            if (strayStart == 0L) strayStart = t
            if (t - strayStart > STRAY_MS) { fail("Stopped because you left the app"); return }
        } else if (pkg != null) strayStart = 0L

        if (phase != Phase.RETURNING && t - appSince > APP_TIMEOUT) { skipCurrent(t, s); return }

        when (phase) {
            Phase.OPENING -> {
                if (isSettings(pkg)) {
                    if (s.readToggle() != Toggle.NOT_FOUND) { phase = Phase.DATA_PAGE; phaseSince = t; return }
                    if (t - lastEntryClick > RECLICK_AFTER && entryClicks < MAX_ENTRY_CLICKS && s.clickDataEntry()) {
                        entryClicks++; lastEntryClick = t; phase = Phase.DATA_PAGE; phaseSince = t
                    } else if (t - phaseSince > OPEN_TIMEOUT) skipCurrent(t, s)
                } else if (t - phaseSince > OPEN_TIMEOUT) skipCurrent(t, s)
            }
            Phase.DATA_PAGE -> {
                if (!isSettings(pkg)) return
                val st = s.readToggle()
                if (st == Toggle.NOT_FOUND) {
                    if (t - phaseSince > PAGE_WAIT) {
                        if (entryClicks < MAX_ENTRY_CLICKS) { phase = Phase.OPENING; phaseSince = t } else skipCurrent(t, s)
                    }
                    return
                }
                val ok = if (wantAllowed) st == Toggle.ALLOWED else st == Toggle.BLOCKED
                if (ok) { markDone(t, already); return }
                if (toggleClicks < MAX_TOGGLE_CLICKS && s.clickToggle()) {
                    toggleClicks++; phase = Phase.VERIFY; phaseSince = t
                } else if (t - phaseSince > PAGE_WAIT) skipCurrent(t, s)
            }
            Phase.VERIFY -> {
                if (!isSettings(pkg)) return
                if (s.confirmDialog()) { phaseSince = t; return }
                if (t - phaseSince < VERIFY_SETTLE) return
                val st = s.readToggle()
                val ok = if (wantAllowed) st == Toggle.ALLOWED else st == Toggle.BLOCKED
                if (ok) markDone(t, changed)
                else if (toggleClicks < MAX_TOGGLE_CLICKS) { phase = Phase.DATA_PAGE; phaseSince = t }
                else skipCurrent(t, s)
            }
            Phase.RETURNING -> {
                if (pkg == ownPackage) {
                    phase = Phase.IDLE; current = null
                    notifyChange(); launchNextIfIdle(); return
                }
                if (t - phaseSince > RETURN_TIMEOUT) { fail("Could not return to the app"); return }
                if (pkg != null && t - lastBack >= BACK_GAP) {
                    if (backs >= MAX_BACKS) { fail("Could not return to the app"); return }
                    s.back(); backs++; lastBack = t
                }
            }
            Phase.IDLE -> {}
        }
    }

    private fun markDone(t: Long, list: MutableList<String>) {
        current?.let { list.add(it) }
        beginReturn(t)
    }

    private fun skipCurrent(t: Long, s: DataBotScreen) {
        try { lastDump = "Package: $current\n" + s.dump() } catch (_: Exception) { }
        current?.let { skipped.add(it) }
        beginReturn(t)
    }

    private fun beginReturn(t: Long) {
        phase = Phase.RETURNING; phaseSince = t; backs = 0; lastBack = 0L
        notifyChange()
    }

    private fun finish() {
        running = false; finished = true; phase = Phase.IDLE; current = null
        notifyChange(); notifyFinished()
    }

    private fun fail(message: String) {
        error = message
        running = false; finished = true; phase = Phase.IDLE; current = null; queue.clear()
        notifyChange(); notifyFinished()
    }

    private fun notifyChange() { try { onChange?.invoke() } catch (_: Exception) { } }
    private fun notifyFinished() { try { onFinished?.invoke() } catch (_: Exception) { } }

    companion object {
        const val OPEN_TIMEOUT = 4000L
        const val PAGE_WAIT = 2500L
        const val RECLICK_AFTER = 900L
        const val VERIFY_SETTLE = 500L
        const val BACK_GAP = 250L
        const val MAX_BACKS = 6
        const val MAX_ENTRY_CLICKS = 3
        const val MAX_TOGGLE_CLICKS = 2
        const val APP_TIMEOUT = 14000L
        const val RETURN_TIMEOUT = 7000L
        const val STRAY_MS = 2500L
    }
}
