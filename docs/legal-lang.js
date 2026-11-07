(function () {
  'use strict';

  /* ── 法律页本地化：浏览器语言自动跳转 + 手动切换偏好记忆 ──
     仅在 privacy*/terms* 页面加载；其余站点页不受影响。 */

  var SUFFIX = {
    'zh-CN': '',
    'en': '-en',
    'ja': '-ja',
    'ko': '-ko',
    'zh-TW': '-zh-TW',
    'zh-HK': '-zh-HK',
    'zh-MO': '-zh-MO'
  };

  /* 用户手动点过切换器后，偏好存 localStorage，优先于浏览器语言 */
  function preferredLang() {
    try {
      var saved = localStorage.getItem('legal-lang');
      if (saved && SUFFIX.hasOwnProperty(saved)) return saved;
    } catch (e) { /* 隐私模式等场景忽略 */ }

    var candidates = navigator.languages || [navigator.language || 'zh-CN'];
    for (var i = 0; i < candidates.length; i++) {
      var l = String(candidates[i]);
      if (/^en/i.test(l)) return 'en';
      if (/^ja/i.test(l)) return 'ja';
      if (/^ko/i.test(l)) return 'ko';
      var m = l.match(/^zh[-_](TW|HK|MO)/i);
      if (m) return 'zh-' + m[1].toUpperCase();
    }
    return 'zh-CN';
  }

  function currentLang() {
    var m = location.pathname.match(/-(en|ja|ko|zh-TW|zh-HK|zh-MO)\.html$/i);
    if (!m) return 'zh-CN';
    var hit = m[1].toLowerCase();
    for (var key in SUFFIX) {
      if (key.toLowerCase() === hit) return key;
    }
    return 'zh-CN';
  }

  function baseName() {
    var file = location.pathname.substring(location.pathname.lastIndexOf('/') + 1);
    file = file.replace(/-(en|ja|ko|zh-TW|zh-HK|zh-MO)\.html$/i, '');
    if (!/\.html$/.test(file)) file = file.replace(/\.html$/, '') || 'privacy';
    return file.replace(/\.html$/, '');
  }

  /* 切换器点击：记录偏好，让自动跳转尊重用户选择 */
  document.addEventListener('click', function (event) {
    var target = event.target;
    while (target && target !== document) {
      if (target.getAttribute && target.getAttribute('data-legal-lang')) {
        try { localStorage.setItem('legal-lang', target.getAttribute('data-legal-lang')); } catch (e) {}
        return;
      }
      target = target.parentNode;
    }
  });

  var current = currentLang();
  var want = preferredLang();
  if (want === current) return;

  /* 每个会话最多自动跳转一次，避免来回弹跳 */
  try {
    if (sessionStorage.getItem('legal-redirected')) return;
    sessionStorage.setItem('legal-redirected', '1');
  } catch (e) { /* 忽略 */ }

  location.replace(baseName() + SUFFIX[want] + '.html' + location.hash);
})();
