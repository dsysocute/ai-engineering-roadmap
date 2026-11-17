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

/** 选手作答链路：开始计时、测试运行、交卷、后台效率评测、代码上报、快捷消息。 */
class PlayApi extends App {

    static void start(HttpExchange ex) throws IOException {
        Session s = auth(ex);
        if (s == null) {
            return;
        }
        if (!requirePlayer(ex, s, "观众身份不能作答")) {
            return;
        }
        String cs = contestStatus();
        if ("idle".equals(cs)) {
            error(ex, 409, "裁判尚未开启比赛");
            return;
        }
        if ("countdown".equals(cs)) {
            error(ex, 409, "比赛马上开始，请等倒计时结束");
            return;
        }
        if ("finished".equals(cs)) {
            error(ex, 409, "本场比赛已结束");
            return;
        }
        if (s.finished) {
            error(ex, 409, "你已经交过卷了，不能重新计时");
            return;
        }
        if (s.startMillis == 0) {
            // putIfAbsent：两个窗口同时点「开始」也只认第一次，计时不会被重置
            long now = System.currentTimeMillis();
            Long prev = startTimes.putIfAbsent(s.username, now);
            s.startMillis = prev == null ? now : prev;
            // 落盘一份开始时刻，服务重启（改代码、换端口）后不会把在场比赛的人计时清零
            store.setState("start." + s.username, String.valueOf(s.startMillis));
        }
        StringBuilder sb = new StringBuilder("{");
        sb.append(Json.bool("ok", true)).append(',');
        sb.append(Json.inum("elapsedSeconds", elapsedSeconds(s)));
        sb.append('}');
        ok(ex, sb.toString());
    }

    static void test(HttpExchange ex) throws IOException {
        Session s = auth(ex);
        if (s == null) {
            return;
        }
        if (!requirePlayer(ex, s, "观众身份不能运行代码")) {
            return;
        }
        if (s.startMillis == 0) {
            error(ex, 409, "请先点「开始」");
            return;
        }
        if (s.finished) {
            error(ex, 409, "已交卷");
            return;
        }
        long now = System.currentTimeMillis();
        if (now - s.lastTestMillis < TEST_COOLDOWN_MS) {
            error(ex, 429, "点太快了，缓 " + (TEST_COOLDOWN_MS / 1000) + " 秒再试");
            return;
        }
        s.lastTestMillis = now;

        Map<String, String> f = form(ex);
        String code = f.getOrDefault("code", "");
        if (code.trim().isEmpty()) {
            error(ex, 400, "代码是空的");
            return;
        }
        boolean acquired;
        try {
            acquired = JUDGE_SLOTS.tryAcquire(30, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            acquired = false;
        }
        if (!acquired) {
            error(ex, 503, "判题机忙，稍后再试");
            return;
        }
        try {
            JudgeEngine engine = new JudgeEngine();
            JudgeEngine.Outcome out = engine.testRun(code);
            StringBuilder sb = new StringBuilder("{");
            sb.append(Json.bool("ok", true)).append(',');
            sb.append(Json.str("terminal", out.terminal)).append(',');
            sb.append(Json.inum("elapsedSeconds", elapsedSeconds(s)));
            sb.append('}');
            ok(ex, sb.toString());
        } finally {
            JUDGE_SLOTS.release();
        }
    }

    /** 交卷：只跑正确性，编码计时在这里定格，效率留给后台 */
    static void finish(HttpExchange ex) throws IOException {
        Session s = auth(ex);
        if (s == null) {
            return;
        }
        if (!requirePlayer(ex, s, "观众身份不能交卷")) {
            return;
        }
        if (s.startMillis == 0) {
            error(ex, 409, "请先点「开始」");
            return;
        }
        if (s.finished) {
            error(ex, 409, "已经交过卷了");
            return;
        }
        // 裁判强制结束之后不再收卷：否则选手可以在截止后继续交，
        // 把已经按白卷/当前代码定下的成绩覆盖掉，等于没有截止时间。
        // 复赛中的选手例外：整场结束了他还有自己的复赛窗口。
        String contestNow = contestStatus();
        boolean reinstated = !store.state("reinst." + s.username, "").isEmpty();
        if (!"running".equals(contestNow) && !reinstated) {
            error(ex, 409, "本场比赛已结束，不能再交卷");
            return;
        }
        Map<String, String> f = form(ex);
        String code = f.getOrDefault("code", "");
        if (code.trim().isEmpty()) {
            error(ex, 400, "代码是空的，没法判");
            return;
        }
        // 同一账号可能有多个窗口在（旧窗口还没被顶掉时），只让一个真正判，避免成绩被覆盖
        if (!finishingAccounts.add(s.username)) {
            error(ex, 409, "这个账号的卷子正在判，别连点");
            return;
        }
        boolean acquired;
        try {
            acquired = JUDGE_SLOTS.tryAcquire(60, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            acquired = false;
            finishingAccounts.remove(s.username);
        }
        if (!acquired) {
            finishingAccounts.remove(s.username);
            error(ex, 503, "判题机忙，稍后再点");
            return;
        }
        s.judging = true;
        try {
            // 编码分按秒算：取整到分钟会让 40:59 仍然拿满分
            long seconds = elapsedSeconds(s);
            JudgeEngine engine = new JudgeEngine();
            JudgeEngine.Outcome out = engine.judgeCorrectOnly(code, seconds);

            Store.ResultRecord r = new Store.ResultRecord();
            r.username = s.username;
            r.no = s.no;
            r.score = out.score;
            r.correct = out.correct;
            r.codingMinutes = seconds / 60L;
            r.codingSeconds = seconds;
            r.efficiencyScore = 0.0;
            r.efficiencyReady = false;
            r.caseOk = out.caseOk;
            r.caseTotal = out.caseTotal;
            r.finishedAt = Store.now();
            r.detail = out.detail;
            r.codeSnapshot = code;
            store.saveResult(r);
            // 复赛窗口用完了，交卷即结束
            store.removeState("reinst." + s.username);
            store.removeState("reinstDeadline." + s.username);
            // 提交的那一刻也留一版快照，观众端在延迟窗口之后能看到最终代码
            pushLiveCode(s.username, code);
            // 效率评测由服务端自己排队：前端关掉页面也不会漏算，
            // 而且用的是刚存下的快照，前端伪造不了代码
            if (out.correct) {
                enqueueEfficiency(s.username);
            }

            s.finished = true;
            ok(ex, resultJson(r) + "");
        } catch (Exception e) {
            error(ex, 500, "判题出错：" + e.getMessage());
        } finally {
            s.judging = false;
            finishingAccounts.remove(s.username);
            JUDGE_SLOTS.release();
        }
    }

    /**
     * 效率评测的触发口。
     *
     * 这里**只做排队**，不再接收前端传来的代码：以前允许前端把任意代码塞进来重算，
     * 等于「交一份错解锁 0 分，再拿另一份快排把效率分刷回来」，实测能拿到 40 分；
     * 被判罚成 0 分的记录也能靠同一个口子回填 40 分。
     * 现在统一用交卷时保存的代码快照，且只有正确性通过的记录才有资格评测。
     *
     * 保留这个接口是为了兼容旧前端和「交卷后关掉页面」的情况：重复调用是幂等的。
     */
    static void judgeEfficiency(HttpExchange ex) throws IOException {
        Session s = auth(ex);
        if (s == null) {
            return;
        }
        if (!requirePlayer(ex, s, "观众身份不能评测")) {
            return;
        }
        Store.ResultRecord rec = store.getResult(s.username);
        if (rec == null) {
            error(ex, 409, "还没有交卷，没有可评测的记录");
            return;
        }
        if (!rec.correct) {
            error(ex, 409, "正确性未通过，不进行效率评测");
            return;
        }
        if (rec.efficiencyReady) {
            ok(ex, "{\"ok\":true,\"msg\":\"效率评测已完成\"}");
            return;
        }
        enqueueEfficiency(s.username);
        ok(ex, "{\"ok\":true,\"msg\":\"效率评测已转后台\"}");
    }

    /** 启动时补跑上次没算完的效率评测（正确但 efficiencyReady=false 的记录） */
    static void recoverPendingEfficiency() {
        for (Store.ResultRecord r : store.allResults()) {
            if (r.correct && !r.efficiencyReady
                    && r.codeSnapshot != null && !r.codeSnapshot.trim().isEmpty()) {
                enqueueEfficiency(r.username);
            }
        }
    }

    /** 同一账号只排一次，避免连点或旧前端重试把评测任务堆起来 */
    static void enqueueEfficiency(String username) {
        if (!efficiencyQueued.add(username)) {
            return;
        }
        EFFICIENCY_POOL.submit(() -> {
            try {
                runEfficiency(username);
            } catch (Throwable t) {
                System.err.println("效率评测异常 " + username + "：" + shortError(t));
            } finally {
                efficiencyQueued.remove(username);
            }
        });
    }

    /**
     * 真正跑效率评测并回填成绩。
     * 全程只认服务端保存的快照；如果评测期间这份记录被删、被判罚、被重新提交
     * （对象已经不是同一条），结果直接丢弃，绝不覆盖新成绩。
     */
    static void runEfficiency(String username) {
        Store.ResultRecord rec = store.getResult(username);
        if (rec == null || !rec.correct) {
            return;
        }
        String code = rec.codeSnapshot == null ? "" : rec.codeSnapshot;
        if (code.trim().isEmpty()) {
            // 没有代码快照也必须给一个终态，不能永远停在「测算中」
            try {
                rec.efficiencyReady = true;
                rec.efficiencyScore = 0.0;
                rec.detail = rec.detail + " 效率评测完成：无代码快照，按 0 分;";
                store.saveResult(rec);
            } catch (IOException ignore) {
            }
            return;
        }

        double eff = 0.0;
        double runMillis = 0.0;
        String note = null;
        try {
            JudgeEngine.EfficiencyResult res = new JudgeEngine().judgeEfficiencyOnly(code);
            eff = res.score;
            runMillis = res.avgMillis;
        } catch (Throwable t) {
            // 评测崩了也得往下走：分数记 0，状态照样标记完成，否则前端永远停在「测算中」
            eff = 0.0;
            note = shortError(t);
            System.err.println("效率评测异常：" + note);
        }

        synchronized (store) {
            Store.ResultRecord cur = store.getResult(username);
            if (cur != rec || !cur.correct) {
                return;   // 评测期间记录被替换或判罚，结果作废
            }
            try {
                double coding = ScoreCalculator.codingScoreSeconds(Store.codingSecondsOf(cur));
                cur.efficiencyScore = eff;
                cur.runMillis = runMillis;
                cur.efficiencyReady = true;
                cur.score = 60.0 + coding + eff;
                cur.detail = cur.detail + " 效率评测完成：" + ScoreCalculator.format(eff) + "/20"
                        + (note == null ? "" : "（评测过程异常：" + note + "）") + ";";
                store.saveResult(cur);
            } catch (IOException io) {
                System.err.println("效率评测结果落盘失败：" + io.getMessage());
            }
        }
    }

    static void problem(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder("{");
        sb.append(Json.str("title", "排序算法测评平台"));
        sb.append(',').append(Json.str("desc",
                "编写类 Solution，实现方法 public void sort(int[] arr)，对传入数组执行升序原地排序。排序算法不限，插入排序、快速排序、归并排序等均可使用。"
                        + "平台自动传入数组，无需读取输入，无需控制台输出，排序结果写回原数组即可。"
                        + "点击【测试运行】可执行多组小规模样例，输出排序前后数组内容，供调试使用；小样例通过不代表正式评测可以通过。"
                        + "正式评测以规模 1000 至 10000 的数据集校验正确性，全部用例通过后，再以十万级数据集统计运行耗时。"
                        + "效率评测在后台异步执行，提交后无需等待，分数自动更新。"));
        sb.append(',').append(Json.str("accountHint", "202530126001 ~ 202530126065，密码统一 123456"));
        sb.append('}');
        ok(ex, sb.toString());
    }

    /**
     * 观赛数据：公开只读，只暴露进度，不返回任何代码。
     * state：idle 未开始 / coding 作答中 / judging 已交卷等效率评测 / finished 已完成
     */
    /** 选手端把编辑器里的内容推上来，供观赛端延迟展示 */
    static void editorApi(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            error(ex, 405, "只支持 POST");
            return;
        }
        Session s = auth(ex);
        if (s == null) {
            return;
        }
        if (!requirePlayer(ex, s, "观众身份不能上报代码")) {
            return;
        }
        Map<String, String> f = form(ex);
        String code = f.getOrDefault("code", "");
        if (code.length() > LIVE_CODE_MAX_CHARS) {
            code = code.substring(0, LIVE_CODE_MAX_CHARS);
        }
        pushLiveCode(s.username, code);
        okTrue(ex);
    }

    static void quickApi(HttpExchange ex) throws IOException {
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            StringBuilder sb = new StringBuilder("{\"serverNow\":").append(System.currentTimeMillis()).append(',');
            sb.append(Json.esc("items")).append(":[");
            int i = 0;
            long now = System.currentTimeMillis();
            for (Object[] q : quickMsgs.values()) {
                long at = (Long) q[1];
                if (now - at > QUICK_LIVE_MS) {
                    continue;
                }
                if (i++ > 0) {
                    sb.append(',');
                }
                sb.append('{').append(Json.inum("id", (Integer) q[0])).append(',')
                        .append(Json.inum("no", (Integer) q[2])).append(',')
                        .append(Json.str("text", (String) q[3])).append(',')
                        .append(Json.inum("at", at)).append('}');
            }
            sb.append("]}");
            ok(ex, sb.toString());
            return;
        }

        Session s = auth(ex);
        if (s == null) {
            return;
        }
        if (!requirePlayer(ex, s, "只有选手可以发送快捷消息")) {
            return;
        }
        // 快捷消息只在赛前（idle）可用：开赛后、倒计时、赛后一律锁
        if (!"idle".equals(contestStatus())) {
            error(ex, 409, "快捷消息仅在比赛开始前可用");
            return;
        }
        Map<String, String> f = form(ex);
        String text = f.getOrDefault("text", "").trim();
        boolean allowed = false;
        for (String t : QUICK_TEXTS) {
            if (t.equals(text)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            error(ex, 400, "快捷消息不在可选范围内");
            return;
        }
        if (tooFast("quick:" + s.username, QUICK_CD_MS)) {
            error(ex, 429, "发送太快了，稍等一下");
            return;
        }
        int id = quickSeq.incrementAndGet();
        quickMsgs.put(s.username + "#" + id, new Object[] {id, System.currentTimeMillis(), s.no, text});
        if (quickMsgs.size() > QUICK_KEEP) {
            long now = System.currentTimeMillis();
            quickMsgs.entrySet().removeIf(e -> now - (Long) e.getValue()[1] > QUICK_LIVE_MS);
        }
        ok(ex, "{\"ok\":true,\"id\":" + id + "}");
    }
}
