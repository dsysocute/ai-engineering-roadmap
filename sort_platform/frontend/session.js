/* 学生登录态：每个标签页一份。
 *
 * 之前用 localStorage 存 token/身份，所有标签页共享同一份：
 * 你同时开「选手 / 观众 / 管理员」几个页面时，最后登录的那个会把别的标签页
 * 一起改掉，于是刷新一下就跳到观众界面、还要重新强刷。
 *
 * 现在的规则：
 *   - 本标签页自己的身份放 sessionStorage，刷新/后退都不会变；
 *   - localStorage 只作为「新开标签页的默认身份」，不会反过来覆盖已有标签页；
 *   - 页面之间跳转可以带 ?t=...&role=... 把身份交给新标签页，用完立刻从地址栏抹掉。
 */
(function () {
  var K = 'sort_platform_token';
  var R = 'sort_platform_role';
  var N = 'sort_platform_no';
  var U = 'sort_platform_username';
  var NK = 'sort_platform_nick';
  var KEYS = [K, R, N, U, NK];

  function sget(k) { try { return sessionStorage.getItem(k) || ''; } catch (e) { return ''; } }
  function sset(k, v) { try { sessionStorage.setItem(k, v == null ? '' : String(v)); } catch (e) { } }
  function lget(k) { try { return localStorage.getItem(k) || ''; } catch (e) { return ''; } }
  function lset(k, v) { try { localStorage.setItem(k, v == null ? '' : String(v)); } catch (e) { } }
  function ldel(k) { try { localStorage.removeItem(k); } catch (e) { } }

  var adopted = false;
  /** 把 URL 里的一次性令牌收进本标签页，并从地址栏删掉，避免被复制传播 */
  function adoptUrl() {
    if (adopted) return;
    adopted = true;
    try {
      var q = new URLSearchParams(location.search);
      var t = q.get('t');
      if (!t) return;
      sset(K, t);
      sset(R, q.get('role') || 'player');
      sset(N, q.get('no') || '');
      sset(U, q.get('u') || '');
      q.delete('t'); q.delete('role'); q.delete('no'); q.delete('u');
      var qs = q.toString();
      history.replaceState(null, '', location.pathname + (qs ? '?' + qs : '') + location.hash);
    } catch (e) { }
  }

  /** 本标签页还没有身份时，继承「最后一次登录」，之后本标签页就固定下来 */
  function ensure() {
    adoptUrl();
    if (sget(K)) return;
    var t = lget(K);
    if (!t) return;
    sset(K, t);
    sset(R, lget(R));
    sset(N, lget(N));
    sset(U, lget(U));
    sset(NK, lget(NK));
  }

  window.AppSession = {
    token: function () { ensure(); return sget(K); },
    role: function () { ensure(); return sget(R); },
    no: function () { ensure(); return sget(N); },
    username: function () { ensure(); return sget(U); },
    nick: function () { ensure(); return sget(NK); },
    isStudent: function () {
      var r = this.role();
      return !!this.token() && (r === 'player' || r === 'viewer');
    },
    set: function (token, role, no, username) {
      sset(K, token); sset(R, role); sset(N, no); sset(U, username);
      lset(K, token); lset(R, role); lset(N, no); lset(U, username);
      try { window.dispatchEvent(new Event('session-changed')); } catch (e) { }
    },
    setUsername: function (u) { sset(U, u); lset(U, u); },
    setNick: function (n) { sset(NK, n); lset(NK, n); },
    /** 退出：只清本标签页；只有本标签页就是“最后一次登录”时才清共享备份 */
    clear: function () {
      var mine = sget(K);
      for (var i = 0; i < KEYS.length; i++) {
        try { sessionStorage.removeItem(KEYS[i]); } catch (e) { }
      }
      if (!mine || lget(K) === mine) {
        for (var j = 0; j < KEYS.length; j++) ldel(KEYS[j]);
      }
      try { window.dispatchEvent(new Event('session-changed')); } catch (e) { }
    },
  };
})();
