package community.auroville.secondtake.remote

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

class MainActivity : Activity() {
    // Colour roles of the CAMERA language: green on, grey off, amber armed, red recording.
    private val green = Color.parseColor("#33D17A")
    private val grey = Color.parseColor("#7A8087")
    private val amber = Color.parseColor("#E8A33D")
    private val dark = Color.parseColor("#33383E")
    private val red = Color.parseColor("#FF3B30")
    private val sand = Color.parseColor("#F2DDB4")

    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("remote", MODE_PRIVATE) }

    private var guide: Guide? = null
    private var shots: List<Shot> = emptyList()
    private var shotsFor = -1
    private var status: Status? = null
    private var online = false
    private var failure = ""
    private var running = true
    private var localRecStart = 0L

    private lateinit var top: TextView
    private lateinit var modeView: TextView
    private lateinit var counter: TextView
    private lateinit var codeView: TextView
    private lateinit var titleView: TextView
    private lateinit var sayView: TextView
    private lateinit var infoView: TextView
    private lateinit var watch: Button
    private lateinit var live: Button
    private lateinit var rec: Button
    private lateinit var again: Button
    private lateinit var next: Button
    private lateinit var list: Button

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun box(color: Int, r: Int = 14) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }

    private fun key(text: String, h: Int, sp: Float): Button = Button(this).apply {
        this.text = text; textSize = sp; typeface = Typeface.DEFAULT_BOLD; isAllCaps = true
        setTextColor(Color.WHITE); stateListAnimator = null; minHeight = 0; minimumHeight = 0
        layoutParams = LinearLayout.LayoutParams(0, dp(h), 1f).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) }
    }

    private fun label(sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        textSize = sp; setTextColor(color); gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setOnApplyWindowInsetsListener { v, ins ->
                v.setPadding(dp(16) + ins.systemWindowInsetLeft, dp(8) + ins.systemWindowInsetTop,
                    dp(16) + ins.systemWindowInsetRight, dp(12) + ins.systemWindowInsetBottom)
                ins
            }
        }
        // top: connection state
        val bar = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        top = label(13f, Color.WHITE, true).apply {
            setPadding(dp(14), dp(8), dp(14), dp(8)); setOnClickListener { connectDialog() }
        }
        modeView = label(24f, grey, true).apply { setPadding(0, dp(8), 0, 0) }
        bar.addView(top); bar.addView(modeView)
        root.addView(bar)

        // middle: the shot
        val scroll = ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        val mid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, dp(12)) }
        counter = label(13f, grey, true)
        codeView = label(20f, amber, true)
        titleView = label(30f, Color.WHITE, true).apply { setPadding(0, dp(4), 0, dp(10)) }
        sayView = label(22f, sand).apply { setLineSpacing(0f, 1.15f) }
        infoView = label(14f, grey).apply { setPadding(0, dp(14), 0, 0) }
        listOf(counter, codeView, titleView, sayView, infoView).forEach { mid.addView(it) }
        scroll.addView(mid)
        root.addView(scroll)

        list = key("Shots", 44, 13f).apply { background = box(dark); setOnClickListener { pickShot() } }
        root.addView(list.also { (it.layoutParams as LinearLayout.LayoutParams).apply { width = -1 } })

        // bottom, centred: WATCH LIVE / RECORD-STOP / AGAIN NEXT
        val r1 = LinearLayout(this).apply { gravity = Gravity.CENTER }
        watch = key("Watch", 64, 20f); live = key("Live", 64, 20f)
        r1.addView(watch); r1.addView(live)
        val r2 = LinearLayout(this).apply { gravity = Gravity.CENTER }
        rec = key("Record", 96, 30f)
        r2.addView(rec)
        val r3 = LinearLayout(this).apply { gravity = Gravity.CENTER }
        again = key("Again", 64, 20f); next = key("Next", 64, 20f)
        r3.addView(again); r3.addView(next)
        root.addView(r1); root.addView(r2); root.addView(r3)
        setContentView(root)

        watch.setOnClickListener { send("watch") }
        live.setOnClickListener { send("live") }
        rec.setOnClickListener { send(if (status?.mode == "recording") "stop" else "record") }
        again.setOnClickListener { send("again") }
        next.setOnClickListener { send("next") }

        val saved = prefs.getString("current", "") ?: ""
        if (saved.isNotEmpty()) use(saved, prefs.getString("code:$saved", "") ?: "") else render()
        poll()
        tick()
        if (saved.isEmpty()) connectDialog()
    }

    override fun onDestroy() { running = false; super.onDestroy() }

    private fun use(addr: String, code: String) {
        guide = Guide(addr, code); shotsFor = -1; shots = emptyList(); status = null; online = false; failure = ""
        prefs.edit().putString("current", addr).putString("code:$addr", code)
            .putStringSet("known", (prefs.getStringSet("known", emptySet()) ?: emptySet()) + addr).apply()
        render()
    }

    private fun poll() {
        val g = guide
        if (g != null) io.execute {
            try {
                val s = g.status()
                if (s.shots != shotsFor || shots.isEmpty()) { shots = g.shots(); shotsFor = s.shots }
                ui.post { status = s; online = true; failure = ""; render() }
            } catch (e: Exception) {
                val m = if (e is SecurityException) "Pairing code refused" else "No answer"
                ui.post { online = false; failure = m; render() }
            }
        }
        if (running) ui.postDelayed({ poll() }, 1000)
    }

    private fun tick() { render(); if (running) ui.postDelayed({ tick() }, 500) }

    private fun send(name: String, n: Int? = null) {
        val g = guide ?: return connectDialog()
        if (name == "record") localRecStart = System.currentTimeMillis()
        io.execute {
            try { g.command(name, n) } catch (e: Exception) { ui.post { failure = "Command failed"; render() } }
        }
    }

    private fun mm(s: Int) = "%02d:%02d".format(s / 60, s % 60)

    private fun render() {
        val s = status
        val mode = if (online && s != null) s.mode else "offline"
        top.text = when {
            guide == null -> "NOT CONNECTED  ·  TAP TO CONNECT"
            online -> "CONNECTED  ·  " + (if (s?.name.isNullOrEmpty()) guide!!.base else s!!.name)
            else -> (failure.ifEmpty { "Connecting" }).uppercase() + "  ·  " + guide!!.base
        }
        top.background = box(if (online) Color.parseColor("#1E6B43") else if (guide == null) dark else Color.parseColor("#7A2B25"), 20)
        modeView.text = mode.uppercase()
        modeView.setTextColor(when (mode) { "recording" -> red; "live" -> amber; "watch" -> green; "idle" -> sand; else -> grey })

        val cur = shots.firstOrNull { it.n == s?.shot }
        counter.text = if (cur != null) "SHOT ${cur.n} OF ${shots.size}" else ""
        codeView.text = cur?.code ?: ""
        titleView.text = cur?.title ?: if (online) "No shot" else ""
        sayView.text = cur?.say ?: ""
        infoView.text = if (s != null && online) "Takes ${s.takes}" + (if (cur != null && cur.secs > 0) "  ·  about ${cur.secs} sec" else "") else ""

        val recording = online && s?.mode == "recording"
        val sec = if (recording) maxOf(s!!.elapsed, if (s.elapsed == 0) ((System.currentTimeMillis() - localRecStart) / 1000).toInt() else 0) else 0
        rec.text = if (recording) "Stop  ${mm(sec)}" else "Record"
        rec.background = box(if (recording) red else if (online) Color.parseColor("#8E2A24") else dark, 18)
        val on = online
        fun style(bt: Button, active: Boolean, color: Int) {
            bt.background = box(if (!on) dark else if (active) color else Color.parseColor("#4A5058"))
            bt.alpha = if (on) 1f else 0.5f
        }
        style(watch, mode == "watch", green); style(live, mode == "live", amber)
        style(again, false, grey); style(next, false, grey)
        list.alpha = if (on) 1f else 0.5f
    }

    private fun pickShot() {
        if (shots.isEmpty()) return
        val items = shots.map { "${it.n}  ${it.code}  ${it.title}".replace("  ", " ") }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Shots").setItems(items) { _, i -> send("shot", shots[i].n) }.show()
    }

    private fun connectDialog() {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val addr = EditText(this).apply {
            hint = "address:port  (for example 192.168.1.20:8965)"; setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(prefs.getString("current", "") ?: "")
        }
        val code = EditText(this).apply { hint = "pairing code (if the Mac asks for one)"; setSingleLine() }
        col.addView(addr); col.addView(code)
        val known = (prefs.getStringSet("known", emptySet()) ?: emptySet()).sorted()
        known.forEach { k ->
            col.addView(Button(this).apply {
                text = k; isAllCaps = false
                setOnClickListener { addr.setText(k); code.setText(prefs.getString("code:$k", "") ?: "") }
            })
        }
        AlertDialog.Builder(this).setTitle("Connect to the shooting guide").setView(col)
            .setPositiveButton("Connect") { _, _ ->
                val a = addr.text.toString().trim().removePrefix("http://").trimEnd('/')
                if (a.isNotEmpty()) use(a, code.text.toString().trim())
            }.setNegativeButton("Cancel", null).show()
    }
}
