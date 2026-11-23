// Scroll mode: decides WHERE the native wheel events of one scroll gesture should go. Run once
// per gesture with evaluateJavascript (main frame only), together with modals.js.
//
// Input:  { fx, fy, dx, dy } - the pointer as fractions of innerWidth/innerHeight, and the
//         gesture direction per axis (-1, 0, +1; positive = down/right).
// Output: { kind: "point" | "modal" | "page", fx, fy } - where to send the wheel events, again
//         as fractions. Fractions, not pixels: CSS pixels, device pixels and the WebView's view
//         pixels differ by the page zoom and devicePixelRatio, but a fraction of the viewport is
//         the same number on both sides, so Kotlin just multiplies by the view's size.
//
// The script never scrolls anything itself; Kotlin sends real wheel events to the point.
function vwbLocateScrollTarget(args) {
  var w = window.innerWidth, h = window.innerHeight;
  var dx = args.dx, dy = args.dy;

  function scrollableStyle(value) {
    return value === 'auto' || value === 'scroll' || value === 'overlay';
  }

  // Can this element scroll further in the gesture's direction?
  function canScroll(el) {
    var style = getComputedStyle(el);
    if (dy !== 0 && scrollableStyle(style.overflowY) && el.scrollHeight > el.clientHeight + 1) {
      if (dy > 0 ? el.scrollTop + el.clientHeight < el.scrollHeight - 1 : el.scrollTop > 0) return true;
    }
    if (dx !== 0 && scrollableStyle(style.overflowX) && el.scrollWidth > el.clientWidth + 1) {
      // Math.abs: in right-to-left layouts scrollLeft runs from 0 down to negative values.
      var left = Math.abs(el.scrollLeft);
      if (dx > 0 ? left + el.clientWidth < el.scrollWidth - 1 : left > 0) return true;
    }
    return false;
  }

  function parentOf(el) {
    if (el.parentElement) return el.parentElement;
    var root = el.getRootNode && el.getRootNode();
    return root && root.host ? root.host : null; // step out of a shadow DOM
  }

  function result(kind, x, y) {
    return { kind: kind, fx: x / w, fy: y / h };
  }

  // (a) Something under the pointer can scroll that way, or the pointer is over an iframe
  //     (possibly cross-origin, so we can't look inside; the wheel event can).
  var px = args.fx * w, py = args.fy * h;
  var hit = document.elementFromPoint(px, py);
  if (hit) {
    if (hit.tagName === 'IFRAME' || hit.tagName === 'FRAME') return result('point', px, py);
    for (var el = hit; el && el !== document.body && el !== document.documentElement; el = parentOf(el)) {
      if (canScroll(el)) return result('point', px, py);
    }
  }

  // (b) A popup is open: scroll its largest scrollable part, wherever the pointer is. Covers
  //     the pointer resting on the dimmed backdrop next to a dialog.
  var modal = vwbTopModal();
  if (modal) {
    var candidates = [modal].concat(Array.prototype.slice.call(modal.querySelectorAll('*'), 0, 3000));
    var best = null, bestArea = 0;
    for (var i = 0; i < candidates.length; i++) {
      var c = candidates[i];
      if (!canScroll(c)) continue;
      var v = vwbVisibleRect(c);
      if (!v) continue;
      var area = (v.right - v.left) * (v.bottom - v.top);
      if (area > bestArea) { best = v; bestArea = area; }
    }
    if (best) return result('modal', (best.left + best.right) / 2, (best.top + best.bottom) / 2);
  }

  // (c) The page itself, at the pointer (wheel events chain up to the document scroller).
  return result('page', px, py);
}
