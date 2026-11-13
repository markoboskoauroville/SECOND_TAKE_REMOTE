# Second Take protocol, version 1

A shooting guide (a Mac app) offers this small HTTP API; the Android app "Second Take Remote (Mantra)" is its remote
control. The phone carries all controls and all text; the guide's screen shows only pictures (the clip during watch,
the camera during live and recording). Shot lists and texts come from the guide at run time; the phone has none built in.

Plain HTTP, JSON, UTF-8. No codes, no pairing: a guide that answers is trusted on its network. Finding the guide: the phone tries, by itself, the last address that worked, `127.0.0.1:8965` (USB with adb reverse) and Bonjour `_secondtake._tcp` on the Wi-Fi; a guide is any host whose GET /status answers JSON with a `protocol` field (an `app` field is accepted too, name taken from it). Several answering: the phone shows a one-tap list of names. A guide should advertise `_secondtake._tcp` with its port. Reaching the guide: same Wi-Fi (its LAN address), a VPN address, or USB with `adb reverse tcp:PORT tcp:PORT` (then
the phone uses `127.0.0.1:PORT`). A guide should listen only where it must (LAN or VPN address, never open to the world).

| Call | Answer / body | Meaning |
|---|---|---|
| GET /status | `{"protocol":1,"name":"Guide name","mode":"idle","shot":3,"shots":11,"takes":2,"elapsed":0}` | mode is `idle`, `watch`, `live` or `recording`; shot is the current shot number n; elapsed is seconds of the running recording |
| GET /shots | `[{"n":1,"title":"...","code":"TR01","say":"...","secs":45}]` | the list, in order (a bare array, or an object {"shots":[...]}); code and secs may be empty or 0 |
| POST /mute | `{"on":true}` | mute or unmute the clip sound on the guide; /status `muted` says which |
| POST /watch | `{}` | play the current shot's reference picture full screen on the guide's screen |
| POST /live | `{}` | show the live camera picture full screen (framing before recording) |
| POST /record | `{}` | start recording the current shot (mode becomes recording) |
| POST /stop | `{}` | stop the recording or the watch (mode idle or live) |
| POST /next | `{}` | go to the next shot |
| POST /again | `{}` | take the current shot again |
| POST /shot | `{"n":5}` | jump to shot n |

Every POST answers 200 with any JSON, e.g. `{"ok":true}`. The phone polls /status once a second.

## One remote at a time: first come, first served

Every request carries `X-Remote-Id` (a random id the phone makes once) and `X-Remote-Name` (for example "Pixel 7"). The first
remote whose GET /status reaches a free guide owns it; its one-second /status polling keeps the connection. While an owner has
asked in the last 5 seconds, any other remote gets `423` with `{"busy":true,"owner":"Pixel 7"}` (on every call), and shows
"IN USE BY Pixel 7" while it looks for another free guide. A guide with no request for 5 seconds is free again. The app lets go
by going quiet: after 30 seconds in the background it stops polling.

`/status` may also carry `muted` (bool) and `message` (text such as "take 1 discarded"); the phone shows both.
