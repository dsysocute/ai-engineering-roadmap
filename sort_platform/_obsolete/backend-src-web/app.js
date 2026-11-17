/* 排序算法测评平台 - 前端逻辑 */

const API = location.origin + '/api';
const TOKEN_KEY = 'sort_platform_token';
const DRAFT_PREFIX = 'sort_platform_draft_';   // 草稿按账号分别存，不会串号

const STARTER_CODE = `public class Solution {
    // ⚠️ 答题须知（先看完这几行再动手）：
    // 1. 只写 public void sort(int[] arr)，把 arr 从小到大排好
    // 2. 不要写 main，不要用 Scanner，不要 System.out 打印
    // 3. 不需要 implements 任何接口，类名必须是 Solution
    // 4. 数组由平台传进来，排完就地生效，什么都不用返回
    public void sort(int[] arr) {
        // ===== 在这里写你的排序代码 =====


        // ===============================
    }
}`;

const $ = (id) => document.getElementById(id);

let token = localStorage.getItem(TOKEN_KEY) || '';
let elapsedBase = 0;       // 服务端返回的已用秒数
let localStart = 0;        // 本地开始计时的时刻
let status = 'NOT_STARTED';
let currentUser = '';      // 当前登录账号，用来区分草稿

// ---------------- 请求 ----------------

async function post(path, params) {
  const res = await fetch(API + path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
      'X-Token': token
    },
    body: new URLSearchParams(params || {}).toString()
  });
  return handle(res);
}

async function get(path) {
  const res = await fetch(API + path, {
    headers: { 'X-Token': token }
  });
  return handle(res);
}

async function handle(res) {
  let data = {};
  try {
    data = await res.json();
  } catch (e) {
    data = {};
  }
  if (res.status === 401) {
    logout(true);
    throw new Error(data.error || '登录过期');
  }
  if (!res.ok) {
    throw new Error(data.error || ('请求失败 ' + res.status));
  }
  return data;
}

// ---------------- 登录 ----------------

$('loginBtn').onclick = doLogin;
$('password').addEventListener('keydown', (e) => {
  if (e.key === 'Enter') doLogin();
});
$('username').addEventListener('keydown', (e) => {
  if (e.key === 'Enter') $('password').focus();
});

async function doLogin() {
  const username = $('username').value.trim();
  const password = $('password').value.trim();
  if (!username || !password) {
    $('loginMsg').textContent = '账号和密码都要填';
    return;
  }
  $('loginMsg').textContent = '登录中…';
  try {
    const res = await fetch(API + '/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
      body: new URLSearchParams({ username, password }).toString()
    });
    const data = await res.json();
    if (!res.ok) {
      $('loginMsg').textContent = data.error || '登录失败';
      return;
    }
    token = data.token;
    localStorage.setItem(TOKEN_KEY, token);
    $('loginMsg').textContent = '';
    $('password').value = '';
    await enterApp();
  } catch (e) {
    $('loginMsg').textContent = '连不上后端，确认服务起了没有';
  }
}

/**
 * 退出登录：把上一个账号留在页面上的东西全部清掉。
 * 包括编辑器里的代码、只读锁、终端日志、计时器。
 * 注意：这个账号的草稿仍然留在 localStorage，重新登录还能拿回来。
 */
function logout(silent) {
  token = '';
  localStorage.removeItem(TOKEN_KEY);

  if (window.__timer) {
    clearInterval(window.__timer);
    window.__timer = null;
  }

  const ed = $('editor');
  if (ed) {
    ed.value = STARTER_CODE;
    ed.readOnly = true;
    updateLines();
  }
  resetConsole();

  status = 'NOT_STARTED';
  elapsedBase = 0;
  localStart = 0;
  currentUser = '';
  if ($('timer')) $('timer').textContent = '00:00:00';

  $('app').classList.add('hidden');
  $('login').classList.remove('hidden');
  if (!silent) {
    $('loginMsg').textContent = '已退出';
    $('username').value = '';
  }
}

$('logoutBtn').onclick = () => logout(false);

// ---------------- 进入比赛 ----------------

async function enterApp() {
  const me = await get('/me');
  currentUser = me.username;

  $('login').classList.add('hidden');
  $('app').classList.remove('hidden');

  $('whoNo').textContent = '选手 ' + String(me.no).padStart(2, '0');
  $('whoName').textContent = me.username;

  status = me.status;
  setStatus(status);
  elapsedBase = me.elapsedSeconds;
  localStart = Date.now();

  // 编辑器内容：作答中的选手优先恢复自己的草稿；其他人一律回到干净模板。
  // 草稿按账号分开存，所以绝不会看到别人留下的代码。
  const saved = localStorage.getItem(DRAFT_PREFIX + currentUser);
  if (status === 'CODING' && saved) {
    $('editor').value = saved;
    log('已恢复你上次没交卷的代码。');
  } else {
    $('editor').value = STARTER_CODE;
    resetConsole();
  }
  updateLines();

  // 只有作答中才允许敲代码：没点开始锁着，交卷后 lockEditor 再锁死
  $('editor').readOnly = status !== 'CODING';

  if (status === 'FINISHED' && me.result) {
    showResult(me.result);
    lockEditor();
  }

  const problem = await get('/problem');
  $('briefDesc').textContent = problem.desc;

  tick();
  if (window.__timer) clearInterval(window.__timer);
  window.__timer = setInterval(tick, 1000);
}

function setStatus(s) {
  const box = $('statusBox');
  box.className = 'status';
  if (s === 'NOT_STARTED') {
    box.textContent = '未开始';
    box.classList.add('s-idle');
  } else if (s === 'CODING') {
    box.textContent = '作答中';
    box.classList.add('s-coding');
  } else {
    box.textContent = '已交卷';
    box.classList.add('s-done');
  }
  $('startBtn').disabled = s !== 'NOT_STARTED';
  $('testBtn').disabled = s !== 'CODING';
  $('finishBtn').disabled = s !== 'CODING';
  $('hint').textContent =
    s === 'NOT_STARTED' ? '先点「开始」再写代码'
    : s === 'CODING' ? '可以先「测试运行」，确认没问题再交卷'
    : '已交卷，代码已锁定';
}

function currentElapsed() {
  if (status === 'NOT_STARTED') return 0;
  return elapsedBase + Math.floor((Date.now() - localStart) / 1000);
}

function tick() {
  const sec = currentElapsed();
  $('timer').textContent = fmtTime(sec);
  if (status === 'CODING' && sec >= 7200) {
    $('hint').textContent = '已超过 120 分钟，建议马上交卷';
  }
  // 每 5 秒和服务端对一次表，防止本地时间跑偏
  if (sec % 5 === 0) resync();
}

let resyncing = false;
async function resync() {
  if (resyncing || !token) return;
  resyncing = true;
  try {
    const me = await get('/me');
    elapsedBase = me.elapsedSeconds;
    localStart = Date.now();
  } catch (e) {
    // 忽略，下一轮再对
  } finally {
    resyncing = false;
  }
}

function fmtTime(sec) {
  const h = String(Math.floor(sec / 3600)).padStart(2, '0');
  const m = String(Math.floor((sec % 3600) / 60)).padStart(2, '0');
  const s = String(sec % 60).padStart(2, '0');
  return h + ':' + m + ':' + s;
}

// ---------------- 按钮 ----------------

$('startBtn').onclick = async () => {
  try {
    await post('/start');
    status = 'CODING';
    elapsedBase = 0;
    localStart = Date.now();
    setStatus(status);
    $('editor').readOnly = false;
    $('editor').focus();
    log('计时开始，可以写代码了。');
  } catch (e) {
    log('开始失败：' + e.message);
  }
};

$('testBtn').onclick = async () => {
  const code = $('editor').value;
  if (!code.trim()) {
    log('代码是空的。');
    return;
  }
  $('testBtn').disabled = true;
  log('正在编译运行…');
  try {
    const res = await post('/test', { code });
    log(res.terminal || '没有输出');
  } catch (e) {
    log('运行失败：' + e.message);
  } finally {
    $('testBtn').disabled = status !== 'CODING';
  }
};

$('finishBtn').onclick = async () => {
  const code = $('editor').value;
  if (!code.trim()) {
    log('代码是空的，没法交卷。');
    return;
  }
  if (!confirm('交卷后不能再修改代码，确定交卷吗？')) {
    return;
  }
  $('finishBtn').disabled = true;
  log('正在判题，请稍等（慢的算法可能要几十秒）…');
  try {
    const r = await post('/finish', { code });
    status = 'FINISHED';
    setStatus(status);
    lockEditor();
    if (currentUser) localStorage.removeItem(DRAFT_PREFIX + currentUser);
    showResult(r);
    log('判题完成。');
  } catch (e) {
    log('交卷失败：' + e.message);
    $('finishBtn').disabled = false;
  }
};

$('clearConsole').onclick = () => resetConsole();

// ---------------- 终端输出 ----------------

function resetConsole() {
  const body = $('consoleBody');
  if (!body) return;
  body.textContent = '等待操作…';
  body.dataset.placeholder = '1';
}

/** 追加式输出：旧的在上面，新的一直往最下面加，并自动滚到底。 */
function log(text) {
  const body = $('consoleBody');
  if (!body) return;
  if (body.dataset.placeholder === '1') {
    body.textContent = '';
    delete body.dataset.placeholder;
  }
  const time = new Date().toLocaleTimeString('zh-CN', { hour12: false });
  const lines = String(text).split('\n');
  let block = '[' + time + '] ' + lines[0];
  for (let i = 1; i < lines.length; i++) {
    const l = lines[i];
    block += '\n' + (l.startsWith(' ') ? l : '    ' + l);
  }
  body.textContent += block + '\n';
  body.scrollTop = body.scrollHeight;
}

// ---------------- 编辑器 ----------------

$('editor').addEventListener('input', () => {
  updateLines();
  // 作答中实时存草稿：刷新页面、手滑关掉浏览器都还能捞回来
  if (status === 'CODING' && currentUser) {
    localStorage.setItem(DRAFT_PREFIX + currentUser, $('editor').value);
  }
});

$('editor').addEventListener('keydown', (e) => {
  if (e.key === 'Tab') {
    e.preventDefault();
    const el = e.target;
    const start = el.selectionStart;
    el.value = el.value.slice(0, start) + '    ' + el.value.slice(el.selectionEnd);
    el.selectionStart = el.selectionEnd = start + 4;
    updateLines();
  }
});

function updateLines() {
  const n = ($('editor').value || '').split('\n').length;
  $('lineCount').textContent = n + ' 行';
}

function lockEditor() {
  $('editor').readOnly = true;
}

// ---------------- 结果 ----------------

function showResult(r) {
  $('resultTitle').textContent = '选手 ' + String(r.no).padStart(2, '0') + ' 成绩';
  const box = $('resultBody');
  box.innerHTML = '';

  const score = document.createElement('div');
  score.className = 'big-score';
  score.textContent = Number(r.score).toFixed(1);
  box.appendChild(score);

  const rows = [
    ['正确性', r.correct ? '通过' : '未通过（得分 0）'],
    ['通过用例', r.caseOk + ' / ' + r.caseTotal],
    ['编码用时', Number(r.codingMinutes).toFixed(0) + ' 分钟'],
    ['效率得分', r.efficiencyScore + ' / 25'],
    ['交卷时间', r.finishedAt]
  ];
  const table = document.createElement('table');
  table.className = 'result-table';
  rows.forEach(([k, v]) => {
    const tr = document.createElement('tr');
    const td1 = document.createElement('td');
    td1.textContent = k;
    const td2 = document.createElement('td');
    td2.textContent = v;
    tr.appendChild(td1);
    tr.appendChild(td2);
    table.appendChild(tr);
  });
  box.appendChild(table);

  if (r.detail) {
    const p = document.createElement('p');
    p.className = 'detail';
    p.textContent = r.detail;
    box.appendChild(p);
  }

  $('resultModal').classList.remove('hidden');
}

$('resultClose').onclick = () => $('resultModal').classList.add('hidden');

// ---------------- 排行榜 ----------------

$('boardBtn').onclick = async () => {
  $('boardModal').classList.remove('hidden');
  $('boardBody').textContent = '加载中…';
  try {
    const list = await get('/leaderboard');
    if (!list.length) {
      $('boardBody').textContent = '还没有人交卷。';
      return;
    }
    let html = '<table class="board-table"><tr><th>名次</th><th>选手号</th><th>账号</th><th>总分</th><th>用时</th><th>效率</th><th>交卷</th></tr>';
    list.forEach((r) => {
      html += '<tr>'
        + '<td>' + r.rank + '</td>'
        + '<td>' + String(r.no).padStart(2, '0') + '</td>'
        + '<td class="mono">' + r.username + '</td>'
        + '<td><b>' + Number(r.score).toFixed(1) + '</b></td>'
        + '<td>' + Number(r.codingMinutes).toFixed(0) + ' 分</td>'
        + '<td>' + r.efficiencyScore + '</td>'
        + '<td>' + (r.finishedAt || '') + '</td>'
        + '</tr>';
    });
    html += '</table>';
    $('boardBody').innerHTML = html;
  } catch (e) {
    $('boardBody').textContent = '加载失败：' + e.message;
  }
};

$('boardClose').onclick = () => $('boardModal').classList.add('hidden');

// ---------------- 启动 ----------------

(async function boot() {
  if (token) {
    try {
      await enterApp();
    } catch (e) {
      logout(true);
    }
  }
})();
