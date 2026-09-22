const pptxgen = require("pptxgenjs");

const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE";            // 13.3 x 7.5
pres.author = "RoadSide";
pres.title = "RoadSide";

// ── palette: roadside hazard amber on charcoal ──────────────────────────────
const INK = "16181D";      // near-black charcoal
const INK_SOFT = "232730";
const PAPER = "FFFFFF";
const MUTED = "6B7280";
const SLATE = "3F4654";
const AMBER = "F5A524";    // hazard amber
const AMBER_DK = "C27803";
const GREEN = "2F9E6B";
const RED = "C0392B";

const H = "Cambria";        // serif headers
const B = "Calibri";        // sans body

const W = 13.3, HT = 7.5;
const M = 0.75;             // side margin

// ── helpers ─────────────────────────────────────────────────────────────────
function darkBg(slide) { slide.background = { color: INK }; }

function title(slide, text, opts = {}) {
    slide.addText(text, {
        x: M, y: opts.y || 0.55, w: W - 2 * M, h: 0.9,
        fontSize: opts.size || 38, bold: true, fontFace: H,
        color: opts.color || INK, align: "left", isTextBox: true, margin: 0,
    });
}

function kicker(slide, text, color) {
    slide.addText(text.toUpperCase(), {
        x: M, y: 0.28, w: W - 2 * M, h: 0.3,
        fontSize: 12, bold: true, fontFace: B, charSpacing: 2,
        color: color || AMBER_DK, isTextBox: true, margin: 0,
    });
}

/** numbered amber disc used as the repeating motif */
function disc(slide, x, y, label, size = 0.44, fill = AMBER, txt = INK) {
    slide.addShape(pres.ShapeType.ellipse, {
        x, y, w: size, h: size, fill: { color: fill },
    });
    slide.addText(String(label), {
        x, y, w: size, h: size, fontSize: size > 0.5 ? 16 : 13, bold: true,
        fontFace: B, color: txt, align: "center", valign: "middle",
        isTextBox: true, margin: 0,
    });
}

function card(slide, x, y, w, h, fill) {
    slide.addShape(pres.ShapeType.roundRect, {
        x, y, w, h, rectRadius: 0.08,
        fill: { color: fill || "F4F5F7" },
    });
}

function stat(slide, x, y, w, value, label, color) {
    slide.addText(value, {
        x, y, w, h: 0.85, fontSize: 42, bold: true, fontFace: H,
        color: color || AMBER_DK, align: "left", isTextBox: true, margin: 0,
    });
    slide.addText(label, {
        x, y: y + 0.8, w, h: 0.5, fontSize: 12, fontFace: B,
        color: MUTED, align: "left", isTextBox: true, margin: 0,
    });
}

// ════════════════════════════════════════════════════════════════════════════
// 1. Title
// ════════════════════════════════════════════════════════════════════════════
{
    const s = pres.addSlide();
    darkBg(s);

    // motif: three amber discs, like hazard lights
    disc(s, M, 1.55, "", 0.30, AMBER, AMBER);
    disc(s, M + 0.42, 1.55, "", 0.30, AMBER_DK, AMBER_DK);
    disc(s, M + 0.84, 1.55, "", 0.30, SLATE, SLATE);

    s.addText("RoadSide", {
        x: M, y: 2.1, w: 9.5, h: 1.5, fontSize: 76, bold: true, fontFace: H,
        color: PAPER, isTextBox: true, margin: 0,
    });
    s.addText("Your motorcycle tells you when something is wrong.\nRoadSide helps you work out what it may be.", {
        x: M, y: 3.6, w: 9.0, h: 1.1, fontSize: 19, fontFace: B,
        color: "C9CDD6", lineSpacing: 28, isTextBox: true, margin: 0,
    });

    s.addText("Offline, on-device audio + vision diagnostics for two-wheelers", {
        x: M, y: 5.0, w: 9.5, h: 0.4, fontSize: 15, italic: true, fontFace: B,
        color: AMBER, isTextBox: true, margin: 0,
    });

    s.addText("Smart Living  ·  iQOO Hackathon  ·  Built and verified on OnePlus 13R", {
        x: M, y: 6.5, w: 11, h: 0.4, fontSize: 12, fontFace: B,
        color: MUTED, isTextBox: true, margin: 0,
    });
    s.addNotes("RoadSide listens to a motorcycle and looks at it, then tells the rider what is likely wrong. Everything runs on the phone with no internet.");
}

// ════════════════════════════════════════════════════════════════════════════
// 2. Problem
// ════════════════════════════════════════════════════════════════════════════
{
    const s = pres.addSlide();
    kicker(s, "The problem");
    title(s, "A strange noise, 40 km from the nearest mechanic");

    const rows = [
        ["You can hear it", "Riders notice a new rattle, squeal or click long before a breakdown. They just cannot name it."],
        ["You cannot search it", "Describing a sound in a search box does not work, and highway coverage in India is patchy at best."],
        ["You cannot risk it", "Ignore a dry chain and it snaps. Over-service it and you pay for work the bike never needed."],
    ];

    let y = 2.05;
    rows.forEach((r, i) => {
        disc(s, M, y + 0.02, i + 1);
        s.addText(r[0], {
            x: M + 0.68, y, w: 4.3, h: 0.4, fontSize: 19, bold: true,
            fontFace: B, color: INK, isTextBox: true, margin: 0,
        });
        s.addText(r[1], {
            x: M + 0.68, y: y + 0.42, w: 6.3, h: 0.9, fontSize: 14,
            fontFace: B, color: SLATE, lineSpacing: 20, isTextBox: true, margin: 0,
        });
        y += 1.5;
    });

    card(s, 8.15, 1.95, 4.4, 4.0, INK);
    s.addText("“", {
        x: 8.45, y: 2.05, w: 1, h: 0.9, fontSize: 60, bold: true,
        fontFace: H, color: AMBER, isTextBox: true, margin: 0,
    });
    s.addText("There is a ticking sound when I pull away. Is it the chain, the engine, or nothing at all?", {
        x: 8.45, y: 2.95, w: 3.8, h: 1.8, fontSize: 18, italic: true,
        fontFace: H, color: PAPER, lineSpacing: 28, isTextBox: true, margin: 0,
    });
    s.addText("Today the only answer is to ride to a mechanic and hope.", {
        x: 8.45, y: 4.9, w: 3.8, h: 0.8, fontSize: 13, fontFace: B,
        color: "9AA1AE", lineSpacing: 19, isTextBox: true, margin: 0,
    });
    s.addNotes("The rider already has the signal. What they lack is interpretation, and connectivity exactly where they need it.");
}

// ════════════════════════════════════════════════════════════════════════════
// 3. What it does
// ════════════════════════════════════════════════════════════════════════════
{
    const s = pres.addSlide();
    kicker(s, "The product");
    title(s, "Record. Point the camera. Get plain-language evidence.");

    const steps = [
        ["Listen", "Hold the phone near the bike for five seconds. RoadSide records at 16 kHz and classifies what it hears."],
        ["Look", "Point the camera at the chain. RoadSide locates the drivetrain in the frame and reports what it can see."],
        ["Decide", "Sound and sight are fused into one advisory, with the likely cause, the tools needed and a safety note."],
    ];

    let x = M;
    const cw = 3.75, gap = 0.42;
    steps.forEach((st, i) => {
        card(s, x, 2.0, cw, 3.4);
        disc(s, x + 0.35, 2.35, i + 1, 0.5);
        s.addText(st[0], {
            x: x + 0.35, y: 3.05, w: cw - 0.7, h: 0.45, fontSize: 22, bold: true,
            fontFace: H, color: INK, isTextBox: true, margin: 0,
        });
        s.addText(st[1], {
            x: x + 0.35, y: 3.55, w: cw - 0.7, h: 1.6, fontSize: 14,
            fontFace: B, color: SLATE, lineSpacing: 21, isTextBox: true, margin: 0,
        });
        x += cw + gap;
    });

    s.addText("No account. No upload. No signal required — the app has no internet permission at all.", {
        x: M, y: 5.75, w: W - 2 * M, h: 0.5, fontSize: 16, bold: true,
        fontFace: B, color: AMBER_DK, isTextBox: true, margin: 0,
    });
    s.addNotes("Three taps. Everything happens on the handset.");
}

// ════════════════════════════════════════════════════════════════════════════
// 4. Architecture
// ════════════════════════════════════════════════════════════════════════════
{
    const s = pres.addSlide();
    darkBg(s);
    kicker(s, "How it works", AMBER);
    title(s, "Two pretrained backbones, two tiny heads, one rules engine", { color: PAPER, size: 32 });

    const lanes = [
        {
            t: "AUDIO", y: 1.95, color: AMBER,
            boxes: [
                ["Microphone", "16 kHz mono"],
                ["YAMNet", "521 sound classes"],
                ["Evidence layer", "max-per-group"],
            ],
        },
        {
            t: "VISION", y: 3.62, color: "6FB1FC",
            boxes: [
                ["Camera", "224×224 centre crop"],
                ["MobileNetV3", "1280-d features"],
                ["Chain head", "logistic regression"],
            ],
        },
    ];

    lanes.forEach((ln) => {
        s.addText(ln.t, {
            x: M, y: ln.y + 0.35, w: 1.0, h: 0.4, fontSize: 13, bold: true,
            fontFace: B, color: ln.color, charSpacing: 1, isTextBox: true, margin: 0,
        });
        let bx = M + 1.05;
        ln.boxes.forEach((bo, i) => {
            s.addShape(pres.ShapeType.roundRect, {
                x: bx, y: ln.y, w: 2.5, h: 1.15, rectRadius: 0.07,
                fill: { color: INK_SOFT },
            });
            s.addText(bo[0], {
                x: bx + 0.18, y: ln.y + 0.2, w: 2.15, h: 0.35, fontSize: 15, bold: true,
                fontFace: B, color: PAPER, isTextBox: true, margin: 0,
            });
            s.addText(bo[1], {
                x: bx + 0.18, y: ln.y + 0.58, w: 2.15, h: 0.35, fontSize: 11,
                fontFace: B, color: "98A0AE", isTextBox: true, margin: 0,
            });
            if (i < ln.boxes.length - 1) {
                s.addText("→", {
                    x: bx + 2.52, y: ln.y + 0.35, w: 0.42, h: 0.45, fontSize: 18,
                    fontFace: B, color: ln.color, align: "center", isTextBox: true, margin: 0,
                });
            }
            bx += 2.94;
        });
    });

    // fusion block
    s.addShape(pres.ShapeType.roundRect, {
        x: 10.6, y: 1.95, w: 1.95, h: 2.82, rectRadius: 0.07,
        fill: { color: AMBER },
    });
    s.addText("Diagnosis\nrules", {
        x: 10.75, y: 2.8, w: 1.65, h: 1.0, fontSize: 17, bold: true,
        fontFace: H, color: INK, align: "center", isTextBox: true, margin: 0,
    });

    s.addText("Both backbones are frozen and pretrained. We train only the small heads, so the whole model set is 35 MB and fits in an APK.", {
        x: M, y: 5.35, w: 11.3, h: 0.9, fontSize: 14, fontFace: B,
        color: "AEB5C2", lineSpacing: 21, isTextBox: true, margin: 0,
    });
    s.addNotes("We deliberately do not fine-tune the backbones. Small heads on frozen features is what makes this trainable from a hundred examples and cheap to run.");
}

// ════════════════════════════════════════════════════════════════════════════
// 5. Measured on device
// ════════════════════════════════════════════════════════════════════════════
{
    const s = pres.addSlide();
    kicker(s, "Not a mockup");
    title(s, "Measured on a real OnePlus 13R, not estimated");

    const stats = [
        ["14 ms", "Audio model cold start"],
        ["2 ms", "Per-window inference"],
        ["43 ms", "Full 5-second clip"],
        ["30 ms", "Per image, vision"],
    ];
    let x = M;
    stats.forEach((st) => {
        stat(s, x, 2.0, 2.9, st[0], st[1]);
        x += 2.95;
    });

    card(s, M, 3.65, 11.8, 2.25, "F4F5F7");
    s.addText("14 / 14 instrumented tests pass on device", {
        x: M + 0.45, y: 3.95, w: 7.0, h: 0.45, fontSize: 20, bold: true,
        fontFace: H, color: INK, isTextBox: true, margin: 0,
    });
    s.addText([
        { text: "Model load, inference, preprocessing, evidence rules, fusion logic and crash checks — run headlessly over ADB", options: { bullet: true, breakLine: true } },
        { text: "Airplane-mode pass: audio and vision both verified with every radio off", options: { bullet: true, breakLine: true } },
        { text: "Zero crashes, zero ANRs, 16 KB page-size compliant for future Android kernels", options: { bullet: true } },
    ], {
        x: M + 0.45, y: 4.45, w: 7.6, h: 1.3, fontSize: 13.5, fontFace: B,
        color: SLATE, paraSpaceAfter: 5, isTextBox: true, margin: 0,
    });

    s.addText("One command\nreproduces it all", {
        x: 9.35, y: 4.05, w: 3.0, h: 1.0, fontSize: 16, bold: true, fontFace: H,
        color: AMBER_DK, align: "right", isTextBox: true, margin: 0,
    });
    s.addText("verify.ps1", {
        x: 9.35, y: 5.0, w: 3.0, h: 0.4, fontSize: 14, fontFace: "Courier New",
        color: INK, align: "right", isTextBox: true, margin: 0,
    });
    s.addNotes("Build, install, launch, test, collect logs, crash-check and airplane-mode pass, all from one script.");
}

// ════════════════════════════════════════════════════════════════════════════
// 6. Honest AI — the differentiator
// ════════════════════════════════════════════════════════════════════════════
{
    const s = pres.addSlide();
    darkBg(s);
    kicker(s, "What makes it different", AMBER);
    title(s, "It reports evidence, and says “I don’t know”", { color: PAPER });

    s.addText("Most demo AI guesses confidently. A wrong confident answer about a brake is worse than no answer.", {
        x: M, y: 1.75, w: 11.5, h: 0.5, fontSize: 15, fontFace: B,
        color: "AEB5C2", isTextBox: true, margin: 0,
    });

    const cols = [
        ["What we refused to ship", RED, [
            "A rust detector keyed on brown backgrounds — we measured it and clean chains on wooden benches scored higher than rusty ones",
            "A chain-condition class trained on 3 images",
            "Any claim that a passing motorcycle is a healthy one",
        ]],
        ["What we shipped instead", GREEN, [
            "Separate acoustic evidence from mechanical verdict",
            "UNKNOWN whenever confidence or margin is too low",
            "Every threshold documented as provisional until real data arrives",
        ]],
    ];

    let cx = M;
    cols.forEach((c) => {
        s.addShape(pres.ShapeType.roundRect, {
            x: cx, y: 2.45, w: 5.75, h: 3.3, rectRadius: 0.08,
            fill: { color: INK_SOFT },
        });
        disc(s, cx + 0.35, 2.75, "", 0.22, c[1], c[1]);
        s.addText(c[0], {
            x: cx + 0.72, y: 2.68, w: 4.7, h: 0.4, fontSize: 17, bold: true,
            fontFace: H, color: PAPER, isTextBox: true, margin: 0,
        });
        s.addText(c[2].map((t, i) => ({
            text: t, options: { bullet: true, breakLine: i < c[2].length - 1 },
        })), {
            x: cx + 0.38, y: 3.2, w: 5.05, h: 2.3, fontSize: 13, fontFace: B,
            color: "C4CAD6", lineSpacing: 19, paraSpaceAfter: 9,
            isTextBox: true, margin: 0,
        });
        cx += 6.05;
    });

    s.addText("Across 98 real traffic recordings, zero motorcycles were falsely flagged as chain or brake faults.", {
        x: M, y: 6.0, w: 11.5, h: 0.5, fontSize: 15, bold: true, italic: true,
        fontFace: B, color: AMBER, isTextBox: true, margin: 0,
    });
    s.addNotes("This is the engineering argument. We tested a rust heuristic, measured that it failed, and removed it rather than shipping a plausible-looking threshold.");
}

// ════════════════════════════════════════════════════════════════════════════
// 7. Validation
// ════════════════════════════════════════════════════════════════════════════
{
    const s = pres.addSlide();
    kicker(s, "Validation");
    title(s, "Tested against public datasets, not our own recordings");

    card(s, M, 1.95, 5.75, 2.1);
    s.addText("Audio · IDMT-Traffic", {
        x: M + 0.38, y: 2.2, w: 5.0, h: 0.4, fontSize: 17, bold: true,
        fontFace: H, color: INK, isTextBox: true, margin: 0,
    });
    s.addText("90 clips pulled from a 9.7 GB Fraunhofer dataset, plus 8 openly licensed close-mic recordings: 58 motorcycles, 20 background, 20 other vehicles.", {
        x: M + 0.38, y: 2.65, w: 5.0, h: 1.2, fontSize: 13, fontFace: B,
        color: SLATE, lineSpacing: 19, isTextBox: true, margin: 0,
    });

    card(s, 6.8, 1.95, 5.75, 2.1);
    s.addText("Vision · Wikimedia Commons", {
        x: 7.18, y: 2.2, w: 5.0, h: 0.4, fontSize: 17, bold: true,
        fontFace: H, color: INK, isTextBox: true, margin: 0,
    });
    s.addText("51 openly licensed chain and non-chain photographs, each labelled by eye rather than trusting the category name.", {
        x: 7.18, y: 2.65, w: 5.0, h: 1.2, fontSize: 13, fontFace: B,
        color: SLATE, lineSpacing: 19, isTextBox: true, margin: 0,
    });

    const results = [
        ["0 / 58", "motorcycles wrongly flagged as a chain fault", GREEN],
        ["0 / 20", "background clips flagged as mechanical", GREEN],
        ["76.5 %", "chain detection, cross-validated", AMBER_DK],
    ];
    let x = M;
    results.forEach((r) => {
        stat(s, x, 4.45, 3.7, r[0], r[1], r[2]);
        x += 3.95;
    });

    s.addText("Cross-validated means every prediction came from a fold that never saw that image. We report that number, not the flattering one.", {
        x: M, y: 6.1, w: 11.5, h: 0.5, fontSize: 13, italic: true, fontFace: B,
        color: MUTED, isTextBox: true, margin: 0,
    });
    s.addNotes("The honest figure is the cross-validated one. Refitting on all the data scores 100 percent and means nothing.");
}

// ════════════════════════════════════════════════════════════════════════════
// 8. Why offline
// ════════════════════════════════════════════════════════════════════════════
{
    const s = pres.addSlide();
    kicker(s, "Why on-device");
    title(s, "The breakdown happens where the signal isn’t");

    const pts = [
        ["Works with no network", "The app declares no internet permission at all — it is structurally incapable of phoning home. Verified in airplane mode."],
        ["Costs nothing to run", "No inference bills, no API keys, no per-user cost. A 35 MB model set runs on the handset the rider already owns."],
        ["Private by construction", "Engine audio and vehicle photos are processed locally and are not uploaded by the app."],
        ["Instant", "43 ms for a five-second clip. The answer is there before the rider has put the phone down."],
    ];

    let y = 2.05;
    pts.forEach((p, i) => {
        const col = i % 2, row = Math.floor(i / 2);
        const px = M + col * 6.05;
        const py = y + row * 2.35;
        disc(s, px, py, i + 1, 0.42);
        s.addText(p[0], {
            x: px + 0.62, y: py - 0.04, w: 5.2, h: 0.4, fontSize: 18, bold: true,
            fontFace: H, color: INK, isTextBox: true, margin: 0,
        });
        s.addText(p[1], {
            x: px + 0.62, y: py + 0.4, w: 5.2, h: 1.3, fontSize: 13.5, fontFace: B,
            color: SLATE, lineSpacing: 20, isTextBox: true, margin: 0,
        });
    });
    s.addText("RoadSide works with the radios off, which is exactly where a breakdown happens.", {
        x: M, y: 6.5, w: 11.5, h: 0.5, fontSize: 15, bold: true, italic: true,
        fontFace: B, color: AMBER_DK, isTextBox: true, margin: 0,
    });
    s.addNotes("Offline is not a constraint we worked around. It is the reason the product is useful at the roadside.");
}

// ════════════════════════════════════════════════════════════════════════════
// 9. Roadmap
// ════════════════════════════════════════════════════════════════════════════
{
    const s = pres.addSlide();
    kicker(s, "What's next");
    title(s, "The pipeline is built. It is waiting on labelled data.");

    const phases = [
        ["Now", "Working prototype", "Audio evidence, chain detection and fusion running offline on device, with 1024-d YAMNet embeddings already extracted and ready.", AMBER],
        ["Next", "Labelled fault data", "A service-centre partnership to record chains, brakes and engines in known good and known bad states. No open dataset contains this.", SLATE],
        ["Then", "Specialist classifier", "A small head over the embeddings: NORMAL / CHAIN / BRAKE / ENGINE. The extraction pipeline is done; only the labels are missing.", SLATE],
    ];

    let y = 1.95;
    phases.forEach((p, i) => {
        s.addShape(pres.ShapeType.roundRect, {
            x: M, y, w: 11.8, h: 1.32, rectRadius: 0.08,
            fill: { color: i === 0 ? "FDF3DF" : "F4F5F7" },
        });
        s.addText(p[0].toUpperCase(), {
            x: M + 0.38, y: y + 0.3, w: 1.1, h: 0.4, fontSize: 12, bold: true,
            fontFace: B, color: p[3], charSpacing: 1, isTextBox: true, margin: 0,
        });
        s.addText(p[1], {
            x: M + 1.6, y: y + 0.22, w: 3.3, h: 0.45, fontSize: 17, bold: true,
            fontFace: H, color: INK, isTextBox: true, margin: 0,
        });
        s.addText(p[2], {
            x: M + 5.0, y: y + 0.2, w: 6.5, h: 1.0, fontSize: 13, fontFace: B,
            color: SLATE, lineSpacing: 19, isTextBox: true, margin: 0,
        });
        y += 1.46;
    });

    s.addText("India has over 220 million two-wheelers, and almost none of them come with a diagnostic tool.", {
        x: M, y: 6.55, w: 11.5, h: 0.5, fontSize: 15, bold: true, italic: true,
        fontFace: B, color: AMBER_DK, isTextBox: true, margin: 0,
    });
    s.addNotes("We are not blocked on engineering. We are blocked on labelled fault recordings, which is a partnership problem.");
}

// ════════════════════════════════════════════════════════════════════════════
// 10. Close
// ════════════════════════════════════════════════════════════════════════════
{
    const s = pres.addSlide();
    darkBg(s);
    disc(s, M, 1.7, "", 0.30, AMBER, AMBER);
    disc(s, M + 0.42, 1.7, "", 0.30, AMBER_DK, AMBER_DK);
    disc(s, M + 0.84, 1.7, "", 0.30, SLATE, SLATE);

    s.addText("RoadSide", {
        x: M, y: 2.25, w: 9, h: 1.2, fontSize: 60, bold: true, fontFace: H,
        color: PAPER, isTextBox: true, margin: 0,
    });
    s.addText("A mechanic's ear, in every rider's pocket — with no signal, no subscription and no guesswork.", {
        x: M, y: 3.5, w: 9.2, h: 1.0, fontSize: 19, fontFace: B,
        color: "C9CDD6", lineSpacing: 28, isTextBox: true, margin: 0,
    });

    const tags = ["Fully offline", "Verified on device", "Evidence, not guesses"];
    let tx = M;
    tags.forEach((t) => {
        s.addShape(pres.ShapeType.roundRect, {
            x: tx, y: 4.85, w: 3.0, h: 0.62, rectRadius: 0.1,
            fill: { color: INK_SOFT },
        });
        s.addText(t, {
            x: tx, y: 4.85, w: 3.0, h: 0.62, fontSize: 14, bold: true, fontFace: B,
            color: AMBER, align: "center", valign: "middle", isTextBox: true, margin: 0,
        });
        tx += 3.2;
    });

    s.addText("Smart Living  ·  iQOO Hackathon", {
        x: M, y: 6.5, w: 11, h: 0.4, fontSize: 12, fontFace: B,
        color: MUTED, isTextBox: true, margin: 0,
    });
    s.addNotes("Thank you. Happy to demo the device verification run live.");
}

pres.writeFile({ fileName: "RoadSide_deck.pptx" }).then(() => console.log("written"));
