package community.auroville.secondtake.remote

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One shot of the guide's list. Everything comes from the Mac at run time. */
data class Shot(val n: Int, val title: String, val code: String, val say: String, val secs: Int)

/** GET /status. mode is idle, watch, live or recording. */
data class Status(val name: String, val mode: String, val shot: Int, val shots: Int, val takes: Int, val elapsed: Int)

/** The wire is described in PROTOCOL.md. Plain HTTP, JSON, an optional pairing code in X-Pair-Code. */
class Guide(val base: String, val code: String) {
    private fun call(method: String, path: String, body: String? = null): String {
        val c = URL("http://$base$path").openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 2500
            c.readTimeout = 4000
            if (code.isNotEmpty()) c.setRequestProperty("X-Pair-Code", code)
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            if (c.responseCode == 401 || c.responseCode == 403) throw SecurityException("pairing code refused")
            if (c.responseCode >= 400) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun status(): Status {
        val j = JSONObject(call("GET", "/status"))
        return Status(
            j.optString("name", ""), j.optString("mode", "idle"), j.optInt("shot", 0),
            j.optInt("shots", 0), j.optInt("takes", 0), j.optInt("elapsed", 0)
        )
    }

    fun shots(): List<Shot> {
        val a = JSONArray(call("GET", "/shots"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Shot(o.optInt("n", it + 1), o.optString("title", ""), o.optString("code", ""), o.optString("say", ""), o.optInt("secs", 0))
        }
    }

    /** watch, live, record, stop, next, again; and shot with {"n":N}. */
    fun command(name: String, n: Int? = null) {
        call("POST", "/$name", if (n != null) "{\"n\":$n}" else "{}")
    }
}
