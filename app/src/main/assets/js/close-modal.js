// Back: tries to close the topmost site popup. Run with evaluateJavascript (main frame only),
// together with modals.js.
//
// Input:  { stuckKey } - key of the popup Kotlin tried to close less than ~2.5 s ago, or null.
// Output: { result, key }
//   "clicked" - clicked a visible close button inside the popup
//   "closed"  - closed a native <dialog>
//   "esc"     - found a popup with no close control; Kotlin sends a native Escape key
//   "stuck"   - the topmost popup is the one we just tried to close: it ignored us, so Kotlin
//               goes back in history instead (Back can never get stuck on a popup)
//   "none"    - no popup; Kotlin goes back in history
// `key` identifies the popup element across Back presses (a data attribute we stamp on it).
function vwbCloseTopModal(args) {
  var modal = vwbTopModal();
  if (!modal) return { result: 'none' };

  if (!modal.dataset.vwbModal) {
    window.__vwbModalSeq = (window.__vwbModalSeq || 0) + 1;
    modal.dataset.vwbModal = String(window.__vwbModalSeq);
  }
  var key = modal.dataset.vwbModal;
  // Checked before clicking: clicking a fake close button again would just keep us stuck.
  if (args.stuckKey && args.stuckKey === key) return { result: 'stuck', key: key };

  // Most specific first. The `i` flag makes the attribute match case-insensitive ("Close").
  var selectors = [
    '[aria-label*="close" i]',
    '[title*="close" i]',
    '.close',
    '.btn-close',
    '.modal-close',
    '[data-dismiss]',
    '[data-bs-dismiss]'
  ];
  for (var s = 0; s < selectors.length; s++) {
    var buttons = modal.querySelectorAll(selectors[s]);
    for (var b = 0; b < buttons.length; b++) {
      if (vwbIsVisible(buttons[b])) {
        buttons[b].click();
        return { result: 'clicked', key: key };
      }
    }
  }

  if (modal.tagName === 'DIALOG' && typeof modal.close === 'function') {
    modal.close();
    return { result: 'closed', key: key };
  }
  return { result: 'esc', key: key };
}
