# Second Take Remote (Mantra): takeover

Android remote control (sister app) for a Mac guide, Second Take (Mantra). General and publishable: no personal data, no lesson text,
no addresses or keys compiled in. The wire is PROTOCOL.md. App: plain Android views in Kotlin, no libraries, portrait,
screen kept on, no sound. Package community.auroville.secondtake.remote. CAMERA colours (green watch, amber live, red recording).

Build: only by the GitHub workflow .github/workflows/android.yml on a push to main (android/gradle.properties appVersion is the one
number); release v<N> is Latest; secret ANDROID_DEBUG_KEYSTORE_B64 keeps updates installing over each other. Never gradlew on the Mac.

Phone: top bar shows the connection (shows the connection; long-press adds an address). Middle: shot
number, code, title, say text, takes. Bottom, centred: WATCH, LIVE, RECORD/STOP (red with elapsed time), AGAIN, NEXT; SHOTS jumps.

mock/guide.py is a loopback test guide (no recording). v2: no codes; the phone finds the guide itself (last address, 127.0.0.1:8965, Bonjour _secondtake._tcp); long-press the top bar adds an address.

## State at close, 13.11.2026

v1-v3 (13.11.2026): Remote Shooting (Mantra) renamed Second Take Remote (Mantra); no codes, the phone finds the guide by itself; one remote at a time (X-Remote-Id, 423 busy, let go after 30 s); v3 MUTE button, one entry per Mac, content descriptions, take message; v2 installed on Nothing and Pixel. Waiting for Marko: press MUTE on the remote once during the Second Take trial.
