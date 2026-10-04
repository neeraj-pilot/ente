package io.ente.auth.watch

import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.window.OnBackInvokedDispatcher
import io.ente.auth.watch.app.R

class WatchActivity : Activity() {
    private lateinit var companion: WatchCompanion
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private val handler = Handler(Looper.getMainLooper())
    private var state = WatchCompanion.State()
    private var screen: Screen? = null

    private val tick = object : Runnable {
        override fun run() {
            if (getSystemService(KeyguardManager::class.java).isDeviceLocked) {
                companion.setVisible(false)
                return
            }
            screen?.tick()
            handler.postDelayed(this, 1000 - System.currentTimeMillis() % 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.setBackgroundDrawableResource(android.R.color.black)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val metrics = resources.displayMetrics
            val horizontal = (metrics.widthPixels * 0.052f).toInt()
            val vertical = (metrics.heightPixels * 0.12f).toInt()
            // Let the last row or wrapped identity scroll into the round screen's wider center.
            setPadding(horizontal, vertical, horizontal, (metrics.heightPixels * 0.35f).toInt())
        }
        scroll = ScrollView(this).apply {
            addView(content)
            isFillViewport = true
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(16))
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
        }
        setContentView(scroll)
        companion = WatchCompanion(this)
        companion.onChange = { next -> state = next; render() }
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { goBack() }
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        companion.setVisible(true)
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        companion.setVisible(false)
        super.onPause()
    }

    override fun onDestroy() { companion.close(); super.onDestroy() }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            val distance = -event.getAxisValue(MotionEvent.AXIS_SCROLL) * ViewConfiguration.get(this).scaledVerticalScrollFactor
            scroll.scrollBy(0, distance.toInt())
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (Build.VERSION.SDK_INT < 33 && keyCode == KeyEvent.KEYCODE_BACK) { goBack(); return true }
        return super.onKeyUp(keyCode, event)
    }

    private fun goBack() {
        if (screen == null || screen is AccountsScreen) finish() else show(AccountsScreen())
    }

    private fun show(next: Screen) {
        screen = next
        content.removeAllViews()
        content.addView(next)
        scroll.scrollTo(0, 0)
        next.update()
        next.tick()
    }

    private fun render() {
        if (state.message == WatchCompanion.Message.hidden) {
            screen = null
            content.removeAllViews()
            content.addSpaced(watchText(getString(R.string.codes_hidden), 16f))
            return
        }
        val current = screen
        if (current == null) {
            show(AccountsScreen())
        } else if (current is CodeScreen) {
            val entry = state.entries.firstOrNull { it.id == current.entry.id }
            if (entry == null) show(AccountsScreen())
            else if (entry != current.entry) show(CodeScreen(entry))
        } else {
            current.update()
        }
    }

    private abstract inner class Screen : LinearLayout(this@WatchActivity) {
        init { orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        open fun update() {}
        open fun tick() {}
    }

    private inner class AccountsScreen : Screen() {
        private var entries: List<Entry>? = null
        private val message = watchText("", 13f, secondary = true)
        private val rows = LinearLayout(this@WatchActivity).apply { orientation = VERTICAL }
        private val updated = watchText("", 12f, secondary = true)
        private val setup = watchButton(getString(R.string.setup), WatchStyle.accent) { companion.enroll() }
        private val manage = watchButton(getString(R.string.manage)) { show(ManageScreen()) }

        init {
            addSpaced(watchText(getString(R.string.app_name), 16f), 0)
            addSpaced(message)
            addSpaced(rows, 0)
            addSpaced(updated, 12)
            addSpaced(setup)
            addSpaced(manage)
        }

        override fun update() {
            // Status changes preserve account views, scroll position, and accessibility focus.
            if (entries != state.entries) {
                rows.removeAllViews()
                state.entries.forEach { rows.addSpaced(accountRow(it)) }
                entries = state.entries
            }
            val text = if (state.message == WatchCompanion.Message.offline && state.entries.isNotEmpty()) "" else statusMessage()
            message.text = text
            message.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
            setup.visibility = if (state.canEnroll) View.VISIBLE else View.GONE
            manage.visibility = if (state.canErase) View.VISIBLE else View.GONE
            tick()
        }

        override fun tick() {
            val time = state.updatedAt
            updated.visibility = if (time == null) View.GONE else View.VISIBLE
            if (time != null) {
                val now = System.currentTimeMillis()
                val label = if (now - time < DateUtils.MINUTE_IN_MILLIS) getString(R.string.updated_just_now)
                    else getString(R.string.updated, DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS))
                if (updated.text.toString() != label) updated.text = label
            }
        }
    }

    private fun accountRow(entry: Entry) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(52)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = WatchStyle.background(context)
        isClickable = true
        isFocusable = true
        contentDescription = listOf(entry.issuer, entry.account).filter { it.isNotEmpty() }.joinToString(", ")
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        setOnClickListener { show(CodeScreen(entry)) }
        addView(watchText(entry.issuer.ifEmpty { entry.account }, 16f).apply {
            gravity = Gravity.START
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        if (entry.issuer.isNotEmpty() && entry.account.isNotEmpty()) {
            addSpaced(watchText(entry.account, 13f, secondary = true).apply {
                gravity = Gravity.START
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.MIDDLE
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, 2)
        }
    }

    private inner class CodeScreen(val entry: Entry) : Screen() {
        private val code = watchText("", 40f).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setSingleLine(true)
            setHorizontallyScrolling(false)
            textDirection = View.TEXT_DIRECTION_LTR
            setAutoSizeTextTypeUniformWithConfiguration(20, 40, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        private val progress = ProgressBar(this@WatchActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = entry.period
            progressBackgroundTintList = ColorStateList.valueOf(WatchStyle.stroke)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        private val countdown = watchText("", 13f, secondary = true)

        init {
            addSpaced(watchText(entry.issuer.ifEmpty { getString(R.string.code) }, 14f).apply {
                val inset = (resources.displayMetrics.widthPixels * 0.10f).toInt()
                setPadding(inset, 0, inset, 0)
                setSingleLine(true); ellipsize = TextUtils.TruncateAt.END
            }, 0)
            if (entry.account.isNotEmpty()) {
                addSpaced(watchText(entry.account, 13f, secondary = true).apply {
                    setSingleLine(true); ellipsize = TextUtils.TruncateAt.MIDDLE
                }, 4)
            }
            addSpaced(code)
            addView(progress, LayoutParams(-1, dp(4)).apply {
                topMargin = dp(8)
                marginStart = (resources.displayMetrics.widthPixels * 0.04f).toInt()
                marginEnd = marginStart
            })
            addSpaced(countdown, 4)
            addSpaced(watchText(listOf(entry.issuer, entry.account).filter { it.isNotEmpty() }.joinToString("\n"), 13f, secondary = true).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, 16)
            addSpaced(watchButton(getString(R.string.back)) { goBack() }, 16, fullWidth = false)
        }

        override fun tick() {
            val seconds = System.currentTimeMillis() / 1000
            val value = entry.code(seconds)
            val formatted = formatCode(value)
            if (code.text.toString() != formatted) {
                code.text = formatted
                code.contentDescription = getString(R.string.code_spoken, value.toCharArray().joinToString(" "))
            }
            val remaining = (entry.period - seconds % entry.period).toInt()
            progress.progress = remaining
            progress.progressTintList = ColorStateList.valueOf(if (remaining <= 5) WatchStyle.warning else WatchStyle.accent)
            countdown.text = resources.getQuantityString(R.plurals.seconds_remaining, remaining, remaining)
        }
    }

    private inner class ManageScreen : Screen() {
        private val message = watchText("", 13f, secondary = true)
        private val erase = watchButton(getString(R.string.erase), Color.BLACK) {
            AlertDialog.Builder(this@WatchActivity).setTitle(R.string.erase_title)
                .setMessage(R.string.erase_description)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.erase_action) { _, _ -> companion.erase(); screen = null; render() }.show()
        }.apply { setTextColor(WatchStyle.warning) }

        init {
            addSpaced(watchText(getString(R.string.manage), 16f), 0)
            addSpaced(message)
            addSpaced(watchButton(getString(R.string.sync)) { companion.retry() })
            addSpaced(erase)
            addSpaced(watchButton(getString(R.string.back)) { goBack() }, fullWidth = false)
        }

        override fun update() {
            message.text = statusMessage().ifEmpty { getString(R.string.sync_help) }
            erase.visibility = if (state.canErase) View.VISIBLE else View.GONE
        }
    }

    private fun statusMessage() = when (state.message) {
        WatchCompanion.Message.setup -> getString(R.string.setup_instructions)
        WatchCompanion.Message.approvePhone -> getString(R.string.approve_phone)
        WatchCompanion.Message.selectAccounts -> getString(R.string.select_accounts)
        WatchCompanion.Message.offline -> getString(R.string.offline)
        WatchCompanion.Message.unlockWatch -> getString(R.string.unlock_watch)
        WatchCompanion.Message.storageFailed -> getString(R.string.storage_failed)
        WatchCompanion.Message.secureStorageUnavailable -> getString(R.string.secure_storage_unavailable)
        WatchCompanion.Message.setupFailed -> getString(R.string.setup_failed)
        WatchCompanion.Message.syncFailed -> getString(R.string.sync_failed)
        WatchCompanion.Message.connectPhone -> getString(R.string.connect_phone)
        WatchCompanion.Message.ready, WatchCompanion.Message.hidden -> ""
    }
}
