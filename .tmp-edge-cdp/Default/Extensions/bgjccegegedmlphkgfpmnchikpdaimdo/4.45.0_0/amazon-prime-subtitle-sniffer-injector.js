(function () {
  function injectScript(src, callback) {
    const s = document.createElement('script');
    s.src = chrome.runtime.getURL(src);
    s.onload = () => {
      s.remove();
      callback?.();
    };
    (document.head || document.documentElement).append(s);
  }
  injectScript('amazon-prime-subtitle-sniffer.js');
})();
