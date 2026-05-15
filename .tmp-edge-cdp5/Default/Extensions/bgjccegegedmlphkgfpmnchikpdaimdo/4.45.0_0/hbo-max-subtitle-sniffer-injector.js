(function () {
  if (!document.querySelector('script[data-ref="HBO_MAX2"]')) {
    function injectScript(src, callback) {
      console.debug('HBO MAX: Initializing');
      const s = document.createElement('script');
      s.src = chrome.runtime.getURL(src);
      s.dataset.ref = 'HBO_MAX2';
      s.onload = () => {
        console.debug('HBO MAX: Initializer loaded');
        s.remove();
        callback?.();
      };
      (document.head || document.documentElement).append(s);
    }
    injectScript('hbo-max-subtitle-sniffer.js');
  }
})();
