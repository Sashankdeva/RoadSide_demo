# Datasets used for RoadSide acoustic validation

None of this audio is redistributed with the app. Nothing here is bundled into the APK.
Source material lives outside the Android project at:

```
C:\Codes\Datasets\roadside_validation\
  originals/    untouched source files (never modified)
  prepared/     16 kHz mono PCM16 conversions used for validation
  tools/        acquisition, preprocessing and validation scripts
```

> **These recordings carry no mechanical-condition labels.**
> A motorcycle passing a microphone is *not* evidence that its chain, brakes or engine are
> healthy. They are used only for acoustic distribution, motorcycle detection, background
> rejection, YAMNet behaviour and false-positive analysis — never as fault labels.

---

## 1. IDMT-Traffic (primary)

| | |
|---|---|
| **Name** | IDMT-Traffic Dataset |
| **Publisher** | Fraunhofer IDMT |
| **DOI** | [10.5281/zenodo.7551553](https://doi.org/10.5281/zenodo.7551553) |
| **Record** | https://zenodo.org/records/7551553 |
| **Licence** | **CC BY-NC-ND 4.0** — https://creativecommons.org/licenses/by-nc-nd/4.0/ |
| **Archive size** | 9.66 GB (17,506 clips) |
| **Downloaded** | 90 clips (~69 MB) |
| **Original format** | 48 kHz, stereo, WAV (IEEE float32), 2.0 s per clip |

**Citation**

> J. Abeßer, S. Gourishetti, A. Kátai, T. Clauß, P. Sharma, J. Liebetrau:
> *IDMT-Traffic: An Open Benchmark Dataset for Acoustic Traffic Monitoring Research*, EUSIPCO, 2021.

### Licence compliance

- **BY** — attributed here and in `validation_results.json`.
- **NC** — prototype research/evaluation only, no commercial use.
- **ND** — no derivative is distributed. The 16 kHz mono conversions in `prepared/` are local
  working copies for evaluation and are not published or shipped.
- **No redistribution.** The subset stays on the dev machine and is excluded from the APK.

### Selective download

The archive is a single 9.66 GB zip, so `tools/fetch_subset.py` reads the ZIP64 central
directory over HTTP range requests and extracts only the selected members — **~69 MB instead
of 9.66 GB**. Selection is deterministic (seed `20260921`).

### Subset composition

| Class | Clips | Notes |
|---|---|---|
| Motorcycle | 50 | 25 sE8 + 25 MEMS, distinct pass-by events |
| Background | 20 | 10 sE8 + 10 MEMS, no vehicle passing |
| Other vehicles | 20 | 10 car, 5 truck, 5 bus |

The two microphones record the same event in parallel, so selection never takes the same
sample position twice — no near-duplicate audio.

**Filename encoding** (from the dataset readme):
`date_location_speed_sampleposition_daytime_weather_vehicle+direction_microphone_channels.wav`
— vehicle is `B`/`C`/`M`/`T` in the *vehicle+direction* token. Note the `M` in the *daytime*
field means "morning", not motorcycle; the two are easy to confuse.

Full per-file metadata: `subset_manifest.json`.

---

## 2. Wikimedia Commons (supplementary close-mic clips)

8 individually licensed motorcycle recordings, closer-range engine/exhaust than the
IDMT pass-bys. Full metadata in `commons_manifest.json`.

| File | Author | Licence | Original format |
|---|---|---|---|
| Jawa250_motorbike_exhaust_sound.ogg | Віктор Ходєєв / Viktor Khodyeyev | Public domain | Ogg Vorbis |
| Jawa350_motorbike_exhaust_sound.ogg | Віктор Ходєєв / Viktor Khodyeyev | Public domain | Ogg Vorbis |
| Jawa350_motorbike_exhaust_sound2.ogg | Віктор Ходєєв / Viktor Khodyeyev | Public domain | Ogg Vorbis |
| Motorbike_1.ogg | ezwa | Public domain | Ogg |
| Motorbike_2.ogg | ezwa | Public domain | Ogg |
| Motorbike_3.ogg | ezwa | Public domain | Ogg |
| Motorbike_4.ogg | ezwa | Public domain | Ogg |
| WWS_MotorcycleTOMOSColibrispecialD-3.ogg | Work With Sounds / Technical Museum | CC BY 4.0 | Ogg Vorbis |

Each file's Commons page, direct URL, author, licence and licence URL are recorded in
`commons_manifest.json`.

Two further Work With Sounds clips (`WWS_MotorcycleTOMOSD-9`, `WWS_Policemotorbike8211engine`)
were **not** downloaded — Wikimedia rate-limited the request (HTTP 429). The fetcher skips
rather than retrying aggressively.

### Licensing rule

Only material with an explicit, verifiable licence on its source page is used. For the
validation set above that excludes YouTube entirely. The chain-head training data (section 5)
adds YouTube audio, but only from videos whose own metadata reports YouTube's *Creative
Commons Attribution (reuse allowed)* licence. No standard-licence YouTube audio is used.

---

## 5. Chain-noise head training data (`C:\Codes\Datasets\roadside_chain_audio\`)

Collected on 2026-09-22 for the specialist chain head (`tools/train_audio_head.py`). None of it
is bundled in the app. Full per-clip metadata is in `manifest.json`, with attribution in
`youtube_cc/ATTRIBUTION.md`.

| Set | Clips | Role | Licence |
|---|---|---|---|
| User's OnePlus 13R chain recordings | 3 (sessions `chain0` ×2, `chainA` ×1) | positives; held out by session | own recordings |
| User's phone recordings without chain noise | 28 (4 sessions) | in-domain negatives; held out by session | own recordings |
| YouTube CC BY chain clips | 6 (1 near-duplicate dropped) — bicycle chain SFX, industrial roller chain, MTB chain slap | weak positives (titles, not listened to) | CC BY |
| YouTube CC BY motorcycle idle | 1 | negative | CC BY |
| Extra IDMT-Traffic clips | 80 (40 motorcycle, 20 other vehicle, 20 background) | training negatives, **disjoint from the 98-clip set by recording event** | CC BY-NC-ND 4.0 |

The 98-clip validation set above is the **guardrail** and is never trained on.

Eight more CC BY chain videos were initially refused by YouTube (HTTP 403). A current yt-dlp,
installed in an isolated venv, later downloaded all of them without sign-in cookies.

### 5b. Expanded multi-source dataset (v2, 2026-09-22)

Built automatically by `tools/make_sources.py` → `build_dataset.py`. Each source carries
its URL, licence, title, category, tier, group, label note and kept time ranges
(`built/segments.json`).

**Searches**
- **YouTube:** 43 queries with the Creative Commons filter, in English, Indonesian, Hindi,
  Bengali, Portuguese, Spanish, Thai, Vietnamese, Turkish, German, French and Italian. That
  gave 1,598 unique hits; every download's licence was re-checked from its own metadata.
- **Freesound:** 20 queries, 267 sound pages checked.
- **Wikimedia Commons and Internet Archive:** searched; no drive-chain audio (only
  podcasts, bells and pronunciations).
- **Pixabay:** blocks automated access (HTTP 403, bot protection). Not circumvented, so not
  used.

**Positives** (label from title and uploader context; nothing was listened to):

| | Collected | Usable* | Chunks | Licence |
|---|---|---|---|---|
| Freesound | 43 | 41 | 254 | CC0 ×26, CC BY 3.0/4.0 ×13, CC BY-NC ×2 |
| YouTube | 26 | 16 | 754 | CC BY |
| User phone | 3 | 3 | 16 | own recording |
| **Total** | **72** | **60** (51 groups) | **1,024** (68 min) | |

\*Usable means at least one clean chain segment survived the speech/music/silence filter.
All 12 unusable sources are narrated tutorials.

- By category (recordings): DRIVETRAIN 37, SPROCKET 13, UNKNOWN_CHAINLIKE 5, RATTLE 3, CLICK 2.
- About 11 are motorcycle or e-moto:
  - Sur-Ron ×4 (one uploader group)
  - Indonesian motorcycle videos ×3
  - Freesound "Motorcycle chain & gear box" ×1
  - the user's recordings ×3
- The rest are bicycle chains, plus one industrial roller chain.

**Excluded as ambiguous (from both classes):** freewheel/hub ratchet, spinning wheels,
general bike rides and pedalling. The chain may or may not be audible in these.

**Negatives:** 583 recordings, 454 groups, 2,307 chunks (157 min).

| Source | Recordings | Licence |
|---|---|---|
| ESC-50 (breathing, snoring, engine, clicks, keyboard, saws, fire, and others) | 375 | CC BY-NC 3.0 |
| YouTube: motorcycle engine, idle and ride; breathing; snoring; background; metal-chain and belt hard negatives; plus narrated speech from tutorial videos | 83 | CC BY |
| Extra IDMT clips (disjoint from the guardrail) | 80 | CC BY-NC-ND 4.0 |
| User phone room recordings | 28 | own recording |
| Freesound: motorcycle engines, bells, horns, tool ratchet, impacts | 17 | CC0 / CC BY / CC BY-NC |

---

## 3. Preprocessing

`tools/preprocess.py` converts working copies to the format `AudioRecorder` produces natively
on device:

```
48 kHz stereo float32  ->  16 kHz mono PCM16
44.1 kHz mono (Commons) ->  16 kHz mono PCM16
```

- Originals are opened read-only and never overwritten.
- Channels are averaged to mono.
- Resampling uses `scipy.signal.resample_poly` (polyphase, anti-aliased) rather than the
  linear interpolation in the on-device `ValidationWavReader`. Because output is already
  16 kHz, the device reader does no resampling at all, so desktop and device see identical
  samples.
- Levels are **not** normalised — clipping only. Normalising would erase the loudness
  difference between a close pass-by and quiet background, which is part of what is measured.
- `ffmpeg` is used as a decoder fallback for Ogg variants libsndfile cannot open.

---

## 4. Reproducing

```bash
python tools/fetch_subset.py     # ~69 MB from Zenodo via range requests
python tools/fetch_commons.py    # 8 clips from Wikimedia Commons
python tools/preprocess.py       # -> prepared/ at 16 kHz mono PCM16
python tools/validate.py         # -> validation_results.json + validation_summary.csv
```

`tools/validate.py` parses the AudioSet index groups and thresholds **directly out of**
`EvidenceTranslator.kt` at run time, so the desktop mirror cannot silently drift from the
app's rules.
