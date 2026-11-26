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

/** 裁判控制台：登录、开赛、强制结束、判罚、禁言/解禁。 */
class JudgeApi extends App {

    static void judgeLogin(HttpExchange ex) throws IOException {
        Map<String, String> f = form(ex);
        String pass = f.getOrDefault("password", "").trim();
        String ju = judgeUser();
        if (ju.isEmpty()) {
            error(ex, 403, "还没有指定裁判，请总管在总控台指定一位同学");
            return;
        }
        if (!judgePass().equals(pass)) {
            error(ex, 401, "裁判口令不正确");
            return;
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        judgeTokens.add(token);
        Store.User u = store.findUser(ju);
        String who = u == null ? ju : ("选手" + String.format("%02d", u.no));
        ok(ex, "{" + Json.str("token", token) + "," + Json.str("role", "judge")
                + "," + Json.str("username", ju) + "," + Json.str("who", who) + "}");
    }

    /** 裁判自己改口令：输入原口令 + 新口令 */
    static void judgePassChange(HttpExchange ex) throws IOException {
        if (!requireJudge(ex)) {
            return;
        }
        Map<String, String> f = form(ex);
        if (!judgePass().equals(f.getOrDefault("oldPass", "").trim())) {
            error(ex, 400, "原口令不对");
            return;
        }
        String np = f.getOrDefault("newPass", "").trim();
        if (!validPass(np)) {
            error(ex, 400, "新口令请控制在 4 ~ 20 位");
            return;
        }
        store.setState("judge.pass", np);
        okTrue(ex);
    }

    /**
     * 裁判判罚：只终止这一个选手的本场作答，成绩直接记 0 分，
     * 违规事由由裁判自己填写，跟着成绩一起留档。
     */
    static void judgeViolate(HttpExchange ex) throws IOException {
        // 判违规是裁判与总管的权限；管理员只能禁言、举报，不能给选手记 0 分
        if (!isJudge(ex) && !isSuper(ex)) {
            error(ex, 401, "没有裁判权限");
            return;
        }
        Map<String, String> f = form(ex);
        String un = field(f, "username");
        String note = cleanCommentText(f.getOrDefault("note", ""));
        Store.User u = store.findUser(un);
        if (u == null) {
            error(ex, 404, "没有这个账号");
            return;
        }
        if (note.isEmpty()) {
            error(ex, 400, "请填写违规事由");
            return;
        }
        if (note.length() > 60) {
            note = note.substring(0, 60);
        }
        penalizeViolation(un, note, "裁判判罚违规");
        ok(ex, "{" + Json.bool("ok", true) + ","
                + Json.str("username", un) + ","
                + Json.str("note", note) + "}");
    }

    /** 裁判可以禁言 / 解禁；设管理员是总管的权限，裁判不再涉及 */
    static void judgeUserOp(HttpExchange ex) throws IOException {
        if (!requireJudge(ex)) {
            return;
        }
        // 权限重排后裁判不再管禁言：禁言归管理员，裁判只负责开赛/结束与判违规
        error(ex, 403, "裁判没有禁言权限，禁言由管理员处理");
    }

    /** 开启比赛：清掉上一场成绩与内存计时，进入 10 秒倒计时 */
    static void judgeStartContest(HttpExchange ex) throws IOException {
        if (!requireJudge(ex)) {
            return;
        }
        // 开赛前先把上一轮成绩另存一份，不直接抹掉历史记录
        try {
            Path old = Paths.get("data", "results.tsv");
            if (Files.exists(old)) {
                Files.copy(old, Paths.get("data", "results.backup-" + System.currentTimeMillis() + ".tsv"));
            }
        } catch (Exception ignore) {
        }
        store.clearAll();
        startTimes.clear();
        // 上一场的违规标记与判罚快照都属于上一场，开赛时一并清掉，
        // 否则新一场申诉可能把早就过期的备份成绩又写回来。
        for (String key : store.keysWithPrefix("violprev.")) {
            store.removeState(key);
        }
        for (String key : store.keysWithPrefix("violation.")) {
            store.removeState(key);
        }
        for (String key : store.keysWithPrefix("start.")) {
            store.removeState(key);
        }
        liveCodes.clear();
        quickMsgs.clear();
        efficiencyQueued.clear();
        for (Session s : sessions.values()) {
            s.startMillis = 0L;
            s.finished = false;
            s.judging = false;
        }
        long now = System.currentTimeMillis();
        // 裁判可以设置本场时长（分钟），默认 120；到点自动收卷，用时按截止时刻封顶
        Map<String, String> f = form(ex);
        long minutes = 120L;
        try {
            long v = Long.parseLong(field(f, "minutes"));
            if (v >= 1) {
                minutes = v;
            }
        } catch (Exception ignore) {
        }
        if (minutes > 600L) {
            minutes = 600L;
        }
        long countdownEnd = now + CONTEST_COUNTDOWN_MS;
        long deadline = countdownEnd + minutes * 60_000L;
        store.setState(K_CONTEST_STATUS, "countdown");
        store.setState(K_CONTEST_COUNTDOWN_END, String.valueOf(countdownEnd));
        store.setState(K_CONTEST_START_AT, "0");
        store.setState(K_CONTEST_END_AT, "0");
        store.setState(K_CONTEST_DURATION, String.valueOf(minutes));
        store.setState(K_CONTEST_DEADLINE, String.valueOf(deadline));
        ok(ex, "{" + Json.bool("ok", true) + ","
                + Json.inum("countdownEnd", countdownEnd) + ","
                + Json.inum("durationMin", minutes) + ","
                + Json.inum("deadline", deadline) + "}");
    }

    /** 强制结束：所有未交卷选手按当前代码提交（没写过代码的记白卷） */
    static void judgeForceEnd(HttpExchange ex) throws IOException {
        if (!requireJudge(ex)) {
            return;
        }
        int total = playerList().size();
        ContestOps.finishAndCollect(0L, "裁判强制结束，选手未完赛，按当前代码收卷");
        ok(ex, "{" + Json.bool("ok", true) + "," + Json.inum("total", total) + "}");
    }
}
