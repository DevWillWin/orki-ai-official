# Startup investigation

## Finding

The original launch path created the Perchance image engine eagerly while constructing
`OrkiViewModel`. `PerchanceImageEngine.init` posted `WebView` creation to the main looper before
Compose could submit its first frame. Constructing Android WebView initializes Chromium and its
renderer process, which is expensive and device-dependent. The embedded warm-up page also contains
a 10-second fallback timer. That timer is asynchronous and was not itself a deliberate app delay,
but its presence and the eager Chromium setup explain why the problem was most visible on devices
where WebView startup or page initialization was slow.

Commit `b741330` partially fixed this by making the image service/WebView lazy. The remaining
first-frame path still did all of the following synchronously or eagerly:

- read all `SharedPreferences` values (the first read can wait for disk loading);
- construct Room/DAO, OkHttp and Play Billing objects;
- start Billing-related collectors before the first frame;
- install unused Firebase/App Check startup providers through unused dependencies;
- use `Theme.DeviceDefault`, which can produce a black system starting window in dark mode;
- specify only `android:windowBackground`, which is not the correct Android 12+ splash contract.

The black color and the delay therefore had related but distinct causes: eager initialization held
up the app frame, while the platform starting-window theme exposed that wait as black.

## Fix

- `MainActivity` now uses AndroidX SplashScreen with an ivory background and Orki logo. There is no
  keep condition or timer; it leaves when the first app frame is ready.
- The normal window background is an explicit light ivory rather than a device-dependent theme.
- `OrkiViewModel` exposes a usable Guest UI state immediately.
- Saved account/settings values are read on `Dispatchers.IO`; a 2 dp progress line is shown while
  that short restore runs.
- Room is lazy, conversation collection runs on `Dispatchers.IO`, and the drawer shows a history
  loading state. Failure leaves new chat usable and shows a snackbar message.
- Play Billing and AI endpoint warm-up begin from the first-draw callback, not from a timer. Both
  are optional and cannot block the first frame.
- WebView/Chromium remains strictly on-demand after an image request. Image generation now waits
  asynchronously for the local JS bridge and fails fast when WebView initialization fails.
- TTS cache directory I/O is lazy.
- Unused Firebase AI/App Check runtime dependencies were removed, eliminating their automatic
  process-start providers without removing any referenced app feature.
- `OrkiStartup` log markers and `scripts/measure-startup.sh` make first-frame timing repeatable.

## Measurement

Measured on the same hardware-accelerated Android API 29 x86_64 emulator using debug APKs. Window
animations were disabled. Each APK was installed fresh, app data was cleared once, and five
cold-process starts were run with `am force-stop` followed by `am start -S -W`.

| Revision | First run after install | Median of 5 (`TotalTime`) | Range |
|---|---:|---:|---:|
| Original `98883d6` (eager WebView) | 4,955 ms | 3,533 ms | 3,516–4,955 ms |
| Partial `b741330` | 3,480 ms | 3,480 ms | 3,430–3,515 ms |
| Optimized branch | 3,170 ms | 3,266 ms | 3,170–3,602 ms |

The optimized build improved the first post-install launch by **1,785 ms (36.0%)** versus the
original eager-WebView build. Across repeated cold-process starts, the median improved by
**267 ms (7.6%)** versus the original and **214 ms (6.1%)** versus the partial fix. The new internal
`first_frame_drawn_ms` marker had a 2,561 ms median; `am` includes launch/system overhead before the
app process timestamp.

The API 29 emulator did not reproduce the approximately 10-second physical-device result; its
WebView implementation and host CPU initialized substantially faster. The source-level cause is
still deterministic: Chromium creation was queued on the main thread ahead of the first Compose
frame. The final implementation removes that work from launch rather than covering it with a
longer splash.

Raw measurements are in [`startup-benchmark.csv`](startup-benchmark.csv). To measure a release on
a physical device, install it and run:

```bash
scripts/measure-startup.sh 5
```
