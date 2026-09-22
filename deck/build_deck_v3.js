// RoadSide deck v3 — follows the team's Drishti deck: same palette, fonts, eyebrow + title
// pattern, dark cards and orange viewfinder corner brackets. Real app screenshots throughout.
const pptxgen = require('pptxgenjs');
const path = require('path');

const B = (f) => path.join(__dirname, 'build', f);
const C = {
  bg: '0E1116', card: '1F2530', cardLow: '181D25', text: 'F3F1EC', muted: '98A0AE',
  amber: 'F2A93B', blue: '5BB8D6',
};
const HEAD = 'Arial', BODY = 'Calibri', MONO = 'Courier New';
const W = 13.333, H = 7.5;

const pres = new pptxgen();
pres.layout = 'LAYOUT_WIDE';
pres.title = 'RoadSide';

const bg = (s) => { s.background = { color: C.bg }; };
const T = (s, text, o) => s.addText(text, Object.assign({ margin: 0, isTextBox: true, fontFace: BODY, color: C.text }, o));
const head = (s, eyebrow, title) => {
  T(s, eyebrow.toUpperCase(), { x: 0.6, y: 0.4, w: 10, h: 0.3, fontFace: HEAD, fontSize: 11, bold: true, color: C.amber, charSpacing: 3 });
  T(s, title, { x: 0.6, y: 0.72, w: 12.1, h: 0.75, fontFace: HEAD, fontSize: 30, bold: true });
};
const card = (s, x, y, w, h, color = C.card) =>
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w, h, rectRadius: 0.08, fill: { color }, line: { color, width: 0 } });
// Viewfinder corners — the Drishti motif.
const brackets = (s, x, y, w, h, arm = 0.55) => {
  const L = (x1, y1, x2, y2) => s.addShape(pres.shapes.LINE, { x: Math.min(x1, x2), y: Math.min(y1, y2), w: Math.abs(x2 - x1), h: Math.abs(y2 - y1), line: { color: C.amber, width: 3 } });
  L(x, y, x + arm, y); L(x, y, x, y + arm);
  L(x + w - arm, y, x + w, y); L(x + w, y, x + w, y + arm);
  L(x, y + h, x + arm, y + h); L(x, y + h - arm, x, y + h);
  L(x + w - arm, y + h, x + w, y + h); L(x + w, y + h - arm, x + w, y + h);
};
const num = (s, n, x, y, color) => {
  s.addShape(pres.shapes.OVAL, { x, y, w: 0.42, h: 0.42, fill: { color }, line: { color, width: 0 } });
  T(s, String(n), { x, y, w: 0.42, h: 0.42, fontFace: HEAD, fontSize: 13, bold: true, color: C.bg, align: 'center', valign: 'middle' });
};
const phone = (s, file, x, y, h) => s.addImage({ path: B(file), x, y, w: h * 600 / 1229, h });

// 1 ── Title ──────────────────────────────────────────────────────────────────
{
  const s = pres.addSlide(); bg(s);
  brackets(s, 0.45, 0.45, W - 0.9, H - 0.9, 0.75);
  T(s, 'ROADSIDE', { x: 1.1, y: 2.0, w: 7, h: 1.1, fontFace: HEAD, fontSize: 60, bold: true, charSpacing: 4 });
  T(s, 'Your vehicle’s roadside mechanic.', { x: 1.1, y: 3.15, w: 7, h: 0.5, fontSize: 22, italic: true, color: C.amber });
  T(s, 'Hold your phone near your vehicle. It listens, looks, and tells you what to do — fully on-device.', { x: 1.1, y: 3.75, w: 6.6, h: 0.8, fontSize: 15, color: C.muted });
  T(s, 'iQOO Hackathon 2026  |  Hyderabad City Battle', { x: 1.1, y: 6.2, w: 6, h: 0.3, fontSize: 11, color: C.muted });
  s.addImage({ path: B('logo.png'), x: 8.55, y: 1.75, w: 3.6, h: 3.6 * 1056 / 1080 });
  s.addNotes('Open with the sound: everyone has heard a new noise from their bike and wondered if it is serious.');
}

// 2 ── The problem ────────────────────────────────────────────────────────────
{
  const s = pres.addSlide(); bg(s);
  head(s, 'The problem', 'A new noise, and only two bad options');
  [['IGNORE IT', C.blue, 'Keep riding and hope.', 'A worn part fails on the road, and a small engine problem becomes an expensive one.'],
   ['GO TO A MECHANIC', C.amber, 'Travel, wait and pay — just to find out.', 'Often far away, often for something you could have checked yourself in five minutes.']]
    .forEach(([h, col, a, b], i) => {
      const x = 0.6 + i * 6.15;
      card(s, x, 1.8, 5.95, 2.6);
      T(s, h, { x: x + 0.35, y: 2.1, w: 5.3, h: 0.45, fontFace: HEAD, fontSize: 20, bold: true, color: col });
      T(s, a, { x: x + 0.35, y: 2.7, w: 5.3, h: 0.4, fontSize: 16 });
      T(s, b, { x: x + 0.35, y: 3.2, w: 5.3, h: 0.9, fontSize: 15, color: C.muted, valign: 'top' });
    });
  T(s, 'There’s nothing in between.', { x: 0.6, y: 4.9, w: 8, h: 0.5, fontFace: HEAD, fontSize: 20, bold: true });
}

// 3 ── The gap ────────────────────────────────────────────────────────────────
{
  const s = pres.addSlide(); bg(s);
  head(s, 'The gap', 'Drivers can describe the sound. Not the fault.');
  ['“It rattles when I ride”', '“It knocks when I accelerate”', '“It squeals when I brake”'].forEach((q, i) => {
    card(s, 0.6, 1.85 + i * 0.92, 7.2, 0.75, C.cardLow);
    T(s, q, { x: 0.95, y: 1.85 + i * 0.92, w: 6.6, h: 0.75, fontSize: 16, italic: true, valign: 'middle' });
  });
  card(s, 8.1, 1.85, 4.6, 2.59);
  T(s, [
    { text: 'Spark plug?', options: { breakLine: true } }, { text: 'Brake pads?', options: { breakLine: true } },
    { text: 'Drive belt?', options: { breakLine: true } }, { text: 'Chain & sprocket?' }],
  { x: 8.5, y: 2.05, w: 4, h: 2.2, fontFace: MONO, fontSize: 22, bold: true, color: C.amber, valign: 'middle', lineSpacingMultiple: 1.15 });
  T(s, 'Everyone can hear this.', { x: 0.6, y: 4.75, w: 6, h: 0.4, fontFace: HEAD, fontSize: 17, bold: true, color: C.blue });
  T(s, 'Nobody knows what it means.', { x: 8.1, y: 4.75, w: 4.6, h: 0.4, fontFace: HEAD, fontSize: 17, bold: true, color: C.amber });
}

// 4 ── The idea ───────────────────────────────────────────────────────────────
{
  const s = pres.addSlide(); bg(s);
  head(s, 'The idea', 'Your phone becomes a roadside mechanic');
  brackets(s, 0.6, 1.8, 7.0, 3.6, 0.5);
  T(s, [
    { text: 'Record the noise. Photograph the part.', options: { breakLine: true, bold: true } },
    { text: ' ', options: { breakLine: true, fontSize: 8 } },
    { text: 'RoadSide listens to the sound, looks at the photo, and tells you in plain words what it found, what it may mean, and what you can do — with a safety note and when to see a mechanic.' }],
  { x: 1.1, y: 2.2, w: 6.1, h: 2.8, fontSize: 17, valign: 'top' });
  T(s, 'No internet. No account. Nothing leaves the phone.', { x: 0.6, y: 5.8, w: 7, h: 0.4, fontSize: 15, italic: true, color: C.amber });
  phone(s, 's_home.png', 8.25, 1.65, 5.0);
  phone(s, 's_listen.png', 10.75, 1.65, 5.0);
}

// 5 ── What RoadSide catches ──────────────────────────────────────────────────
{
  const s = pres.addSlide(); bg(s);
  head(s, 'What RoadSide catches', 'Current + planned checks, across bikes and cars');
  T(s, [
    { text: '●  Working today', options: { color: C.amber, bold: true } },
    { text: '        ' },
    { text: '○  Planned', options: { color: C.muted, bold: true } }],
  { x: 0.6, y: 1.5, w: 6, h: 0.3, fontSize: 12 });
  const rows = [
    ['Chain rattle', 'Clean and lube the chain, check slack', 'BIKE', true],
    ['Worn sprocket teeth', 'Ride slowly on smooth roads, see a mechanic', 'BIKE', true],
    ['Brake squeal', 'Check the pads, never oil near the brakes', 'BOTH', false],
    ['Starter clicking', 'Check battery terminals, charge the battery', 'BOTH', false],
    ['Engine knocking', 'Ease off, check fuel and engine oil', 'BOTH', false],
    ['Misfire / spark plug', 'Inspect or replace the spark plug', 'BOTH', false],
    ['Belt squeal', 'Check belt tension and wear', 'CAR', false],
    ['Uneven idle', 'Check air filter and plugs, then a mechanic', 'BOTH', false],
  ];
  rows.forEach(([a, b, veh, today], i) => {
    const y = 1.92 + i * 0.63;
    card(s, 0.6, y, 12.1, 0.53, C.cardLow);
    T(s, today ? '●' : '○', { x: 0.85, y, w: 0.3, h: 0.53, fontSize: 12, color: today ? C.amber : C.muted, valign: 'middle' });
    T(s, a, { x: 1.25, y, w: 4.0, h: 0.53, fontFace: HEAD, fontSize: 15, bold: true, color: today ? C.amber : C.text, valign: 'middle' });
    T(s, '→', { x: 5.3, y, w: 0.5, h: 0.53, fontSize: 16, color: C.muted, valign: 'middle' });
    T(s, b, { x: 5.9, y, w: 5.1, h: 0.53, fontSize: 14, color: today ? C.text : C.muted, valign: 'middle' });
    const col = veh === 'BIKE' ? C.amber : (veh === 'CAR' ? C.blue : C.muted);
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 11.2, y: y + 0.11, w: 1.3, h: 0.31, rectRadius: 0.15, fill: { color: col, transparency: 82 }, line: { color: col, width: 0 } });
    T(s, veh === 'BOTH' ? 'BIKE + CAR' : veh, { x: 11.2, y: y + 0.11, w: 1.3, h: 0.31, fontFace: HEAD, fontSize: 10, bold: true, color: col, align: 'center', valign: 'middle' });
  });
  s.addNotes('One pipeline for bikes and cars: each fault is a small trained check on the same on-device sound and vision models.');
}

// 6 ── How it works ───────────────────────────────────────────────────────────
{
  const s = pres.addSlide(); bg(s);
  head(s, 'How it works', 'Three stages, all on the phone');
  [[C.blue, 'Listen', 'An on-device sound model picks out the fault in a short recording — a rattle, a knock, a squeal or a misfire.'],
   [C.amber, 'Look', 'An on-device vision model checks the photo for visible problems — a dirty chain, worn teeth, a corroded terminal.'],
   [C.blue, 'Advise', 'A rules engine combines sound and sight into one assessment, a plain-language solution and Ask RoadSide.']]
    .forEach(([col, h, b], i) => {
      const x = 0.6 + i * 4.1;
      card(s, x, 1.85, 3.9, 3.1);
      num(s, i + 1, x + 0.35, 2.15, col);
      T(s, h, { x: x + 0.35, y: 2.8, w: 3.2, h: 0.45, fontFace: HEAD, fontSize: 19, bold: true });
      T(s, b, { x: x + 0.35, y: 3.35, w: 3.25, h: 1.4, fontSize: 14, color: C.muted, valign: 'top' });
    });
  T(s, 'Only the microphone and camera decide. What the rider types never changes the result.', { x: 0.6, y: 5.35, w: 12, h: 0.4, fontSize: 15, italic: true, color: C.amber });
}

// 7 ── See it work (screenshots) ──────────────────────────────────────────────
{
  const s = pres.addSlide(); bg(s);
  head(s, 'See it work', 'From one photo to plain advice');
  [['s_inspect.png', 'Inspect', 'Chain detected, sprocket teeth look worn'],
   ['s_assess.png', 'Assessment', 'What it saw, kept apart from what it concludes'],
   ['s_solution.png', 'What you can do', 'Meaning, actions, tools and safety'],
   ['s_ask.png', 'Ask RoadSide', '“Is it safe to ride?” answered in context']]
    .forEach(([f, h, b], i) => {
      const x = 0.75 + i * 3.1;
      phone(s, f, x + 0.25, 1.6, 4.4);
      T(s, h, { x, y: 6.1, w: 2.8, h: 0.35, fontFace: HEAD, fontSize: 15, bold: true, color: C.amber });
      T(s, b, { x, y: 6.45, w: 2.8, h: 0.6, fontSize: 12, color: C.muted, valign: 'top' });
    });
}

// 7b ── The prototype ────────────────────────────────────────────────────────
{
  const s = pres.addSlide(); bg(s);
  head(s, 'The prototype', 'What we’ve already built');
  T(s, 'We built the first check end to end — chain and drivetrain — to prove the whole pipeline runs on a phone.', {
    x: 0.6, y: 1.6, w: 7.6, h: 0.7, fontSize: 16, color: C.muted, valign: 'top' });
  const rows = [
    [C.blue, 'Sound', 'YAMNet — an open, on-device sound model — with our trained drivetrain-noise head'],
    [C.amber, 'Vision', 'MobileNetV3 — an open, on-device image model — with our trained head for the chain, visible dirt and possible sprocket wear'],
    [C.blue, 'Advice', 'Rules engine that fuses sound and sight into a plain-language solution, plus Ask RoadSide'],
    [C.amber, 'Runs on', 'Android · Kotlin · Jetpack Compose · CameraX · LiteRT — fully offline, no internet permission'],
  ];
  rows.forEach(([col, h, b], i) => {
    const y = 2.45 + i * 0.95;
    card(s, 0.6, y, 7.6, 0.8, C.cardLow);
    T(s, h, { x: 0.9, y, w: 1.4, h: 0.8, fontFace: HEAD, fontSize: 15, bold: true, color: col, valign: 'middle' });
    T(s, b, { x: 2.3, y, w: 5.75, h: 0.8, fontSize: 13, valign: 'middle' });
  });
  T(s, 'At the hackathon, the same pipeline grows to engine, brake and car checks — one small trained head per fault.', {
    x: 0.6, y: 6.35, w: 7.8, h: 0.5, fontSize: 14, italic: true, color: C.amber });
  phone(s, 's_assess.png', 8.9, 1.55, 5.3);
  phone(s, 's_ask.png', 11.0, 2.05, 4.3);
  s.addNotes('Frozen open backbones with tiny trained heads: adding a new fault means training a small head, not a new model. The models can be swapped later without changing the app flow.');
}

// 8 ── Why on-device ──────────────────────────────────────────────────────────
{
  const s = pres.addSlide(); bg(s);
  head(s, 'Why on-device', 'It works where breakdowns happen');
  [['Instant', C.amber, 'No upload, no server queue. The answer appears right after you record.'],
   ['Private', C.blue, 'Recordings and photos never leave the phone.'],
   ['Anywhere', C.amber, 'Airplane mode, no signal, no data pack — still works.'],
   ['Free to run', C.blue, 'No per-use cost, nothing to host.']]
    .forEach(([h, col, b], i) => {
      const x = 0.6 + (i % 2) * 6.15, y = 1.85 + Math.floor(i / 2) * 1.45;
      card(s, x, y, 5.95, 1.25);
      T(s, h, { x: x + 0.35, y: y + 0.2, w: 5.3, h: 0.4, fontFace: HEAD, fontSize: 18, bold: true, color: col });
      T(s, b, { x: x + 0.35, y: y + 0.62, w: 5.3, h: 0.5, fontSize: 14, color: C.muted });
    });
  T(s, 'Compact sound and vision models run on the phone itself, with no internet permission at all.', { x: 0.6, y: 4.95, w: 12, h: 0.4, fontSize: 13, color: C.muted });
}

// 9 ── The demo ───────────────────────────────────────────────────────────────
{
  const s = pres.addSlide(); bg(s);
  head(s, 'The demo', 'Live on the phone, in airplane mode');
  brackets(s, 0.6, 1.75, 7.2, 4.6, 0.5);
  ['Record the noise from the vehicle', 'Photograph the part', 'RoadSide shows what it heard and saw',
   'The assessment and what to do next', 'A judge asks “Is it safe to ride?”']
    .forEach((t, i) => {
      const y = 2.15 + i * 0.78;
      num(s, i + 1, 1.05, y, i % 2 ? C.blue : C.amber);
      T(s, t, { x: 1.7, y, w: 5.8, h: 0.42, fontSize: 16, valign: 'middle' });
    });
  phone(s, 's_inspect.png', 8.55, 1.6, 5.0);
  phone(s, 's_solution2.png', 10.95, 1.6, 5.0);
}

// 10 ── Honest limits ─────────────────────────────────────────────────────────
{
  const s = pres.addSlide(); bg(s);
  head(s, 'Honest limits', 'What we’re claiming, and what we’re not');
  [['A first check, not a mechanic', 'RoadSide tells you what to look at and when to get help. It does not replace an inspection.'],
   ['Unsure means unsure', 'When a sound or photo isn’t clear, it says so and asks for a retake instead of guessing.'],
   ['No invented measurements', 'No fake frequencies, millimetres or percentages — only what it actually observed.']]
    .forEach(([h, b], i) => {
      const y = 1.8 + i * 1.08;
      card(s, 0.6, y, 12.1, 0.9);
      T(s, h, { x: 0.95, y: y + 0.12, w: 11.4, h: 0.38, fontFace: HEAD, fontSize: 16, bold: true, color: C.amber });
      T(s, b, { x: 0.95, y: y + 0.48, w: 11.4, h: 0.35, fontSize: 13, color: C.muted });
    });
  T(s, 'Built to be the first thing a rider opens, and honest about when to see a mechanic.', { x: 0.6, y: 5.25, w: 12, h: 0.45, fontFace: HEAD, fontSize: 16, bold: true });
}

// 11 ── Stack and team ────────────────────────────────────────────────────────
{
  const s = pres.addSlide(); bg(s);
  head(s, 'Build', 'Stack and team');
  card(s, 0.6, 1.8, 5.95, 3.8);
  T(s, 'Stack', { x: 0.95, y: 2.05, w: 5, h: 0.45, fontFace: HEAD, fontSize: 18, bold: true, color: C.amber });
  T(s, [
    { text: 'Android, Kotlin, Jetpack Compose, CameraX', options: { bullet: true, breakLine: true } },
    { text: 'Compact on-device sound model with a small trained head per fault', options: { bullet: true, breakLine: true } },
    { text: 'Compact on-device vision model for visible wear and damage', options: { bullet: true, breakLine: true } },
    { text: 'On-device rules engine and Ask RoadSide', options: { bullet: true, breakLine: true } },
    { text: 'No network permission, no cloud services', options: { bullet: true } }],
  { x: 0.95, y: 2.6, w: 5.3, h: 2.8, fontSize: 14, paraSpaceAfter: 6, valign: 'top' });
  card(s, 6.75, 1.8, 5.95, 3.8);
  T(s, 'Team', { x: 7.1, y: 2.05, w: 5, h: 0.45, fontFace: HEAD, fontSize: 18, bold: true, color: C.blue });
  T(s, [
    { text: 'D Sri Sashank', options: { breakLine: true } }, { text: ' ', options: { breakLine: true, fontSize: 8 } },
    { text: 'M N B Prasad Reddy', options: { breakLine: true } }, { text: ' ', options: { breakLine: true, fontSize: 8 } },
    { text: 'L Atchut Kumar' }],
  { x: 7.1, y: 2.7, w: 5.2, h: 2.4, fontFace: HEAD, fontSize: 17, valign: 'top' });
}

// 12 ── Close ─────────────────────────────────────────────────────────────────
{
  const s = pres.addSlide(); bg(s);
  brackets(s, 0.45, 0.45, W - 0.9, H - 0.9, 0.75);
  T(s, 'A new noise means guesswork.', { x: 1.3, y: 2.3, w: 10, h: 0.6, fontFace: HEAD, fontSize: 26, bold: true, color: C.muted });
  T(s, 'A mechanic means a trip.', { x: 1.3, y: 2.95, w: 10, h: 0.6, fontFace: HEAD, fontSize: 26, bold: true, color: C.muted });
  T(s, 'RoadSide tells you right there.', { x: 1.3, y: 3.85, w: 10, h: 0.8, fontFace: HEAD, fontSize: 36, bold: true, color: C.amber });
  T(s, 'ROADSIDE  |  iQOO Hackathon 2026  |  Hyderabad', { x: 7.5, y: 6.55, w: 5, h: 0.3, fontSize: 10, color: C.muted, align: 'right' });
}

pres.writeFile({ fileName: path.join(__dirname, 'RoadSide_deck_v3.pptx') }).then(() => console.log('written RoadSide_deck_v3.pptx'));
