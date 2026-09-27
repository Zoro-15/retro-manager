/* ═══════════════════════════════════════════════════════════════
   RETROPACK · PRODUCT PAGE — particles.js
   A drifting field of phosphor pixels — the "alive" background.
   DPR-aware, mouse-parallaxed, pauses when hidden, respects
   prefers-reduced-motion.
   ═══════════════════════════════════════════════════════════════ */
(function () {
  'use strict';

  const canvas = document.getElementById('particles');
  if (!canvas) return;
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

  const ctx = canvas.getContext('2d');
  const COLORS = [
    'rgba(0, 245, 155, A)',    // laser mint
    'rgba(16, 185, 129, A)',   // emerald
    'rgba(110, 231, 183, A)',  // light mint
    'rgba(52, 211, 153, A)',   // sea green
    'rgba(255, 255, 255, A)',  // crisp starlight
    'rgba(5, 150, 105, A)',    // deep emerald
  ];

  let W = 0, H = 0, DPR = 1;
  let particles = [];
  let mouse = { x: 0.5, y: 0.5 };      // normalized
  let scroll = 0;
  let running = true;
  let last = performance.now();

  const rand = (min, max) => min + Math.random() * (max - min);

  function build() {
    const count = window.innerWidth < 700 ? 34 : 68;
    particles = Array.from({ length: count }, () => {
      const c = COLORS[(Math.random() * COLORS.length) | 0];
      return {
        x: Math.random() * W,
        y: Math.random() * H,
        size: Math.random() < 0.72 ? rand(2, 3.5) : rand(4, 6),
        depth: rand(0.25, 1),                       // parallax weight
        vx: rand(-0.08, 0.08),
        vy: rand(-0.16, -0.035),
        alpha: rand(0.14, 0.5),
        twinkle: rand(0, Math.PI * 2),
        twinkleSpeed: rand(0.6, 1.8),
        color: c.replace('A', '1'),
        glow: Math.random() < 0.14,
      };
    });
  }

  function resize() {
    DPR = Math.min(window.devicePixelRatio || 1, 2);
    W = window.innerWidth;
    H = window.innerHeight;
    canvas.width = W * DPR;
    canvas.height = H * DPR;
    ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
    build();
  }

  function step(now) {
    if (!running) { last = now; requestAnimationFrame(step); return; }
    const dt = Math.min((now - last) / 16.667, 3);
    last = now;

    ctx.clearRect(0, 0, W, H);

    const mx = (mouse.x - 0.5) * 34;
    const my = (mouse.y - 0.5) * 22;

    for (const p of particles) {
      p.x += p.vx * dt;
      p.y += p.vy * dt;
      p.twinkle += 0.02 * p.twinkleSpeed * dt;

      if (p.y < -10) { p.y = H + 10; p.x = Math.random() * W; }
      if (p.x < -10) p.x = W + 10;
      if (p.x > W + 10) p.x = -10;

      const tw = 0.55 + 0.45 * Math.sin(p.twinkle);
      const a = p.alpha * tw;

      const px = p.x + mx * p.depth - (scroll * 0.04 * p.depth);
      const py = p.y + my * p.depth - (scroll * 0.02 * p.depth);
      const yy = ((py % (H + 40)) + H + 40) % (H + 40) - 20;

      const col = p.color.replace('1)', a.toFixed(2) + ')');

      if (p.glow) {
        ctx.save();
        ctx.shadowBlur = 12;
        ctx.shadowColor = col;
        ctx.fillStyle = col;
        ctx.fillRect(px, yy, p.size, p.size);
        ctx.restore();
      } else {
        ctx.fillStyle = col;
        ctx.fillRect(px, yy, p.size, p.size);
      }
    }

    requestAnimationFrame(step);
  }

  window.addEventListener('resize', resize, { passive: true });
  window.addEventListener('pointermove', (e) => {
    mouse.x = e.clientX / window.innerWidth;
    mouse.y = e.clientY / window.innerHeight;
  }, { passive: true });
  window.addEventListener('scroll', () => { scroll = window.scrollY; }, { passive: true });
  document.addEventListener('visibilitychange', () => {
    running = !document.hidden;
  });

  resize();
  requestAnimationFrame(step);
})();
