# Second Take Remote tests, 13.11.2026
Phone app versionName 1 (v2 released 18:29, not checked installed). Pixel 7 screenshots by adb screencap.
| Test | Result |
|---|---|
| OFFLINE state with Mac app down | PASS (red NO ANSWER bar, dimmed buttons) |
| CONNECTED state, shot text, Takes count, WATCH green, RECORD red, STOP 00:11 | PASS |
| Taps via adb, phone lock | UNTESTED (stopped by the coordinator) |
| IN USE BY screen on 423 | UNTESTED (no screenshot taken while locked) |
See MPA_LESSONS_ANALYZER/TESTS-13.11.2026.md for the protocol results.

## Fixes (Second Take Remote developer)
- 13.11.2026 20:50: v2 (no codes, auto-find, first come first served) installed on the Nothing (100.67.149.40:5555) and on the Pixel (100.70.23.53:5555), no taps; adb reverse tcp:8965 set on both. No open bugs in this file yet.
