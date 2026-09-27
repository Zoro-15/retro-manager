/* ═══════════════════════════════════════════════════════════════
   RETROPACK · PRODUCT PAGE — boot.js
   CRT power-on boot sequence. Lines stream in, bar fills,
   then the screen flashes and scales open like a tube warming up.
   ═══════════════════════════════════════════════════════════════ */
(function () {
  'use strict';

  const boot = document.getElementById('boot');
  if (!boot) return;
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    boot.remove();
    document.documentElement.classList.add('booted');
    return;
  }

  const linesEl = document.getElementById('bootLines');
  const barFill = document.getElementById('bootBarFill');

  const LINES = [
    { text: 'RETROPACK BIOS v0.1.0 — arm64', cls: '' },
    { text: 'verifying runtime registry .......... ', cls: '', ok: 'OK' },
    { text: 'mounting 10 cores ................... ', cls: '', ok: 'OK' },
    { text: '16 KB page alignment ................ ', cls: '', ok: 'OK' },
    { text: 'hybrid keystore · AES-256-GCM ....... ', cls: '', ok: 'OK' },
    { text: 'CI memory: 41 suites / 0 failures ... ', cls: '', ok: 'OK' },
  ];

  const LINE_MS = 170;
  let done = false;

  function addLine(entry, i) {
    const div = document.createElement('div');
    div.textContent = entry.text;
    if (entry.ok) {
      const ok = document.createElement('span');
      ok.className = 'bl-ok';
      ok.textContent = '[' + entry.ok + ']';
      div.appendChild(ok);
    }
    linesEl.appendChild(div);
    const pct = Math.round(((i + 1) / LINES.length) * 100);
    barFill.style.width = pct + '%';
  }

  function finish() {
    if (done) return;
    done = true;
    barFill.style.width = '100%';
    boot.classList.add('is-crt-off');
    document.documentElement.classList.add('booted');
    window.setTimeout(() => boot.classList.add('is-done'), 480);
    window.setTimeout(() => boot.remove(), 1100);
  }

  // stream the lines
  LINES.forEach((l, i) => {
    window.setTimeout(() => addLine(l, i), 260 + i * LINE_MS);
  });

  const total = 260 + LINES.length * LINE_MS + 420;
  window.setTimeout(finish, total);

  // "PRESS START" — click or key skips ahead
  boot.addEventListener('pointerdown', finish);
  window.addEventListener('keydown', function onKey() {
    finish();
    window.removeEventListener('keydown', onKey);
  }, { once: true });

  // hard safety net — never trap the user behind the boot screen
  window.setTimeout(finish, 3400);
})();
