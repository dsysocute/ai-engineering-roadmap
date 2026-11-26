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

/** 观赛与公共数据：观赛列表、实时代码、弹幕、赛事状态、排行榜。 */
class WatchApi extends App {

    static void leaderboard(HttpExchange ex) throws IOException {
        List<Store.ResultRecord> list = store.leaderboard();
        StringBuilder sb = new StringBuilder("[");
        int rank = 1;
        for (Store.ResultRecord r : list) {
            // 越界分数（旧口径污染、手改文件）不进排行榜，避免比赛当天出现假名次
            if (r.score < 0 || r.score > 100) {
                continue;
            }
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append('{')
                    .append(Json.inum("rank", rank++)).append(',')
                    .append(Json.inum("no", r.no)).append(',')
                    .append(Json.str("username", r.username)).append(',')
                    .append(Json.num("score", r.score)).append(',')
                    .append(Json.inum("codingSeconds", r.codingSeconds)).append(',')
                    .append(Json.num("runMillis", r.runMillis)).append(',')
                    .append(Json.num("baseScore", r.correct ? 60.0 : 0.0)).append(',')
                    .append(Json.num("codingScore", r.correct ? ScoreCalculator.codingScoreSeconds(Store.codingSecondsOf(r)) : 0.0)).append(',')
                    .append(Json.num("efficiencyScore", r.efficiencyScore)).append(',')
                    .append(Json.bool("efficiencyReady", r.efficiencyReady)).append(',')
                    .append(Json.bool("correct", r.correct)).append(',')
                    .append(Json.str("finishedAt", r.finishedAt))
                    .append('}');
        }
        ok(ex, sb.append(']').toString());
    }

    /**
     * 观众拉某位选手的代码：直接返回最新一版。
     * 公平性靠「比赛中弹幕关闭 + 防提示词拦截」保证，不再靠时间延迟。
     */
    static void watchCodeApi(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            error(ex, 405, "只支持 GET");
            return;
        }
        // 实时代码不是公开数据：至少要有登录态；正在比赛的选手不能看别人的代码。
        // 观众、裁判、总管、后台管理员不受影响（观赛页仍然「不设延迟」）。
        if (!isJudge(ex) && !isSuper(ex) && !isAdmin(ex)) {
            Session viewer = auth(ex);
            if (viewer == null) {
                return;
            }
            if ("player".equals(viewer.role)
                    && !viewer.finished
                    && store.getResult(viewer.username) == null) {
                error(ex, 403, "本人交卷后才能查看其他选手的实时代码");
                return;
            }
        }
        long no = queryLong(ex, "no", 0L);
        Store.User target = null;
        for (Store.User u : store.allUsers()) {
            if (u.no == no) {
                target = u;
                break;
            }
        }
        if (target == null) {
            error(ex, 404, "没有这个选手");
            return;
        }
        String code = null;
        long at = 0L;
        List<CodeSnap> list = liveCodes.get(target.username);
        if (list != null) {
            synchronized (list) {
                // 弹幕里已经做了防提示拦截，这里直接给最新一版，不再做时间延迟
                if (!list.isEmpty()) {
                    CodeSnap last = list.get(list.size() - 1);
                    code = last.code;
                    at = last.at;
                }
            }
        }
        if (code == null) {
            ok(ex, "{" + Json.inum("no", no) + ","
                    + Json.bool("ready", false) + ","
                    + Json.str("code", "") + "}");
            return;
        }
        ok(ex, "{" + Json.inum("no", no) + ","
                + Json.bool("ready", true) + ","
                + Json.inum("at", at) + ","
                + Json.str("code", code) + "}");
    }

    /** 观赛页只给一句摘要：压成一行，去掉用例明细，超长截断 */
    static String watchDetailText(String detail) {
        String s = detail.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        int idx = s.indexOf('；');
        if (idx < 0) {
            idx = s.indexOf(';');
        }
        if (idx > 0) {
            s = s.substring(0, idx);
        }
        if (s.length() > 80) {
            s = s.substring(0, 80) + "…";
        }
        return s.trim();
    }

    static void watchApi(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            error(ex, 405, "只支持 GET");
            return;
        }
        long nowMs = System.currentTimeMillis();
        Map<String, Store.ResultRecord> recByUser = new HashMap<>();
        for (Store.ResultRecord r : store.leaderboard()) {
            recByUser.put(r.username, r);
        }

        // 效率跑完的人才给名次；没跑完的排在他们后面，观赛页按状态分组显示
        List<Store.ResultRecord> doneList = new ArrayList<>();
        for (Store.ResultRecord r : recByUser.values()) {
            if (r.efficiencyReady) {
                doneList.add(r);
            }
        }
        doneList.sort((a, b) -> {
            int c = Double.compare(b.score, a.score);
            if (c != 0) {
                return c;
            }
            c = Long.compare(Store.codingSecondsOf(a), Store.codingSecondsOf(b));
            if (c != 0) {
                return c;
            }
            return Integer.compare(a.no, b.no);
        });
        Map<String, Integer> ranks = new HashMap<>();
        for (int i = 0; i < doneList.size(); i++) {
            ranks.put(doneList.get(i).username, i + 1);
        }

        int codingCount = 0;
        int judgingCount = 0;
        int finishedCount = 0;
        int idleCount = 0;
        StringBuilder items = new StringBuilder();
        int n = 0;
        List<String> players = playerList();
        for (String un : players) {
            Store.User u = store.findUser(un);
            if (u == null) {
                continue;
            }
            Store.ResultRecord r = recByUser.get(u.username);
            String state;
            long elapsed;
            int rank = 0;
            if (r != null && (r.efficiencyReady || !r.correct)) {
                // 跑完效率的，以及正确性就没过的，都算这一轮已经结束
                state = "finished";
                elapsed = r.codingSeconds;
                rank = ranks.containsKey(u.username) ? ranks.get(u.username) : 0;
                finishedCount++;
            } else if (r != null) {
                state = "judging";
                elapsed = r.codingSeconds;
                judgingCount++;
            } else if (startTimes.containsKey(u.username)) {
                state = "coding";
                long st = startTimes.get(u.username);
                elapsed = st > 0 ? Math.max(0, (nowMs - st) / 1000) : 0;
                codingCount++;
            } else {
                state = "idle";
                elapsed = 0;
                idleCount++;
            }
            if (n++ > 0) {
                items.append(',');
            }
            boolean online = isOnline(u.username);
            items.append('{')
                    .append(Json.inum("no", u.no)).append(',')
                    .append(Json.str("username", u.username)).append(',')
                    .append(Json.str("state", state)).append(',')
                    .append(Json.bool("online", online)).append(',')
                    .append(Json.bool("submitted", r != null)).append(',')
                    .append(Json.inum("elapsed", elapsed)).append(',')
                    .append(Json.inum("rank", rank)).append(',')
                    .append(Json.num("score", r == null ? 0.0 : r.score)).append(',')
                    .append(Json.num("baseScore", r != null && r.correct ? 60.0 : 0.0)).append(',')
                    .append(Json.num("codingScore", r != null && r.correct
                            ? ScoreCalculator.codingScoreSeconds(Store.codingSecondsOf(r)) : 0.0)).append(',')
                    .append(Json.num("efficiencyScore", r == null ? 0.0 : r.efficiencyScore)).append(',')
                    .append(Json.num("runMillis", r == null ? 0.0 : r.runMillis)).append(',')
                    .append(Json.bool("correct", r != null && r.correct)).append(',')
                    .append(Json.inum("caseOk", r == null ? 0 : r.caseOk)).append(',')
                    .append(Json.inum("caseTotal", r == null ? 0 : r.caseTotal)).append(',')
                    .append(Json.bool("efficiencyReady", r != null && r.efficiencyReady)).append(',')
                    .append(Json.str("finishedAt", r == null || r.finishedAt == null ? "" : r.finishedAt)).append(',')
                    .append(Json.str("detail", r == null || r.detail == null ? "" : watchDetailText(r.detail)))
                    .append('}');
        }
        ok(ex, "{" + Json.inum("total", players.size()) + ","
                + Json.inum("coding", codingCount) + ","
                + Json.inum("judging", judgingCount) + ","
                + Json.inum("finished", finishedCount) + ","
                + Json.inum("idle", idleCount) + ","
                + Json.esc("items") + ":[" + items + "]}");
    }

    /** 观众弹幕：需登录的观众身份；比赛进行中关闭，赛前与赛后开放 */
    static void watchDanmaku(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            error(ex, 405, "只支持 POST");
            return;
        }
        Session s = auth(ex);
        if (s == null) {
            return;
        }
        // 观众一直能发；选手交卷后才能发（赛前用快捷消息）；裁判/总管走 staffComment
        if (!"viewer".equals(s.role) && !CommentApi.canReadDiscussion(s)) {
            error(ex, 403, "选手交卷后才能发弹幕，赛前请用快捷消息");
            return;
        }
        Map<String, String> f = form(ex);
        String text = cleanCommentText(f.getOrDefault("text", ""));
        if (text.isEmpty()) {
            error(ex, 400, "内容不能为空");
            return;
        }
        String cs = contestStatus();
        boolean duringContest = "running".equals(cs) || "countdown".equals(cs);
        if (!speechAllowed(ex, s.username, text)) {
            return;
        }
        if (duringContest && promptBlockedWord(text) != null) {
            error(ex, 400, "弹幕含有比赛提示内容，已拦截");
            return;
        }
        if (tooFast(s.username, DANMAKU_CD_MS)) {
            error(ex, 429, "发送太快了，缓一下再发");
            return;
        }
        String role = "player".equals(s.role) ? "student" : "viewer";
        String who = "player".equals(s.role) ? s.username : ("观众" + s.no);
        Store.Comment c = store.addComment(role, who, s.no, text, "danmaku");
        ok(ex, "{" + Json.bool("ok", true) + ","
                + Json.esc("comment") + ":" + commentJson(c) + "}");
    }

    /** 赛事公共状态：所有端都靠它判断倒计时、弹幕开关、评论是否上锁 */
    static void contestApi(HttpExchange ex) throws IOException {
        String st = contestStatus();
        StringBuilder sb = new StringBuilder("{");
        sb.append(Json.str("status", st)).append(',');
        sb.append(Json.inum("serverNow", System.currentTimeMillis())).append(',');
        sb.append(Json.inum("countdownEnd", store.stateLong(K_CONTEST_COUNTDOWN_END, 0L))).append(',');
        sb.append(Json.inum("startAt", store.stateLong(K_CONTEST_START_AT, 0L))).append(',');
        sb.append(Json.inum("endAt", store.stateLong(K_CONTEST_END_AT, 0L))).append(',');
        long deadline = store.stateLong(K_CONTEST_DEADLINE, 0L);
        long remain = ("running".equals(st) && deadline > 0)
                ? Math.max(0L, deadline - System.currentTimeMillis()) : 0L;
        sb.append(Json.inum("deadline", deadline)).append(',');
        sb.append(Json.inum("remainMillis", remain)).append(',');
        sb.append(Json.inum("durationMin", store.stateLong(K_CONTEST_DURATION, 0L))).append(',');
        List<String> players = playerList();
        int fin = 0;
        StringBuilder names = new StringBuilder();
        StringBuilder doneNames = new StringBuilder();
        int ni = 0;
        for (String un : players) {
            if (store.getResult(un) != null) {
                fin++;
                if (doneNames.length() > 0) {
                    doneNames.append(',');
                }
                doneNames.append(Json.esc(un));
            }
            if (ni++ > 0) {
                names.append(',');
            }
            names.append(Json.esc(un));
        }
        sb.append(Json.inum("players", players.size())).append(',');
        sb.append(Json.inum("finishedPlayers", fin)).append(',');
        sb.append(Json.esc("playerNames")).append(":[").append(names).append("],");
        sb.append(Json.esc("finishedNames")).append(":[").append(doneNames).append("],");
        // 观众一直可以发弹幕；选手是否可发由前端按“是否交卷”判断，服务端另有校验
        sb.append(Json.bool("danmakuOpen", true));
        sb.append('}');
        ok(ex, sb.toString());
    }
}
