// Debug builds only: evaluated after each page load by BrowserWebViewClient.
// Reports the viewport size and pixel ratio websites see on this device, so later milestones
// (pointer speed, scroll step) can be calibrated. The object's JSON is logged to Logcat.
(function () {
  return {
    innerWidth: window.innerWidth,
    innerHeight: window.innerHeight,
    devicePixelRatio: window.devicePixelRatio,
    screenWidth: window.screen.width,
    screenHeight: window.screen.height
  };
})();
