package com.senza.assistivetouch

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class AssistiveService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private var button: View? = null
    private var buttonParams: WindowManager.LayoutParams? = null
    private var scrim: View? = null
    private var closingScrim: View? = null
    private var menuPanel: View? = null
    private var blurAnim: ValueAnimator? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Actions.K_SIZE || key == Actions.K_OPACITY || key == Actions.K_GLASS) rebuildButton()
    }

    private val fadeRunnable = Runnable {
        button?.animate()?.alpha(idleAlpha())?.setDuration(300)?.start()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun idleAlpha() = prefs.getInt(Actions.K_OPACITY, 60) / 100f
    private fun screenW() = resources.displayMetrics.widthPixels
    private fun screenH() = resources.displayMetrics.heightPixels

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(Actions.PREFS, Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        rebuildButton()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hideMenuNow()
        val p = buttonParams ?: return
        p.x = p.x.coerceIn(0, (screenW() - p.width).coerceAtLeast(0))
        p.y = p.y.coerceIn(0, (screenH() - p.height).coerceAtLeast(0))
        snapToEdge(false)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        if (::wm.isInitialized) {
            hideMenuNow()
            closingScrim?.let { runCatching { wm.removeView(it) } }
            closingScrim = null
            removeButton()
        }
        super.onDestroy()
    }

    // ------------------------------------------------------------ Tombol melayang

    private fun removeButton() {
        button?.let { runCatching { wm.removeView(it) } }
        button = null
    }

    private fun rebuildButton() {
        removeButton()
        val d = resources.displayMetrics.density
        val size = dp(prefs.getInt(Actions.K_SIZE, 54))
        val glass = prefs.getInt(Actions.K_GLASS, 60)

        val outer = GlassDrawable(d, size * 0.30f, glass, 0.24f)
        val inner = GlassDrawable(d, -1f, (glass + 20).coerceAtMost(100), 0.10f)
        val core = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#EBFFFFFF"))
        }
        val layers = LayerDrawable(arrayOf(outer, inner, core)).apply {
            val i1 = (size * 0.20f).toInt()
            val i2 = (size * 0.38f).toInt()
            setLayerInset(1, i1, i1, i1, i1)
            setLayerInset(2, i2, i2, i2, i2)
        }

        val v = View(this)
        v.background = layers
        v.alpha = idleAlpha()

        val p = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = prefs.getInt(Actions.K_X, screenW() - size)
        p.y = prefs.getInt(Actions.K_Y, screenH() / 2)
        buttonParams = p

        attachGestures(v)
        wm.addView(v, p)
        button = v
        snapToEdge(false)
    }

    private fun attachGestures(v: View) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                perform(prefs.getString(Actions.K_TAP, Actions.MENU) ?: Actions.MENU); return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                perform(prefs.getString(Actions.K_DOUBLE, Actions.HOME) ?: Actions.HOME); return true
            }
            override fun onLongPress(e: MotionEvent) {
                perform(prefs.getString(Actions.K_LONG, Actions.RECENTS) ?: Actions.RECENTS)
            }
        })

        var startX = 0f; var startY = 0f
        var winX = 0; var winY = 0
        var dragging = false

        v.setOnTouchListener { _, e ->
            val p = buttonParams ?: return@setOnTouchListener false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    handler.removeCallbacks(fadeRunnable)
                    v.animate().cancel()
                    v.alpha = 1f
                    startX = e.rawX; startY = e.rawY
                    winX = p.x; winY = p.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - startX
                    val dy = e.rawY - startY
                    if (!dragging && (Math.abs(dx) > slop || Math.abs(dy) > slop)) dragging = true
                    if (dragging) {
                        p.x = (winX + dx).toInt().coerceIn(0, (screenW() - p.width).coerceAtLeast(0))
                        p.y = (winY + dy).toInt().coerceIn(0, (screenH() - p.height).coerceAtLeast(0))
                        runCatching { wm.updateViewLayout(v, p) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) snapToEdge(true)
                    scheduleFade()
                }
            }
            pressAnim(v, e, 0.88f)
            if (!dragging) {
                detector.onTouchEvent(e)
            } else {
                val cancel = MotionEvent.obtain(e)
                cancel.action = MotionEvent.ACTION_CANCEL
                detector.onTouchEvent(cancel)
                cancel.recycle()
            }
            true
        }
    }

    private fun scheduleFade() {
        handler.removeCallbacks(fadeRunnable)
        handler.postDelayed(fadeRunnable, 3000)
    }

    private fun snapToEdge(animate: Boolean) {
        val v = button ?: return
        val p = buttonParams ?: return
        val targetX = if (p.x + p.width / 2 < screenW() / 2) 0 else screenW() - p.width
        fun save() {
            prefs.edit().putInt(Actions.K_X, p.x).putInt(Actions.K_Y, p.y).apply()
        }
        if (!animate) {
            p.x = targetX
            runCatching { wm.updateViewLayout(v, p) }
            save()
            return
        }
        ValueAnimator.ofInt(p.x, targetX).apply {
            duration = 260
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener {
                p.x = it.animatedValue as Int
                runCatching { wm.updateViewLayout(v, p) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { save() }
            })
            start()
        }
    }

    // ------------------------------------------------------------ Menu liquid glass

    private class Item(val label: String, val icon: Int, val onClick: () -> Unit)

    private fun showMenu(items: List<Item>, fromButton: Boolean = true) {
        hideMenuNow()

        val d = resources.displayMetrics.density
        val glass = prefs.getInt(Actions.K_GLASS, 60)
        val blur = prefs.getInt(Actions.K_BLUR, 60)
        val blurOn = Build.VERSION.SDK_INT >= 31 && blur > 0 && wm.isCrossWindowBlurEnabled

        val shadow = dp(22)
        val pad = dp(14) + shadow
        val cellW = minOf(dp(100), (screenW() - 2 * pad - dp(8)) / 3)
        val cellH = dp(108)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = DarkGlassPanelDrawable(
                d, dp(46).toFloat(), shadow.toFloat(),
                (0.30f + 0.20f * glass / 100f) * (if (blurOn) 0.9f else 1.15f)
            )
        }

        items.chunked(3).forEach { rowItems ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowItems.forEach { item ->
                row.addView(buildCell(item), LinearLayout.LayoutParams(cellW, cellH))
            }
            repeat(3 - rowItems.size) {
                row.addView(View(this), LinearLayout.LayoutParams(cellW, cellH))
            }
            panel.addView(row)
        }

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor(if (blurOn) "#33000000" else "#66000000"))
        root.setOnClickListener { dismissMenu() }
        root.addView(panel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (blurOn && Build.VERSION.SDK_INT >= 31) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            lp.blurBehindRadius = 0
        }

        panel.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View, l: Int, t: Int, r: Int, b: Int,
                ol: Int, ot: Int, or2: Int, ob: Int
            ) {
                panel.removeOnLayoutChangeListener(this)
                animateIn(panel, l, t, fromButton)
            }
        })

        wm.addView(root, lp)
        scrim = root
        menuPanel = panel
        button?.visibility = View.INVISIBLE

        if (blurOn && Build.VERSION.SDK_INT >= 31) {
            val target = (blur / 100f * dp(34)).toInt().coerceAtLeast(1)
            blurAnim = ValueAnimator.ofInt(0, target).apply {
                duration = 340
                addUpdateListener {
                    lp.blurBehindRadius = it.animatedValue as Int
                    runCatching { wm.updateViewLayout(root, lp) }
                }
                start()
            }
        }
    }

    private fun animateIn(panel: LinearLayout, left: Int, top: Int, fromButton: Boolean) {
        val bp = buttonParams
        if (fromButton && bp != null) {
            panel.pivotX = bp.x + bp.width / 2f - left
            panel.pivotY = bp.y + bp.height / 2f - top
            panel.scaleX = 0.15f; panel.scaleY = 0.15f
        } else {
            panel.pivotX = panel.width / 2f
            panel.pivotY = panel.height / 2f
            panel.scaleX = 0.92f; panel.scaleY = 0.92f
        }
        panel.alpha = 0f
        panel.animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(if (fromButton) 420L else 220L)
            .setInterpolator(OvershootInterpolator(if (fromButton) 1.15f else 0.8f))
            .start()

        if (fromButton) {
            var idx = 0
            for (ri in 0 until panel.childCount) {
                val row = panel.getChildAt(ri) as LinearLayout
                for (ci in 0 until row.childCount) {
                    val c = row.getChildAt(ci)
                    c.alpha = 0f; c.scaleX = 0.6f; c.scaleY = 0.6f
                    c.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setStartDelay(70L + idx * 22L).setDuration(320)
                        .setInterpolator(OvershootInterpolator(1.6f)).start()
                    idx++
                }
            }
        }
    }

    private fun buildCell(item: Item): View {
        val d = resources.displayMetrics.density
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setOnClickListener { item.onClick() }
            setOnTouchListener { v, e -> pressAnim(v, e, 0.9f); false }
        }
        val tile = FrameLayout(this).apply {
            background = GlassBubbleDrawable(d)
        }
        tile.addView(
            ImageView(this).apply { setImageResource(item.icon) },
            FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER)
        )
        box.addView(tile, LinearLayout.LayoutParams(dp(66), dp(66)))
        box.addView(TextView(this).apply {
            text = item.label
            textSize = 13.5f
            gravity = Gravity.CENTER
            maxLines = 1
            setTextColor(Color.WHITE)
            setShadowLayer(5f, 0f, 1f, Color.parseColor("#99000000"))
            setPadding(0, dp(4), 0, 0)
        })
        return box
    }

    /** Tutup menu tanpa animasi (ganti submenu / rotasi / service mati). */
    private fun hideMenuNow() {
        blurAnim?.cancel()
        blurAnim = null
        scrim?.let { runCatching { wm.removeView(it) } }
        scrim = null
        menuPanel = null
        button?.visibility = View.VISIBLE
    }

    /** Tutup menu dengan animasi mengecil ke tombol. */
    private fun dismissMenu(then: (() -> Unit)? = null) {
        val root = scrim
        if (root == null) { then?.invoke(); return }
        scrim = null
        closingScrim = root
        blurAnim?.cancel()
        blurAnim = null
        menuPanel?.animate()?.scaleX(0.3f)?.scaleY(0.3f)?.alpha(0f)
            ?.setDuration(170)?.setInterpolator(AccelerateInterpolator())?.start()
        menuPanel = null
        root.animate().alpha(0f).setDuration(170).start()
        handler.postDelayed({
            runCatching { wm.removeView(root) }
            closingScrim = null
            button?.let {
                it.visibility = View.VISIBLE
                it.alpha = 1f
                scheduleFade()
            }
            then?.invoke()
        }, 180)
    }

    private fun mainMenu(): List<Item> = listOf(
        Item("Notifikasi", R.drawable.ic_notif) { run(Actions.NOTIF) },
        Item("Kontrol", R.drawable.ic_control) { run(Actions.QS) },
        Item("Asisten", R.drawable.ic_assistant) { run(Actions.ASSIST) },
        Item("Perangkat", R.drawable.ic_device) { showMenu(deviceMenu(), false) },
        Item("Home", R.drawable.ic_home) { run(Actions.HOME) },
        Item("Screenshot", R.drawable.ic_screenshot) { run(Actions.SHOT) },
        Item("Kembali", R.drawable.ic_back) { run(Actions.BACK) },
        Item("Terbaru", R.drawable.ic_recents) { run(Actions.RECENTS) },
        Item("Kunci", R.drawable.ic_lock) { run(Actions.LOCK) }
    )

    private fun deviceMenu(): List<Item> = listOf(
        Item("Volume +", R.drawable.ic_vol_up) { volume(AudioManager.ADJUST_RAISE) },
        Item("Volume -", R.drawable.ic_vol_down) { volume(AudioManager.ADJUST_LOWER) },
        Item("Bisukan", R.drawable.ic_mute) { volume(AudioManager.ADJUST_TOGGLE_MUTE) },
        Item("Kunci", R.drawable.ic_lock) { run(Actions.LOCK) },
        Item("Kembali", R.drawable.ic_back) { showMenu(mainMenu(), false) },
        Item("Screenshot", R.drawable.ic_screenshot) { run(Actions.SHOT) },
        Item("Menu daya", R.drawable.ic_power) { run(Actions.POWER) }
    )

    private fun volume(dir: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
    }

    /** Tutup menu dulu (animasi), baru jalankan aksi. */
    private fun run(code: String) {
        dismissMenu { doAction(code) }
    }

    private fun perform(code: String) {
        if (code == Actions.MENU) {
            if (scrim == null) showMenu(mainMenu()) else dismissMenu()
        } else {
            doAction(code)
        }
    }

    private fun doAction(code: String) {
        when (code) {
            Actions.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            Actions.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            Actions.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            Actions.NOTIF -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            Actions.QS -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            Actions.POWER -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            Actions.LOCK -> if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            Actions.SHOT -> if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            Actions.ASSIST -> openAssistant()
            else -> {}
        }
    }

    private fun openAssistant() {
        val intents = listOf(Intent(Intent.ACTION_VOICE_COMMAND), Intent(Intent.ACTION_ASSIST))
        for (i in intents) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { startActivity(i) }.isSuccess) return
        }
    }
}
