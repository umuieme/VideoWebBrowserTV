/*
 * Media agent. Injected by the app with addDocumentStartJavaScript into every frame (main page
 * and iframes, any origin) before the page's own scripts run.
 *
 * It finds this frame's <video> elements, reports the best one's state to the app through
 * window.vwbMedia (a WebMessageListener object), and accepts a tiny fixed set of commands:
 * togglePlay, play, pause, seekBy(seconds), getState. Anything else is ignored. It never
 * evaluates code and never navigates.
 *
 * Wire format (JSON strings, v = protocol version):
 *   to app:   {v:1, type:"state", playing, currentTime, duration|null, inFullscreen, area, ev}
 *             {v:1, type:"gone"}
 *   from app: {v:1, type:"cmd", cmd:"togglePlay"|"play"|"pause"|"getState"}
 *             {v:1, type:"cmd", cmd:"seekBy", seconds:<finite number>}
 */
(function () {
  'use strict';

  var bridge = window.vwbMedia;
  if (!bridge || typeof bridge.postMessage !== 'function') return;

  // Capture what we rely on now, before page scripts can replace globals or prototypes.
  var VERSION = 1;
  var MAX_SEEK = 600;
  var THROTTLE_MS = 1000;
  var stringify = JSON.stringify;
  var parse = JSON.parse;
  var postToApp = bridge.postMessage.bind(bridge);
  var mediaProto = HTMLMediaElement.prototype;
  var nativePlay = mediaProto.play;
  var nativePause = mediaProto.pause;
  var now = function () { return Date.now(); };

  var videos = new Set();
  var visibleArea = new WeakMap(); // video -> visible CSS px²
  var lastInteract = new WeakMap(); // video -> ms
  var reported = false;
  var lastSentAt = 0;
  var trailingTimer = 0;
  var pendingEv = null;

  var io = typeof IntersectionObserver === 'function'
    ? new IntersectionObserver(function (entries) {
        for (var i = 0; i < entries.length; i++) {
          var r = entries[i].intersectionRect;
          visibleArea.set(entries[i].target, r.width * r.height);
        }
        report('time', false);
      }, { threshold: [0, 0.1, 0.25, 0.5, 0.75, 1] })
    : null;

  // --- Tracking ---

  function track(v) {
    if (videos.has(v)) return;
    videos.add(v);
    if (io) io.observe(v);
    report('init', true);
  }

  function scan(root) {
    if (!root) return;
    if (root.nodeName === 'VIDEO') { track(root); return; }
    if (root.querySelectorAll) {
      var found = root.querySelectorAll('video');
      for (var i = 0; i < found.length; i++) track(found[i]);
    }
  }

  function prune() {
    videos.forEach(function (v) {
      if (!v.isConnected) {
        videos.delete(v);
        if (io) io.unobserve(v);
      }
    });
  }

  new MutationObserver(function (mutations) {
    var removed = false;
    for (var i = 0; i < mutations.length; i++) {
      var m = mutations[i];
      for (var j = 0; j < m.addedNodes.length; j++) scan(m.addedNodes[j]);
      if (m.removedNodes.length) removed = true;
    }
    if (removed) { prune(); report('time', false); }
  }).observe(document, { childList: true, subtree: true });

  // --- Selection: fullscreen > playing > largest visible > most recently interacted ---

  function fullscreenElement() {
    return document.fullscreenElement || document.webkitFullscreenElement || null;
  }

  function isPlaying(v) { return !v.paused && !v.ended; }

  function inFullscreen(v) {
    var fe = fullscreenElement();
    return !!fe && (fe === v || fe.contains(v));
  }

  function areaOf(v) {
    var a = visibleArea.get(v);
    if (typeof a === 'number') return a;
    var r = v.getBoundingClientRect(); // Before the first IntersectionObserver callback.
    return Math.max(0, r.width) * Math.max(0, r.height);
  }

  function score(v) {
    return [inFullscreen(v) ? 1 : 0, isPlaying(v) ? 1 : 0, areaOf(v), lastInteract.get(v) || 0];
  }

  function better(a, b) {
    for (var i = 0; i < a.length; i++) {
      if (a[i] !== b[i]) return a[i] > b[i];
    }
    return false;
  }

  function pick() {
    var best = null;
    var bestScore = null;
    videos.forEach(function (v) {
      if (!v.isConnected) return;
      var s = score(v);
      if (!best || better(s, bestScore)) { best = v; bestScore = s; }
    });
    return best;
  }

  // --- Reporting (throttled) ---

  function finite(x) { return typeof x === 'number' && isFinite(x) ? x : null; }

  function send(ev) {
    lastSentAt = now();
    pendingEv = null;
    var v = pick();
    if (!v) {
      if (reported) { reported = false; postToApp(stringify({ v: VERSION, type: 'gone' })); }
      return;
    }
    reported = true;
    var d = finite(v.duration);
    postToApp(stringify({
      v: VERSION,
      type: 'state',
      playing: isPlaying(v),
      currentTime: Math.max(0, finite(v.currentTime) || 0),
      duration: d !== null && d > 0 ? d : null,
      inFullscreen: inFullscreen(v),
      area: Math.round(areaOf(v)),
      ev: ev
    }));
  }

  /** Immediate for discrete events; at most once per THROTTLE_MS for time/layout updates. */
  function report(ev, immediate) {
    if (immediate) {
      if (trailingTimer) { clearTimeout(trailingTimer); trailingTimer = 0; }
      send(ev);
      return;
    }
    // A pending interesting event must not be downgraded to "time".
    if (!pendingEv || pendingEv === 'time') pendingEv = ev;
    var wait = THROTTLE_MS - (now() - lastSentAt);
    if (wait <= 0) {
      send(pendingEv);
    } else if (!trailingTimer) {
      trailingTimer = setTimeout(function () { trailingTimer = 0; send(pendingEv || 'time'); }, wait);
    }
  }

  // Media events don't bubble, but they do pass through document in the capture phase. That
  // also discovers videos the MutationObserver missed (e.g. ones that existed before it ran).
  function onMedia(ev, immediate) {
    return function (e) {
      var t = e.target;
      if (!t || t.nodeName !== 'VIDEO') return;
      track(t);
      if (ev !== 'time' && ev !== 'ended') lastInteract.set(t, now());
      report(ev, immediate);
    };
  }

  document.addEventListener('play', onMedia('play', true), true);
  document.addEventListener('pause', onMedia('pause', true), true);
  document.addEventListener('seeked', onMedia('seeked', true), true);
  document.addEventListener('ended', onMedia('ended', true), true);
  document.addEventListener('timeupdate', onMedia('time', false), true);
  document.addEventListener('durationchange', onMedia('time', false), true);

  function onFullscreenChange() { report('fullscreen', true); }
  document.addEventListener('fullscreenchange', onFullscreenChange, true);
  document.addEventListener('webkitfullscreenchange', onFullscreenChange, true);

  // A click or tap over a video (often on an overlay above it) marks it as the one in use.
  document.addEventListener('pointerdown', function (e) {
    videos.forEach(function (v) {
      var r = v.getBoundingClientRect();
      if (e.clientX >= r.left && e.clientX <= r.right && e.clientY >= r.top && e.clientY <= r.bottom) {
        lastInteract.set(v, now());
        report('interact', true);
      }
    });
  }, true);

  // The document is going away (navigation, reload, iframe removed): tell the app to forget us.
  window.addEventListener('pagehide', function () {
    if (reported) { reported = false; postToApp(stringify({ v: VERSION, type: 'gone' })); }
  }, true);
  // Restored from the back/forward cache: report again.
  window.addEventListener('pageshow', function (e) { if (e.persisted) report('init', true); }, true);

  document.addEventListener('DOMContentLoaded', function () { scan(document); }, true);

  // --- Commands from the app ---

  function play(v) {
    var p = nativePlay.call(v);
    if (p && typeof p.catch === 'function') p.catch(function () {});
  }

  function seekBy(v, seconds) {
    var target = (finite(v.currentTime) || 0) + seconds;
    var lo = 0;
    var hi = finite(v.duration);
    // Live streams have duration Infinity; their seekable window is the limit instead.
    if (v.seekable && v.seekable.length > 0) {
      lo = v.seekable.start(0);
      hi = v.seekable.end(v.seekable.length - 1);
    }
    if (hi !== null) target = Math.min(target, Math.max(lo, hi - 0.5));
    v.currentTime = Math.max(lo, target);
  }

  function onCommand(e) {
    var data = e && e.data;
    if (typeof data !== 'string' || data.length > 256) return;
    var m;
    try { m = parse(data); } catch (_) { return; }
    if (!m || typeof m !== 'object' || m.v !== VERSION || m.type !== 'cmd') return;

    if (m.cmd === 'getState') { report('init', true); return; }

    var v = pick();
    if (!v) return;
    switch (m.cmd) {
      case 'togglePlay':
        if (v.paused || v.ended) play(v); else nativePause.call(v);
        break;
      case 'play':
        play(v);
        break;
      case 'pause':
        nativePause.call(v);
        break;
      case 'seekBy':
        if (typeof m.seconds !== 'number' || !isFinite(m.seconds) || Math.abs(m.seconds) > MAX_SEEK) return;
        seekBy(v, m.seconds);
        break;
      default:
        return; // Unknown command: ignore.
    }
    lastInteract.set(v, now());
  }

  if (typeof bridge.addEventListener === 'function') {
    // addEventListener can't be undone by a page script assigning vwbMedia.onmessage.
    bridge.addEventListener('message', onCommand);
  } else {
    bridge.onmessage = onCommand;
  }

  scan(document);
})();
