# RoadSide on-device models

Two YAMNet variants ship in `app/src/main/assets/`. The score-only model remains the
application's default path; the embedding model is additive and exists for future transfer
learning.

| | **Baseline (score-only)** | **Embedding-capable** |
|---|---|---|
| Asset | `yamnet.tflite` | `yamnet_embedding.tflite` |
| Kotlin | `YamNetInterpreter` / `YamNetAudioClassifier` | `YamNetEmbeddingExtractor` |
| Source | Kaggle `google/yamnet` → `tfLite/classification-tflite/1` | Kaggle `google/yamnet` → `tfLite/tflite/1` |
| Licence | Apache 2.0 | Apache 2.0 |
| Size | 3.94 MB (4,126,810 bytes) | 15.35 MB (16,096,668 bytes) |
| Precision | Quantized (retrained with Relu6) | Float32 |
| Input | fixed `float32[15600]` — one window per call | variable `float32[N]` — whole waveform |
| Outputs | 1 — scores `[1, 521]` | 3 — scores `[N, 521]`, **embeddings `[N, 1024]`**, log-mel `[M, 64]` |
| Framing | caller-side: 15,600 window / 7,800 hop | internal: 15,360 (0.96 s) / 7,680 (0.48 s) |
| Embeddings | none (returns zeros) | **1024-D per frame** |

`sha256(yamnet_embedding.tflite)` begins `141fba1cdaae842c816f28edc4937e8b`.

## Why two models

The originally bundled model is the `classification-tflite` variant, which Google describes
as *"specialized for inference on mobile devices"* with a deliberately simplified signature:
a single fixed 15,600-sample frame in, one 521-vector of scores out. It has **no embedding
tensor at all** — the planned specialist classifier cannot be built on it.

The `tflite` variation is the official TF-Hub successor and exposes the canonical YAMNet
3-tuple `(scores, embeddings, log_mel_spectrogram)`. That is the one used for embeddings.

Neither was taken from a third party claiming to be "YAMNet with embeddings" — both come
from Google's own `google/yamnet` model page.

## Framing arithmetic

The embedding model frames internally, so `YamNetEmbeddingExtractor` must pre-size output
buffers before `invoke()` (TFLite does not propagate the dynamic output shape at
`allocateTensors()` time):

```
frames = 1 + ceil((samples - 15360) / 7680)     for samples >= 15360
frames = 1                                       for 0 < samples < 15360
```

Verified against the model at 0.5 / 0.96 / 1 / 1.5 / 2 / 3 / 5 / 10 / 53.07 s, and matched
**98/98** clips in the validation set. `runWaveform` always uses this formula. The tensors'
reported shapes are only logged: they are unresolved before invoke, and after an invoke they
keep the *previous* waveform's frame count. An earlier version preferred a reported count
over the formula. Reusing one extractor for a second, different-length clip then decoded it
with the first clip's frame count (77 frames for a 23-frame clip, score 0.43 instead of 0.88),
and a third clip crashed with a buffer-size mismatch. `ChainAudioSpecialistTest`'s parity
check found this on the OnePlus 13R on 2026-09-22; after the fix, device and desktop scores
agree to 0.0000 on all 7 parity clips.

Note this differs from the baseline path's 15,600/7,800 framing, so the two models produce
different frame counts for the same clip (a 5 s clip gives 10 frames here, 11 windows there).

## Score agreement between the two models

They are not numerically identical and should not be expected to be: one is a quantized
Relu6 retrain fed fixed windows, the other float with canonical framing. Measured across the
98-clip validation set:

| Embedding-model top-1 score | Clips | Top-1 agreement | Mean top-5 overlap |
|---|---|---|---|
| ≥ 0.30 | 38 | **36/38 (95 %)** | 3.11 / 5 |
| 0.15 – 0.30 | 38 | 16/38 (42 %) | 2.50 / 5 |
| < 0.15 | 22 | 2/22 (9 %) | 1.50 / 5 |
| **overall** | 98 | 54/98 (55 %) | 2.51 / 5 |

Agreement is strong where either model is actually confident, and collapses on weak audio
where both are effectively guessing. That is the expected signature of two implementations of
the same architecture at different precision, not evidence that one is wrong.

## Per-frame embeddings are preserved

`YamNetEmbeddingExtractor.Result.embeddings` is `[frames][1024]` and is **not** pooled.
Mean pooling, max pooling, temporal statistics and small temporal models are downstream
decisions; pooling at extraction time would discard what is needed to choose between them.
`Result.meanScores()` exists only to mean-pool *scores* for parity with the baseline path.

## Specialist chain head (built; not enabled — failed its gate)

```
waveform -> yamnet_embedding.tflite (1024-d per 0.96 s frame)
         -> chain_audio_head.json (StandardScaler + logistic regression, C=0.05)
         -> mean per-frame p(chain_noise) >= 0.50  ->  possible_chain_noise
```

**Why:** YAMNet has no chain class. Real chain recordings made on the OnePlus 13R score
Breathing/Snoring (0.3–0.9), and every rattle-family class stays below 0.05 in every window.
`EvidenceTranslator` can never reach its chain threshold on them, and lowering the threshold
far enough would flag 30–43 of the 98 guardrail clips.

**Integration** (`ChainAudioSpecialist`, called from `YamNetAudioClassifier`):
- The baseline EvidenceTranslator result is unchanged, and its thresholds are untouched.
- The specialist can only *add* chain evidence. It never overrides brake or electrical evidence.
- It is inactive unless `chain_audio_head.json` is in the app's assets.
- `train_audio_head.py` writes that asset **only if a pre-registered gate passes**.

**Gate:**
- 0 of 98 guardrail clips flagged
- 0 held-out phone negatives flagged
- every held-out chain recording detected
- all three, in every fold

**Result (2026-09-22): gate failed; no head is shipped.** The best configuration uses
in-domain phone negatives, holding out one chain session and one negative session per fold
(8 folds):

| | Result |
|---|---|
| Held-out chain recordings detected | 3 of 3, in every fold (scores 0.58–0.75) |
| Held-out phone negatives flagged | 1 of 28 (one clip at 0.511) |
| Guardrail clips flagged | 2–3 of 98 in every fold (the same few clips: two quiet backgrounds, one car, the Commons TOMOS motorcycle) |

Scores overlap (held-out chain as low as 0.581, guardrail as high as 0.632), so no threshold
would pass the gate honestly. Earlier configurations did worse:
- Training on YouTube clips only: 1 of 3 chain recordings detected, 4 of 4 phone negatives flagged.
- Without in-domain negatives: 4 of 4 phone negatives flagged. The head learned "OnePlus room
  recording", not "chain".

**v2 — multi-source public dataset (2026-09-22): gate failed; not shipped.**
`roadside_chain_audio/tools/{make_sources,build_dataset,train_chain_head_v2}.py`.

- **Data:** 60 usable positive recordings (51 source groups, 1,024 chunks, 68 min) and
  583 negative recordings (454 groups, 2,307 chunks, 157 min). See `DATASETS.md` §5.
- **Evaluation:** 5-fold StratifiedGroupKFold by source identity.
- **Design fixed before running:** C = 0.05, threshold 0.5, equal weight per group.

| Out-of-fold | Run 1 | Run 2 (negatives keep quiet frames) | Gate |
|---|---|---|---|
| Chunk precision / recall | 0.761 / 0.566 | 0.765 / 0.632 | ≥ 0.90 / ≥ 0.70 |
| Positive recordings detected | 40 / 60 | 44 / 60 | ≥ 50 % |
| Guardrail flagged (final model) | 12 / 98 | 4 / 98 | 0 |
| User phone negatives flagged | 7 / 26 | 10 / 28 | 0 |
| User chain recordings detected | 0 / 3 | 1 / 3 | ≥ 2 |

Run 2 corrects a pipeline defect found in run 1. Silent negative frames had been dropped,
while the app and the guardrail score whole clips, so quiet road backgrounds were flagged.
There were no further iterations, to avoid tuning on the evaluation.

Why it fails:
- 49 of the 60 positives are **bicycle** or industrial chain recordings. Only ~11 are
  motorcycle or e-moto, and 3 of those are the user's.
- Out-of-fold false positives concentrate on crackling fire, keyboard, mouse clicks and
  washing machines, and on room recordings from the user's own phone.

**What would make it pass:** more chain recordings from several separate sessions, and
no-chain recordings from the same bike and place (engine idling, wheel still, people talking
or breathing nearby). Only 3 chain recordings from 2 sessions exist today. See `DATASETS.md`
§5.

Device checks (`ChainAudioSpecialistTest`, OnePlus 13R):
- **Parity:** a candidate head bundled only in the test APK scores 7 clips identically to the
  desktop script (max |diff| 0.0000).
- **Production path, no head shipped:** 0 of 102 negatives flagged as chain.
- **Cost:** the embedding pass takes about 5.6 ms per 0.96 s frame (430 ms for a 37 s clip).

## Extracted embeddings

`C:\Codes\Datasets\roadside_validation\embeddings\<class>\<clip>.npy` — 98 files, 4.00 MB
total, each `float32[frames, 1024]`. Full per-clip statistics in `embedding_results.json`.
