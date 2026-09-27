/* ═══════════════════════════════════════════════════════════════
   RETROPACK · PRODUCT PAGE — terminal.js
   Live replay of the real BuildEngine 15-step transformation
   pipeline: typed command, streamed steps with spinners → checks,
   per-step details, synced progress bar, replay on demand.
   ═══════════════════════════════════════════════════════════════ */
(function () {
  'use strict';

  const term = document.getElementById('terminal');
  const body = document.getElementById('terminalBody');
  const progress = document.getElementById('terminalProgress');
  const replayBtn = document.getElementById('terminalReplay');
  if (!term || !body) return;

  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const COMMAND = 'retropack build --rom "Tobu Tobu Girl Deluxe (EU).gbc" --runtime mgba-unified';

  /* Steps mirror masterplan.md §6 — the real sequence */
  const STEPS = [
    { label: 'streaming checksums',        dots: 18, detail: 'crc32 ✓  md5 ✓  sha1 ✓  sha256 ✓ · 1.0 MB zero-heap', ms: 560 },
    { label: 'template integrity',         dots: 21, detail: 'SHA-256 pinned match · mgba-unified v0.10.5',        ms: 420 },
    { label: 'pre-flight package check',   dots: 17, detail: 'com.retropack.game.tobu_7f3a91c2e0 → versionCode 2', ms: 380 },
    { label: 'scratch allocation',         dots: 19, detail: 'same-volume atomic staging · EXDEV-safe',            ms: 300 },
    { label: 'signature residue strip',    dots: 17, detail: 'META-INF/*.SF removed · signing block cleared',      ms: 340 },
    { label: 'streamed asset injection',   dots: 16, detail: 'assets/game.rom · assets/retropack.json (schema 1)', ms: 520 },
    { label: 'AXML mutation',              dots: 23, detail: 'ARSCLib · string pool re-indexed · label inlined',   ms: 480 },
    { label: 'adaptive icon',              dots: 23, detail: 'foreground + background layers · 108dp safe zone',    ms: 400 },
    { label: 'protected entries',          dots: 20, detail: 'classes.dex ✓  lib/arm64-v8a/*.so ✓  hashes match',  ms: 440 },
    { label: '16 KB zipalign',             dots: 21, detail: 'zipflinger padding · uncompressed natives',          ms: 460 },
    { label: 'alignment assertion',        dots: 19, detail: 'payload offsets % 16384 == 0 · all ABIs',             ms: 360 },
    { label: 'cryptographic signing',      dots: 17, detail: 'HybridKeystore · v1 + v2 + v3 single pass',           ms: 540 },
    { label: 'post-sign verification',     dots: 17, detail: 'ApkVerifier · chunk hashes ✓ · manifest sane',        ms: 420 },
    { label: 'atomic finalization',        dots: 18, detail: 'scratch → target rename · fsync',                    ms: 320 },
    { label: 'BuildResult emitted',        dots: 18, detail: '3.2 s · 4.7 MB · installable',                        ms: 380 },
  ];

  const FINAL_LINES = [
    'BUILD VERIFIED — artifact signed, sealed, ready.',
    'install → launches from your launcher like any app.  ◼',
  ];

  let timers = [];
  let started = false;

  function clearAll() {
    timers.forEach(clearTimeout);
    timers = [];
  }

  function line(cls) {
    const div = document.createElement('span');
    div.className = 'tl ' + (cls || '');
    body.appendChild(div);
    return div;
  }

  function padDots(label, dots) {
    const filled = label + ' ';
    const target = dots;
    return filled + '.'.repeat(Math.max(0, target - label.length));
  }

  function sleep(fn, ms) {
    timers.push(window.setTimeout(fn, ms));
  }

  function typeCommand(onDone) {
    const div = line('tl-cmd');
    const prompt = document.createElement('span');
    prompt.className = 'tl-prompt';
    prompt.textContent = '$ ';
    div.appendChild(prompt);

    const caret = document.createElement('span');
    caret.className = 'tl-caret';
    div.appendChild(caret);

    let i = 0;
    function tick() {
      if (i <= COMMAND.length) {
        caret.insertAdjacentText('beforebegin', COMMAND[i - 1] || '');
        i++;
        sleep(tick, 14 + Math.random() * 22);
      } else {
        sleep(() => { caret.remove(); onDone(); }, 260);
      }
    }
    sleep(tick, 60);
  }

  function runStep(idx) {
    if (idx >= STEPS.length) { runFinal(); return; }

    const s = STEPS[idx];
    const div = line('tl-step');

    const idxSpan = document.createElement('span');
    idxSpan.className = 'tl-idx';
    idxSpan.textContent = `[${String(idx + 1).padStart(2, '0')}/15] `;

    const labelSpan = document.createElement('span');
    labelSpan.textContent = padDots(s.label, s.dots) + ' ';

    const spinner = document.createElement('span');
    spinner.className = 'tl-spinner';

    div.append(idxSpan, labelSpan, spinner);
    body.scrollTop = body.scrollHeight;

    const progressPct = ((idx + 1) / (STEPS.length + 1)) * 100;
    progress.style.width = progressPct + '%';

    sleep(() => {
      spinner.remove();
      const ok = document.createElement('span');
      ok.className = 'tl-ok';
      ok.textContent = '✓';
      div.appendChild(ok);

      const detail = document.createElement('span');
      detail.className = 'tl-detail';
      detail.textContent = '  ' + s.detail;
      div.appendChild(detail);
      body.scrollTop = body.scrollHeight;

      sleep(() => runStep(idx + 1), 150);
    }, s.ms);
  }

  function runFinal() {
    progress.style.width = '100%';
    STEPS.forEach(() => {});
    FINAL_LINES.forEach((text, i) => {
      sleep(() => {
        const div = line(i === 0 ? 'tl-done' : 'tl-dim');
        div.textContent = text;
        body.scrollTop = body.scrollHeight;
      }, 260 + i * 420);
    });
  }

  function run() {
    clearAll();
    body.innerHTML = '';
    progress.style.width = '0%';
    typeCommand(() => sleep(() => runStep(0), 300));
  }

  function start() {
    if (started) return;
    started = true;
    if (reduced) {
      // static render — every line instantly
      const cmd = line('tl-cmd');
      cmd.textContent = '$ ' + COMMAND;
      STEPS.forEach((s, i) => {
        const div = line('tl-step');
        const idx = document.createElement('span');
        idx.className = 'tl-idx';
        idx.textContent = `[${String(i + 1).padStart(2, '0')}/15] `;
        const ok = document.createElement('span');
        ok.className = 'tl-ok';
        ok.textContent = '✓';
        const det = document.createElement('span');
        det.className = 'tl-detail';
        det.textContent = '  ' + s.detail;
        div.append(idx, padDots(s.label, s.dots) + ' ', ok, det);
      });
      FINAL_LINES.forEach((t, i) => {
        const div = line(i === 0 ? 'tl-done' : 'tl-dim');
        div.textContent = t;
      });
      progress.style.width = '100%';
      return;
    }
    run();
  }

  if (replayBtn) {
    replayBtn.addEventListener('click', () => {
      started = true;
      run();
    });
  }

  const io = new IntersectionObserver((entries) => {
    entries.forEach((e) => {
      if (e.isIntersecting) {
        start();
        io.disconnect();
      }
    });
  }, { threshold: 0.25 });
  io.observe(term);
})();
