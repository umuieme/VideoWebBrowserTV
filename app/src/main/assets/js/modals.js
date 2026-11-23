// Shared helpers for finding site popups (modals). Not run on its own: PageScripts places this
// file and one entry script (scroll-locator.js or close-modal.js) inside one function wrapper,
// so these declarations stay private to that call and never touch the page's globals.

// Common markers of an open popup: native <dialog>, ARIA dialogs, and the class names used by
// Bootstrap (.show / .in), Foundation and many hand-made popups (.open / .active).
var VWB_MODAL_SELECTOR = [
  'dialog[open]',
  '[role="dialog"]',
  '[role="alertdialog"]',
  '[aria-modal="true"]',
  '.modal.show',
  '.modal.open',
  '.modal.active',
  '.modal.in'
].join(',');

// True if the element takes visible space inside the viewport. Hidden popups often stay in the
// DOM (display:none, visibility:hidden, opacity:0, or parked off-screen) and must be skipped.
function vwbIsVisible(el) {
  var r = el.getBoundingClientRect();
  if (r.width < 2 || r.height < 2) return false;
  if (r.bottom <= 0 || r.right <= 0 || r.top >= window.innerHeight || r.left >= window.innerWidth) return false;
  if (getComputedStyle(el).visibility === 'hidden') return false; // inherited, so one check
  for (var e = el; e && e.nodeType === 1; e = e.parentElement) {
    if (parseFloat(getComputedStyle(e).opacity) === 0) return false; // not inherited: walk up
  }
  return true;
}

// The part of the element's rect that is on screen, or null if none.
function vwbVisibleRect(el) {
  var r = el.getBoundingClientRect();
  var left = Math.max(r.left, 0), top = Math.max(r.top, 0);
  var right = Math.min(r.right, window.innerWidth), bottom = Math.min(r.bottom, window.innerHeight);
  if (right - left < 1 || bottom - top < 1) return null;
  return { left: left, top: top, right: right, bottom: bottom };
}

// True if nothing covers the element at the centre of its visible part: it is on top.
function vwbIsOnTop(el) {
  var v = vwbVisibleRect(el);
  if (!v) return false;
  var hit = document.elementFromPoint((v.left + v.right) / 2, (v.top + v.bottom) / 2);
  return !!hit && (hit === el || el.contains(hit));
}

// The topmost visible open popup, or null. A popup nested in another candidate (Bootstrap's
// .modal[role=dialog] > .modal-dialog[role=document]...) counts once, as the outermost element,
// so its close button is found wherever it sits.
function vwbTopModal() {
  var list = document.querySelectorAll(VWB_MODAL_SELECTOR);
  var best = null;
  for (var i = 0; i < list.length; i++) {
    var el = list[i];
    if (best && best.contains(el)) continue;
    if (!vwbIsVisible(el) || !vwbIsOnTop(el)) continue;
    best = el; // Later in document order paints above earlier ones with equal stacking.
  }
  return best;
}
