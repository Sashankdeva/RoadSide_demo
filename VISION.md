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
| either, low confidence | `UNKNOWN` | Below `p ≥ 0.60` or margin `≥ 0.15`. |

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
| Head | `chain_head.json`, 51.8 KB — multinomial logistic regression, `C=0.05`, `class_weight=balanced` |

The backbone is frozen; only the linear head is fitted. That is what makes this trainable
from ~50 images, and why the head ships as a few thousand floats rather than a second
TFLite model — a dot product in Kotlin avoids needing TensorFlow on the build machine
(there are no TF wheels for the Python 3.14 on this box anyway).

Same split as the audio side: general pretrained backbone + tiny task-specific head.

---

## Training data

| Source | Count | Licence |
|---|---|---|
| Wikimedia Commons — `Bicycle chains`, `Motorcycle chains`, `Roller chains` | 53 | per-file, recorded in `images_manifest.json` |
| Wikimedia Commons — `Rusty bicycles`, `Rusty chains`, `Rust` | 10 | per-file |
| **Usable after labelling** | **51** (26 `chain_visible`, 25 `not_chain`) | |
| Excluded | 12 | greyscale, line art, extreme aspect ratio, anchor/industrial chains |

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

## Honest performance

**Stratified 5-fold cross-validation, 51 images:**

```
               precision  recall  f1-score  support
chain_visible      0.750   0.808     0.778       26
    not_chain      0.783   0.720     0.750       25
       accuracy                      0.765       51

confusion matrix (rows true, cols predicted)
                chain_visible  not_chain
chain_visible   21             5
not_chain       7              18
```

**Cross-validated accuracy: 76.5 %.** That is the number to quote. Every prediction in it
comes from a fold that never saw that image.

Refitting on all 51 images scores 100 % on those same images. That figure is meaningless as
a generalisation estimate and is reported only as a desktop/device parity reference.

76.5 % on a 2-class problem with 51 images is a working prototype, not a reliable inspector.
The confidence thresholds (`p ≥ 0.60`, margin `≥ 0.15`) route uncertain photos to `UNKNOWN`
rather than guessing, so the practical failure mode is "no evidence" rather than wrong
evidence.

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

`DiagnosisRules` selects a fault **only from positive sensor evidence**: `possible_chain_noise`,
`brake_squeal` or `clicking_electrical` from audio, or a condition label from vision.
`chain_visible` and `UNKNOWN` are not positive evidence, so they cannot block the audio route
and cannot create a fault on their own.

The rider's typed problem description does **not** affect the verdict. Before 2026-09-22 the
words "chain", "brake", "battery" or "start" in the description selected a fault on their
own. On the OnePlus 13R, a recording classified as background-only plus the text "my chain
makes a noise" produced *Chain maintenance*; it now produces *Inconclusive / Unknown*.
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

Note that the 50 pushed `device_images` are the same images the head was trained on, so the
on-device accuracy printed for them (100 %) is a desktop/device parity check only. The
generalisation figure is the 76.5 % cross-validated accuracy above.

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

Only non-chain scenes were available for this run (a room corner and dark frames). All six
captures gave `UNKNOWN` (the four with probabilities logged were `not_chain`, p = 0.69–0.85),
which is correct. The
`chain_visible` path through the live camera has **not** been exercised with a real chain; it
is covered by `ChainVisionTest` on the 50 pushed images, which are the training images.

## Reproducing the training

```bash
python tools/fetch_images.py          # Commons candidates (throttled; skips on 429)
python tools/make_contact_sheets.py chain_candidates
#   ... inspect sheets/*.jpg, write labels into labels_*.json ...
python tools/train_head.py            # -> app/src/main/assets/chain_head.json
```
