/* ═══════════════════════════════════════════════════════════════
   RETROPACK · PRODUCT PAGE — phone.js
   A tiny handcrafted pixel scene living inside the hero phone:
   parallax night sky, scrolling terrain, a running hero collecting
   cartridges. Pure canvas, no assets. 240×427 logical pixels,
   upscaled with image-rendering: pixelated.
   ═══════════════════════════════════════════════════════════════ */
(function () {
  'use strict';

  const canvas = document.getElementById('phoneCanvas');
  if (!canvas) return;

  const ctx = canvas.getContext('2d');
  const W = canvas.width;    // 270
  const H = canvas.height;   // 480

  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  ctx.imageSmoothingEnabled = false;

  /* palette — GBC "Tobu Tobu Girl" at night */
  const C = {
    skyTop: '#0B1030', skyMid: '#1A2350', skyLow: '#2B1B4D',
    star: '#DCE6FF', starDim: '#8A97C9',
    moon: '#F4F0D8', moonGlow: 'rgba(244, 240, 216, 0.16)',
    cityFar: '#141A38', cityNear: '#0E1226',
    window: '#FBBF24', windowDim: '#7A5A10',
    ground: '#0A0D1E', groundTop: '#1B2340',
    grass: '#123524', grassLite: '#1B4D33',
    hero: '#E2E8F0', heroSkin: '#F4C9A8', heroHair: '#EC4899',
    heroScarf: '#22D3EE', cartridge: '#818CF8', cartLabel: '#22D3EE',
    spark: '#FBBF24', one: '#34D399', cloud: '#232B52',
  };

  const rand = (a, b) => a + Math.random() * (b - a);

  /* world state */
  const stars = Array.from({ length: 46 }, () => ({
    x: Math.random() * W, y: Math.random() * 200,
    s: Math.random() < 0.75 ? 1 : 2, tw: rand(0, 6.28), sp: rand(0.8, 2.2),
  }));

  const cityFar = Array.from({ length: 14 }, (_, i) => ({
    x: i * 26, w: 16 + ((i * 7) % 12), h: 30 + ((i * 13) % 42),
  }));
  const cityNear = Array.from({ length: 12 }, (_, i) => ({
    x: i * 32, w: 20 + ((i * 11) % 14), h: 20 + ((i * 17) % 30),
  }));

  const clouds = [
    { x: 30, y: 58, w: 54, h: 7 },
    { x: 150, y: 92, w: 40, h: 5 },
    { x: 90, y: 130, w: 62, h: 6 },
  ];

  /* hero — runs in place, hops to catch cartridges */
  const hero = { x: 64, y: 0, vy: 0, onGround: true, frame: 0, blink: 0 };
  const GROUND_Y = 366;
  const GRAV = 0.34, JUMP = -5.6;

  /* cartridges drifting right → left, hero jumps to catch */
  let carts = [];
  let spawnTimer = 40;
  let score = 0;
  let pops = []; // "+1" floaters
  let sparks = [];
  let t = 0;
  let scrollX = 0;

  function spawnCart() {
    carts.push({ x: W + 14, y: GROUND_Y - rand(34, 84), v: 1.05 + rand(-0.2, 0.35), bob: rand(0, 6.28) });
  }

  function burst(x, y, color) {
    for (let i = 0; i < 7; i++) {
      sparks.push({ x, y, vx: rand(-1.6, 1.6), vy: rand(-2.4, 0.4), life: rand(16, 30), color });
    }
  }

  /* ── drawing helpers ── */
  function px(x, y, w, h, color) {
    ctx.fillStyle = color;
    ctx.fillRect(Math.round(x), Math.round(y), w, h);
  }

  function drawCartridge(c) {
    const bob = Math.sin(c.bob) * 3;
    const x = c.x, y = c.y + bob;
    px(x - 5, y - 7, 10, 12, C.cartridge);
    px(x - 5, y + 5, 10, 3, '#4F46E5');
    px(x - 3, y - 4, 6, 5, C.cartLabel);
    px(x - 2, y - 3, 1, 1, '#E2E8F0');
    px(x + 1, y - 1, 1, 1, '#E2E8F0');
    px(x - 2, y + 1, 2, 1, '#E2E8F0');
  }

  function drawHero() {
    const runBob = hero.onGround ? Math.sin(hero.frame * 0.32) * 1.5 : 0;
    const x = Math.round(hero.x), y = Math.round(hero.y + runBob);

    /* shadow */
    ctx.fillStyle = 'rgba(0,0,0,0.35)';
    ctx.fillRect(x - 7, GROUND_Y + 16, 14, 3);

    /* legs (2-frame run) */
    const step = hero.onGround ? (Math.floor(hero.frame * 0.32) % 2) : 0;
    if (step === 0) {
      px(x - 5, y + 10, 4, 6, '#3B4A6B');
      px(x + 1, y + 10, 4, 6, '#2C3852');
    } else {
      px(x - 6, y + 10, 4, 6, '#2C3852');
      px(x + 2, y + 10, 4, 6, '#3B4A6B');
    }

    /* body */
    px(x - 6, y, 12, 11, C.hero);
    px(x - 6, y, 12, 2, '#B9C2D8');
    /* scarf */
    px(x - 6, y + 2, 12, 2, C.heroScarf);
    px(x - 9 + Math.sin(hero.frame * 0.2) * 2, y + 2, 4, 2, C.heroScarf);
    /* backpack strap */
    px(x + 1, y + 4, 3, 6, '#4F46E5');
    /* arms */
    px(x - 8, y + 4, 3, 5, C.heroSkin);
    px(x + 5, y + 3, 3, 5, C.heroSkin);

    /* head */
    px(x - 6, y - 9, 12, 9, C.heroSkin);
    /* hair */
    px(x - 7, y - 11, 14, 4, C.heroHair);
    px(x - 7, y - 8, 3, 6, C.heroHair);
    px(x + 4, y - 8, 3, 6, C.heroHair);
    /* face — blink */
    const eyesShut = hero.blink > 0;
    if (!eyesShut) {
      px(x - 4, y - 5, 2, 2, '#1B2340');
      px(x + 2, y - 5, 2, 2, '#1B2340');
    } else {
      px(x - 4, y - 4, 2, 1, '#1B2340');
      px(x + 2, y - 4, 2, 1, '#1B2340');
    }
    px(x - 1, y - 2, 2, 1, '#D9776B'); /* smile */
  }

  function draw() {
    /* sky gradient */
    const g = ctx.createLinearGradient(0, 0, 0, H);
    g.addColorStop(0, C.skyTop);
    g.addColorStop(0.45, C.skyMid);
    g.addColorStop(0.72, C.skyLow);
    g.addColorStop(1, '#0A0D1E');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, W, H);

    /* stars */
    for (const s of stars) {
      const tw = 0.4 + 0.6 * Math.abs(Math.sin(s.tw));
      ctx.fillStyle = tw > 0.72 ? C.star : C.starDim;
      ctx.globalAlpha = tw;
      ctx.fillRect(Math.round(s.x), Math.round(s.y), s.s, s.s);
    }
    ctx.globalAlpha = 1;

    /* moon */
    ctx.fillStyle = C.moonGlow;
    ctx.fillRect(168, 52, 58, 58);
    px(186, 70, 22, 22, C.moon);
    px(191, 75, 4, 3, '#D9D4B8');
    px(200, 84, 3, 3, '#D9D4B8');
    px(192, 86, 2, 2, '#D9D4B8');

    /* clouds */
    for (const cl of clouds) {
      px(cl.x, cl.y, cl.w, cl.h, C.cloud);
      px(cl.x + 6, cl.y - 3, cl.w - 14, 3, C.cloud);
    }

    /* cities (parallax) */
    for (const b of cityFar) {
      const bx = ((b.x - scrollX * 0.22) % (W + 40) + W + 40) % (W + 40) - 20;
      px(bx, GROUND_Y - 54 - b.h, b.w, b.h + 54, C.cityFar);
      /* lit windows */
      for (let wy = 0; wy < Math.floor(b.h / 10); wy++) {
        for (let wx = 0; wx < 2; wx++) {
          if (((b.x + wy * 3 + wx * 7) | 0) % 3 === 0) {
            px(bx + 3 + wx * 7, GROUND_Y - 50 - b.h + wy * 10 + 2, 3, 4, (wy % 2) ? C.window : C.windowDim);
          }
        }
      }
    }
    for (const b of cityNear) {
      const bx = ((b.x - scrollX * 0.5) % (W + 44) + W + 44) % (W + 44) - 22;
      px(bx, GROUND_Y - 26 - b.h, b.w, b.h + 26, C.cityNear);
    }

    /* ground */
    px(0, GROUND_Y, W, H - GROUND_Y, C.ground);
    px(0, GROUND_Y, W, 3, C.groundTop);
    /* scrolling terrain stripes */
    for (let i = 0; i < W + 24; i += 24) {
      const gx = ((i - scrollX) % (W + 24) + W + 24) % (W + 24) - 12;
      px(gx, GROUND_Y + 6, 12, 3, C.grass);
      px(gx + 4, GROUND_Y + 6, 4, 3, C.grassLite);
      px(gx + 14, GROUND_Y + 16, 6, 2, C.grass);
    }
    /* pixel pebbles */
    for (let i = 0; i < 9; i++) {
      const rx = ((i * 37 - scrollX * 0.8) % (W + 20) + W + 20) % (W + 20) - 10;
      px(rx, GROUND_Y + 28 + (i % 3) * 22, 2, 2, '#1E2433');
    }

    /* cartridges */
    for (const c of carts) drawCartridge(c);

    /* hero */
    drawHero();

    /* sparks */
    for (const s of sparks) px(s.x, s.y, 2, 2, s.color);

    /* "+1" pops */
    ctx.font = '10px monospace';
    ctx.textAlign = 'center';
    for (const p of pops) {
      ctx.globalAlpha = Math.max(0, p.life / 40);
      ctx.fillStyle = C.one;
      ctx.fillText('+1', p.x, p.y);
    }
    ctx.globalAlpha = 1;

    /* score chip */
    ctx.font = 'bold 10px monospace';
    ctx.textAlign = 'left';
    ctx.fillStyle = 'rgba(7,8,11,0.65)';
    ctx.fillRect(10, H - 30, 86, 20);
    ctx.strokeStyle = 'rgba(44,51,69,1)';
    ctx.strokeRect(10.5, H - 29.5, 85, 19);
    ctx.fillStyle = '#34D399';
    ctx.fillText('APK ×' + score, 18, H - 16);
  }

  function update(dt) {
    t += dt;
    scrollX += 0.9 * dt;

    for (const s of stars) s.tw += 0.04 * s.sp * dt;
    for (const cl of clouds) {
      cl.x -= 0.06 * dt;
      if (cl.x < -70) cl.x = W + 20;
    }

    /* hero physics — auto-jump when a cartridge is near */
    hero.frame += 0.5 * dt;
    if (hero.blink > 0) hero.blink -= dt;
    else if (Math.random() < 0.006 * dt) hero.blink = 5;

    let wantJump = false;
    for (const c of carts) {
      if (c.x - hero.x < 74 && c.x - hero.x > 24 && c.y < GROUND_Y - 46) wantJump = true;
    }
    if (hero.onGround && wantJump) { hero.vy = JUMP; hero.onGround = false; }

    if (!hero.onGround) {
      hero.vy += GRAV * dt;
      hero.y += hero.vy * dt;
      if (hero.y >= GROUND_Y - 26) { hero.y = GROUND_Y - 26; hero.vy = 0; hero.onGround = true; }
    } else {
      hero.y = GROUND_Y - 26;
    }

    /* cartridges */
    spawnTimer -= dt;
    if (spawnTimer <= 0) { spawnCart(); spawnTimer = 95 + rand(0, 70); }
    for (const c of carts) {
      c.x -= c.v * dt;
      c.bob += 0.09 * dt;
      /* catch check */
      const dx = Math.abs(c.x - hero.x), dy = Math.abs(c.y + Math.sin(c.bob) * 3 - (hero.y - 4));
      if (dx < 12 && dy < 18) {
        c.dead = true;
        score++;
        burst(c.x, c.y, C.spark);
        pops.push({ x: c.x, y: c.y - 10, life: 40 });
      }
      if (c.x < -16) c.dead = true;
    }
    carts = carts.filter((c) => !c.dead);

    for (const s of sparks) {
      s.x += s.vx * dt; s.y += s.vy * dt; s.vy += 0.12 * dt; s.life -= dt;
    }
    sparks = sparks.filter((s) => s.life > 0);
    for (const p of pops) { p.y -= 0.5 * dt; p.life -= dt; }
    pops = pops.filter((p) => p.life > 0);
  }

  let last = performance.now();
  let visible = true;

  function loop(now) {
    const dt = Math.min((now - last) / 16.667, 3);
    last = now;
    if (visible) {
      if (reduced) { update(0); draw(); }
      else { update(dt); draw(); }
    }
    requestAnimationFrame(loop);
  }

  document.addEventListener('visibilitychange', () => { visible = !document.hidden; });

  hero.y = GROUND_Y - 26;
  draw();
  requestAnimationFrame(loop);
})();
