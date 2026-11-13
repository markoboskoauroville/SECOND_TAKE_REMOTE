package community.auroville.secondtake.remote

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
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
    private var stoppedAt = 0L
    private val myId by lazy {
        prefs.getString("id", null) ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString("id", it).apply() }
    }
    private fun newGuide(a: String) = Guide(a, myId, android.os.Build.MODEL)
    private var busyOwner = ""
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
    private lateinit var mute: Button
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
            setPadding(dp(14), dp(8), dp(14), dp(8)); setOnLongClickListener { connectDialog(); true }
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
        list.layoutParams = LinearLayout.LayoutParams(-1, dp(44)).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) }
        root.addView(list)

        // bottom, centred: WATCH LIVE / RECORD-STOP / AGAIN NEXT
        val r1 = LinearLayout(this).apply { gravity = Gravity.CENTER }
        watch = key("Watch", 64, 17f); live = key("Live", 64, 17f); mute = key("Mute", 64, 17f)
        r1.addView(watch); r1.addView(live); r1.addView(mute)
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
        mute.setOnClickListener {
            val g = guide ?: return@setOnClickListener
            val want = !(status?.muted ?: true)
            io.execute { try { g.mute(want) } catch (e: Exception) { ui.post { failure = "Command failed"; render() } } }
        }
        again.setOnClickListener { send("again") }
        next.setOnClickListener { send("next") }

        render()
        startDiscovery()
        poll()
        tick()
    }

    override fun onStop() { stoppedAt = System.currentTimeMillis(); super.onStop() }
    override fun onStart() { super.onStart(); stoppedAt = 0L; busyOwner = "" }

    override fun onDestroy() { running = false; super.onDestroy() }

    private fun use(addr: String) {
        guide = newGuide(addr); shotsFor = -1; shots = emptyList(); status = null; online = false; failure = ""
        prefs.edit().putString("current", addr).apply()
        render()
    }

    // ---- finding the Mac by itself: last address, USB (127.0.0.1:8965), Bonjour on the Wi-Fi ----
    private val found = java.util.concurrent.ConcurrentHashMap<String, String>() // address -> name
    private val nsd by lazy { getSystemService(NSD_SERVICE) as NsdManager }
    private var offered = false

    private fun startDiscovery() {
        try {
            nsd.discoverServices("_secondtake._tcp", NsdManager.PROTOCOL_DNS_SD, object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(t: String) {}
                override fun onDiscoveryStopped(t: String) {}
                override fun onStartDiscoveryFailed(t: String, e: Int) {}
                override fun onStopDiscoveryFailed(t: String, e: Int) {}
                override fun onServiceLost(i: NsdServiceInfo) {}
                @Suppress("DEPRECATION")
                override fun onServiceFound(i: NsdServiceInfo) {
                    nsd.resolveService(i, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(i2: NsdServiceInfo, e: Int) {}
                        override fun onServiceResolved(i2: NsdServiceInfo) {
                            val h = i2.host?.hostAddress ?: return
                            extra.add("$h:${i2.port}")
                        }
                    })
                }
            })
        } catch (e: Exception) { }
    }
    private val extra = java.util.concurrent.CopyOnWriteArraySet<String>()

    /** Probe every candidate; the ones that answer with "protocol" are Second Take guides. */
    private fun search() {
        val cands = LinkedHashSet<String>()
        prefs.getString("last", null)?.let { cands.add(it) }
        cands.add("127.0.0.1:8965")
        cands.addAll(extra)
        cands.addAll(prefs.getStringSet("added", emptySet()) ?: emptySet())
        val hits = java.util.concurrent.ConcurrentHashMap<String, String>()
        val latch = java.util.concurrent.CountDownLatch(cands.size)
        cands.forEach { a ->
            Thread {
                try { hits[a] = newGuide(a).status().name.ifEmpty { a } } catch (e: BusyException) { busyOwner = e.owner } catch (e: Exception) { }
                latch.countDown()
            }.start()
        }
        latch.await(4, java.util.concurrent.TimeUnit.SECONDS)
        ui.post {
            if (guide != null && online) return@post
            found.clear(); found.putAll(hits)
            // The same Mac answers on several interfaces: one entry per name, the first candidate wins.
            val byName = LinkedHashMap<String, String>()
            cands.forEach { a -> hits[a]?.let { n -> if (!byName.containsKey(n)) byName[n] = a } }
            when {
                byName.size == 1 -> use(byName.values.first())
                byName.size > 1 && !offered -> {
                    offered = true
                    val addrs = byName.values.toList()
                    AlertDialog.Builder(this).setTitle("Which Mac?")
                        .setItems(byName.keys.toTypedArray()) { _, i -> use(addrs[i]) }
                        .setOnDismissListener { offered = false }.show()
                }
            }
        }
    }

    private fun poll() {
        val g = guide
        val away = stoppedAt != 0L && System.currentTimeMillis() - stoppedAt > 30000
        if (away) { if (running) ui.postDelayed({ poll() }, 1000); return }
        if (g != null) io.execute {
            try {
                val s = g.status()
                if (s.shots != shotsFor || shots.isEmpty()) { shots = g.shots(); shotsFor = s.shots }
                prefs.edit().putString("last", g.base).apply()
                ui.post { status = s; online = true; failure = ""; render() }
            } catch (e: BusyException) {
                ui.post { online = false; busyOwner = e.owner; failure = "In use by ${e.owner}"; guide = null; render() }
            } catch (e: Exception) {
                val m = "No answer"
                ui.post { online = false; failure = m; render() }
            }
        }
        if (g == null || !online) {
            if (!searching) { searching = true; Thread { try { search() } finally { searching = false } }.start() }
        }
        if (running) ui.postDelayed({ poll() }, 1000)
    }
    private var searching = false

    private fun tick() { render(); if (running) ui.postDelayed({ tick() }, 500) }

    private fun send(name: String, n: Int? = null) {
        val g = guide ?: return
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
            guide == null -> if (busyOwner.isNotEmpty()) "IN USE BY ${busyOwner.uppercase()}  ·  LOOKING FOR ANOTHER" else "LOOKING FOR THE MAC"
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

        val msg = s?.message ?: ""
        if (msg.isNotEmpty() && online) infoView.text = infoView.text.toString() + "\n" + msg
        val recording = online && s?.mode == "recording"
        val sec = if (recording) maxOf(s!!.elapsed, if (s.elapsed == 0) ((System.currentTimeMillis() - localRecStart) / 1000).toInt() else 0) else 0
        rec.text = if (recording) "Stop  ${mm(sec)}" else "Record"
        rec.background = box(if (recording) red else if (online) Color.parseColor("#8E2A24") else dark, 18)
        val on = online
        fun style(bt: Button, active: Boolean, color: Int) {
            bt.background = box(if (!on) dark else if (active) color else Color.parseColor("#4A5058"))
            bt.alpha = if (on) 1f else 0.5f
        }
        val muted = s?.muted ?: true
        mute.text = if (muted) "Unmute" else "Mute"
        style(mute, on && !muted, green)
        style(watch, mode == "watch", green); style(live, mode == "live", amber)
        style(again, false, grey); style(next, false, grey)
        list.alpha = if (on) 1f else 0.5f
        top.contentDescription = top.text
        modeView.contentDescription = "Mode " + modeView.text
        counter.contentDescription = counter.text
        codeView.contentDescription = "Code " + codeView.text
        titleView.contentDescription = "Title " + titleView.text
        sayView.contentDescription = "Say " + sayView.text
        infoView.contentDescription = "Info " + infoView.text
        listOf(watch, live, mute, rec, again, next, list).forEach { it.contentDescription = it.text }
    }

    private fun pickShot() {
        if (shots.isEmpty()) return
        val items = shots.map { "${it.n}  ${it.code}  ${it.title}".replace("  ", " ") }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Shots").setItems(items) { _, i -> send("shot", shots[i].n) }.show()
    }

    /** Hidden: long-press the top bar. Adds an address to look at; nothing else to type. */
    private fun connectDialog() {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val addr = EditText(this).apply {
            hint = "address:port"; setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        col.addView(addr)
        AlertDialog.Builder(this).setTitle("Add an address").setView(col)
            .setPositiveButton("Add") { _, _ ->
                val a = addr.text.toString().trim().removePrefix("http://").trimEnd('/')
                if (a.isNotEmpty()) prefs.edit().putStringSet("added", (prefs.getStringSet("added", emptySet()) ?: emptySet()) + a).apply()
            }.setNegativeButton("Cancel", null).show()
    }
}
