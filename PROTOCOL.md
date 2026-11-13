# Remote shooting protocol, version 1

A shooting guide (a Mac app) offers this small HTTP API; the Android app "Remote Shooting (Mantra)" is its remote
control. The phone carries all controls and all text; the guide's screen shows only pictures (the clip during watch,
the camera during live and recording). Shot lists and texts come from the guide at run time; the phone has none built in.

Plain HTTP, JSON, UTF-8. The guide may require a pairing code in the header `X-Pair-Code`; wrong or missing: 401 or 403.
Reaching the guide: same Wi-Fi (its LAN address), a VPN address, or USB with `adb reverse tcp:PORT tcp:PORT` (then
the phone uses `127.0.0.1:PORT`). A guide should listen only where it must (LAN or VPN address, never open to the world).

| Call | Answer / body | Meaning |
|---|---|---|
| GET /status | `{"protocol":1,"name":"Guide name","mode":"idle","shot":3,"shots":11,"takes":2,"elapsed":0}` | mode is `idle`, `watch`, `live` or `recording`; shot is the current shot number n; elapsed is seconds of the running recording |
| GET /shots | `[{"n":1,"title":"...","code":"TR01","say":"...","secs":45}]` | the list, in order; code and secs may be empty or 0 |
| POST /watch | `{}` | play the current shot's reference picture full screen on the guide's screen |
| POST /live | `{}` | show the live camera picture full screen (framing before recording) |
| POST /record | `{}` | start recording the current shot (mode becomes recording) |
| POST /stop | `{}` | stop the recording or the watch (mode idle or live) |
| POST /next | `{}` | go to the next shot |
| POST /again | `{}` | take the current shot again |
| POST /shot | `{"n":5}` | jump to shot n |

Every POST answers 200 with any JSON, e.g. `{"ok":true}`. The phone polls /status once a second.
