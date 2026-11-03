package com.mycompany.datatracker.cleaner

enum class ClearResult { NOT_FOUND, DISABLED, CLICKED }

interface BotScreen {
    fun activePackage(): String?
    fun clickStorage(): Boolean
    fun clearCache(): ClearResult
    fun back()
}

class CacheBotMachine(
    private val ownPackage: String,
    private val settingsPackages: Set<String>,
    private val now: () -> Long
) {
    enum class Phase { IDLE, OPENING, STORAGE, CLEARED, RETURNING }

    var screen: BotScreen? = null
    var launcher: ((String) -> Unit)? = null
    var onChange: (() -> Unit)? = null

    var running = false
        private set
    var finished = false
        private set
    var phase = Phase.IDLE
        private set
    var current: String? = null
        private set
    var total = 0
        private set
    var cleared = 0
        private set
    var skipped = 0
        private set
    var error: String? = null
        private set

    val done: Int get() = cleared + skipped

    private val queue = ArrayDeque<String>()
    private var phaseSince = 0L
    private var appSince = 0L
    private var backs = 0
    private var lastBack = 0L
    private var storageClicks = 0
    private var strayStart = 0L

    fun begin(packages: List<String>) {
        queue.clear()
        queue.addAll(packages)
        total = packages.size
        cleared = 0
        skipped = 0
        error = null
        finished = false
        phase = Phase.IDLE
        current = null
        strayStart = 0L
        running = packages.isNotEmpty()
        if (!running) finished = true
        changed()
    }

    fun cancel() {
        running = false
        finished = true
        phase = Phase.IDLE
        current = null
        queue.clear()
        changed()
    }

    fun launchNextIfIdle() {
        while (running && phase == Phase.IDLE) {
            val next = queue.removeFirstOrNull()
            if (next == null) {
                finish()
                return
            }
            val l = launcher
            if (l == null) {
                fail("The cleaner screen was closed")
                return
            }
            current = next
            phase = Phase.OPENING
            phaseSince = now()
            appSince = phaseSince
            storageClicks = 0
            strayStart = 0L
            changed()
            val ok = try {
                l(next)
                true
            } catch (_: Exception) {
                false
            }
            if (ok) return
            skipped++
            phase = Phase.IDLE
            current = null
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

        if (pkg != null && pkg != ownPackage && pkg !in settingsPackages) {
            if (strayStart == 0L) strayStart = t
            if (t - strayStart > STRAY_MS) {
                fail("Stopped because you left the cleaner")
                return
            }
        } else if (pkg != null) {
            strayStart = 0L
        }

        if (phase != Phase.RETURNING && t - appSince > APP_TIMEOUT) {
            skipCurrent(t)
            return
        }

        when (phase) {
            Phase.OPENING -> {
                if (pkg != null && pkg in settingsPackages) {
                    if (s.clearCache() == ClearResult.CLICKED) {
                        markCleared(t)
                    } else if (s.clickStorage()) {
                        storageClicks++
                        phase = Phase.STORAGE
                        phaseSince = t
                    } else if (t - phaseSince > OPEN_TIMEOUT) {
                        skipCurrent(t)
                    }
                } else if (t - phaseSince > OPEN_TIMEOUT) {
                    skipCurrent(t)
                }
            }
            Phase.STORAGE -> {
                if (pkg == null || pkg !in settingsPackages) return
                when (s.clearCache()) {
                    ClearResult.CLICKED -> markCleared(t)
                    ClearResult.DISABLED -> if (t - phaseSince > DISABLED_WAIT) {
                        skipCurrent(t)
                    }
                    ClearResult.NOT_FOUND -> if (t - phaseSince > RECLICK_AFTER) {
                        if (storageClicks < MAX_STORAGE_CLICKS) {
                            phase = Phase.OPENING
                            phaseSince = t
                        } else {
                            skipCurrent(t)
                        }
                    }
                }
            }
            Phase.CLEARED -> {
                if (t - phaseSince >= CLEAR_SETTLE) beginReturn(t)
            }
            Phase.RETURNING -> {
                if (pkg == ownPackage) {
                    phase = Phase.IDLE
                    current = null
                    changed()
                    launchNextIfIdle()
                    return
                }
                if (t - phaseSince > RETURN_TIMEOUT) {
                    fail("Could not return to the cleaner screen")
                    return
                }
                if (pkg != null && t - lastBack >= BACK_GAP) {
                    if (backs >= MAX_BACKS) {
                        fail("Could not return to the cleaner screen")
                        return
                    }
                    s.back()
                    backs++
                    lastBack = t
                }
            }
            Phase.IDLE -> {}
        }
    }

    private fun markCleared(t: Long) {
        cleared++
        phase = Phase.CLEARED
        phaseSince = t
        changed()
    }

    private fun skipCurrent(t: Long) {
        skipped++
        beginReturn(t)
    }

    private fun beginReturn(t: Long) {
        phase = Phase.RETURNING
        phaseSince = t
        backs = 0
        lastBack = 0L
        changed()
    }

    private fun finish() {
        running = false
        finished = true
        phase = Phase.IDLE
        current = null
        changed()
    }

    private fun fail(message: String) {
        error = message
        running = false
        finished = true
        phase = Phase.IDLE
        current = null
        queue.clear()
        changed()
    }

    private fun changed() {
        try {
            onChange?.invoke()
        } catch (_: Exception) {
        }
    }

    companion object {
        const val OPEN_TIMEOUT = 3500L
        const val DISABLED_WAIT = 2200L
        const val RECLICK_AFTER = 1100L
        const val CLEAR_SETTLE = 150L
        const val BACK_GAP = 220L
        const val MAX_BACKS = 5
        const val MAX_STORAGE_CLICKS = 3
        const val APP_TIMEOUT = 10000L
        const val RETURN_TIMEOUT = 6000L
        const val STRAY_MS = 2000L
    }
}
