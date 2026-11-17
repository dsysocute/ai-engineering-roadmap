/* 右上角虚拟头像与菜单：修改ID（昵称）、修改密码、我要举报、我要申诉、登出账户。
   首页与观赛页共用，由页面给出挂载点 data-avatar-anchor。 */
(function () {
  // 头像菜单的样式自带一份：观赛页不引用 style.css，菜单会退化成裸按钮堆在页面上
  const CSS = `
.av-btn{width:30px;height:30px;padding:0;border-radius:50%;border:1px solid #3a4654;
  background:linear-gradient(145deg,#2f81f7,#1f6feb);cursor:pointer;display:inline-flex;
  align-items:center;justify-content:center;margin-right:8px;vertical-align:middle}
.av-face{font-size:12px;font-weight:700;color:#fff;letter-spacing:.5px}
/* 工作人员头像：裁判显示学号，总管/管理员显示头衔，胶囊形状装得下两个字 */
.av-btn.staff{width:auto;min-width:30px;padding:0 9px;border-radius:15px;cursor:default;
  background:linear-gradient(145deg,#d29922,#b8860b);font-size:11px;font-weight:700;color:#fff}
.av-menu{position:fixed;z-index:1200;width:150px;padding:6px;background:#161b22;
  border:1px solid #242c37;border-radius:8px;box-shadow:0 12px 30px rgba(0,0,0,.45);
  display:flex;flex-direction:column}
.av-menu.hidden{display:none}
.av-menu button{display:block;width:100%;padding:8px 10px;text-align:left;font-size:12.5px;
  color:#c8d1da;background:transparent;border:none;border-radius:5px;cursor:pointer}
.av-menu button:hover{background:#1f2731;color:#fff}
.av-menu button.danger{color:#f85149}
.av-modal{position:fixed;inset:0;z-index:1100;background:rgba(3,6,10,.72);
  display:flex;align-items:center;justify-content:center}
.av-card{width:420px;max-width:calc(100vw - 32px);max-height:calc(100vh - 40px);overflow:auto;
  background:#0d1117;border:1px solid #242c37;
  border-radius:10px;box-shadow:0 24px 60px rgba(0,0,0,.55)}
.av-card-head{display:flex;align-items:center;justify-content:space-between;padding:12px 16px;
  border-bottom:1px solid #242c37;color:#e6edf3;font-size:13.5px}
.av-x{cursor:pointer;color:#8a94a3;font-size:18px;line-height:1}
.av-card-body{padding:14px 16px 16px}
.av-tip{margin:0 0 10px;font-size:12px;color:#8a94a3;line-height:1.6}
.av-input{width:100%;box-sizing:border-box;margin-bottom:8px;padding:8px 10px;font-size:12.5px;
  color:#e6edf3;background:#161b22;border:1px solid #30363d;border-radius:6px;font-family:inherit}
textarea.av-input{resize:vertical}
.av-actions{display:flex;gap:8px;margin-top:4px}
.av-ok,.av-cancel{padding:7px 14px;font-size:12.5px;border-radius:6px;cursor:pointer;border:1px solid #30363d}
.av-ok{color:#fff;background:#2f81f7;border-color:#2f81f7}
.av-cancel{color:#c8d1da;background:#161b22}
.av-msg{min-height:16px;margin:8px 0 0;font-size:12px;color:#f85149}
.av-msg.ok{color:#3fb950}
.av-hist{margin-top:12px;border-top:1px solid #242c37;padding-top:10px;font-size:12px;color:#c8d1da}
.av-row{display:flex;justify-content:space-between;gap:10px;padding:4px 0;color:#8a94a3}
`;
  const style = document.createElement('style');
  style.textContent = CSS;
  document.head.appendChild(style);

  // 登录态在「本标签页」里：登录后由 app.js 广播 session-changed，这里重新挂载，
  // 不再需要在登录后手动强刷一次页面。
  const S = window.AppSession;

  function tok() {
    return S ? S.token() : (localStorage.getItem('sort_platform_token') || '');
  }
  function role() {
    return S ? S.role() : (localStorage.getItem('sort_platform_role') || '');
  }

  function api(path, body, method) {
    // GET 请求不能带 body，否则浏览器直接抛 TypeError，头像菜单会一直停在「正在加载」
    const opts = {
      method: method || 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8', 'X-Token': tok() },
    };
    if (opts.method !== 'GET' && body) opts.body = body;
    return fetch(path, opts).then((r) => r.json().then((d) => ({ ok: r.ok, data: d })));
  }

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  }

  let overlay = null;
  let reportTimer = null;
  function close() {
    if (reportTimer) { clearInterval(reportTimer); reportTimer = null; }
    if (overlay) { overlay.remove(); overlay = null; }
  }
  function modal(title, bodyHtml, onMount) {
    close();
    overlay = document.createElement('div');
    overlay.className = 'av-modal';
    overlay.innerHTML = '<div class="av-card"><div class="av-card-head"><b>' + esc(title) +
      '</b><span class="av-x" title="关闭">×</span></div><div class="av-card-body">' + bodyHtml + '</div></div>';
    document.body.appendChild(overlay);
    overlay.addEventListener('click', (e) => {
      // 取消按钮以前没人接管，点了没反应（举报框还会一直轮询）
      if (e.target === overlay || e.target.classList.contains('av-x') || e.target.classList.contains('av-cancel')) {
        close();
      }
    });
    if (onMount) onMount(overlay);
  }

  let mounted = false;
  function unmount() {
    document.querySelectorAll('.av-btn, .av-menu').forEach((n) => n.remove());
    mounted = false;
  }

  function mount() {
    // 工作人员视角（裁判/总管/管理员从控制台带令牌进观赛页）：
    // 不要用浏览器里残留的学生会话挂头像，身份由 watch.html 按令牌渲染
    if (window.__STAFF_VIEW__) {
      unmount();
      return;
    }
    // 身份不是选手/观众（比如裁判/管理员的控制台页面）就不显示学生菜单
    if (!tok() || (role() !== 'player' && role() !== 'viewer')) {
      unmount();
      return;
    }
    if (mounted) return;
    const anchor = document.getElementById('avAnchor');
    if (!anchor) return;
    mounted = true;
    const no = S ? S.no() : (localStorage.getItem('sort_platform_no') || '');
    const btn = document.createElement('button');
    btn.className = 'av-btn';
    btn.title = '账户菜单';
    btn.innerHTML = '<span class="av-face">' + esc(String(no).slice(-2) || '?') + '</span>';
    anchor.parentNode.insertBefore(btn, anchor);

    const menu = document.createElement('div');
    menu.className = 'av-menu hidden';
    menu.innerHTML =
      '<button data-a="nick">修改ID</button>' +
      '<button data-a="pass">修改密码</button>' +
      '<button data-a="report">我要举报</button>' +
      '<button data-a="appeal">我要申诉</button>' +
      '<button data-a="logout" class="danger">登出账户</button>';
    document.body.appendChild(menu);

    function place() {
      const r = btn.getBoundingClientRect();
      menu.style.top = (r.bottom + 6) + 'px';
      // 从头像的左边缘向下展开（右下方向），靠近屏幕右侧时再往回收，避免超出视口
      menu.style.left = Math.min(r.left, window.innerWidth - 158) + 'px';
    }
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      place();
      menu.classList.toggle('hidden');
    });
    document.addEventListener('click', () => menu.classList.add('hidden'));

    menu.addEventListener('click', (e) => {
      const a = e.target.getAttribute('data-a');
      if (!a) return;
      menu.classList.add('hidden');
      if (a === 'nick') nickDialog();
      if (a === 'pass') passDialog();
      if (a === 'report') reportDialog();
      if (a === 'appeal') appealDialog();
      if (a === 'logout') {
        fetch('/api/logout', { method: 'POST', headers: { 'X-Token': tok() } }).catch(() => {});
        if (S) {
          S.clear();
        } else {
          localStorage.removeItem('sort_platform_token');
          localStorage.removeItem('sort_platform_role');
          localStorage.removeItem('sort_platform_no');
        }
        location.href = '/';
      }
    });
  }

  function nickDialog() {
    const cur = S ? S.nick() : (localStorage.getItem('sort_platform_nick') || '');
    modal('修改ID（显示昵称）',
      '<p class="av-tip">学号账户不能改，这里只改页面上显示的昵称，留空即恢复原样。</p>' +
      '<input id="avNick" class="av-input" maxlength="10" placeholder="最多 10 个字" value="' + esc(cur) + '">' +
      '<div class="av-actions"><button class="av-ok" id="avNickOk">保存</button><button class="av-cancel">取消</button></div>' +
      '<p class="av-msg" id="avMsg"></p>', (box) => {
        box.querySelector('#avNickOk').onclick = async () => {
          const nick = box.querySelector('#avNick').value.trim();
          const r = await api('/api/user/nick', 'nick=' + encodeURIComponent(nick));
          if (!r.ok) { box.querySelector('#avMsg').textContent = r.data.error || '保存失败'; return; }
          if (S) S.setNick(nick); else localStorage.setItem('sort_platform_nick', nick);
          close();
          location.reload();
        };
      });
  }

  async function passDialog() {
    const username = S ? S.username() : (localStorage.getItem('sort_platform_username') || '');
    // 已经设置过密保的账号只改密码；没设置过的要先补两道，和登录页的弹窗保持一致
    let hasSec = true;
    try {
      const q = await fetch('/api/reset/quiz?username=' + encodeURIComponent(username));
      hasSec = q.ok;
    } catch (e) {
      hasSec = false;
    }
    modal('修改密码',
      '<p class="av-tip">账号（学号）不可修改；首次修改密码需要同时设置两道密保问题。</p>' +
      '<input id="avUser" class="av-input" type="text" readonly value="' + esc(username) + '" placeholder="账号">' +
      '<input id="avOld" class="av-input" type="password" placeholder="原密码">' +
      '<input id="avNew" class="av-input" type="password" placeholder="新密码 4~20 位">' +
      '<div id="avSecBox"' + (hasSec ? ' style="display:none"' : '') + '>' +
      '<input id="avQ1" class="av-input" type="text" placeholder="密保问题一，例如：我的小学班主任姓什么">' +
      '<input id="avA1" class="av-input" type="text" placeholder="答案一">' +
      '<input id="avQ2" class="av-input" type="text" placeholder="密保问题二，例如：我出生的城市是哪里">' +
      '<input id="avA2" class="av-input" type="text" placeholder="答案二">' +
      '</div>' +
      '<div class="av-actions"><button class="av-ok" id="avPassOk">确认修改</button><button class="av-cancel">取消</button></div>' +
      '<p class="av-msg" id="avMsg"></p>', (box) => {
        const btn = box.querySelector('#avPassOk');
        const msg = box.querySelector('#avMsg');
        box.querySelector('#avOld').focus();
        btn.onclick = async () => {
          const user = box.querySelector('#avUser').value.trim();
          const oldPass = box.querySelector('#avOld').value.trim();
          const newPass = box.querySelector('#avNew').value.trim();
          const needSec = box.querySelector('#avSecBox').style.display !== 'none';
          if (!user) { msg.textContent = '请重新登录后再修改密码'; return; }
          if (!oldPass || !newPass) { msg.textContent = '原密码、新密码都要填'; return; }
          let body = 'username=' + encodeURIComponent(user) +
            '&oldPass=' + encodeURIComponent(oldPass) + '&newPass=' + encodeURIComponent(newPass);
          if (needSec) {
            const q1 = box.querySelector('#avQ1').value.trim();
            const a1 = box.querySelector('#avA1').value.trim();
            const q2 = box.querySelector('#avQ2').value.trim();
            const a2 = box.querySelector('#avA2').value.trim();
            if (!q1 || !a1 || !q2 || !a2) { msg.textContent = '首次修改密码，两道密保问题和答案都要填'; return; }
            body += '&q1=' + encodeURIComponent(q1) + '&a1=' + encodeURIComponent(a1) +
              '&q2=' + encodeURIComponent(q2) + '&a2=' + encodeURIComponent(a2);
          }
          msg.className = 'av-msg';
          msg.textContent = '提交中…';
          btn.disabled = true;
          btn.textContent = '提交中…';
          try {
            const r = await api('/api/user/pass', body);
            if (!r.ok) {
              msg.textContent = r.data.error || '修改失败';
              btn.disabled = false;
              btn.textContent = '确认修改';
              return;
            }
            msg.className = 'av-msg ok';
            msg.textContent = '密码已修改，下次登录请用新密码';
            btn.textContent = '已修改';
            setTimeout(close, 1200);
          } catch (e) {
            msg.textContent = '网络异常，请稍后再试';
            btn.disabled = false;
            btn.textContent = '确认修改';
          }
        };
      });
  }

  async function reportDialog() {
    modal('我要举报', '<p class="av-msg">正在加载可举报的账号…</p>', async (box) => {
      const r = await api('/api/case/targets', '', 'GET');
      if (!r.ok) { box.querySelector('.av-msg').textContent = r.data.error || '加载失败'; return; }
      const items = r.data.items || [];
      if (!items.length) {
        box.querySelector('.av-msg').textContent = '当前没有可举报的账号';
        return;
      }
      box.querySelector('.av-card-body').innerHTML =
        '<p class="av-tip">举报会按类型分派：违规交给裁判与总管，不当言论交给管理员与总管。<br>' +
        '名单实时更新，比赛中有选手登录后会出现在「选手」分组里。</p>' +
        '<select id="avTarget" class="av-input"></select>' +
        '<select id="avType" class="av-input"><option value="violation">违规（交头接耳、徇私舞弊等）</option>' +
        '<option value="speech">不当言论（辱骂、攻击他人）</option></select>' +
        '<textarea id="avReason" class="av-input" rows="4" maxlength="120" placeholder="写清时间、现象，最多 120 字"></textarea>' +
        '<div class="av-actions"><button class="av-ok" id="avRepOk">提交举报</button><button class="av-cancel">取消</button></div>' +
        '<p class="av-msg" id="avMsg"></p>';
      const sel = box.querySelector('#avTarget');

      // 选手排前面、观众排后面；有人登录/退出身份，3 秒内自动反映到这里
      function paint(list) {
        const keep = sel.value;
        const players = list.filter((u) => u.player);
        const others = list.filter((u) => !u.player);
        const opts = (arr) => arr.map((u) =>
          '<option value="' + esc(u.username) + '">' + esc(u.label) +
          (u.admin ? ' · 管理员' : '') + (u.judge ? ' · 裁判' : '') +
          (u.online ? ' · 在线' : '') + '</option>').join('');
        sel.innerHTML =
          (players.length ? '<optgroup label="选手（' + players.length + '）">' + opts(players) + '</optgroup>' : '') +
          '<optgroup label="观众（' + others.length + '）">' + opts(others) + '</optgroup>';
        if (keep && list.some((u) => u.username === keep)) sel.value = keep;
      }
      paint(items);

      reportTimer = setInterval(async () => {
        try {
          const rr = await api('/api/case/targets', '', 'GET');
          if (rr.ok) paint(rr.data.items || []);
        } catch (e) { /* 网络抖动，下一轮再拉 */ }
      }, 3000);

      box.querySelector('#avRepOk').onclick = async () => {
        const body = 'username=' + encodeURIComponent(box.querySelector('#avTarget').value)
          + '&type=' + box.querySelector('#avType').value
          + '&reason=' + encodeURIComponent(box.querySelector('#avReason').value);
        const rr = await api('/api/case/report', body);
        if (!rr.ok) { box.querySelector('#avMsg').textContent = rr.data.error || '提交失败'; return; }
        box.querySelector('#avMsg').className = 'av-msg ok';
        box.querySelector('#avMsg').textContent = '已提交，编号 #' + rr.data.id + '，等待处理';
      };
    });
  }

  async function appealDialog() {
    const r = await api('/api/case/mine', '', 'GET');
    if (!r.ok) { modal('我要申诉', '<p class="av-msg">加载失败</p>'); return; }
    const d = r.data;
    const list = [];
    if (d.banned) list.push(['封号', 'ban']);
    if (d.muteRemainMs > 0) list.push(['禁言（剩余 ' + Math.ceil(d.muteRemainMs / 60000) + ' 分钟）', 'mute']);
    if (d.violation) list.push(['违规记录：' + d.violation, 'violation']);
    const history = (d.items || []).slice(0, 6).map((c) =>
      '<div class="av-row"><span>#' + c.id + ' ' + esc(c.kindLabel) + '·' + esc(c.typeLabel) + '</span><span>' +
      ({ pending: '处理中', approved: '已通过', rejected: '已驳回' }[c.status] || c.status) +
      (c.handler ? ' · ' + esc(c.handler) : '') + '</span></div>').join('');
    modal('我要申诉',
      (list.length
        ? '<p class="av-tip">你当前的处罚如下，选择一项说明理由；通过后处罚自动撤销，违规申诉会把成绩恢复到判罚前。</p>' +
          '<select id="avPun" class="av-input">' + list.map((x) => '<option value="' + x[1] + '">' + esc(x[0]) + '</option>').join('') + '</select>' +
          '<textarea id="avReason" class="av-input" rows="4" maxlength="120" placeholder="说明申诉理由，最多 120 字"></textarea>' +
          '<div class="av-actions"><button class="av-ok" id="avAppOk">提交申诉</button><button class="av-cancel">取消</button></div>'
        : '<p class="av-tip">暂未查询到违规记录。</p>' +
          '<p class="av-tip">禁言、封号记录也会显示在这里；若确认没有处罚，无需申诉。</p>') +
      (history ? '<div class="av-hist"><b>我的记录</b>' + history + '</div>' : '') +
      '<p class="av-msg" id="avMsg"></p>', (box) => {
        const ok = box.querySelector('#avAppOk');
        if (!ok) return;
        ok.onclick = async () => {
          const pun = box.querySelector('#avPun');
          const rr = await api('/api/case/appeal', 'type=' + encodeURIComponent(pun ? pun.value : '') +
            '&reason=' + encodeURIComponent(box.querySelector('#avReason').value));
          if (!rr.ok) { box.querySelector('#avMsg').textContent = rr.data.error || '提交失败'; return; }
          box.querySelector('#avMsg').className = 'av-msg ok';
          box.querySelector('#avMsg').textContent = '申诉已提交，编号 #' + rr.data.id + '，等待处理';
        };
      });
  }

  window.addEventListener('session-changed', () => {
    unmount();
    mount();
  });
  mount();
})();
