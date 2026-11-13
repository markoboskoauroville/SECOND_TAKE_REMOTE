#!/usr/bin/env python3
"""A fake shooting guide for testing the Second Take app. Loopback only. Records nothing."""
import json, sys, time
from http.server import BaseHTTPRequestHandler, HTTPServer
S = [dict(n=i, title=t, code=c, say=s, secs=45) for i, (t, c, s) in enumerate([
    ("Welcome", "A01", "Say hello and tell what the lesson is about."),
    ("The first exercise", "B02", "Show the grip slowly. Two sentences, then play it once."),
    ("Closing", "C03", "Thank the viewer and say what comes next.")], 1)]
st = dict(mode="idle", shot=1, takes=0, t0=0)
class H(BaseHTTPRequestHandler):
    def out(self, o):
        b = json.dumps(o).encode(); self.send_response(200); self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(b))); self.end_headers(); self.wfile.write(b)
    def do_GET(self):
        if self.path == "/status":
            e = int(time.time() - st["t0"]) if st["mode"] == "recording" else 0
            self.out(dict(protocol=1, name="Test guide", mode=st["mode"], shot=st["shot"], shots=len(S), takes=st["takes"], elapsed=e))
        elif self.path == "/shots": self.out(S)
        else: self.send_error(404)
    def do_POST(self):
        n = int(self.headers.get("Content-Length") or 0); body = json.loads(self.rfile.read(n) or b"{}")
        p = self.path.strip("/")
        if p in ("watch", "live"): st["mode"] = p
        elif p == "record": st["mode"] = "recording"; st["t0"] = time.time()
        elif p == "stop": st["takes"] += st["mode"] == "recording"; st["mode"] = "idle"
        elif p == "next": st["shot"] = min(len(S), st["shot"] + 1)
        elif p == "again": st["mode"] = "idle"
        elif p == "shot": st["shot"] = body.get("n", 1)
        else: return self.send_error(404)
        self.out(dict(ok=True))
    def log_message(self, *a): pass
HTTPServer(("127.0.0.1", int(sys.argv[1]) if len(sys.argv) > 1 else 8975), H).serve_forever()
