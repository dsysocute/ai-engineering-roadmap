import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 应用上下文：全局状态（会话、令牌、计时、线程池）+ 各接口共用的业务工具方法。
 *
 * 拆分说明：Main 只保留启动、路由与静态文件；接口按域拆到 AuthApi / PlayApi / WatchApi / CommentApi /
 * AdminApi / JudgeApi / SuperApi。这些类都继承 App，直接复用这里的静态状态与工具方法，
 * 因此没有实例、没有依赖注入，调用方式与拆分前一致。
 */
class App extends Http {

    /** 四句固定的快捷消息，选手只能从这几句里选 */
    static final String[] QUICK_TEXTS = {
        "赛出风格", "友谊第一", "享受比赛", "勇夺第二吧各位",
    };

    static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    static final long TEST_COOLDOWN_MS = 1000L;

    static final String ADMIN_PASS = System.getenv().getOrDefault("ADMIN_PASS", "sort2026#admin");
    static final int ADMIN_MAX_FAILS = 8;
    static final long ADMIN_LOCK_MS = 60_000L;
    static final AtomicInteger adminFails = new AtomicInteger(0);
    static volatile long adminLockUntil = 0L;

    static Store store;
    static final Map<String, Session> sessions = new ConcurrentHashMap<>();
    static final Set<String> adminTokens = ConcurrentHashMap.newKeySet();
    /** 管理员 token → 是谁登的（master = 主口令，其余是被授予权限的学号） */
    static final Map<String, String> adminTokenUsers = new ConcurrentHashMap<>();

    /** 账号 → 当前唯一有效的 token，用来做「同账号顶号」 */
    static final Map<String, String> activeToken = new ConcurrentHashMap<>();
    /** 被顶掉/失效的 token → 失效时刻，让前端能分清「被顶号」和「普通过期」 */
    static final Map<String, Long> killedTokens = new ConcurrentHashMap<>();
    /** 账号 → 第一次点「开始」的时刻。放在会话外面，重登、被顶都不会重置计时 */
    static final Map<String, Long> startTimes = new ConcurrentHashMap<>();
    /** 正在判卷的账号，防止同一个人连点两次交卷 */
    static final Set<String> finishingAccounts = ConcurrentHashMap.newKeySet();
    static final long KILLED_TOKEN_TTL_MS = 10 * 60 * 1000L;
    static volatile long lastKilledClean = 0L;

    /** 发言限流：账号 → 上一次发言时刻，防止刷屏；评论要快，弹幕略慢 */
    static final Map<String, Long> lastCommentAt = new ConcurrentHashMap<>();
    static final long COMMENT_CD_MS = 500L;
    static final long DANMAKU_CD_MS = 1500L;
    /** 快捷消息：同一句/同一人 3 秒内只放一条，避免连续闪屏 */
    static final long QUICK_CD_MS = 3000L;
    static final int COMMENT_MAX_LEN = 120;

    /** 在线判定：账号 → 最后一次带着有效令牌请求的时刻（页面轮询会持续刷新） */
    static final Map<String, Long> lastSeen = new ConcurrentHashMap<>();
    static final long ONLINE_WINDOW_MS = 60_000L;

    /** 观赛用的实时代码：选手端每 3 秒上报一次，只在内存里留最近十几版 */
    static final Map<String, List<CodeSnap>> liveCodes = new ConcurrentHashMap<>();
    static final int LIVE_CODE_KEEP = 14;
    /** 观赛端实时取代码：直接给最新一版（公平性由弹幕防提示拦截保障） */
    static final int LIVE_CODE_MAX_CHARS = 60000;

    /** 一版代码快照：内容 + 收到时刻 */
    static final class CodeSnap {
        final String code;
        final long at;

        CodeSnap(String code, long at) {
            this.code = code;
            this.at = at;
        }
    }

    /**
     * 赛中防提示词表：比赛进行中，弹幕里出现这些内容就是在给选手递思路。
     * 只拒发、不计违规；赛后的正常讨论不受影响。
     */
    static final String[] PROMPT_BLOCK = {
        // 算法与数据结构名
        "快排", "快速排序", "归并", "堆排", "堆排序", "冒泡", "插入排序", "选择排序",
        "桶排", "基数排序", "计数排序", "希尔", "递归", "分治", "双指针", "三路",
        "哨兵", "基准值", "pivot", "partition", "arrays", "collections", "timsort",
        "复杂度", "logn", "nlogn",
        // 明确的写法指导 / 纠错
        "改成", "换成", "应该用", "试试这", "建议用", "建议你", "写错", "错在", "漏了",
        "少了个", "加个", "删掉", "你这里", "第几行", "行号", "越界", "边界条件",
        "空指针", "编译错", "会超时", "超时了", "过不了", "通过不了",
    };

    /** 违规词表：辱骂、侮辱与违规聚集类，命中即计违规并禁言 */
    static final String[] ABUSE_BLOCK = {
        "垃圾", "废物", "太菜", "真菜", "滚吧", "滚开", "闭嘴", "别写了", "放弃吧",
        "退赛", "作弊", "抄一下", "关机", "断网",
        "傻逼", "傻b", "煞笔", "智障", "脑残", "去死", "狗东西", "贱人", "婊子",
        "尼玛", "cnm", "nmsl", "fuck", "shit", "bitch", "妈的", "逼",
        "法轮", "台独", "藏独", "疆独", "港独", "分裂国家", "游行示威",
    };

    /** 去空白、转小写后做包含匹配 */
    static String hitWord(String[] words, String text) {
        String s = text.replaceAll("[ \\t\\u3000]", "").toLowerCase();
        for (String w : words) {
            if (s.contains(w)) {
                return w;
            }
        }
        return null;
    }

    static String promptBlockedWord(String text) {
        return hitWord(PROMPT_BLOCK, text);
    }

    static String abuseBlockedWord(String text) {
        return hitWord(ABUSE_BLOCK, text);
    }

    // ---------------- 赛事状态 ----------------

    /** 赛事全局状态键：idle 未开始 / countdown 倒计时 / running 进行中 / finished 已结束 */
    static final String K_CONTEST_STATUS = "contest.status";
    static final String K_CONTEST_COUNTDOWN_END = "contest.countdownEnd";
    static final String K_CONTEST_START_AT = "contest.startAt";
    static final String K_CONTEST_END_AT = "contest.endAt";
    /** 裁判开赛时设置的比赛时长与截止时刻（毫秒时间戳，0 表示没设） */
    static final String K_CONTEST_DURATION = "contest.durationMin";
    static final String K_CONTEST_DEADLINE = "contest.deadline";
    /** 裁判口令：只负责开赛与强制结束，与后台管理员是两套身份 */
    static final String JUDGE_PASS = System.getenv().getOrDefault("JUDGE_PASS", "abc2511298");
    static final Set<String> judgeTokens = ConcurrentHashMap.newKeySet();
    /** 总管（开发者）单独账户：只有总管能授予、撤销管理员 */
    static final String SUPER_USER = System.getenv().getOrDefault("SUPER_USER", "supervisor");
    static final String SUPER_PASS = System.getenv().getOrDefault("SUPER_PASS", "super2026");
    static final Set<String> superTokens = ConcurrentHashMap.newKeySet();
    /** 倒计时长度：裁判点开赛后先给 10 秒准备 */
    static final long CONTEST_COUNTDOWN_MS = 10_000L;
    /** 快照式快捷消息：只在内存里留最近 30 条，5 秒后前端不再展示，不落库 */
    static final int QUICK_KEEP = 30;
    static final long QUICK_LIVE_MS = 6000L;
    static final Map<String, Object[]> quickMsgs = new ConcurrentHashMap<>();
    static final AtomicInteger quickSeq = new AtomicInteger(0);
    /** 禁言档位：30 秒 / 5 分钟 / 1 小时 / 1 天 / 10 年 */
    static final long[] MUTE_STEPS_MS = {
        30_000L, 300_000L, 3_600_000L, 86_400_000L, 315_360_000_000L,
    };
    /** 连续 24 小时没有新违规，违规次数清零 */
    static final long MUTE_RESET_MS = 86_400_000L;

    /** 同时最多 2 个判题，其余排队，避免把机器打满 */
    static final Semaphore JUDGE_SLOTS = new Semaphore(2, true);
    /** 后台效率评测线程池，最多 2 个任务同时跑 */
    static final ExecutorService EFFICIENCY_POOL = Executors.newFixedThreadPool(2);
    /** 已排队等待效率评测的账号，防止同一份卷子重复排队 */
    static final Set<String> efficiencyQueued = ConcurrentHashMap.newKeySet();
    /** 裁判强制收卷用的线程池：几十个人同时收卷，不能占着判题信号量排队 */
    static final ExecutorService FORCE_POOL = Executors.newFixedThreadPool(4);

    static final class Session {
        String username;
        int no;
        /** player 选手 / viewer 观众，登录时选定，整个会话不变 */
        String role = "player";
        long startMillis;
        long lastTestMillis;
        boolean finished;
        boolean judging;
        /** 最后一次带着本令牌请求的时刻，用来回收长期不动的会话 */
        long lastSeenMillis;
    }

    /** 会话空闲超过 12 小时就回收：比赛当天秒级轮询，不会误伤 */
    static final long SESSION_IDLE_MS = 12 * 3600 * 1000L;
    static volatile long lastSessionSweep = 0L;

    static String commentJson(Store.Comment c) {
        return "{" + Json.inum("id", c.id) + ","
                + Json.str("role", c.role) + ","
                + Json.inum("no", c.no) + ","
                + Json.str("username", c.username) + ","
                + Json.str("text", c.text) + ","
                + Json.str("time", c.time) + ","
                + Json.str("kind", c.kind) + "}";
    }

    /** 记一版代码：内容没变就不重复存，队列最多留 LIVE_CODE_KEEP 版 */
    static void pushLiveCode(String username, String code) {
        long now = System.currentTimeMillis();
        List<CodeSnap> list = liveCodes.computeIfAbsent(username, k -> new ArrayList<>());
        synchronized (list) {
            if (!list.isEmpty() && list.get(list.size() - 1).code.equals(code)) {
                return;
            }
            list.add(new CodeSnap(code, now));
            while (list.size() > LIVE_CODE_KEEP) {
                list.remove(0);
            }
        }
    }

    /** 统一清理发言内容：去换行、压空白、截断到上限 */
    static String cleanCommentText(String raw) {
        String text = (raw == null ? "" : raw).replace('\r', ' ').replace('\n', ' ').trim();
        if (text.length() > COMMENT_MAX_LEN) {
            text = text.substring(0, COMMENT_MAX_LEN);
        }
        return text;
    }

    static boolean isAdmin(HttpExchange ex) {
        String token = tokenOf(ex);
        if (!adminTokens.contains(token)) {
            return false;
        }
        // 被撤销管理员后，旧 token 不能继续用：每次都回查当前权限
        String who = adminTokenUsers.get(token);
        if (who == null) {
            return false;
        }
        return "master".equals(who) || isAdminAccount(who);
    }

    static boolean isSuper(HttpExchange ex) {
        return superTokens.contains(tokenOf(ex));
    }

    /** 后台数据类接口：管理员和总管都能看，只有「设置管理员」这一项留给总管 */
    static boolean isAdminOrSuper(HttpExchange ex) {
        return isAdmin(ex) || isSuper(ex);
    }

    /** 选手专属动作的闸门：观众身份直接回 403 */
    static boolean requirePlayer(HttpExchange ex, Session s, String msg) throws IOException {
        if ("player".equals(s.role)) {
            return true;
        }
        error(ex, 403, msg);
        return false;
    }

    /** 权限闸门：校验失败时已经写好 401 响应，调用方一句 if (!requireJudge(ex)) return; 即可 */
    static boolean requireAdmin(HttpExchange ex) throws IOException {
        if (isAdmin(ex)) {
            return true;
        }
        error(ex, 401, "没有权限");
        return false;
    }

    static boolean requireAdminOrSuper(HttpExchange ex) throws IOException {
        if (isAdminOrSuper(ex)) {
            return true;
        }
        error(ex, 401, "没有权限");
        return false;
    }

    static boolean requireJudge(HttpExchange ex) throws IOException {
        if (isJudge(ex)) {
            return true;
        }
        error(ex, 401, "没有裁判权限");
        return false;
    }

    /** 口令统一要求 4~20 位，四处改口令都走这里 */
    static boolean validPass(String pass) {
        return pass != null && pass.length() >= 4 && pass.length() <= 20;
    }

    /** 同一身份在冷却期内重复发言时返回 true */
    static boolean tooFast(String key, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long last = lastCommentAt.get(key);
        if (last != null && now - last < cooldownMs) {
            return true;
        }
        lastCommentAt.put(key, now);
        return false;
    }

    /** 当前赛事状态：倒计时到点自动转进行中，全部选手完赛自动转已结束 */
    static String contestStatus() {
        String s = store.state(K_CONTEST_STATUS, "idle");
        if ("countdown".equals(s)) {
            long end = store.stateLong(K_CONTEST_COUNTDOWN_END, 0L);
            if (end > 0 && System.currentTimeMillis() >= end) {
                try {
                    store.setState(K_CONTEST_STATUS, "running");
                    store.setState(K_CONTEST_START_AT, String.valueOf(end));
                } catch (IOException ignore) {
                }
                // 裁判开赛后统一开始计时：没打开页面的选手也从这一刻起算
                for (String un : playerList()) {
                    if (!startTimes.containsKey(un)) {
                        startTimes.put(un, end);
                        try {
                            store.setState("start." + un, String.valueOf(end));
                        } catch (IOException ignore) {
                        }
                    }
                }
                return "running";
            }
        }
        if ("running".equals(s)) {
            if (allPlayersFinished()) {
                try {
                    store.setState(K_CONTEST_STATUS, "finished");
                    store.setState(K_CONTEST_END_AT, String.valueOf(System.currentTimeMillis()));
                } catch (IOException ignore) {
                }
                return "finished";
            }
            // 到点的第一件事就是收卷：未交卷的按当前代码判分，用时按截止时刻封顶
            long deadline = store.stateLong(K_CONTEST_DEADLINE, 0L);
            if (deadline > 0 && System.currentTimeMillis() >= deadline) {
                try {
                    ContestOps.finishAndCollect(deadline, "比赛时间到，按当前代码收卷");
                } catch (IOException ignore) {
                }
                return "finished";
            }
        }
        return s;
    }

    /** 报名名单：以选手身份登录过的人 */
    static List<String> playerList() {
        List<String> out = new ArrayList<>();
        for (String k : store.keysWithPrefix("player.")) {
            out.add(k.substring("player.".length()));
        }
        return out;
    }

    static boolean allPlayersFinished() {
        List<String> players = playerList();
        if (players.isEmpty()) {
            return false;
        }
        for (String un : players) {
            if (store.getResult(un) == null) {
                return false;
            }
        }
        return true;
    }

    static boolean isBanned(String username) {
        return store.stateLong("ban." + username, 0L) > System.currentTimeMillis();
    }

    static long muteUntil(String username) {
        return store.stateLong("mute." + username + ".until", 0L);
    }

    static long muteRemainMs(String username) {
        return Math.max(0L, muteUntil(username) - System.currentTimeMillis());
    }

    /** 禁言 N 分钟：直接写 until 时间戳 */
    static void muteFor(String username, long minutes) throws IOException {
        long ms = Math.max(1L, minutes) * 60_000L;
        store.setState("mute." + username + ".until", String.valueOf(System.currentTimeMillis() + ms));
    }

    /** 解禁并清空违规档位，下次违规从 30 秒重新算 */
    static void unmuteUser(String username) throws IOException {
        store.removeState("mute." + username + ".until");
        store.removeState("mute." + username + ".count");
        store.removeState("mute." + username + ".last");
    }

    /** 后台表单里的禁言分钟数，缺省 30 分钟 */
    static long minutesOf(Map<String, String> f) {
        try {
            return Long.parseLong(f.getOrDefault("minutes", "30").trim());
        } catch (Exception ignore) {
            return 30L;
        }
    }

    /** 命中违规词：累计次数并按档位禁言，返回本次禁言的毫秒数 */
    static long punishViolation(String username) throws IOException {
        long now = System.currentTimeMillis();
        long lastAt = store.stateLong("mute." + username + ".last", 0L);
        int count = (int) store.stateLong("mute." + username + ".count", 0);
        // 24 小时内没有新违规，档位从头开始
        if (lastAt > 0 && now - lastAt > MUTE_RESET_MS) {
            count = 0;
        }
        count++;
        long ms = MUTE_STEPS_MS[Math.min(count - 1, MUTE_STEPS_MS.length - 1)];
        store.setState("mute." + username + ".count", String.valueOf(count));
        store.setState("mute." + username + ".last", String.valueOf(now));
        store.setState("mute." + username + ".until", String.valueOf(now + ms));
        return ms;
    }

    static String muteText(long ms) {
        if (ms >= 86_400_000L * 365L * 5L) {
            return "10 年";
        }
        if (ms >= 86_400_000L) {
            return (ms / 86_400_000L) + " 天";
        }
        if (ms >= 3_600_000L) {
            return (ms / 3_600_000L) + " 小时";
        }
        if (ms >= 60_000L) {
            return (ms / 60_000L) + " 分钟";
        }
        return Math.max(1, ms / 1000L) + " 秒";
    }

    /** 发言闸门：封号 / 禁言 / 违规词，命中即回错误并结束本次请求 */
    static boolean speechAllowed(HttpExchange ex, String username, String text) throws IOException {
        if (isBanned(username)) {
            error(ex, 403, "该账号已被封禁");
            return false;
        }
        long remain = muteRemainMs(username);
        if (remain > 0) {
            error(ex, 403, "账号处于禁言状态，还需等待 " + muteText(remain));
            return false;
        }
        if (abuseBlockedWord(text) != null) {
            long ms = punishViolation(username);
            error(ex, 403, "内容含违规词，已按规则禁言 " + muteText(ms) + "，如有异议请联系管理员");
            return false;
        }
        return true;
    }

    /** 学生密码：改过就用改过的，没改过还是统一的 123456 */
    static String passwordOf(String username) {
        String p = store.state("pass." + username, "");
        if (!p.isEmpty()) {
            return p;
        }
        Store.User u = store.findUser(username);
        return u == null ? "" : u.password;
    }

    /** 管理员口令：默认就是被授予权限的那个学号 */
    static String adminPassOf(String username) {
        String p = store.state("adminpass." + username, "");
        return p.isEmpty() ? username : p;
    }

    static boolean isAdminAccount(String username) {
        return !store.state("admin." + username, "").isEmpty();
    }

    static String judgePass() {
        String p = store.state("judge.pass", "");
        return p.isEmpty() ? JUDGE_PASS : p;
    }

    static boolean hasSec(String username) {
        return !store.state("sec." + username + ".q1", "").isEmpty()
                && !store.state("sec." + username + ".a1", "").isEmpty();
    }

    static String superPass() {
        String p = store.state("super.pass", "");
        return p.isEmpty() ? SUPER_PASS : p;
    }

    /** 当前被指定的裁判学号；总管设置，全平台只有一个，避免两个裁判同时开赛打架 */
    static String judgeUser() {
        return store.state("judge.user", "");
    }

    /** 判违规的落盘动作：终止本场、记 0 分、留档事由。裁判直判与举报核实共用 */
    static void penalizeViolation(String un, String note, String scene) throws IOException {
        Store.User u = store.findUser(un);
        if (u == null) {
            return;
        }
        // 判罚前先把已有成绩存一份快照：申诉通过时可以还原，
        // 否则「先交卷再被判违规」的人零分会永久生效，申诉等于没有补救。
        // 同时记下他到底交没交过卷：交过 → 申诉通过只还原成绩；没交过 → 放回比赛区复赛。
        Store.ResultRecord prev = store.getResult(un);
        if (prev != null) {
            saveViolationSnapshot(un, prev);
            store.setState("violationSubmitted." + un, "1");
        } else {
            store.setState("violationSubmitted." + un, "0");
        }
        long now = System.currentTimeMillis();
        Long st = startTimes.get(un);
        long startMs = st == null ? 0L : st;
        long sec = startMs == 0L ? 0L : Math.max(0L, (now - startMs) / 1000L);
        // 记下判罚时刻和已用时长：申诉通过且他还没交过卷时，要按“判罚时还剩多少时间”复赛
        store.setState("violationAt." + un, String.valueOf(now));
        store.setState("violationUsedMs." + un, String.valueOf(startMs == 0L ? 0L : Math.max(0L, now - startMs)));

        Store.ResultRecord r = new Store.ResultRecord();
        r.username = un;
        r.no = u.no;
        r.score = 0.0;
        r.correct = false;
        r.codingSeconds = sec;
        r.codingMinutes = sec / 60L;
        r.caseTotal = JudgeEngine.correctnessCaseCount();
        r.caseOk = 0;
        r.efficiencyReady = false;
        r.efficiencyScore = 0.0;
        r.runMillis = 0.0;
        r.finishedAt = Store.now();
        r.codeSnapshot = latestCode(un);
        r.detail = scene + "，本场成绩记 0 分；违规事由：" + note + ";";
        store.saveResult(r);
        startTimes.remove(un);
        store.removeState("start." + un);
        store.setState("violation." + un, note);
        Session ss = sessionOf(un);
        if (ss != null) {
            ss.finished = true;
        }
    }

    /** 申诉通过时的统一入口：交过卷就恢复原成绩；没交过卷就恢复比赛资格（复赛） */
    static void onViolationAppealApproved(String un) throws IOException {
        store.removeState("violation." + un);
        boolean submitted = "1".equals(store.state("violationSubmitted." + un, ""));
        store.removeState("violationSubmitted." + un);
        store.removeState("violationAt." + un);
        store.removeState("violationUsedMs." + un);
        if (submitted) {
            restoreViolationSnapshot(un);
            return;
        }
        reinstatePlayer(un);
    }

    /**
     * 复赛：把还在答题时被判 0 分的选手放回比赛区。
     *
     * 时间口径（按你选的方案）：补回“判罚那一刻本场还剩多少时间”，
     * 从复赛这一刻起独立倒计时；被罚期间和申诉等待的时间都不算他的。
     * 已用时长保留（接着原来的计时走），个人截止时刻单独记在 reinstDeadline。
     */
    static void reinstatePlayer(String un) throws IOException {
        long now = System.currentTimeMillis();
        long penaltyAt = store.stateLong("violationAt." + un, now);
        long usedMs = store.stateLong("violationUsedMs." + un, 0L);
        long deadline = store.stateLong(K_CONTEST_DEADLINE, 0L);
        long leftMs = deadline > 0 ? Math.max(0L, deadline - penaltyAt) : 0L;
        long personalDeadline = deadline > 0 ? now + leftMs : 0L;

        long newStart = now - usedMs;
        startTimes.put(un, newStart);
        store.setState("start." + un, String.valueOf(newStart));
        store.setState("reinst." + un, String.valueOf(now));
        store.setState("reinstDeadline." + un, String.valueOf(personalDeadline));
        store.removeState("violation." + un);
        store.removeState("violationAt." + un);
        store.removeState("violationUsedMs." + un);
        store.removeState("violprev." + un);
        // 0 分记录删掉，人才会重新变成“作答中”
        store.deleteResult(un);
        for (Session s : sessions.values()) {
            if (un.equals(s.username) && "player".equals(s.role)) {
                s.finished = false;
                s.startMillis = newStart;   // 会话里缓存的是旧开始时刻，一起改掉
            }
        }
    }

    /** 复赛选手的个人截止时刻到了：只收他一个，按当前代码判分 */
    static void enforceReinstDeadline(Session s) {
        if (s == null || !"player".equals(s.role) || s.finished) {
            return;
        }
        long dl = store.stateLong("reinstDeadline." + s.username, 0L);
        if (dl <= 0 || System.currentTimeMillis() < dl || store.getResult(s.username) != null) {
            return;
        }
        ContestOps.collectOne(s.username, dl, "复赛时间到，按当前代码收卷");
        s.finished = true;
        try {
            store.removeState("reinst." + s.username);
            store.removeState("reinstDeadline." + s.username);
        } catch (IOException ignore) {
        }
    }

    /** 判罚前成绩快照：整行压成 TSV，Base64 后放进状态表，申诉通过时原样写回 */
    static void saveViolationSnapshot(String un, Store.ResultRecord r) throws IOException {
        String line = String.join("\t",
                r.username, String.valueOf(r.no),
                String.valueOf(r.score), String.valueOf(r.correct),
                String.valueOf(r.codingMinutes), String.valueOf(r.efficiencyScore),
                String.valueOf(r.caseOk), String.valueOf(r.caseTotal),
                oneLine(r.finishedAt), oneLine(r.detail),
                String.valueOf(r.efficiencyReady), oneLine(r.codeSnapshot),
                String.valueOf(r.codingSeconds), String.valueOf(r.runMillis));
        String b64 = Base64.getEncoder().encodeToString(line.getBytes(StandardCharsets.UTF_8));
        store.setState("violprev." + un, b64);
    }

    /** 申诉通过时还原判罚前的成绩；没有快照（判罚前就没成绩）返回 false */
    static boolean restoreViolationSnapshot(String un) throws IOException {
        String b64 = store.state("violprev." + un, "");
        if (b64.isEmpty()) {
            return false;
        }
        String[] p = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8).split("\t", -1);
        store.removeState("violprev." + un);
        if (p.length < 14) {
            return false;
        }
        Store.ResultRecord r = new Store.ResultRecord();
        r.username = p[0];
        r.no = (int) numOr(p[1], 0);
        r.score = numOr(p[2], 0);
        r.correct = Boolean.parseBoolean(p[3]);
        r.codingMinutes = numOr(p[4], 0);
        r.efficiencyScore = numOr(p[5], 0);
        r.caseOk = (int) numOr(p[6], 0);
        r.caseTotal = (int) numOr(p[7], 0);
        r.finishedAt = p[8];
        r.detail = p[9];
        r.efficiencyReady = Boolean.parseBoolean(p[10]);
        r.codeSnapshot = p[11];
        r.codingSeconds = (long) numOr(p[12], 0);
        r.runMillis = numOr(p[13], 0);
        store.saveResult(r);
        return true;
    }

    /** 快照里不能带换行/制表符，否则一行记录会被拆散 */
    static String oneLine(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }

    static double numOr(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    static boolean isJudge(HttpExchange ex) {
        String token = ex.getRequestHeaders().getFirst("X-Token");
        return token != null && judgeTokens.contains(token);
    }

    /** 踢掉某账号的所有在线会话（封号、删成绩时用） */
    static void killSessionsOf(String username) {
        for (Map.Entry<String, Session> e : sessions.entrySet()) {
            Session s = e.getValue();
            if (s.username.equals(username) && sessions.remove(e.getKey(), s)) {
                activeToken.remove(s.username + "#" + s.role, e.getKey());
            }
        }
        lastSeen.remove(username);
    }

    /**
     * 设为裁判/管理员之后：退出选手名单、清掉计时与复赛状态、踢掉他的选手会话。
     * 学生账号仍可登录，但只能以观众身份观赛，不参与本场排名。
     */
    static void demoteToViewer(String un) throws IOException {
        store.removeState("player." + un);
        store.removeState("start." + un);
        startTimes.remove(un);
        store.removeState("reinst." + un);
        store.removeState("reinstDeadline." + un);
        for (Map.Entry<String, Session> e : sessions.entrySet()) {
            Session s = e.getValue();
            if (un.equals(s.username) && "player".equals(s.role) && sessions.remove(e.getKey(), s)) {
                activeToken.remove(s.username + "#" + s.role, e.getKey());
            }
        }
    }

    /** 找到某账号当前的选手会话（强制收卷后要把它标记成已交卷） */
    static Session sessionOf(String username) {
        for (Session s : sessions.values()) {
            if (s.username.equals(username) && "player".equals(s.role)) {
                return s;
            }
        }
        return null;
    }

    /** 最新一版代码快照，没有就返回 null */
    static String latestCode(String username) {
        List<CodeSnap> list = liveCodes.get(username);
        if (list == null) {
            return null;
        }
        synchronized (list) {
            return list.isEmpty() ? null : list.get(list.size() - 1).code;
        }
    }

    /**
     * 封管理员或裁判之后，把他从「工作人员 + 选手」降成「观众」：
     * 撤销后台身份与口令、清掉报名，之后只能以观众身份登录观赛。
     */
    static void demoteStaffOnBan(String target) throws IOException {
        if (isAdminAccount(target)) {
            store.removeState("admin." + target);
            store.removeState("adminpass." + target);
            adminTokens.removeIf(t -> target.equals(adminTokenUsers.get(t)));
            adminTokenUsers.entrySet().removeIf(e -> target.equals(e.getValue()));
        }
        if (target.equals(judgeUser())) {
            store.removeState("judge.user");
            store.removeState("judge.pass");
            judgeTokens.clear();
        }
        store.removeState("player." + target);
        startTimes.remove(target);
        store.removeState("start." + target);
        store.removeState("violation." + target);
    }

    /** 异常压成一行短说明，塞进 detail 里方便老师判断是哪种失败 */
    static String shortError(Throwable t) {
        String msg = t.getClass().getSimpleName();
        if (t.getMessage() != null) {
            msg += ": " + t.getMessage().replace('\n', ' ').replace('\r', ' ');
        }
        return msg.length() > 100 ? msg.substring(0, 100) + "…" : msg;
    }

    static String resultJson(Store.ResultRecord r) {
        // codingSeconds 是交卷那一刻定格的计时秒数；编码分、基础分、运行耗时都直接给出，
        // 前端与后台只需要展示，不重复计算同一套规则。
        double codingScore = r.correct ? ScoreCalculator.codingScoreSeconds(Store.codingSecondsOf(r)) : 0.0;
        return "{" + Json.inum("no", r.no) + ","
                + Json.str("username", r.username) + ","
                + Json.num("score", r.score) + ","
                + Json.bool("correct", r.correct) + ","
                + Json.num("codingMinutes", r.codingMinutes) + ","
                + Json.inum("codingSeconds", r.codingSeconds) + ","
                + Json.num("codingScore", codingScore) + ","
                + Json.num("baseScore", r.correct ? 60.0 : 0.0) + ","
                + Json.num("efficiencyScore", r.efficiencyScore) + ","
                + Json.num("runMillis", r.runMillis) + ","
                + Json.bool("efficiencyReady", r.efficiencyReady) + ","
                + Json.inum("caseOk", r.caseOk) + ","
                + Json.inum("caseTotal", r.caseTotal) + ","
                + Json.str("finishedAt", r.finishedAt) + ","
                + Json.str("detail", r.detail) + "}";
    }

    static long elapsedSeconds(Session s) {
        if (s.startMillis == 0) {
            return 0;
        }
        return Math.max(0, (System.currentTimeMillis() - s.startMillis) / 1000);
    }

    static Session auth(HttpExchange ex) throws IOException {
        String token = ex.getRequestHeaders().getFirst("X-Token");
        if (token == null) {
            token = "";
        }
        Session s = sessions.get(token);
        if (s == null) {
            Long killedAt = killedTokens.get(token);
            if (killedAt != null) {
                // 这个 token 是被同一账号的新登录顶下来的：前端要弹「账号已在别处登录」，
                // 不能只说一句「登录过期」让人以为是网络问题。
                errorCode(ex, 401, "KICKED", "该账号已在其他终端登录，本次会话已下线");
            } else {
                error(ex, 401, "登录已失效（服务可能重启过），请重新登录");
            }
            return null;
        }
        cleanKilledTokens();
        // 封号不再踢掉会话：封号账号会被强制降为观众，只能观赛与申诉，
        // 发言和参赛另有 isBanned 拦截，不需要在鉴权这一步直接断掉。
        sweepIdleSessions();
        lastSeen.put(s.username, System.currentTimeMillis());
        s.lastSeenMillis = System.currentTimeMillis();
        // 复赛选手的个人倒计时到点：收卷并置为已结束
        if ("player".equals(s.role)) {
            enforceReinstDeadline(s);
        }
        return s;
    }

    /**
     * 回收长期没人用的会话。
     * 以前 sessions 只增不减（被顶号、关页面、换设备都留着），
     * 一学期跑下来会缓慢涨内存；这里按最后活动时间清掉 12 小时没动静的。
     */
    static void sweepIdleSessions() {
        long now = System.currentTimeMillis();
        if (now - lastSessionSweep < 5 * 60 * 1000L) {
            return;
        }
        lastSessionSweep = now;
        for (Map.Entry<String, Session> e : sessions.entrySet()) {
            Session s = e.getValue();
            if (s.lastSeenMillis > 0 && now - s.lastSeenMillis > SESSION_IDLE_MS) {
                if (sessions.remove(e.getKey(), s)) {
                    activeToken.remove(s.username + "#" + s.role, e.getKey());
                }
            }
        }
    }

    /**
     * 在线判定：一分钟内有过带令牌请求就算在线。
     * 选手端/观赛端都是秒级轮询，关掉窗口或断网后自然掉线，不会像以前那样
     * 只看 activeToken 导致所有登录过的人永远显示在线。
     */
    static boolean isOnline(String username) {
        Long t = lastSeen.get(username);
        return t != null && System.currentTimeMillis() - t <= ONLINE_WINDOW_MS;
    }

    /** 顺手清理过期的失效标记，别让 killedTokens 无限增长 */
    static void cleanKilledTokens() {
        long now = System.currentTimeMillis();
        if (now - lastKilledClean < 5 * 60 * 1000L) {
            return;
        }
        lastKilledClean = now;
        killedTokens.entrySet().removeIf(e -> now - e.getValue() > KILLED_TOKEN_TTL_MS);
    }
}
