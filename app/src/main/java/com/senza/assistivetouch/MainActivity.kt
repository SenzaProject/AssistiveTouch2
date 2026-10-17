package com.senza.assistivetouch

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView

/** Latar warna-warni supaya efek kaca terlihat. */
class AuroraView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawColor(Color.parseColor("#090912"))
        blob(c, w * 0.12f, h * 0.10f, w * 0.85f, "#3A7BFF")
        blob(c, w * 0.95f, h * 0.38f, w * 0.80f, "#FF4FA3")
        blob(c, w * 0.10f, h * 0.72f, w * 0.80f, "#22D3C5")
        blob(c, w * 0.90f, h * 0.98f, w * 0.90f, "#8B5CF6")
    }

    private fun blob(c: Canvas, x: Float, y: Float, r: Float, hex: String) {
        val base = Color.parseColor(hex) and 0x00FFFFFF
        p.shader = RadialGradient(
            x, y, r, intArrayOf(base or (0x99 shl 24), base), null, Shader.TileMode.CLAMP
        )
        c.drawCircle(x, y, r, p)
    }
}

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private val prefs by lazy { getSharedPreferences(Actions.PREFS, Context.MODE_PRIVATE) }
    private val d get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        val frame = FrameLayout(this)
        frame.addView(AuroraView(this), FrameLayout.LayoutParams(-1, -1))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(64), dp(20), dp(40))
        }
        frame.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(root)
        }, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)

        root.addView(TextView(this).apply {
            text = "Senza\nAssistive Touch"
            textSize = 32f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setShadowLayer(8f, 0f, 2f, Color.parseColor("#55000000"))
        })

        statusText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = GlassDrawable(d, -1f, 70, 0.14f)
        }
        root.addView(statusText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16); bottomMargin = dp(22) })

        // --- Aktivasi
        val c1 = card("AKTIVASI", root)
        c1.addView(glassButton("Buka Pengaturan Aksesibilitas") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        c1.addView(TextView(this).apply {
            text = "Cari \"Senza Assistive Touch\" lalu aktifkan."
            textSize = 13f
            setTextColor(Color.parseColor("#B3FFFFFF"))
            setPadding(0, dp(10), 0, 0)
        })

        // --- Tampilan
        val c2 = card("TAMPILAN", root)
        slider(c2, "Ukuran tombol", Actions.K_SIZE, 54, 36, 90)
        slider(c2, "Transparansi saat diam", Actions.K_OPACITY, 60, 20, 100)
        slider(c2, "Tingkat blur", Actions.K_BLUR, 60, 0, 100)
        slider(c2, "Efek glass", Actions.K_GLASS, 60, 0, 100)

        // --- Gestur
        val c3 = card("GESTUR", root)
        picker(c3, "Ketuk sekali", Actions.K_TAP, Actions.MENU)
        picker(c3, "Ketuk dua kali", Actions.K_DOUBLE, Actions.HOME)
        picker(c3, "Tekan lama", Actions.K_LONG, Actions.RECENTS)
    }

    private fun card(title: String, root: LinearLayout): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(18))
            background = GlassDrawable(d, dp(28).toFloat(), 55, 0.16f)
        }
        c.addView(TextView(this).apply {
            text = title
            textSize = 12f
            letterSpacing = 0.1f
            setTextColor(Color.parseColor("#B3FFFFFF"))
            setPadding(0, 0, 0, dp(8))
        })
        root.addView(c, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
        return c
    }

    private fun glassButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 16f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GlassDrawable(d, -1f, 85, 0.10f)
        setOnClickListener { onClick() }
        setOnTouchListener { v, e -> pressAnim(v, e, 0.96f); false }
    }

    private fun slider(parent: LinearLayout, title: String, key: String, def: Int, min: Int, max: Int) {
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        head.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val value = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#B3FFFFFF"))
        }
        head.addView(value)
        parent.addView(head)

        val sb = SeekBar(this).apply {
            this.max = max - min
            progress = prefs.getInt(key, def) - min
            progressTintList = ColorStateList.valueOf(Color.WHITE)
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#44FFFFFF"))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    value.text = (p + min).toString()
                    if (fromUser) prefs.edit().putInt(key, p + min).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        value.text = (sb.progress + min).toString()
        parent.addView(sb, LinearLayout.LayoutParams(-1, -2).apply {
            leftMargin = -dp(8); rightMargin = -dp(8)
        })
    }

    private fun picker(parent: LinearLayout, title: String, key: String, def: String) {
        parent.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, dp(10), 0, dp(6))
        })

        val box = FrameLayout(this).apply {
            background = GlassDrawable(d, dp(16).toFloat(), 45, 0.10f)
        }
        val sp = Spinner(this).apply {
            background = null
            setPadding(dp(8), dp(10), dp(36), dp(10))
            adapter = ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_dropdown_item, Actions.labels
            )
            setSelection(Actions.codes.indexOf(prefs.getString(key, def)).coerceAtLeast(0))
            setPopupBackgroundDrawable(GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#F01C1C26"))
            })
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    prefs.edit().putString(key, Actions.codes[pos]).apply()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        box.addView(sp, FrameLayout.LayoutParams(-1, -2))
        box.addView(TextView(this).apply {
            text = "▾"
            textSize = 16f
            setTextColor(Color.parseColor("#B3FFFFFF"))
        }, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL).apply {
            rightMargin = dp(14)
        })
        parent.addView(box, LinearLayout.LayoutParams(-1, -2))
    }

    override fun onResume() {
        super.onResume()
        val on = isServiceEnabled()
        statusText.text = if (on) "●  AKTIF" else "●  Belum aktif"
        statusText.setTextColor(if (on) Color.parseColor("#7CF29A") else Color.parseColor("#FF8A80"))
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("$packageName/${AssistiveService::class.java.name}")
    }
}
