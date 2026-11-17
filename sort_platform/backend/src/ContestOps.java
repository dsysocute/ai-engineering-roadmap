import java.io.IOException;

/**
 * 赛事收尾：按当前代码给未交卷选手收卷。
 *
 * 三个入口共用：
 *   裁判点「强制结束」          → capMillis = 0，按点下去的这一刻算；
 *   比赛倒计时到 0（自动收卷）  → capMillis = 截止时刻，选手用时按截止时刻封顶；
 *   复赛选手的个人倒计时到 0    → collectOne()，只收这一个人。
 *
 * 复赛中的选手（有 reinst.* 状态）有自己的截止时刻，不会被整场收卷带走。
 */
class ContestOps extends App {

    private ContestOps() {
    }

    /** 结束整场并给所有还没成绩的报名选手收卷 */
    static void finishAndCollect(long capMillis, String detailMsg) throws IOException {
        long now = System.currentTimeMillis();
        long endAt = capMillis > 0 ? capMillis : now;
        store.setState(K_CONTEST_STATUS, "finished");
        store.setState(K_CONTEST_END_AT, String.valueOf(endAt));
        for (String un : playerList()) {
            if (store.getResult(un) != null) {
                continue;
            }
            // 复赛选手按自己的截止时刻走，不跟整场一起收
            if (!store.state("reinst." + un, "").isEmpty()) {
                continue;
            }
            submitCollect(un, endAt, detailMsg);
        }
    }

    /** 只收一个选手（复赛个人时间到） */
    static void collectOne(String username, long capMillis, String detailMsg) {
        submitCollect(username, capMillis > 0 ? capMillis : System.currentTimeMillis(), detailMsg);
    }

    /**
     * 排队收一个人的卷：已有成绩跳过；排队期间自己交卷了也不覆盖。
     */
    private static void submitCollect(String un, long capMillis, String detailMsg) {
        final String code = latestCode(un);
        Store.User u = store.findUser(un);
        final int no = u == null ? 0 : u.no;
        final long startMs = startTimes.containsKey(un) ? startTimes.get(un) : 0L;
        final long cap = capMillis;
        final String msg = detailMsg;
        final String username = un;
        FORCE_POOL.submit(() -> {
            try {
                long until = Math.min(System.currentTimeMillis(), cap);
                long seconds = startMs == 0L ? 0L : Math.max(0L, (until - startMs) / 1000L);
                Store.ResultRecord r = new Store.ResultRecord();
                r.username = username;
                r.no = no;
                r.codingMinutes = seconds / 60L;
                r.codingSeconds = seconds;
                r.finishedAt = Store.now();
                r.caseTotal = JudgeEngine.correctnessCaseCount();
                r.efficiencyReady = false;
                r.efficiencyScore = 0.0;
                if (code == null || code.trim().isEmpty()) {
                    r.score = 0.0;
                    r.correct = false;
                    r.caseOk = 0;
                    r.detail = "未编写代码，按白卷处理（" + msg + "）;";
                } else {
                    JudgeEngine engine = new JudgeEngine();
                    JudgeEngine.Outcome out = engine.judgeCorrectOnly(code, seconds);
                    r.score = out.score;
                    r.correct = out.correct;
                    r.caseOk = out.caseOk;
                    r.caseTotal = out.caseTotal;
                    r.detail = out.detail + " " + msg + ";";
                    r.codeSnapshot = code;
                }
                synchronized (store) {
                    if (store.getResult(username) != null) {
                        return;   // 这期间已经自己交卷了，不覆盖
                    }
                    store.saveResult(r);
                }
                if (r.correct) {
                    PlayApi.enqueueEfficiency(username);
                }
                Session ss = sessionOf(username);
                if (ss != null) {
                    ss.finished = true;
                }
            } catch (Throwable t) {
                System.err.println("收卷失败 " + username + ":" + shortError(t));
            }
        });
    }
}
