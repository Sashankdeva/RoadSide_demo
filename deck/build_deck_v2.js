// RoadSide pitch deck v2 — matches the app's Stitch design (dark automotive, safety orange).
const pptxgen = require('pptxgenjs');
const React = require('react');
const ReactDOMServer = require('react-dom/server');
const sharp = require('sharp');
const fa = require('react-icons/fa');
const path = require('path');

const B = (f) => path.join(__dirname, 'build', f);
const C = {
  bg: '111316', card: '1E2023', cardLow: '1A1C1F', well: '0C0E11',
  amber: 'E57A24', amberSoft: 'FFB787', text: 'E2E2E6', muted: 'A9A29C',
  safe: '10B981', advisory: 'D97706', critical: 'DC2626',
};
const HEAD = 'Arial', BODY = 'Calibri';

async function icon(Comp, color = C.amber, size = 256) {
  const svg = ReactDOMServer.renderToStaticMarkup(React.createElement(Comp, { color: '#' + color, size }));
  const png = await sharp(Buffer.from(svg)).png().toBuffer();
  return 'image/png;base64,' + png.toString('base64');
}

(async () => {
  const pres = new pptxgen();
  pres.layout = 'LAYOUT_16x9'; // 10 x 5.625 in
  pres.title = 'RoadSide';

  const I = {
    mic: await icon(fa.FaMicrophone), cam: await icon(fa.FaCamera), tools: await icon(fa.FaTools),
    search: await icon(fa.FaSearch), signal: await icon(fa.FaSignal), wallet: await icon(fa.FaRupeeSign),
    brain: await icon(fa.FaProjectDiagram), shield: await icon(fa.FaShieldAlt), eye: await icon(fa.FaEye),
    q: await icon(fa.FaQuestionCircle), hash: await icon(fa.FaBan), key: await icon(fa.FaKeyboard),
    moto: await icon(fa.FaMotorcycle), car: await icon(fa.FaCar), bicycle: await icon(fa.FaBicycle),
    chat: await icon(fa.FaComments), check: await icon(fa.FaCheckCircle, C.safe), arrow: await icon(fa.FaArrowRight, C.muted),
    plane: await icon(fa.FaPlane),
  };

  const bg = (s) => { s.background = { color: C.bg }; };
  const title = (s, t, sub) => {
    s.addText(t, { x: 0.5, y: 0.35, w: 9, h: 0.6, fontFace: HEAD, fontSize: 28, bold: true, color: C.text, margin: 0, isTextBox: true });
    if (sub) s.addText(sub, { x: 0.5, y: 0.95, w: 9, h: 0.35, fontFace: BODY, fontSize: 14, color: C.muted, margin: 0, isTextBox: true });
  };
  const eyebrow = (s, t, x, y, w = 4) => s.addText(t.toUpperCase(), {
    x, y, w, h: 0.25, fontFace: HEAD, fontSize: 10, bold: true, color: C.amberSoft, charSpacing: 2, margin: 0, isTextBox: true });
  const card = (s, x, y, w, h, color = C.card) => s.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x, y, w, h, rectRadius: 0.18, fill: { color }, line: { color, width: 0 } });
  const iconDot = (s, img, x, y, d = 0.5, tint = C.amber) => {
    s.addShape(pres.shapes.OVAL, { x, y, w: d, h: d, fill: { color: tint, transparency: 82 }, line: { color: tint, transparency: 100 } });
    s.addImage({ data: img, x: x + d * 0.25, y: y + d * 0.25, w: d * 0.5, h: d * 0.5 });
  };
  const phone = (s, file, x, y, h) => s.addImage({ path: B(file), x, y, w: h * 600 / 1229, h });

  // 1 ── Title ────────────────────────────────────────────────────────────────
  {
    const s = pres.addSlide(); bg(s);
    s.addImage({ path: B('logo.png'), x: 0.7, y: 1.05, w: 3.3, h: 3.3 * 1056 / 1080 });
    s.addText('RoadSide', { x: 4.5, y: 1.35, w: 5, h: 0.9, fontFace: HEAD, fontSize: 48, bold: true, color: C.text, margin: 0, isTextBox: true });
    s.addText('Hold your phone near your bike. It listens to the noise, looks at the chain, and tells you what may be wrong and what to do.', {
      x: 4.5, y: 2.3, w: 4.9, h: 1.0, fontFace: BODY, fontSize: 17, color: C.muted, margin: 0, isTextBox: true });
    const chips = [['Fully on-device', C.safe], ['Works offline', C.safe], ['Private by design', C.amberSoft]];
    let cx = 4.5;
    chips.forEach(([t, col]) => {
      const w = 0.22 + t.length * 0.085;
      s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: cx, y: 3.55, w, h: 0.36, rectRadius: 0.18, fill: { color: col, transparency: 85 }, line: { color: col, transparency: 100 } });
      s.addText(t, { x: cx, y: 3.55, w, h: 0.36, fontFace: BODY, fontSize: 11, bold: true, color: col, align: 'center', valign: 'middle', margin: 0, isTextBox: true });
      cx += w + 0.15;
    });
    s.addText('iQOO Hackathon · Open Innovation', { x: 4.5, y: 4.35, w: 5, h: 0.3, fontFace: BODY, fontSize: 12, color: C.muted, margin: 0, isTextBox: true });
    s.addNotes('RoadSide: an on-device roadside check. Sound plus sight, plain-language help, no internet needed.');
  }

  // 2 ── Problem ──────────────────────────────────────────────────────────────
  {
    const s = pres.addSlide(); bg(s);
    title(s, 'A new noise, and no way to know what it means', 'Every rider knows the moment: a rattle, click or squeal appears out of nowhere.');
    const items = [
      [I.search, "You can't search a sound", 'Is it a dirty chain, worn sprocket teeth, or nothing at all? Typing "weird rattle" into a search box does not help.'],
      [I.signal, 'Breakdowns happen off-grid', 'The problem shows up on the highway or a village road — exactly where there is no signal for online help.'],
      [I.wallet, 'Paying just to find out', 'The fallback is ignoring it and risking a snapped chain, or a mechanic visit for work the bike may not need.'],
    ];
    items.forEach(([img, h, b], i) => {
      const x = 0.5 + i * 3.07;
      card(s, x, 1.65, 2.85, 3.3);
      iconDot(s, img, x + 0.3, 1.95, 0.62);
      s.addText(h, { x: x + 0.3, y: 2.75, w: 2.3, h: 0.7, fontFace: HEAD, fontSize: 16, bold: true, color: C.text, margin: 0, valign: 'top', isTextBox: true });
      s.addText(b, { x: x + 0.3, y: 3.45, w: 2.3, h: 1.35, fontFace: BODY, fontSize: 13, color: C.muted, margin: 0, valign: 'top', isTextBox: true });
    });
    s.addNotes('Three pains: sounds are unsearchable, faults happen without signal, and finding out costs money.');
  }

  // 3 ── Solution ─────────────────────────────────────────────────────────────
  {
    const s = pres.addSlide(); bg(s);
    title(s, 'Your phone becomes a roadside check', 'Three steps, about a minute, nothing leaves the phone.');
    const steps = [
      [I.mic, 'Listen', 'Record the noise for a few seconds while it happens.'],
      [I.cam, 'Inspect', 'Take a close photo of the chain and sprocket.'],
      [I.tools, 'Get help', 'What RoadSide found, what it may mean, what to do — and a safety note.'],
    ];
    steps.forEach(([img, h, b], i) => {
      const y = 1.6 + i * 1.12;
      iconDot(s, img, 0.5, y, 0.7);
      s.addText(`${i + 1}. ${h}`, { x: 1.4, y, w: 3.9, h: 0.35, fontFace: HEAD, fontSize: 17, bold: true, color: C.text, margin: 0, isTextBox: true });
      s.addText(b, { x: 1.4, y: y + 0.37, w: 3.9, h: 0.55, fontFace: BODY, fontSize: 13, color: C.muted, margin: 0, isTextBox: true });
    });
    phone(s, 's_home.png', 5.55, 1.45, 3.85);
    phone(s, 's_listen.png', 7.55, 1.45, 3.85);
    s.addNotes('Home, then Describe, Listen, Inspect, Assessment, Solution. Screens are real captures from the app.');
  }

  // 4 ── How it works ─────────────────────────────────────────────────────────
  {
    const s = pres.addSlide(); bg(s);
    title(s, 'How it works — two senses, one plain answer', 'Open pretrained models with small trained heads, all running on the handset.');
    const lane = (y, img, h1, mdl, out) => {
      card(s, 0.5, y, 1.75, 0.95, C.cardLow); iconDot(s, img, 0.62, y + 0.2, 0.55);
      s.addText(h1, { x: 1.22, y: y + 0.2, w: 1.0, h: 0.55, fontFace: HEAD, fontSize: 13, bold: true, color: C.text, valign: 'middle', margin: 0, isTextBox: true });
      s.addImage({ data: I.arrow, x: 2.35, y: y + 0.35, w: 0.25, h: 0.25 });
      card(s, 2.7, y, 2.6, 0.95);
      s.addText(mdl, { x: 2.85, y: y + 0.08, w: 2.35, h: 0.8, fontFace: BODY, fontSize: 12, color: C.text, valign: 'middle', margin: 0, isTextBox: true });
      s.addImage({ data: I.arrow, x: 5.4, y: y + 0.35, w: 0.25, h: 0.25 });
      card(s, 5.75, y, 1.75, 0.95, C.cardLow);
      s.addText(out, { x: 5.88, y: y + 0.08, w: 1.5, h: 0.8, fontFace: BODY, fontSize: 12, color: C.amberSoft, bold: true, valign: 'middle', margin: 0, isTextBox: true });
    };
    lane(1.6, I.mic, 'Sound', [{ text: 'YAMNet', options: { bold: true, breakLine: true } }, { text: 'open everyday-sound model + a trained drivetrain-sound head' }], 'Chain-like rattling?');
    lane(2.8, I.cam, 'Photo', [{ text: 'MobileNetV3', options: { bold: true, breakLine: true } }, { text: 'chain + condition head: dirt, dryness, worn teeth' }], 'What the photo shows');
    // fusion
    card(s, 7.8, 1.6, 1.7, 2.15, C.amber);
    s.addText([{ text: 'Rules engine', options: { bold: true, breakLine: true, fontSize: 14 } }, { text: 'fuses sound + sight into one assessment', options: { fontSize: 11 } }], {
      x: 7.9, y: 1.7, w: 1.5, h: 1.95, fontFace: BODY, color: C.bg, valign: 'middle', align: 'center', margin: 0, isTextBox: true });
    const outs = ['Assessment', 'What you can do', 'Ask RoadSide'];
    outs.forEach((t, i) => {
      const x = 0.5 + i * 3.07;
      card(s, x, 4.1, 2.85, 0.6, C.cardLow);
      s.addText(t, { x, y: 4.1, w: 2.85, h: 0.6, fontFace: HEAD, fontSize: 13, bold: true, color: C.text, align: 'center', valign: 'middle', margin: 0, isTextBox: true });
    });
    s.addText('No internet needed · works in airplane mode · nothing leaves the phone', {
      x: 0.5, y: 4.9, w: 9, h: 0.3, fontFace: BODY, fontSize: 12, color: C.muted, margin: 0, isTextBox: true });
    s.addNotes('Frozen open backbones (YAMNet, MobileNetV3) plus tiny trained heads. Rules fuse the evidence; typed text never changes the result.');
  }

  // 5 ── Demo screens ─────────────────────────────────────────────────────────
  {
    const s = pres.addSlide(); bg(s);
    title(s, 'From photo to plain advice', 'Real screens from the working app.');
    const shots = [
      ['s_inspect.png', 'Inspect', 'Chain detected; sprocket teeth look worn'],
      ['s_assess.png', 'Assessment', 'Observations kept separate from the conclusion'],
      ['s_solution.png', 'What you can do', 'What it may mean, actions, tools, safety'],
      ['s_ask.png', 'Ask RoadSide', '"Is it safe to ride?" answered in context'],
    ];
    shots.forEach(([f, h, b], i) => {
      const x = 0.55 + i * 2.3;
      phone(s, f, x + 0.12, 1.4, 3.1);
      s.addText(h, { x, y: 4.6, w: 1.95, h: 0.28, fontFace: HEAD, fontSize: 13, bold: true, color: C.text, margin: 0, isTextBox: true });
      s.addText(b, { x, y: 4.88, w: 1.95, h: 0.5, fontFace: BODY, fontSize: 11, color: C.muted, margin: 0, isTextBox: true });
    });
    s.addNotes('Worn sprocket example: RoadSide suggests riding slowly on smooth roads until it is checked, and points to a mechanic.');
  }

  // 6 ── Honest by design ─────────────────────────────────────────────────────
  {
    const s = pres.addSlide(); bg(s);
    title(s, 'Honest by design', 'A wrong "your chain is fine" is worse than "not sure". RoadSide is built to say so.');
    const cards = [
      [I.eye, 'Observations ≠ conclusions', 'What the camera saw and what RoadSide concludes are shown separately.'],
      [I.q, '"Not clear enough" beats a guess', 'Low-confidence photos and sounds return an honest unknown, with a tip to retake.'],
      [I.hash, 'No invented numbers', 'No fake Hz, millimetres, torque or percentages anywhere a rider reads.'],
      [I.key, 'Your words never change the result', 'Only the microphone and camera decide; typed text is context for you.'],
    ];
    cards.forEach(([img, h, b], i) => {
      const x = 0.5 + (i % 2) * 4.6, y = 1.55 + Math.floor(i / 2) * 1.75;
      card(s, x, y, 4.4, 1.55);
      iconDot(s, img, x + 0.25, y + 0.28, 0.6);
      s.addText(h, { x: x + 1.05, y: y + 0.25, w: 3.15, h: 0.4, fontFace: HEAD, fontSize: 15, bold: true, color: C.text, margin: 0, isTextBox: true });
      s.addText(b, { x: x + 1.05, y: y + 0.68, w: 3.15, h: 0.7, fontFace: BODY, fontSize: 12, color: C.muted, margin: 0, valign: 'top', isTextBox: true });
    });
    s.addNotes('Each of these rules is checked automatically on the device.');
  }

  // 7 ── Built for the roadside ──────────────────────────────────────────────
  {
    const s = pres.addSlide(); bg(s);
    title(s, 'Built for the roadside', 'Designed for the places where help is hardest to find.');
    const feats = [
      [I.plane, 'Works offline', 'No signal, no problem. Every check runs in airplane mode.'],
      [I.shield, 'Private by design', 'Recordings and photos never leave the phone. The app has no internet access at all.'],
      [I.check, 'Answers in moments', 'Results appear right after you record or take the photo, with smooth, responsive screens.'],
      [I.chat, 'Speaks plainly', 'No jargon: what was found, what it may mean, what to do, and when to see a mechanic.'],
    ];
    feats.forEach(([img, h, b], i) => {
      const x = 0.5 + i * 2.3;
      card(s, x, 1.55, 2.1, 3.3);
      iconDot(s, img, x + 0.25, 1.85, 0.62);
      s.addText(h, { x: x + 0.25, y: 2.65, w: 1.7, h: 0.4, fontFace: HEAD, fontSize: 15, bold: true, color: C.text, margin: 0, isTextBox: true });
      s.addText(b, { x: x + 0.25, y: 3.1, w: 1.7, h: 1.6, fontFace: BODY, fontSize: 12, color: C.muted, margin: 0, valign: 'top', isTextBox: true });
    });
    s.addNotes('Offline, private, quick and plain-spoken: the four things a rider stuck at the roadside needs.');
  }

  // 8 ── Today and next ───────────────────────────────────────────────────────
  {
    const s = pres.addSlide(); bg(s);
    title(s, 'From two-wheelers to cars', 'One on-device pipeline; every new fault is a new small trained head.');
    // today
    card(s, 0.5, 1.5, 4.4, 3.6);
    iconDot(s, I.moto, 0.75, 1.72, 0.6);
    s.addText('What RoadSide checks', { x: 1.5, y: 1.8, w: 3.3, h: 0.45, fontFace: HEAD, fontSize: 15, bold: true, color: C.text, margin: 0, isTextBox: true });
    s.addText([
      { text: 'Chain and sprocket condition from a photo: dirt, dryness, worn teeth', options: { bullet: true, breakLine: true } },
      { text: 'Drivetrain rattling from sound', options: { bullet: true, breakLine: true } },
      { text: 'Listens for brake squeal and starter or battery clicking', options: { bullet: true, breakLine: true } },
      { text: 'Plain-language solution and Ask RoadSide, fully offline', options: { bullet: true } },
    ], { x: 0.75, y: 2.45, w: 3.95, h: 2.5, fontFace: BODY, fontSize: 13, color: C.muted, paraSpaceAfter: 6, margin: 0, valign: 'top', isTextBox: true });
    // next
    card(s, 5.1, 1.5, 4.4, 3.6, C.cardLow);
    iconDot(s, I.car, 5.35, 1.72, 0.6);
    s.addText('Coming next', { x: 6.1, y: 1.8, w: 3.3, h: 0.45, fontFace: HEAD, fontSize: 15, bold: true, color: C.text, margin: 0, isTextBox: true });
    s.addText([
      { text: 'Cars: engine knock, belt squeal, brake wear, battery and starter faults', options: { bullet: true, breakLine: true } },
      { text: 'Scooters and more motorcycles, tyres and brakes by photo', options: { bullet: true, breakLine: true } },
      { text: 'Car-specific sound and photo checks trained on labelled data', options: { bullet: true, breakLine: true } },
      { text: 'An on-device language model for richer Ask RoadSide answers', options: { bullet: true } },
    ], { x: 5.35, y: 2.45, w: 3.95, h: 2.5, fontFace: BODY, fontSize: 13, color: C.muted, paraSpaceAfter: 6, margin: 0, valign: 'top', isTextBox: true });
    s.addNotes('Two-wheeler checks work today; cars are next on the same pipeline.');
  }

  // 9 ── Close ────────────────────────────────────────────────────────────────
  {
    const s = pres.addSlide(); bg(s);
    s.addImage({ path: B('logo.png'), x: 3.55, y: 0.55, w: 2.9, h: 2.9 * 1056 / 1080 });
    s.addText('Ride smarter.', { x: 0.5, y: 3.55, w: 9, h: 0.7, fontFace: HEAD, fontSize: 34, bold: true, color: C.text, align: 'center', margin: 0, isTextBox: true });
    s.addText('A roadside check that listens, looks and explains — on the phone you already carry.', {
      x: 1, y: 4.25, w: 8, h: 0.5, fontFace: BODY, fontSize: 15, color: C.muted, align: 'center', margin: 0, isTextBox: true });
    s.addNotes('Thank you.');
  }

  await pres.writeFile({ fileName: path.join(__dirname, 'RoadSide_deck_v2.pptx') });
  console.log('written RoadSide_deck_v2.pptx');
})();
