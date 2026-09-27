/* ═══════════════════════════════════════════════════════════════
   RETROPACK · PRODUCT PAGE — interactions.js
   Nav state · smooth anchors · scroll progress · custom cursor ·
   magnetic buttons · 3D tilt · count-ups · reveals · shader lab ·
   DMG phosphor mode · copy-to-clipboard · marquee cloning.
   ═══════════════════════════════════════════════════════════════ */
(function () {
  'use strict';

  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const finePointer = window.matchMedia('(hover: hover) and (pointer: fine)').matches;

  /* ── nav: scrolled state + progress bar ─────────────────── */
  const nav = document.getElementById('nav');
  const progress = document.getElementById('scrollProgress');

  let scrollTicking = false;
  function onScroll() {
    if (scrollTicking) return;
    scrollTicking = true;
    requestAnimationFrame(() => {
      const y = window.scrollY;
      nav.classList.toggle('is-scrolled', y > 24);
      const max = document.documentElement.scrollHeight - window.innerHeight;
      progress.style.width = (max > 0 ? (y / max) * 100 : 0) + '%';
      scrollTicking = false;
    });
  }
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();

  /* ── nav: active section highlighting ───────────────────── */
  const navLinks = Array.from(document.querySelectorAll('[data-nav-link]'));
  const sections = navLinks
    .map((a) => document.querySelector(a.getAttribute('href')))
    .filter(Boolean);

  if ('IntersectionObserver' in window && sections.length) {
    const sectionIO = new IntersectionObserver((entries) => {
      entries.forEach((e) => {
        if (e.isIntersecting) {
          navLinks.forEach((a) =>
            a.classList.toggle('is-active', a.getAttribute('href') === '#' + e.target.id));
        }
      });
    }, { rootMargin: '-38% 0px -55% 0px' });
    sections.forEach((s) => sectionIO.observe(s));
  }

  /* ── nav: mobile toggle ─────────────────────────────────── */
  const navToggle = document.getElementById('navToggle');
  const navMenu = document.getElementById('navMenu');
  if (navToggle && navMenu) {
    navToggle.addEventListener('click', () => {
      const open = navMenu.classList.toggle('open');
      navToggle.setAttribute('aria-expanded', String(open));
      document.body.style.overflow = open ? 'hidden' : '';
    });
    navMenu.querySelectorAll('a').forEach((a) => {
      a.addEventListener('click', () => {
        navMenu.classList.remove('open');
        navToggle.setAttribute('aria-expanded', 'false');
        document.body.style.overflow = '';
      });
    });
  }

  /* ── custom cursor ──────────────────────────────────────── */
  if (finePointer && !reduced) {
    const dot = document.querySelector('.cursor-dot');
    const ring = document.querySelector('.cursor-ring');
    let mx = -100, my = -100, rx = -100, ry = -100;

    window.addEventListener('pointermove', (e) => {
      mx = e.clientX; my = e.clientY;
      if (!document.body.classList.contains('has-cursor')) {
        document.body.classList.add('has-cursor');
        rx = mx; ry = my;
      }
    }, { passive: true });

    (function cursorLoop() {
      rx += (mx - rx) * 0.16;
      ry += (my - ry) * 0.16;
      dot.style.transform = `translate(${mx - 3}px, ${my - 3}px)`;
      const size = ring.offsetWidth / 2;
      ring.style.transform = `translate(${rx - size}px, ${ry - size}px)`;
      requestAnimationFrame(cursorLoop);
    })();

    document.querySelectorAll('a, button, [data-tilt], .shader-chip').forEach((el) => {
      el.addEventListener('pointerenter', () => document.body.classList.add('cursor-hover'));
      el.addEventListener('pointerleave', () => document.body.classList.remove('cursor-hover'));
    });
  }

  /* ── magnetic buttons ───────────────────────────────────── */
  if (finePointer && !reduced) {
    document.querySelectorAll('[data-magnetic]').forEach((el) => {
      const strength = 0.28;
      el.addEventListener('pointermove', (e) => {
        const r = el.getBoundingClientRect();
        const x = (e.clientX - r.left - r.width / 2) * strength;
        const y = (e.clientY - r.top - r.height / 2) * strength;
        el.style.transform = `translate(${x}px, ${y}px)`;
      });
      el.addEventListener('pointerleave', () => {
        el.style.transform = '';
      });
    });
  }

  /* ── 3D tilt cards ──────────────────────────────────────── */
  if (finePointer && !reduced) {
    document.querySelectorAll('[data-tilt]').forEach((el) => {
      let raf = null;
      el.addEventListener('pointermove', (e) => {
        if (raf) return;
        raf = requestAnimationFrame(() => {
          const r = el.getBoundingClientRect();
          const px = (e.clientX - r.left) / r.width - 0.5;
          const py = (e.clientY - r.top) / r.height - 0.5;
          el.style.transform = `perspective(900px) rotateX(${(-py * 6).toFixed(2)}deg) rotateY(${(px * 8).toFixed(2)}deg) translateY(-4px)`;
          raf = null;
        });
      });
      el.addEventListener('pointerleave', () => {
        el.style.transform = '';
      });
    });
  }

  /* ── scroll reveals + counters + ring ───────────────────── */
  const counters = Array.from(document.querySelectorAll('[data-counter]'));
  const revealEls = Array.from(document.querySelectorAll('[data-reveal]'));

  function runCounter(span) {
    const target = parseInt(span.dataset.counter, 10) || 0;
    if (reduced || target === 0) { span.textContent = String(target); return; }
    const dur = 1500;
    const start = performance.now();
    function tick(now) {
      const p = Math.min((now - start) / dur, 1);
      const eased = 1 - Math.pow(1 - p, 4);
      span.textContent = String(Math.round(target * eased));
      if (p < 1) requestAnimationFrame(tick);
    }
    requestAnimationFrame(tick);
  }

  if ('IntersectionObserver' in window) {
    const revealIO = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        const el = entry.target;
        el.classList.add('is-revealed');
        el.querySelectorAll('[data-counter]').forEach(runCounter);
        if (el.matches('[data-counter]')) runCounter(el);
        revealIO.unobserve(el);
      });
    }, { threshold: 0.18, rootMargin: '0px 0px -6% 0px' });

    revealEls.forEach((el) => revealIO.observe(el));

    /* counters living inside already-revealed containers */
    counters.forEach((span) => {
      if (!span.closest('[data-reveal]')) revealIO.observe(span);
    });
  } else {
    revealEls.forEach((el) => el.classList.add('is-revealed'));
    counters.forEach(runCounter);
  }

  /* ── shader lab ─────────────────────────────────────────── */
  const shaderScreen = document.getElementById('shaderPreview');
  const shaderChips = Array.from(document.querySelectorAll('.shader-chip'));
  if (shaderScreen && shaderChips.length) {
    shaderChips.forEach((chip) => {
      chip.addEventListener('click', () => {
        const mode = chip.dataset.shader;
        shaderScreen.dataset.shader = mode;
        shaderChips.forEach((c) => {
          const active = c === chip;
          c.classList.toggle('is-active', active);
          c.setAttribute('aria-selected', String(active));
        });
      });
    });
  }

  /* ── DMG phosphor mode ──────────────────────────────────── */
  const dmgToggle = document.getElementById('dmgToggle');
  const dmgHint = document.getElementById('dmgHint');

  function setDmg(on) {
    document.documentElement.classList.toggle('dmg', on);
    document.body.dataset.dmg = on ? 'on' : 'off';
    if (dmgToggle) dmgToggle.setAttribute('aria-pressed', String(on));
  }

  if (dmgToggle) {
    dmgToggle.addEventListener('click', () => {
      setDmg(!document.documentElement.classList.contains('dmg'));
    });
  }
  if (dmgHint) {
    dmgHint.addEventListener('click', () => {
      setDmg(true);
      window.scrollTo({ top: 0, behavior: reduced ? 'auto' : 'smooth' });
    });
  }

  /* ── copy clone command ─────────────────────────────────── */
  const copyBtn = document.getElementById('copyClone');
  const copyCmd = document.getElementById('cloneCmd');
  const copyToast = document.getElementById('copyToast');

  if (copyBtn && copyCmd) {
    copyBtn.addEventListener('click', async () => {
      const text = copyCmd.textContent.trim();
      try {
        await navigator.clipboard.writeText(text);
      } catch {
        const ta = document.createElement('textarea');
        ta.value = text;
        ta.style.position = 'fixed';
        ta.style.opacity = '0';
        document.body.appendChild(ta);
        ta.select();
        try { document.execCommand('copy'); } catch {}
        ta.remove();
      }
      if (copyToast) {
        copyToast.classList.add('show');
        setTimeout(() => copyToast.classList.remove('show'), 1600);
      }
    });
  }

  /* ── marquee: clone content for a seamless loop ─────────── */
  document.querySelectorAll('.marquee-track').forEach((track) => {
    track.innerHTML += track.innerHTML;
  });

  /* ── hero glitch: extra burst on hover ──────────────────── */
  const glitch = document.querySelector('.glitch');
  if (glitch && !reduced) {
    glitch.addEventListener('pointerenter', () => {
      glitch.style.animation = 'none';
      const before = glitch; // re-trigger by toggling a class
      glitch.classList.remove('glitch-burst');
      void glitch.offsetWidth;
      glitch.classList.add('glitch-burst');
    });
  }
})();
