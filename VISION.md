# RoadSide vision path

Replaces `MockVisionClassifier` with a real, fully offline on-device model. The mock is kept
only for deterministic UI testing; it is **not** a fallback. If the backbone or head fails to
load, `ChainVisionClassifier` returns `UNKNOWN` (an earlier version fell back to the mock,
which reported `chain_appears_dry` and so produced chain-maintenance advice from a broken
install).

Classification runs off the main thread: `RoadSideAgent` dispatches it to
`Dispatchers.Default`.

```
CameraX capture (JPEG)
  → ImagePreprocessor        EXIF orient, centre-crop square, resize 224, scale to [0,1]
  → MobileNetFeatureExtractor  mobilenet_v3_feature.tflite  → 1280-d feature vector
  → ChainConditionHead         chain_head.json (logistic regression) → class probabilities
  → ChainVisionClassifier      thresholds → RoadSide visual evidence
  → RoadSideAgent → DiagnosisRules → DiagnosisScreen
```

No network access at any point. The app still declares no `INTERNET` permission.

---

## What the model actually does

**It answers one question: is a drive chain the subject of this photo?**

It does **not** assess the chain's condition. That is a deliberate limit, not an oversight —
see *Why condition is not assessed* below.

| Head class | Evidence emitted | Meaning |
|---|---|---|
| `chain_visible` | `chain_visible` | A drive chain is in view. **Condition not assessed.** |
| `not_chain` | `UNKNOWN` | No drive chain is the subject of this photo. |
| either, low confidence | `UNKNOWN` | Below `p ≥ 0.95` or margin `≥ 0.90`. |

Thresholds live in the head's own metadata, not in Kotlin constants, so the UNKNOWN policy
travels with the head it was validated against. `ChainVisionClassifier.MIN_PROBABILITY` /
`MIN_MARGIN` are only a fallback for a head that declares neither.

`chain_appears_dry` is **no longer produced by the real classifier.** The mock asserted it
from nothing, which was the fabricated-evidence problem this work set out to remove.

---

## Models

| | |
|---|---|
| Backbone | `mobilenet_v3_feature.tflite` — Kaggle `google/mobilenet-v3`, framework `tfLite`, variation `large-100-224-feature-vector`, v1 |
| Licence | Apache 2.0 |
| Size | 16.05 MB, sha256 begins `b24cc3ccf79c499163d6ec991f249ed9` |
| Input / output | `float32[1,224,224,3]` in [0,1] → `float32[1,1280]` |
| Head | `chain_head.json` (**v2**, 2026-09-22), 86 KB — multinomial logistic regression, `C=0.01`, `class_weight=balanced`, `min_probability=0.95`, `min_margin=0.90` |

The v1 head described in the first version of this document (51 images, `p ≥ 0.60`) is kept at
`roadside_vision/chain_head_v1_backup.json` and is **no longer shipped**. On 81 images neither
head was trained on, v1 reached 0.43 precision with a 25 % false-chain rate; v2 reached 1.00
precision with no false chains. Everything below describes v2.

The backbone is frozen; only the linear head is fitted. That is what makes this trainable
from a few hundred images, and why the head ships as a few thousand floats rather than a second
TFLite model — a dot product in Kotlin avoids needing TensorFlow on the build machine
(there are no TF wheels for the Python 3.14 on this box anyway).

Same split as the audio side: general pretrained backbone + tiny task-specific head.

---

## Training data (v2)

| Source | Images used | Licences |
|---|---|---|
| Wikimedia Commons — chain/derailleur/sprocket categories and motorcycle & bicycle part categories | 373 (104 chain, 269 not-chain) | CC BY-SA 4.0/3.0/2.0, CC BY 2.0, CC0, public domain, GFDL, FAL — per file in `manifest_v2.json` |
| Openverse (almost all Flickr) | 101 (11 chain, 90 not-chain) | CC BY 2.0, BY-SA 2.0, BY-ND 2.0 — per file in `manifest_openverse_v2.json` |
| **Total used for train/val/test** | **474** (115 `chain_visible`, 359 `not_chain`) | 304 distinct photographers |
| Labelled ambiguous and held out of training | 138 | scored separately, see below |

Pixabay was excluded: it returns HTTP 403 to scripted fetches, and that was left alone rather
than worked around.

**Labels were assigned by looking at the images**, via numbered contact sheets
(`tools/make_contact_sheets.py` → `sheets/*.jpg` → `labels_*.json`). Category names and
search queries were used only to find *candidates*, never as labels: the `Bicycle chains`
category turned out to be full of chain tools, retail packaging, engineering diagrams and
ruler-measurement shots, and a `rusty chain` query mostly returns marine anchor chains.

Anchor and industrial chains were excluded even though they are corroded chains — they look
nothing like a drive chain, so they would teach the wrong thing.

### Acquisition notes

Both image sources fight bulk download. Wikimedia rate-limits image fetches with HTTP 429
after roughly 40 files (the API itself is fine; it is `upload.wikimedia.org` that throttles).
Openverse worked initially then returned persistent 401s — anonymous access now needs a key.
`tools/fetch_images.py` and `tools/fetch_openverse.py` both back off and skip rather than
hammering. This is the main reason the dataset is small.

---

## Honest performance (v2)

**Split by photographer, not by image**, so no photographer's pictures appear on both sides:
train 284 images / 185 groups, validation 95 / 62, test 95 / 57. `C` and the probability
threshold were chosen on validation only; the test split was scored once.

**Held-out test split, 95 images** (rows = true label, columns = what the app would show):

```
                CHAIN_PRESENT   NOT_CHAIN   UNKNOWN
chain (23)            12             2          9
not chain (72)         2            42         28
```

| | |
|---|---|
| Precision when it says chain | **0.857** (12 of 14) |
| Recall | 0.522 |
| False-chain rate on non-chain photos | 2.8 % |
| Coverage (a decision rather than UNKNOWN) | 0.611 |
| Accuracy ignoring thresholds (argmax) | 0.884 |

On the 131 deliberately ambiguous images (chain partly visible, tiny in frame, motion blur):
5 CHAIN, 53 NOT, 73 UNKNOWN.

**0.857 precision is below the 0.90 bar that was set before training**, so the training script
records `safe: false` and the head is shipped at the user's explicit direction for the fixed
demo, not because it cleared the bar. What it does clear comfortably is the model it replaced:
on 81 images neither head trained on, v1 scored 0.43 precision with a 25 % false-chain rate.

The practical failure mode is "no evidence" rather than wrong evidence: roughly 4 in 10 photos
get UNKNOWN, and about half of real chain photos are missed. Standing further back, a dirty
lens, or a chain that does not fill the frame all push a photo below `p ≥ 0.95`.

---

## Why condition is not assessed

Two things were tried and both failed on the available data.

**1. A learned condition class.** Only **3** obtainable images showed a clearly corroded
drive chain. A class trained on 3 examples would look confident and mean nothing, so
`train_head.py` drops any class below 12 examples and records the drop in the exported
metadata.

**2. A measured rust-hue statistic** (fraction of pixels in an orange-brown hue band with
moderate saturation), as an alternative that needs no labels. Measured over the labelled set:

| | n | median | range of interest |
|---|---|---|---|
| corroded chains | 3 | 0.325 | 0.290 – 0.375 |
| clean chains | 23 | 0.020 | top three: **0.437**, 0.353, 0.298 |

Clean chains photographed on brown wooden benches score **higher** than genuinely corroded
chains. The statistic does not separate the classes, so it was rejected rather than shipped
with a threshold picked to look plausible.

Condition assessment needs labelled corrosion photographs — ideally of the actual N160
chain in known states.

---

## Diagnosis fusion

`chain_visible` **corroborates** the chain hypothesis; it never establishes or rules out a
fault by itself:

| Audio | Vision | Diagnosis |
|---|---|---|
| — | `chain_visible` | `unknown` — seeing a chain is not a fault |
| `possible_chain_noise` | `chain_visible` | `chain_maintenance` |
| `possible_chain_noise` | — | `chain_maintenance` |
| `possible_chain_noise` | `UNKNOWN` | `chain_maintenance` |
| — | `UNKNOWN` | `unknown` |
| `engine_noise` / `mechanical_noise` / `ambient_only` / `UNKNOWN` | any of the above | `unknown` |

`possible_chain_noise` now has two possible origins: `EvidenceTranslator`'s rattle rule, or the
specialist embedding head when it is shipped. The head currently in assets did not pass its
held-out gate (`MODELS.md`), so an audio-only chain diagnosis rests on a demo-scoped model —
the audio + vision row is the one with two independent sensors behind it.

`DiagnosisRules` selects a fault **only from positive sensor evidence**: `possible_chain_noise`,
`brake_squeal` or `clicking_electrical` from audio, or a condition label from vision.
`chain_visible` and `UNKNOWN` are not positive evidence, so they cannot block the audio route
and cannot create a fault on their own.

The rider's typed problem description does **not** affect the verdict. Before 2026-09-22 the
words "chain", "brake", "battery" or "start" in the description selected a fault on their
own. On the OnePlus 13R, a recording classified as background-only plus the text "my chain
makes a noise" produced *Chain maintenance*; it now produces *No clear finding*.
`DiagnosisFlowTest#typedKeywordsDoNotOverrideSensorEvidence` checks 93 text × evidence
combinations.

`chain_appears_dry`, `brake_rotor_worn` and `battery_terminal_corroded` are honoured by the
rules, but no shipped model produces them; only the (unused) mock does.

User-facing evidence text never shows internal labels such as `ambient_only`:
`DiagnosisFlowTest#userFacingTextHasNoInternalLabels` checks every audio × vision label pair.

---

## Running it

```bash
# optional: push validation images (filename prefix = expected label)
adb push C:\Codes\Datasets\roadside_vision\device_images\. \
  /sdcard/Android/data/com.roadside/files/vision_test/
```

```powershell
.\verify.ps1 -Offline
```

`ChainVisionTest` covers model load and shapes, preprocessing (range, crop, EXIF), batch
classification of pushed images with per-image latency, and the fusion rules above.
`DiagnosisFlowTest` covers the load-failure path (missing backbone, missing head → `UNKNOWN`)
and checks that inference runs off the main thread with the real models.

Note what the pushed `device_images` are: of the 50, 46 appear in the v2 dataset (21 train,
11 validation, 14 test) and 4 are outside it. The on-device accuracy printed for them is
therefore mostly a desktop/device **parity** check, not a generalisation estimate — the
generalisation figures are the held-out test numbers above. Parity was measured at 68 of 72
images matching the desktop prediction exactly, median |Δp| 0.005.

Measured per-image latency on the OnePlus 13R (2026-09-22): feature extraction 24–58 ms
(mean ~30 ms on a cool device, ~48 ms on others), plus ~9–14 ms of decode and preprocessing.

### In-app camera capture (verified 2026-09-22, OnePlus 13R)

The full flow was run in the app: CameraX capture → `ChainVisionClassifier` on a worker
thread → visual evidence card → fusion with a recording → Diagnosis → Guide → Finish (which
clears the session). CameraX saves 4096×3072 JPEGs with EXIF orientation 6; the pulled files
were confirmed upright after `ImagePreprocessor`'s rotation.

| Stage | Measured |
|---|---|
| Capture request → evidence, first photo (includes model load) | 531–961 ms |
| Capture request → evidence, later photos | 450–486 ms |
| of which vision (decode+preprocess / features / head) | 45–61 / 32–50 / 0–1 ms |

The rest is CameraX taking and saving the 12 MP JPEG.

In the first run only non-chain scenes were available (a room corner and dark frames). All six
captures gave `UNKNOWN` (the four with probabilities logged were `not_chain`, p = 0.69–0.85),
which is correct.

**Positive camera test (2026-09-22, v2 head).** The phone was pointed at a held-out chain
photograph displayed full-screen on a monitor — an image from the test split, never trained
on. Three captures were taken and re-scored on the device through the same classifier:

| Capture | p(chain) | Result |
|---|---|---|
| 1 | 0.72 | `UNKNOWN` — "not clear enough to confirm a drive chain" |
| 2 | 0.83 | `UNKNOWN` |
| 3 | **0.96** | **`chain_visible`** — shown in the app as chain detected, condition not assessed |

So the live `chain_visible` path is exercised, and the two sub-threshold captures returned
UNKNOWN rather than guessing. One in three crossing `p ≥ 0.95` is consistent with the 0.611
coverage measured on the test split: the camera must be close and steady.

## Reproducing the training

```bash
python tools/fetch_v2.py              # Commons candidates (throttled; skips on 429)
python tools/fetch_openverse_v2.py    # Openverse/Flickr candidates
python tools/make_sheets_v2.py        # -> sheets_v2/*.jpg contact sheets
#   ... label every image by eye into labels_v2_raw.txt as C / N / A ...
python tools/train_vision_v2.py       # -> chain_head_v2_candidate.json + report_vision_v2.json
```

The candidate is copied to `app/src/main/assets/chain_head.json` by hand, so a training run
can never silently change what the app ships. v1 is preserved at `chain_head_v1_backup.json`.
