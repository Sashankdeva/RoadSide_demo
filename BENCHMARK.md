# RoadSide — YAMNet benchmark & validation notes

Corrected reference for the audio windowing math and the validation harness.
Supersedes the earlier walkthrough figure of "50 windows for a 5 second recording", which is wrong.

---

## 1. Windowing math (corrected)

Constants as implemented in `YamNetInterpreter` / `YamNetAudioClassifier`:

| Parameter | Value | Seconds @ 16 kHz |
|---|---|---|
| Window | 15,600 samples | 0.975 s |
| Hop | 7,800 samples (`WINDOW_SAMPLES / 2`) | 0.4875 s |
| Overlap | 50 % | — |
| Sample rate | 16,000 Hz mono | — |

The loop in `YamNetAudioClassifier.buildWindows` (and the mirrored loop in
`YamNetDebugScreen.runDebugInference`) is:

```kotlin
var start = 0
while (start < samples.size) {
    // emit one zero-padded window
    start += HOP_SAMPLES
}
```

A window is emitted for **every** hop position that starts before the end of the signal,
regardless of how much real audio remains. So:

```
windows = ceil(totalSamples / hop)
```

### Window counts at the current hop

| Clip duration | Samples | Windows | Zero-padding in final window |
|---|---|---|---|
| 0.5 s | 8,000 | 2 | 0.963 s |
| 1 s | 16,000 | 3 | 0.950 s |
| 2 s | 32,000 | 5 | 0.925 s |
| 3 s | 48,000 | 7 | 0.900 s |
| **5 s** | **80,000** | **11** | **0.850 s** |
| 10 s | 160,000 | 21 | 0.725 s |
| 15 s | 240,000 | 31 | 0.600 s |
| 30 s | 480,000 | 62 | 0.713 s |

**A 5 second recording produces 11 windows, not 50.**

### Where the "50" came from

50 is not reachable from a 5 s clip with the implemented hop. Two readings explain it:

- **A 0.1 s hop.** `80,000 / 1,600 = 50` exactly. A 0.1 s hop is 93.6 % overlap — roughly
  5× more inference work per clip than the code actually does. This is the only hop that
  yields exactly 50 for 5 s, so the figure most likely came from an assumed dense-hop
  design that was never implemented.
- **A 24.4 s clip.** At the real hop, 50 windows corresponds to `50 × 0.4875 s = 24.375 s`
  of audio. If the benchmark clip was not actually 5 s, this would explain the count.

Either way the reported pairing of "5 s → 50 windows" is internally inconsistent with the
source, and any per-window latency figure derived by dividing a total by 50 is wrong by
roughly 4.5×.

For reference, canonical YAMNet framing uses a 0.48 s hop (7,680 samples), which gives 11
windows for a 5 s clip — the same as the current implementation. The 0.4875 s hop here is a
close, harmless approximation of the canonical framing.

### Consequence for latency reporting

Per-window latency must be computed as `total_inference_ms / actual_window_count`. The
validation harness reports the window count it actually executed, plus first-window (cold)
and warm-average timings separately, so this cannot silently drift again.

---

## 2. Validation harness

`YamNetBatchValidator` runs a folder of WAV recordings through the **production** inference
path (`YamNetInterpreter` → `EvidenceTranslator`) and reports raw model output alongside the
derived RoadSide category. It observes the production classes without modifying them, so its
output is what the app would actually produce.

### Running it

```bash
adb push idle_01.wav /sdcard/Android/data/com.roadside/files/roadside_test/
adb push chain_01.wav /sdcard/Android/data/com.roadside/files/roadside_test/
```

Then on the device: **Home → YAMNet Debug → Run Batch Validation**.

```bash
adb pull /sdcard/Android/data/com.roadside/files/roadside_test/validation_report.txt
```

The report is also written to logcat under tag `YamNetBatchValidator`.

### Recording format

`ValidationWavReader` (harness-only) parses the real WAV chunk table and handles 8/16/24/32-bit
PCM and 32-bit float, any channel count, any sample rate — downmixing to mono and resampling
to 16 kHz as needed.

This exists because the production `WavDecoder` assumes exactly the format `AudioRecorder`
writes: 16 kHz mono 16-bit with a fixed 44-byte header. Feeding it a 48 kHz stereo phone
recording does not error — it silently produces garbage samples, which would invalidate every
validation result.

**Record the validation set at 16 kHz mono where possible.** The harness resampler is linear
interpolation with no anti-alias filter, so content above 8 kHz folds back when downsampling
from 44.1/48 kHz. Brake squeal energy sits near that edge, so treat high-frequency results
from resampled clips as indicative rather than authoritative.

### Validation set

Clip names are free-form; group them by prefix for readability:

| Group | Clips |
|---|---|
| Normal | motorcycle idling, normal riding / engine under load |
| Drivetrain | chain noise, low-speed drivetrain, varied RPM / gear |
| Brake | brake squeal (only where safely recordable) |
| Background | road noise, wind noise, nearby people / traffic |

Background clips are the false-positive control: they must **not** land in a mechanical
category. Record 5–15 s per clip.

---

## 3. Interpretation rule

```
YAMNet raw prediction  →  EvidenceTranslator  →  RoadSide evidence category
```

YAMNet is a general AudioSet sound-event model. Its outputs are acoustic observations
("Motorcycle", "Rattle", "Vehicle") and never fault verdicts. It has no class for chain wear,
sprocket damage or pad glazing, and no training signal that distinguishes a healthy chain
from a worn one.

Any mechanical meaning is introduced downstream by `EvidenceTranslator` and is only as good
as that mapping. Statements of the form "YAMNet detected a chain fault" are not supportable
from this pipeline. The defensible statement is "YAMNet scored *Rattle* and *Motorcycle*
highly, which `EvidenceTranslator` maps to `possible_chain_noise`."

The validator prints these as two separate blocks — `RAW YAMNET` and
`ROADSIDE INTERPRETATION` — precisely so the two are never conflated in a report.

---

## 4. EvidenceTranslator mapping (corrected)

The original index mapping was almost entirely wrong: "brake" pointed at Spray/Pump/Stir,
"electrical" at four music genres, and 12 of 31 indices were beyond the 0..520 valid range
and silently contributed zero. Every index below was re-derived by resolving a label name
against the shipped `yamnet_class_map.csv`.

| Group | Indices | Labels |
|---|---|---|
| VEHICLE | 294, 300, 301, 320, 321 | Vehicle, Motor vehicle (road), Car, Motorcycle, Traffic noise |
| ENGINE | 337, 338, 342, 343, 344, 345, 346, 347 | Engine, Light/Medium/Heavy engine, Engine knocking, Engine starting, Idling, Accelerating |
| FRICTION | 306, 307, 327, 355, 479 | Skidding, Tire squeal, Train wheels squealing, Squeak, Squeal |
| RATTLE | 130, 398, 403, 483, 486 | Rattle, Mechanisms, Gears, Clatter, Clickety-clack |
| MECHANICAL | 130, 398, 401, 402, 403, 469, 482, 483, 485, 486 | rattle family + Tick, Tick-tock, Scrape, Whir, Clicking |
| ELECTRICAL | 125, 392, 485, 490, 509, 510 | Buzz, Buzzer, Clicking, Hum, Static, Mains hum |
| AMBIENT | 277, 279, 494, 500–504, 507, 508, 514, 515 | Wind, Wind noise, Silence, Inside/Outside, Noise, Environmental noise, White/Pink noise |
| HUMAN/MUSIC | 0, 65, 132 | Speech, Hubbub, Music |

Note: AudioSet has no "Grinding" class — do not add one.

### Aggregation: max, not sum

Groups aggregate by **max of member scores**, not sum. YAMNet emits independent per-class
sigmoids, so summing members is not a calibrated confidence and makes a group with more
members easier to trigger purely because it has more members.

The resulting value is exposed as `evidenceScore`, **not** `confidence`. It is uncalibrated:
it means "the strongest single supporting observation scored this high", not "this category
is this likely". Do not render it to users as a percentage certainty.

### Chain evidence is deliberately hard to reach

The old rule fired `possible_chain_noise` on `mechanical >= 0.10 && (vehicle >= 0.05 || engine >= 0.05)`,
where "mechanical" had been reduced to *Light engine (high frequency)* and *Dental drill*.
A normally running motorcycle satisfied both halves, and `DiagnosisRules` maps
`possible_chain_noise` straight to chain-maintenance advice — so a healthy bike was told to
service its chain.

The corrected rule requires a rattle-family class to be **both** above its own (higher)
threshold **and** louder than every engine class, with vehicle context present. The rattle
group contains no engine classes at all, so a normal engine cannot satisfy it and falls
through to `engine_noise`.

Even when it does fire, this remains weak evidence. AudioSet has no chain class; "Rattle
near a vehicle" is the most that can honestly be claimed, and the category may still be
retired once real recordings are in.

### Thresholds are provisional

Every threshold in `EvidenceTranslator` is a placeholder chosen to keep the pipeline
runnable. None is tuned. The tuning sequence is:

```
real N160 recording → raw YAMNet output → observe actual behaviour → change thresholds
```

Do not adjust them from theory.

---

## 5. Embedding path (built since this section was written)

```
YAMNet embeddings → 1024-d per 0.96 s frame → chain_audio_head.json → p(chain_noise)
```

The original blocker recorded here still holds for `yamnet.tflite`: it is the MediaPipe
single-output variant, scores only, no embedding tensor (`YamNetInterpreter` returns zeros
for embeddings). A second model, `yamnet_embedding.tflite`, was added for the 1024-d output
and is what `YamNetEmbeddingExtractor` and `ChainAudioSpecialist` use.

The narrower head that exists today answers chain / no-chain only, not the five classes
sketched above, and it did not pass its held-out gate. See `MODELS.md` for what it was
trained on and what it is safe to claim.

---

## 6. Verified measurements — OnePlus 13R (re-measured 2026-09-22)

Device: OnePlus CPH2691 (OP5D3BL1), Android 16 / API 36, arm64-v8a, page size 4096.
Measured by the instrumented suite via `adb shell am instrument`, across 9 separate runs on
2026-09-22 (including two with every radio off). Timings vary run to run with device state,
so **ranges** are reported; quote the range, not the best run.

| Metric | Value |
|---|---|
| YAMNet model init (in a running process, not an app launch) | 5–25 ms, typically ~13 ms |
| First window inference | 2–4 ms |
| Warm inference, per window | 1–3 ms (direct loop); **3.1 ms mean** over 1,112 windows of the 98-clip set |
| Windows for a 5 s clip | 11 (confirmed on device) |
| Full 5 s clip, production `YamNetAudioClassifier` (synthetic probe, **includes model load**) | 35–63 ms |
| Recorded clip in the app, decode → evidence (7.8 s, 16 windows, first use) | ~130 ms, off the main thread |
| Vision: MobileNetV3 feature extraction per image | 24–58 ms (mean ~30 ms on a cool device, ~48 ms on others) |
| Vision: JPEG decode + EXIF + crop + resize | ~9–14 ms mean (up to ~70 ms for large images) |
| App cold launch (`am start -W`, TotalTime) | 595–801 ms |
| Input tensor | `[15600]` float32 |
| Output tensor | `[1, 521]` float32 (single output — no embeddings) |
| TFLite runtime | XNNPACK delegate, 41/47 nodes delegated, 9 partitions |
| Debug APK (clean build) | 71.45 MiB — all 4 ABIs (19.3 MiB native libs) + 35.40 MiB assets |
| Release APK, unsigned, no minification | 66.36 MiB |
| Test APK | 0.40 MiB |

Of the 35.40 MiB of assets, `yamnet_embedding.tflite` (15.35 MiB) is used only by the
instrumented tests; the app flow uses `yamnet.tflite` + `mobilenet_v3_feature.tflite` +
`chain_head.json` = 20.0 MiB.

Inference is confirmed genuinely running, not returning zeros: score sum 1.4574, max 0.2457,
with a coherent distribution.

### Threading

`RoadSideAgent` runs audio and vision classification on `Dispatchers.Default`, serialised
per model by a `Mutex` (the TFLite interpreters are not thread-safe). Before 2026-09-22 the
UI's main-thread coroutine scope ran decode, model load and inference directly on the main
thread. `DiagnosisFlowTest#sensorInferenceRunsOffMainThread` calls the agent from the main
thread with the real models and asserts both classifiers run elsewhere.

### Reproducing

```powershell
.\verify.ps1 -Offline
```

Runs the full instrumented suite (25 tests as of 2026-09-22, including the recorder-lifecycle and specialist-head tests) twice: once normally, and once
with airplane mode on **and Wi-Fi and Bluetooth explicitly off**. On this OnePlus, airplane
mode alone leaves Wi-Fi connected (`wifi_on=2`), so the script also confirms that
`dumpsys connectivity` reports no default network and that a ping to 8.8.8.8 fails, both
before and after the tests. If the device still has a network the offline pass is not run
and the script fails. Radio state is restored afterwards. Logcat is streamed to
`app/build/verify/` during each pass, because the device log buffer is too small to hold
the whole report. The exit code is non-zero if anything fails.

### 16 KB page-size compatibility

All four arm64-v8a `.so` files in the APK have `PT_LOAD p_align = 0x4000` (16 KB):
`libtensorflowlite_jni.so`, `libandroidx.graphics.path.so`, `libimage_processing_util_jni.so`,
`libsurface_util_jni.so`. LiteRT 1.4.0 is compliant. The OnePlus 13R currently reports a
4096-byte page size, so the requirement is not yet active on it, but the APK is ready if it
moves to 16 KB kernels. No risk.

### Gradle "Unable to establish loopback connection"

Not a firewall or daemon-port problem. Gradle's daemon calls `Selector.open()`, which on
Windows builds a `PipeImpl` over an **AF_UNIX** socket created under `java.io.tmpdir`.
On this machine AF_UNIX socket creation fails with `EINVAL` for any path under
`%LOCALAPPDATA%` — `C:\Users\<user>\afx` works, `C:\Users\<user>\AppData\Local\afx` does not —
which points at a security-product filter scoped to that directory. `afunix.sys` is present
and ordinary TCP loopback works fine, so nothing is wrong with networking.

Fix (no Windows configuration change, no elevation): point `TEMP`/`TMP` outside
`%LOCALAPPDATA%` before invoking Gradle, so both the client and the forked daemon inherit it.
`verify.ps1` does this automatically. Setting `-Dorg.gradle.jvmargs=-Djdk.net.unixdomain.tmpdir=...`
is **not** sufficient — Gradle strips that property from the daemon's options.
