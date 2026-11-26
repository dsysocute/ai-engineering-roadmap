/* 排序算法测评平台 - 前端逻辑 */

const API = location.origin + '/api';
const TOKEN_KEY = 'sort_platform_token';
const DRAFT_PREFIX = 'sort_platform_draft_';   // 草稿按账号分别存，不会串号

const STARTER_CODE = `public class Solution {
    // 实现 sort 方法，对 arr 进行升序原地排序
    // 禁止编写 main、输入输出代码，不要添加 static 修饰符
    public void sort(int[] arr) {
        // 在此处编写排序代码


    }
}`;

const $ = (id) => document.getElementById(id);

let token = window.AppSession ? AppSession.token() : (localStorage.getItem(TOKEN_KEY) || '');
let elapsedBase = 0;
let localStart = 0;
let status = 'NOT_STARTED';
// 交卷那一刻把计时定格，之后不再跟着服务器走
let frozenElapsed = null;
let currentUser = '';
let currentRole = 'player';
let contestState = 'idle';
/** 本场截止时刻（毫秒）与服务器时钟偏差，用来在本地每秒续走倒计时 */
let contestDeadline = 0;
let serverSkew = 0;
/** 复赛选手的个人截止时刻（0 = 没有） */
let personalDeadline = 0;
let contestPolling = null;
let countdownTimer = null;
let activePanel = 'brief';
let discussLocked = true;
let effPolling = null;
let kickedShown = false;

// ---------------- 请求 -------------

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

/**
 * 统一处理响应。
 * 401 分两种：服务端明确回了 code=KICKED 说明这个账号在别处登录被顶掉了，
 * 要弹专门的提示；其余 401 是登录过期或服务重启，安静回登录页即可。
 */
async function handle(res) {
  let data = {};
  try {
    data = await res.json();
  } catch (e) {
    data = {};
  }
  if (res.status === 401) {
    if (data && data.code === 'KICKED') {
      showKicked();
      const err = new Error(data.error || '账号已在别处登录');
      err.kicked = true;
      throw err;
    }
    logout(true);
    throw new Error(data.error || '登录过期');
  }
  if (!res.ok) {
    throw new Error(data.error || ('请求失败 ' + res.status));
  }
  return data;
}

// ---------------- 登录 -------------

$('loginBtn').onclick = doLogin;
$('password').addEventListener('keydown', (e) => {
  if (e.key === 'Enter') doLogin();
});
$('username').addEventListener('keydown', (e) => {
  if (e.key === 'Enter') $('password').focus();
});

let loggingIn = false;
async function doLogin() {
  if (loggingIn) return;   // 连点两次登录会把自己顶下线
  const username = $('username').value.trim();
  const password = $('password').value.trim();
  const roleEl = document.querySelector('input[name="role"]:checked');
  const role = roleEl ? roleEl.value : 'player';
  if (!username || !password) {
    $('loginMsg').textContent = '账号和密码都要填';
    return;
  }
  loggingIn = true;
  $('loginBtn').disabled = true;
  $('loginMsg').textContent = '登录中…';
  try {
    const res = await fetch(API + '/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
      body: new URLSearchParams({ username, password, role }).toString()
    });
    const data = await res.json();
    if (!res.ok) {
      $('loginMsg').textContent = data.error || '登录失败';
      return;
    }
    token = data.token;
    // 身份存进“本标签页”的会话里：多开标签页时互不覆盖
    if (window.AppSession) {
      AppSession.set(token, data.role || role, data.no || '', username);
    } else {
      localStorage.setItem(TOKEN_KEY, token);
      localStorage.setItem('sort_platform_role', data.role || role);
      localStorage.setItem('sort_platform_no', String(data.no || ''));
      localStorage.setItem('sort_platform_username', username);
    }
    $('loginMsg').textContent = '';
    $('password').value = '';
    kickedShown = false;
    if ((data.role || role) === 'viewer') {
      // 观众直接进观赛页，不在做题页停留；迟到的选手会收到降级说明
      if (data.notice) {
        alert(data.notice);
      }
      location.href = '/watch';
      return;
    }
    await enterApp();
    if (data.kickedPrevious) {
      // 界面切换完再提示，避免被 resetConsole 冲掉
      setTimeout(() => log('该账号已在其他终端登录，原会话已下线。'), 0);
    }
  } catch (e) {
    $('loginMsg').textContent = '无法连接后端，请确认服务已启动';
  } finally {
    loggingIn = false;
    if ($('loginBtn')) $('loginBtn').disabled = false;
  }
}

/** 只清前端状态，不动界面可见性 */
function clearSession() {
  token = '';
  if (window.AppSession) AppSession.clear(); else localStorage.removeItem(TOKEN_KEY);

  if (window.__timer) {
    clearInterval(window.__timer);
    window.__timer = null;
  }
  if (effPolling) {
    clearInterval(effPolling);
    effPolling = null;
  }
  // 赛事轮询和开赛倒计时也属于登录态，退出后必须停掉，否则会一直空转
  if (contestPolling) {
    clearInterval(contestPolling);
    contestPolling = null;
  }
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
  stopCodePush();

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
  // 交卷定格的计时必须一起清掉，否则同一浏览器换个账号登录会沿用上一个人的时间
  frozenElapsed = null;
  currentUser = '';
  stopComments();
  if ($('timer')) $('timer').textContent = '00:00:00';
}

function logout(silent) {
  clearSession();
  $('app').classList.add('hidden');
  $('login').classList.remove('hidden');
  if (!silent) {
    $('loginMsg').textContent = '已退出';
    $('username').value = '';
  }
}

/** 被同账号的新登录顶下线：先弹窗讲清楚，再回登录页 */
function showKicked() {
  if (kickedShown) {
    return;
  }
  kickedShown = true;
  const oldToken = token;
  clearSession();
  // 顺手让服务端把这个已经作废的 token 标成失效，避免重复清理
  fetch(API + '/logout', { method: 'POST', headers: { 'X-Token': oldToken } }).catch(() => {});

  $('app').classList.add('hidden');
  $('login').classList.add('hidden');
  $('resultModal').classList.add('hidden');
  $('boardModal').classList.add('hidden');
  $('kickedModal').classList.remove('hidden');
}

$('logoutBtn').onclick = () => {
  const oldToken = token;
  logout(false);
  // 通知服务端作废 token，作废完别人再登录就不算「被顶号」
  fetch(API + '/logout', { method: 'POST', headers: { 'X-Token': oldToken } }).catch(() => {});
};

$('kickedOk').onclick = () => {
  $('kickedModal').classList.add('hidden');
  kickedShown = false;
  $('login').classList.remove('hidden');
  $('loginMsg').textContent = '该账号刚在其他终端登录过，重新登录将使原会话下线';
  $('password').value = '';
  $('password').focus();
};

// ---------------- 进入比赛 ----------------

async function enterApp() {
  const me = await get('/me');
  if (me.role === 'viewer' || me.status === 'VIEWER') {
    // 观众身份不进做题界面
    location.href = '/watch';
    return;
  }
  currentUser = me.username;

  $('login').classList.add('hidden');
  $('app').classList.remove('hidden');

  $('whoNo').textContent = '选手' + String(me.no).padStart(2, '0');
  $('whoName').textContent = me.nick ? me.nick : me.username;
  if (window.AppSession) {
    AppSession.setUsername(me.username);
    AppSession.setNick(me.nick || '');
  }
  currentRole = me.role || 'player';
  contestState = me.contest || 'idle';
  personalDeadline = Number(me.personalDeadline || 0);
  if (me.reinstated) {
    log('申诉已通过：你已回到比赛区，按个人剩余时间独立倒计时，交卷后正常计分。');
  }

  status = me.status;
  setStatus(status);
  elapsedBase = me.elapsedSeconds;
  localStart = Date.now();

  const saved = localStorage.getItem(DRAFT_PREFIX + currentUser);
  if (status === 'CODING' && saved) {
    $('editor').value = saved;
    log('已恢复上次未提交的代码。');
  } else {
    $('editor').value = STARTER_CODE;
    resetConsole();
  }
  updateLines();

  $('editor').readOnly = status !== 'CODING';
  if (status === 'CODING') {
    startCodePush();
  } else {
    stopCodePush();
  }

  if (status === 'FINISHED' && me.result) {
    // 交卷后计时定格在提交那一刻，不随后端继续增长
    if (me.result.codingSeconds > 0) frozenElapsed = Number(me.result.codingSeconds);
    showResult(me.result);
    lockEditor();
    // 上次交卷了但效率还没算完，登录进来继续盯着；
    // 正确性没过的记录永远不会再有结果，别再空转轮询
    if (me.result.correct && !me.result.efficiencyReady) {
      // 交卷后直接关掉页面就不会有人触发评测，这里补一次（服务端幂等）
      post('/judge-efficiency', {}).catch(() => {});
      startEfficiencyPolling();
    }
  }

  const problem = await get('/problem');
  $('briefDesc').textContent = problem.desc;

  tick();
  if (window.__timer) clearInterval(window.__timer);
  window.__timer = setInterval(tick, 1000);

  startComments();
  applyDiscussLock();
  startContestPoll();
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
  if ($('tabStat')) {
    $('tabStat').textContent = s === 'NOT_STARTED' ? '未开始' : (s === 'CODING' ? '作答中' : '已交卷');
  }
  $('startBtn').disabled = s !== 'NOT_STARTED';
  $('testBtn').disabled = s !== 'CODING';
  $('finishBtn').disabled = s !== 'CODING';
  $('hint').textContent =
    s === 'NOT_STARTED' ? '请先点击【开始】'
    : s === 'CODING' ? '可先执行【测试运行】自测，确认无误后提交'
    : '已提交，代码已锁定';
  applyDiscussLock();
  applyPlayBar();
}

/** 选手专用操作条：赛前显示快捷消息，交卷后显示弹幕输入，其余时间整条隐藏 */
function applyPlayBar() {
  const bar = $('playBar');
  if (!bar) return;
  const isPlayer = currentRole === 'player';
  const showQuick = isPlayer && status === 'NOT_STARTED' && contestState === 'idle';
  const showSay = isPlayer && status === 'FINISHED';
  if ($('quickRow')) $('quickRow').classList.toggle('hidden', !showQuick);
  if ($('sayRow')) $('sayRow').classList.toggle('hidden', !showSay);
  bar.classList.toggle('hidden', !(showQuick || showSay));
}

function currentElapsed() {
  if (frozenElapsed !== null) return frozenElapsed;
  if (status === 'NOT_STARTED') return 0;
  return elapsedBase + Math.floor((Date.now() - localStart) / 1000);
}

let tickCount = 0;
function tick() {
  const sec = currentElapsed();
  $('timer').textContent = fmtTime(sec);
  updateRemain();
  if (status === 'CODING' && sec >= 7200) {
    $('hint').textContent = '已超过 120 分钟，建议立即提交';
  }
  // 每 5 秒和后端对一次：一是校准计时，二是尽早发现被别的窗口顶下线、
  // 或者被裁判判罚。用 tick 计数而不是 sec%5：未开始时 sec 恒为 0，
  // 以前会退化成每秒都请求一次 /me。
  tickCount++;
  if ((status === 'CODING' || status === 'NOT_STARTED') && tickCount % 5 === 0) resync();
}

/** 顶部「剩余」倒计时：整场倒计时或复赛个人倒计时，最后 5 分钟变红 */
function updateRemain() {
  const el = $('remain');
  if (!el) return;
  const dl = personalDeadline || contestDeadline;
  const show = personalDeadline > 0 || (contestState === 'running' && contestDeadline);
  if (!show) {
    el.textContent = '--:--:--';
    el.classList.remove('low');
    return;
  }
  const left = Math.max(0, Math.floor((dl - (Date.now() + serverSkew)) / 1000));
  el.textContent = fmtTime(left);
  el.classList.toggle('low', left <= 300);
}

let resyncing = false;
async function resync() {
  if (resyncing || !token) return;
  if (frozenElapsed !== null) return;   // 已交卷，计时定格，不再和服务端对表
  resyncing = true;
  try {
    const me = await get('/me');
    applyMe(me);
  } catch (e) {
    // 被顶号的情况 handle 里已经弹过窗了，这里不用再处理
  } finally {
    resyncing = false;
  }
}

/**
 * 统一把 /me 的最新状态落到界面上。
 * 以前只有 /contest 状态变化才刷新，裁判判罚（violation）不改赛事状态，
 * 被罚的选手会一直停在「作答中」，还能继续编辑和推代码。
 */
function applyMe(me) {
  if (!me) return;
  if (me.contest) contestState = me.contest;
  personalDeadline = Number(me.personalDeadline || 0);
  applyPlayBar();
  if (me.status && me.status !== status) {
    status = me.status;
    setStatus(status);
    if ($('editor')) $('editor').readOnly = status !== 'CODING';
    if (status === 'CODING') {
      // 复赛：从已结束回到作答中，必须解冻计时，否则 currentElapsed() 永远返回旧值
      frozenElapsed = null;
      if (typeof me.elapsedSeconds === 'number') {
        elapsedBase = me.elapsedSeconds;
        localStart = Date.now();
      }
      $('resultModal').classList.add('hidden');
      startCodePush();
    } else {
      stopCodePush();
    }
    if (status === 'FINISHED') {
      // 0 秒完赛也要定格：以前 codingSeconds 为 0 时不会设 frozen，计时会继续跑
      frozenElapsed = me.result ? Number(me.result.codingSeconds) || 0 : currentElapsed();
      lockEditor();
      if (me.result) showResult(me.result);
      if (currentUser) localStorage.removeItem(DRAFT_PREFIX + currentUser);
      log(me.result && !me.result.correct
        ? '本场已结束，成绩以服务端记录为准。'
        : '已完赛，讨论区与观赛视角已解锁。');
    } else if (status === 'NOT_STARTED') {
      frozenElapsed = null;
      resetConsole();
    }
  }
  if (typeof me.elapsedSeconds === 'number') {
    elapsedBase = me.elapsedSeconds;
    localStart = Date.now();
  }
}

function fmtTime(sec) {
  const h = String(Math.floor(sec / 3600)).padStart(2, '0');
  const m = String(Math.floor((sec % 3600) / 60)).padStart(2, '0');
  const s = String(sec % 60).padStart(2, '0');
  return h + ':' + m + ':' + s;
}

// ---------------- 按钮 -------------

$('startBtn').onclick = async () => {
  try {
    const r = await post('/start');
    status = 'CODING';
    // 用服务端返回的用时做基准，重登/换设备都不会把计时清零
    elapsedBase = r.elapsedSeconds || 0;
    localStart = Date.now();
    setStatus(status);
    $('editor').readOnly = false;
    $('editor').focus();
    log('计时已开始。');
    startCodePush();
  } catch (e) {
    log('开始失败：' + e.message);
  }
};

$('testBtn').onclick = async () => {
  const code = $('editor').value;
  if (!code.trim()) {
    log('代码为空。');
    return;
  }
  $('testBtn').disabled = true;
  log('正在编译运行…');

  try {
    const res = await post('/test', { code });
    log(res.terminal || '无输出内容');
  } catch (e) {
    log('运行失败：' + e.message);
  } finally {
    $('testBtn').disabled = status !== 'CODING';
  }
};

$('finishBtn').onclick = async () => {
  const code = $('editor').value;
  if (!code.trim()) {
    log('代码为空，无法提交。');
    return;
  }
  if (!confirm('提交后代码不可修改，确认提交？')) {
    return;
  }
  $('finishBtn').disabled = true;
  log('正在校验正确性…');
  try {
    const r = await post('/finish', { code });
    // 先定格计时，再切状态：倒计时停在提交那一刻，不再继续跑
    frozenElapsed = r.codingSeconds > 0 ? Number(r.codingSeconds) : currentElapsed();
    tick();
    status = 'FINISHED';
    setStatus(status);
    lockEditor();
    stopCodePush();
    if (currentUser) localStorage.removeItem(DRAFT_PREFIX + currentUser);
    showResult(r);

    if (r.correct && !r.efficiencyReady) {
      log('正确性校验通过，效率评测在后台执行，无需等待。');
      post('/judge-efficiency', { username: currentUser, code }).catch(() => {});
      startEfficiencyPolling();
    } else if (!r.correct) {
      log('正确性校验未通过，不再进行效率评测。');
    } else {
      log('评测完成。');
    }
  } catch (e) {
    log('交卷失败：' + e.message);
    $('finishBtn').disabled = false;
  }
};

$('clearConsole').onclick = () => resetConsole();

// ---------------- 终端输出 -------------

function resetConsole() {
  const body = $('consoleBody');
  if (!body) return;
  body.textContent = '等待操作…';
  body.dataset.placeholder = '1';
}

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

// ---------------- 编辑器 --------------

$('editor').addEventListener('input', () => {
  updateLines();
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

/** 效率评测期间每 3 秒查一次，跑完自动更新并提示 */
function startEfficiencyPolling() {
  if (effPolling) clearInterval(effPolling);
  let tries = 0;
  effPolling = setInterval(async () => {
    try {
      // 最多盯 10 分钟，评测线程出意外时不要无限轮询
      if (++tries > 200) {
        clearInterval(effPolling);
        effPolling = null;
        return;
      }
      const me = await get('/me');
      if (me.result && me.result.efficiencyReady) {
        clearInterval(effPolling);
        effPolling = null;
        log('效率评测完成，效率分：' + Number(me.result.efficiencyScore).toFixed(1) + ' / 20，'
            + '总分：' + Number(me.result.score).toFixed(1));
        if (!$('resultModal').classList.contains('hidden')) showResult(me.result);
      }
    } catch (e) {
      // 网络抖动就下一轮再查
    }
  }, 3000);
}

function showResult(r) {
  $('resultTitle').textContent = '选手 ' + String(r.no).padStart(2, '0') + ' 成绩';
  const box = $('resultBody');
  box.innerHTML = '';

  // 只有「正确性通过、效率还没算完」才算是评测中；
  // 正确性没过的根本不会再跑效率，不能一直显示"测算中"骗人。
  const pending = r.correct && !r.efficiencyReady;
  const effText = !r.correct ? '不评测（正确性未通过）'
    : (pending ? '评测中…' : Number(r.efficiencyScore).toFixed(1) + ' / 20');
  const codingScoreText = !r.correct ? '0.0 / 20'
    : (Number(r.codingScore || 0).toFixed(1) + ' / 20');

  const score = document.createElement('div');
  score.className = 'big-score';
  score.textContent = Number(r.score).toFixed(1);
  box.appendChild(score);

  const runText = !r.correct ? '不评测'
    : (r.efficiencyReady ? Number(r.runMillis).toFixed(1) + ' ms' : '评测中…');

  const rows = [
    ['正确性', r.correct ? '通过' : '未通过（得分 0）'],
    ['基础分', Number(r.baseScore || 0).toFixed(1) + ' / 60'],
    ['通过用例', r.caseOk + ' / ' + r.caseTotal],
    ['编码时长', fmtTime(Number(r.codingSeconds || 0))],
    ['编码速率分', codingScoreText],
    ['运行时长', runText],
    ['效率分', effText],
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

  if (pending) {
    const tip = document.createElement('p');
    tip.className = 'detail';
    tip.style.color = '#e3b341';
    tip.textContent = '效率评测正在后台执行，完成后分数将自动更新。'
      + '关闭当前窗口不影响评测。';
    box.appendChild(tip);
  }

  const closeBtn = $('resultClose');
  const bgBtn = $('resultBg');
  if (pending) {
    closeBtn.disabled = true;
    closeBtn.textContent = '效率评测中…';
    bgBtn.classList.remove('hidden');
  } else {
    closeBtn.disabled = false;
    closeBtn.textContent = '关闭';
    bgBtn.classList.add('hidden');
  }

  $('resultModal').classList.remove('hidden');
}

$('resultClose').onclick = () => $('resultModal').classList.add('hidden');

$('resultBg').onclick = () => {
  $('resultModal').classList.add('hidden');
  log('已关闭弹窗，效率评测在后台继续执行。完成后终端会给出提示，也可在排行榜查看最新分数。');
};

// ---------------- 观赛代码上报 ----------------

let codePushTimer = null;
let lastPushedCode = null;

/** 作答中每 2 秒推一次编辑器内容，观赛端据此实时看到代码 */
function startCodePush() {
  if (codePushTimer) {
    return;
  }
  lastPushedCode = null;
  codePushTimer = setInterval(() => {
    if (status !== 'CODING') {
      return;
    }
    const code = $('editor').value;
    if (code === lastPushedCode) {
      return;
    }
    lastPushedCode = code;
    post('/editor', { code }).catch(() => {
      // 推送失败不影响本地答题；把标记清掉，下一轮才会重试
      // （以前先写 lastPushedCode，失败后代码没变就再也不会重推）
      lastPushedCode = null;
    });
  }, 2000);
}

function stopCodePush() {
  if (codePushTimer) {
    clearInterval(codePushTimer);
    codePushTimer = null;
  }
}

// ---------------- 课堂讨论 ----------------

let commentPolling = null;
let danmakuIndex = 0;
let commentKey = '';
let danmakuOn = localStorage.getItem('danmakuOff') !== '1';
/** 赛中未交卷时弹幕区锁定 */
let danmakuLocked = false;
/** 刚解锁时先记录存量、不重播 */
let danmakuNeedsBaseline = false;
const seenComments = new Set();

/** "2026-11-11 02:46:01" → "02:46" */
function clockText(t) {
  const m = String(t || '').match(/(\d{1,2}:\d{2}):\d{2}$/);
  return m ? m[1] : '';
}

/** 发言者前缀：总管 / 裁判 / 管理员 / 选手05 / 观众·昵称 */
function whoText(c) {
  if (c.role === 'super') {
    return '总管';
  }
  if (c.role === 'judge') {
    return '裁判';
  }
  if (c.role === 'admin') {
    return '管理员';
  }
  if (c.role === 'viewer') {
    // 观众昵称统一是「观众37」；管理员后台发的旧数据可能只存了昵称
    return c.username && c.username.indexOf('观众') === 0 ? c.username : '观众 ' + (c.username || '');
  }
  return '选手' + String(c.no).padStart(2, '0');
}

function roleClass(c) {
  if (c.role === 'super') {
    return ' from-super';
  }
  if (c.role === 'judge') {
    return ' from-judge';
  }
  if (c.role === 'admin') {
    return ' from-admin';
  }
  if (c.role === 'viewer') {
    return ' from-viewer';
  }
  return '';
}

function commentNode(c) {
  const div = document.createElement('div');
  div.className = 'comment-item' + roleClass(c);
  const who = document.createElement('b');
  who.textContent = whoText(c);
  div.appendChild(who);
  const text = document.createElement('span');
  text.className = 'txt';
  text.textContent = c.text;
  div.appendChild(text);
  const t = document.createElement('span');
  t.className = 't';
  t.textContent = clockText(c.time);
  div.appendChild(t);
  return div;
}

/** 按 id 集合整体重绘：管理员删掉的发言，各端下一轮一起消失 */
function renderComments(all) {
  const items = all.filter((c) => c.kind !== 'danmaku');
  const key = items.map((c) => c.id).join(',');
  if (key === commentKey) return;
  const list = $('commentList');
  const atBottom = list.scrollHeight - list.scrollTop - list.clientHeight < 36;
  commentKey = key;
  list.innerHTML = '';
  if (!items.length) {
    list.innerHTML = '<div class="comment-empty">暂未有人发言</div>';
  } else {
    items.forEach((c) => list.appendChild(commentNode(c)));
  }
  if (atBottom) list.scrollTop = list.scrollHeight;
  $('commentCount').textContent = items.length ? items.length + ' 条' : '暂无发言';
}

/** 中间停留式消息：选手弹幕、快捷消息、裁判/管理员/总管弹幕都走这里（不横向滚动） */
function pushCenter(c, opts) {
  if (!danmakuOn) return;
  const track = $('danmakuTrack');
  if (!track) return;
  const empty = $('danmakuEmpty');
  if (empty) empty.style.display = 'none';
  // 同一时刻只留一条，新的顶掉旧的
  track.querySelectorAll('.danmaku.quick').forEach((el) => el.remove());
  const staff = c.role === 'judge' || c.role === 'admin' || c.role === 'super';
  const item = document.createElement('div');
  item.className = 'danmaku quick' + (staff ? ' staff ' + c.role : '');
  item.textContent = whoText(c) + '：' + c.text;
  item.style.top = 'calc(50% - 12px)';
  track.appendChild(item);
  const ttl = opts && opts.ttl ? opts.ttl : 5200;
  setTimeout(() => {
    item.remove();
    if (!track.querySelectorAll('.danmaku').length && $('danmakuEmpty')) $('danmakuEmpty').style.display = '';
  }, ttl);
}

/** 选手快捷消息：赛前专用，中间停留约 5 秒 */
function pushQuick(q) {
  pushCenter({ role: 'student', no: q.no, text: q.text }, { ttl: 5200 });
}

/** 弹幕分流：观众横向飘过；选手/裁判/管理员/总管在中间停留 */
function pushDanmaku(c) {
  if (!danmakuOn) return;
  if (c.role === 'student' || c.role === 'judge' || c.role === 'admin' || c.role === 'super') {
    pushCenter(c);
    return;
  }
  const track = $('danmakuTrack');
  if (!track) return;
  if (track.querySelectorAll('.danmaku:not(.quick)').length >= 5) return;
  const empty = $('danmakuEmpty');
  if (empty) empty.style.display = 'none';
  const item = document.createElement('div');
  item.className = 'danmaku' + roleClass(c);
  item.textContent = whoText(c) + '：' + c.text;
  item.style.top = ((danmakuIndex++) % 5) * 21 + 6 + 'px';
  track.appendChild(item);
  item.addEventListener('animationend', () => {
    item.remove();
    if (!track.querySelectorAll('.danmaku').length && $('danmakuEmpty')) $('danmakuEmpty').style.display = '';
  });
}

/** 弹幕开关：状态记在本地，刷新后保持 */
function applyDanmakuSwitch() {
  const bar = $('danmakuBar');
  const btn = $('danmakuToggle');
  if (!bar || !btn) return;
  bar.classList.toggle('off', !danmakuOn);
  btn.textContent = danmakuOn ? '弹幕 开' : '弹幕 关';
  btn.title = danmakuOn ? '关闭弹幕' : '打开弹幕';
  localStorage.setItem('danmakuOff', danmakuOn ? '0' : '1');
  if (!danmakuOn) {
    const track = $('danmakuTrack');
    if (track) track.innerHTML = '';
    if ($('danmakuEmpty')) $('danmakuEmpty').style.display = '';
  }
}

function toggleDanmaku() {
  danmakuOn = !danmakuOn;
  applyDanmakuSwitch();
}

async function pollComments(initial) {
  if (!token) return;
  try {
    // 本人未完赛时只拉弹幕（可以看弹幕，但看不到讨论内容）
    const data = await get('/comments?limit=80' + (discussLocked ? '&kind=danmaku' : ''));
    const items = data.items || [];
    danmakuLocked = !!data.locked;
    if (danmakuLocked) {
      // 赛中、未交卷：弹幕区锁住，清空轨道并给一句明确提示
      const track = $('danmakuTrack');
      if (track) track.innerHTML = '';
      const empty = $('danmakuEmpty');
      if (empty) {
        empty.style.display = '';
        empty.textContent = '比赛进行中，弹幕已锁定 · 交卷后解锁';
      }
      danmakuNeedsBaseline = true;
      return;
    }
    const baseline = danmakuNeedsBaseline;   // 刚解锁时把存量当基线，避免一次性刷出一大片旧弹幕
    danmakuNeedsBaseline = false;
    if (!discussLocked) renderComments(items);
    items.forEach((c) => {
      const id = String(c.id);
      if (!seenComments.has(id)) {
        seenComments.add(id);
        if (!initial && !baseline && c.kind === 'danmaku') pushDanmaku(c);
      }
    });
  } catch (e) {
    // 网络抖动、被顶号都在 handle 里处理过了，下一轮再拉
  }
}

function startComments() {
  if (commentPolling) return;
  seenComments.clear();
  commentKey = '';
  applyDanmakuSwitch();
  pollComments(true);
  commentPolling = setInterval(() => pollComments(false), 2000);
}

function stopComments() {
  if (commentPolling) {
    clearInterval(commentPolling);
    commentPolling = null;
  }
}

/** kind=comment 进讨论区列表，kind=danmaku 只在弹幕轨道上飘 */
async function sendComment(kind) {
  const input = $('commentInput');
  const text = input.value.trim();
  if (!text) return;
  if (kind === 'comment' && discussLocked) {
    input.placeholder = '本人完赛后讨论区才开放';
    return;
  }
  if (kind === 'danmaku') {
    if (contestState === 'running' || contestState === 'countdown') {
      input.placeholder = '比赛进行中弹幕关闭，结束后开放';
      return;
    }
  }
  input.disabled = true;
  try {
    await post('/comments', { text, kind });
    input.value = '';
    pollComments(false);
  } catch (e) {
    if (kind === 'danmaku') {
      // 被防提示词拦下时，直接在输入框里说明原因
      input.placeholder = e.message;
      setTimeout(() => {
        input.placeholder = '输入内容，选择发送方式';
      }, 4000);
    } else {
      log('发送失败：' + e.message);
    }
  } finally {
    input.disabled = false;
    input.focus();
  }
}

$('commentSend').onclick = () => sendComment('comment');
$('danmakuSend').onclick = () => sendComment('danmaku');
$('danmakuToggle').onclick = toggleDanmaku;
applyDanmakuSwitch();

// ---------------- 赛事状态 / 页签 / 快捷消息 ----------------

function switchPanel(name) {
  activePanel = name;
  document.querySelectorAll('.tab[data-panel]').forEach((t) => {
    t.classList.toggle('active', t.getAttribute('data-panel') === name);
  });
  if ($('panelBrief')) $('panelBrief').classList.toggle('hidden', name !== 'brief');
  if ($('panelDiscuss')) $('panelDiscuss').classList.toggle('hidden', name !== 'discuss');
  if (name === 'discuss' && !discussLocked) pollComments(false);
}

/** 讨论区锁：选手本人没交卷就锁住；交卷后与观众一致 */
function applyDiscussLock() {
  discussLocked = status !== 'FINISHED';
  if ($('discussLock')) $('discussLock').classList.toggle('hidden', !discussLocked);
  if ($('discussBody')) $('discussBody').classList.toggle('hidden', discussLocked);
  if (discussLocked && activePanel === 'discuss') switchPanel('brief');
}

async function refreshStatus() {
  try {
    const me = await get('/me');
    applyMe(me);
  } catch (e) {
    // 被顶号已在 handle 里处理
  }
}

/** 裁判开赛倒计时：10 → 1，到点自动收起 */
function showCountdown(end) {
  const modal = $('contestModal');
  if (!modal) return;
  modal.classList.remove('hidden');
  $('contestTitle').textContent = '裁判已开启比赛';
  $('contestText').textContent = '比赛即将开始，请做好准备';
  const paint = () => {
    const left = Math.max(0, Math.ceil((end - Date.now()) / 1000));
    $('contestCount').textContent = String(left);
    if (left <= 0) {
      $('contestTitle').textContent = '比赛开始';
      $('contestCount').textContent = 'GO';
      $('contestText').textContent = '计时已启动，可以开始作答';
      if (countdownTimer) {
        clearInterval(countdownTimer);
        countdownTimer = null;
      }
      setTimeout(() => modal.classList.add('hidden'), 1200);
    }
  };
  paint();
  if (countdownTimer) clearInterval(countdownTimer);
  countdownTimer = setInterval(paint, 250);
}

let shownCountdownEnd = 0;
async function pollContest() {
  if (!token) return;
  try {
    const cs = await get('/contest');
    const prev = contestState;
    contestState = cs.status;
    contestDeadline = Number(cs.deadline || 0);
    serverSkew = Number(cs.serverNow || Date.now()) - Date.now();
    updateRemain();
    applyPlayBar();
    if ($('danmakuEmpty') && !$('danmakuTrack').children.length) {
      $('danmakuEmpty').textContent = currentRole === 'player'
        ? (status === 'FINISHED' ? '弹幕区 · 交卷后可以和观众互动'
          : (danmakuLocked ? '比赛进行中，弹幕已锁定 · 交卷后解锁' : '弹幕区 · 这里显示观众弹幕'))
        : '弹幕区 · 你可以发弹幕给选手打气';
    }
    if ($('danmakuSend')) $('danmakuSend').disabled = !cs.danmakuOpen;
    if (cs.status === 'countdown' && cs.countdownEnd && shownCountdownEnd !== cs.countdownEnd) {
      // 用「这一场倒计时有没有显示过」判断：刷新页面时 contestState 一开始就是
      // countdown，按状态变化判断会永远不弹倒计时
      shownCountdownEnd = cs.countdownEnd;
      showCountdown(cs.countdownEnd);
    }
    if (cs.status !== prev) {
      await refreshStatus();
    }
    if ($('hint') && status === 'NOT_STARTED' && cs.status !== 'running') {
      $('hint').textContent = '等待裁判开启比赛';
    }
  } catch (e) {
    // 轮询失败下一轮再试
  }
}

function startContestPoll() {
  if (contestPolling) clearInterval(contestPolling);
  pollContest();
  pollQuick(true);
  contestPolling = setInterval(() => {
    pollContest();
    pollQuick(false);
  }, 1500);
}

/** 选手快捷消息：只允许四条固定文案 */
async function sendQuick(text) {
  try {
    const r = await post('/quick', { text });
    const no = Number((window.AppSession ? AppSession.no() : localStorage.getItem('sort_platform_no')) || 0);
    if (r && r.id) quickSeen.add(String(r.id));
    pushQuick({ no, text });
  } catch (e) {
    log('快捷消息发送失败：' + e.message);
  }
}

let quickSeen = new Set();
async function pollQuick(initial) {
  if (!token) return;
  try {
    const data = await get('/quick');
    (data.items || []).forEach((q) => {
      const key = String(q.id);
      if (quickSeen.has(key)) return;
      quickSeen.add(key);
      if (!initial) pushQuick(q);
    });
  } catch (e) {
    // 下一轮再拉
  }
}

document.querySelectorAll('.tab[data-panel]').forEach((t) => {
  t.addEventListener('click', () => switchPanel(t.getAttribute('data-panel')));
});

Array.from(document.querySelectorAll('.quick-btn')).forEach((b) => {
  b.addEventListener('click', () => sendQuick(b.getAttribute('data-text')));
});

/** 交卷后的选手弹幕：和观众互动，服务端会校验“必须已交卷” */
async function sendSay() {
  const input = $('sayInput');
  const btn = $('saySend');
  if (!input || !btn) return;
  const text = input.value.trim();
  if (!text) return;
  btn.disabled = true;
  if ($('sayHint')) $('sayHint').textContent = '发送中…';
  try {
    const r = await post('/comments', { text, kind: 'danmaku' });
    const c = r && r.comment;
    if (c) {
      seenComments.add(String(c.id));
      pushDanmaku(c);
    }
    input.value = '';
    if ($('sayHint')) $('sayHint').textContent = '已发送，观众可以看到';
  } catch (e) {
    if ($('sayHint')) $('sayHint').textContent = e.message || '发送失败';
  } finally {
    btn.disabled = false;
  }
}

if ($('saySend')) $('saySend').onclick = sendSay;
if ($('sayInput')) {
  $('sayInput').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') sendSay();
  });
}

$('watchBtn').onclick = () => {
  if (status !== 'FINISHED') {
    log('本人完赛前不能观看其他选手，避免相互提示。');
    return;
  }
  // 新标签页拿不到本标签页的 sessionStorage，用 ?t= 把身份交过去，打开即已登录
  const q = new URLSearchParams({ t: token, role: currentRole, no: String(AppSession ? AppSession.no() : '') });
  window.open('/watch?' + q.toString(), '_blank');
};
$('commentInput').addEventListener('keydown', (e) => {
  if (e.key === 'Enter') sendComment('comment');
});

// ---------------- 排行榜 ----------------

$('boardBtn').onclick = async () => {
  $('boardModal').classList.remove('hidden');
  $('boardBody').textContent = '加载中…';
  try {
    const list = await get('/leaderboard');
    if (!list.length) {
      $('boardBody').textContent = '暂无提交记录。';
      return;
    }
    let html = '<table class="board-table"><tr>'
      + '<th>名次</th><th>选手号</th><th>账号</th><th>总分</th>'
      + '<th>编码时长</th><th>运行时长</th><th>基础分</th><th>编码速率分</th><th>效率分</th><th>交卷时间</th>'
      + '</tr>';
    list.forEach((r) => {
      const eff = !r.correct ? '—'
        : (r.efficiencyReady ? Number(r.efficiencyScore).toFixed(1) : '评测中');
      const run = !r.correct ? '—'
        : (r.efficiencyReady ? Number(r.runMillis).toFixed(1) + ' ms' : '评测中');
      html += '<tr>'
        + '<td>' + r.rank + '</td>'
        + '<td>' + String(r.no).padStart(2, '0') + '</td>'
        + '<td class="mono">' + r.username + '</td>'
        + '<td><b>' + Number(r.score).toFixed(1) + '</b></td>'
        + '<td class="mono">' + fmtTime(Number(r.codingSeconds || 0)) + '</td>'
        + '<td class="mono">' + run + '</td>'
        + '<td>' + Number(r.baseScore || 0).toFixed(1) + '</td>'
        + '<td>' + Number(r.codingScore || 0).toFixed(1) + '</td>'
        + '<td>' + eff + '</td>'
        + '<td class="mono">' + (r.finishedAt || '') + '</td>'
        + '</tr>';
    });
    html += '</table>';
    $('boardBody').innerHTML = html;
  } catch (e) {
    if (e.kicked) return;   // 被顶号已经弹窗了，别再覆盖
    $('boardBody').textContent = '加载失败：' + e.message;
  }
};

$('boardClose').onclick = () => $('boardModal').classList.add('hidden');

// ---------------- 修改密码 / 忘记密码 ----------------

/** 打开修改密码弹窗；已经设过密保的不再重复要求填写 */
function openPassModal() {
  $('pmUser').value = currentUser || $('username').value.trim();
  $('pmOld').value = '';
  $('pmNew').value = '';
  ['pmQ1', 'pmA1', 'pmQ2', 'pmA2'].forEach((id) => { $(id).value = ''; });
  $('pmMsg').textContent = '';
  $('pmSecBox').classList.remove('hidden');
  $('passModal').classList.remove('hidden');
  if (token) {
    get('/user/sec').then((d) => {
      if (d.hasSec) $('pmSecBox').classList.add('hidden');
    }).catch(() => {});
  }
}

async function submitPass() {
  const username = $('pmUser').value.trim();
  const oldPass = $('pmOld').value.trim();
  const newPass = $('pmNew').value.trim();
  const needSec = !$('pmSecBox').classList.contains('hidden');
  const params = { username, oldPass, newPass };
  if (needSec) {
    params.q1 = $('pmQ1').value.trim();
    params.a1 = $('pmA1').value.trim();
    params.q2 = $('pmQ2').value.trim();
    params.a2 = $('pmA2').value.trim();
    if (!params.q1 || !params.a1 || !params.q2 || !params.a2) {
      $('pmMsg').textContent = '首次修改密码，两道密保问题和答案都要填';
      return;
    }
  }
  if (!username || !oldPass || !newPass) {
    $('pmMsg').textContent = '账号、原密码、新密码都要填';
    return;
  }
  $('pmOk').disabled = true;
  $('pmMsg').textContent = '提交中…';
  try {
    await post('/user/pass', params);
    $('pmMsg').textContent = '密码已修改，下次登录请用新密码';
    setTimeout(() => $('passModal').classList.add('hidden'), 900);
    if ($('password')) $('password').value = '';
  } catch (e) {
    $('pmMsg').textContent = e.message || '修改失败';
  } finally {
    $('pmOk').disabled = false;
  }
}

/** 忘记密码：先按账号取密保问题，答对两道再换新密码 */
async function loadQuiz() {
  const username = $('rsUser').value.trim();
  if (!username) {
    $('rsMsg').textContent = '请输入账号';
    return;
  }
  $('rsMsg').textContent = '查询中…';
  try {
    const res = await fetch(API + '/reset/quiz?username=' + encodeURIComponent(username));
    const data = await res.json();
    if (!res.ok) {
      $('rsMsg').textContent = data.error || '查询失败';
      return;
    }
    $('rsQ1Label').textContent = '密保问题一：' + data.q1;
    $('rsQ2Label').textContent = '密保问题二：' + data.q2;
    $('rsQuizBox').classList.remove('hidden');
    $('rsMsg').textContent = '答对两道密保即可重置密码';
  } catch (e) {
    $('rsMsg').textContent = '无法连接后端';
  }
}

async function submitReset() {
  const username = $('rsUser').value.trim();
  const a1 = $('rsA1').value.trim();
  const a2 = $('rsA2').value.trim();
  const newPass = $('rsNew').value.trim();
  if (!a1 || !a2 || !newPass) {
    $('rsMsg').textContent = '两道答案和新密码都要填';
    return;
  }
  $('rsOk').disabled = true;
  $('rsMsg').textContent = '提交中…';
  try {
    const res = await fetch(API + '/reset/pass', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
      body: new URLSearchParams({ username, a1, a2, newPass }).toString()
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      $('rsMsg').textContent = data.error || '重置失败';
      return;
    }
    $('rsMsg').textContent = '密码已重置，请用新密码登录';
    setTimeout(() => $('resetModal').classList.add('hidden'), 900);
    if ($('password')) $('password').value = '';
  } catch (e) {
    $('rsMsg').textContent = '无法连接后端';
  } finally {
    $('rsOk').disabled = false;
  }
}

$('linkPass').onclick = openPassModal;
$('pmCancel').onclick = () => $('passModal').classList.add('hidden');
$('pmOk').onclick = submitPass;

$('linkReset').onclick = () => {
  $('rsUser').value = $('username').value.trim();
  $('rsQuizBox').classList.add('hidden');
  $('rsMsg').textContent = '';
  $('resetModal').classList.remove('hidden');
};
$('rsQuizBtn').onclick = loadQuiz;
$('rsCancel').onclick = () => $('resetModal').classList.add('hidden');
$('rsOk').onclick = submitReset;

// ---------------- 启动 ----------------

(async function boot() {
  if (!token) return;
  // 不再按本地存的角色猜：/me 返回什么就是什么，观众由 enterApp 统一转去观赛页
  try {
    await enterApp();
  } catch (e) {
    if (!e.kicked) logout(true);
  }
})();
