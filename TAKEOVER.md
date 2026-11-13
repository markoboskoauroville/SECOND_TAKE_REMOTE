# Second Take Remote (Mantra): takeover

Android remote control (sister app) for a Mac guide, Second Take (Mantra). General and publishable: no personal data, no lesson text,
no addresses or keys compiled in. The wire is PROTOCOL.md. App: plain Android views in Kotlin, no libraries, portrait,
screen kept on, no sound. Package com.mantra.remoteshooting. CAMERA colours (green watch, amber live, red recording).

Build: only by the GitHub workflow .github/workflows/android.yml on a push to main (android/gradle.properties appVersion is the one
number); release v<N> is Latest; secret ANDROID_DEBUG_KEYSTORE_B64 keeps updates installing over each other. Never gradlew on the Mac.

Phone: top bar shows the connection (tap to enter address:port and pairing code; known guides are remembered). Middle: shot
number, code, title, say text, takes. Bottom, centred: WATCH, LIVE, RECORD/STOP (red with elapsed time), AGAIN, NEXT; SHOTS jumps.

mock/guide.py is a loopback test guide (no recording). Not done in v1: Bonjour discovery (_secondtake._tcp), QR pairing.
