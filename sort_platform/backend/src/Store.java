import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 数据层：65 个内测账号 + 成绩记录。
 *
 * 账号不落文件，按规则现生成。
 * 成绩落成 TSV（data/results.tsv），Excel 双击就能打开，也不用装数据库。
 */
public class Store {

    public static final int USER_COUNT = 65;
    public static final String ID_PREFIX = "2025301260";
    public static final String DEFAULT_PASSWORD = "123456";

    private final Path dataDir;
    private final Path resultFile;
    private final Path commentFile;
    /** 赛事状态、管理员/裁判名单、禁言封号记录、选手报名名单，统一用一张 key/value 表落盘 */
    private final Path stateFile;
    /** 状态按用途分文件存：赛事 / 账号与口令 / 处罚，避免一张表变成杂物间 */
    private final Path contestState;
    private final Path userState;
    private final Path punishState;
    private final Path caseFile;
    private final Map<String, String> kv = new LinkedHashMap<>();
    private final Map<String, User> users = new LinkedHashMap<>();
    /** username -> 成绩记录，内存里存一份，写盘时整体覆盖 */
    private final Map<String, ResultRecord> results = new LinkedHashMap<>();
    /** 课堂讨论区，内存里保留最近若干条，落盘在 data/comments.tsv */
    private final List<Comment> comments = new ArrayList<>();
    private long nextCommentId = 1L;
    /** 举报与申诉，落盘在 data/cases.tsv；量不大，整体重写 */
    private final List<CaseRecord> cases = new ArrayList<>();
    private long nextCaseId = 1L;
    private static final int KEEP_COMMENTS = 300;

    public Store(Path dataDir) throws IOException {
        this.dataDir = dataDir;
        Files.createDirectories(dataDir);
        this.resultFile = dataDir.resolve("results.tsv");
        this.commentFile = dataDir.resolve("comments.tsv");
        this.stateFile = dataDir.resolve("state.tsv");
        Path stateDir = dataDir.resolve("state");
        Files.createDirectories(stateDir);
        this.contestState = stateDir.resolve("contest.tsv");
        this.userState = stateDir.resolve("users.tsv");
        this.punishState = stateDir.resolve("punish.tsv");
        this.caseFile = dataDir.resolve("cases.tsv");
        buildUsers();
        loadResults();
        loadComments();
        loadCases();
        loadState();
    }

    // ---------------- 状态表 ----------------

    public synchronized String state(String key, String def) {
        String v = kv.get(key);
        return v == null ? def : v;
    }

    public synchronized long stateLong(String key, long def) {
        String v = kv.get(key);
        if (v == null) {
            return def;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (Exception e) {
            return def;
        }
    }

    /** 写一个状态；值为 null 或空表示删除该键 */
    public synchronized void setState(String key, String value) throws IOException {
        if (value == null || value.isEmpty()) {
            if (kv.remove(key) == null) {
                return;
            }
        } else {
            kv.put(key, value);
        }
        writeState();
    }

    public synchronized void removeState(String key) throws IOException {
        setState(key, null);
    }

    /** 取所有以 prefix 开头的键（含前缀），用于枚举管理员、禁言名单等 */
    public synchronized List<String> keysWithPrefix(String prefix) {
        List<String> out = new ArrayList<>();
        for (String k : kv.keySet()) {
            if (k.startsWith(prefix)) {
                out.add(k);
            }
        }
        return out;
    }

    // ---------------- 举报 / 申诉 ----------------

    public synchronized CaseRecord addCase(String kind, String type, String target, int targetNo,
                                           String reporterRole, String reporter, int reporterNo, String reason) {
        CaseRecord c = new CaseRecord();
        c.id = nextCaseId++;
        c.kind = kind;
        c.type = type;
        c.target = target;
        c.targetNo = targetNo;
        c.reporterRole = reporterRole;
        c.reporter = reporter;
        c.reporterNo = reporterNo;
        c.reason = clean(reason);
        c.status = "pending";
        c.handler = "";
        c.handledAt = "";
        c.note = "";
        c.createdAt = now();
        cases.add(c);
        if (cases.size() > 200) {
            cases.remove(0);
        }
        try {
            writeCases();
        } catch (IOException ignore) {
        }
        return c;
    }

    public synchronized List<CaseRecord> cases() {
        return new ArrayList<>(cases);
    }

    public synchronized CaseRecord caseById(long id) {
        for (CaseRecord c : cases) {
            if (c.id == id) {
                return c;
            }
        }
        return null;
    }

    public synchronized void updateCase(long id, String status, String handler, String note) throws IOException {
        CaseRecord c = caseById(id);
        if (c == null) {
            return;
        }
        c.status = status;
        c.handler = handler;
        c.note = clean(note);
        c.handledAt = now();
        writeCases();
    }

    private void loadCases() throws IOException {
        if (!Files.exists(caseFile)) {
            return;
        }
        for (String line : Files.readAllLines(caseFile, StandardCharsets.UTF_8)) {
            if (line.startsWith("#") || line.trim().isEmpty()) {
                continue;
            }
            String[] p = line.split("\t", -1);
            if (p.length < 12) {
                continue;
            }
            CaseRecord c = new CaseRecord();
            c.id = parseLong(p[0]);
            c.kind = p[1];
            c.type = p[2];
            c.target = p[3];
            c.targetNo = parseInt(p[4]);
            c.reporterRole = p[5];
            c.reporter = p[6];
            c.reporterNo = parseInt(p[7]);
            c.reason = p[8];
            c.status = p[9];
            c.handler = p[10];
            c.handledAt = p[11];
            c.note = p.length > 12 ? p[12] : "";
            c.createdAt = p.length > 13 ? p[13] : "";
            cases.add(c);
            nextCaseId = Math.max(nextCaseId, c.id + 1);
        }
    }

    private void writeCases() throws IOException {
        StringBuilder sb = new StringBuilder("#id\tkind\ttype\ttarget\ttargetNo\treporterRole\treporter\treporterNo\treason\tstatus\thandler\thandledAt\tnote\tcreatedAt\n");
        for (CaseRecord c : cases) {
            sb.append(c.id).append('\t')
                    .append(clean(c.kind)).append('\t')
                    .append(clean(c.type)).append('\t')
                    .append(clean(c.target)).append('\t')
                    .append(c.targetNo).append('\t')
                    .append(clean(c.reporterRole)).append('\t')
                    .append(clean(c.reporter)).append('\t')
                    .append(c.reporterNo).append('\t')
                    .append(clean(c.reason)).append('\t')
                    .append(clean(c.status)).append('\t')
                    .append(clean(c.handler)).append('\t')
                    .append(clean(c.handledAt)).append('\t')
                    .append(clean(c.note)).append('\t')
                    .append(clean(c.createdAt)).append('\n');
        }
        atomicWrite(caseFile, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void loadState() throws IOException {
        // 先读旧的大表（兼容升级前写下的数据），再用分文件的值覆盖，
        // 这样第一次启动会自动完成迁移，不需要人手动搬 key。
        readStateFile(stateFile);
        readStateFile(contestState);
        readStateFile(userState);
        readStateFile(punishState);
    }

    private void readStateFile(Path file) throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.startsWith("#") || line.trim().isEmpty()) {
                continue;
            }
            String[] p = line.split("\t", 2);
            if (p.length < 2) {
                continue;
            }
            kv.put(p[0], p[1]);
        }
    }

    /** 按 key 前缀决定落哪个文件；认不出的键仍旧写回 state.tsv */
    private Path sectionOf(String key) {
        if (key.startsWith("contest.")) {
            return contestState;
        }
        if (key.startsWith("player.") || key.startsWith("admin.")
                || key.startsWith("adminpass.") || key.startsWith("pass.")
                || key.startsWith("sec.") || key.startsWith("judge.")
                || key.startsWith("super.") || key.startsWith("nick.")
                || key.startsWith("start.")) {
            return userState;
        }
        if (key.startsWith("mute.") || key.startsWith("ban.")
                || key.startsWith("banreason.") || key.startsWith("bancase.")
                || key.startsWith("violation.") || key.startsWith("violprev.")) {
            return punishState;
        }
        return null;
    }

    /**
     * 状态表整体重写。先写临时文件再原子替换，
     * 避免多个请求同时落盘时互相抢文件句柄（Windows 上会直接抛 IOException）。
     */
    private synchronized void writeState() throws IOException {
        StringBuilder contest = new StringBuilder("#key\tvalue\n");
        StringBuilder users = new StringBuilder("#key\tvalue\n");
        StringBuilder punish = new StringBuilder("#key\tvalue\n");
        StringBuilder legacy = new StringBuilder("#key\tvalue\n");
        for (Map.Entry<String, String> e : kv.entrySet()) {
            Path target = sectionOf(e.getKey());
            StringBuilder sb = target == null ? legacy : (target == contestState ? contest
                    : target == userState ? users : punish);
            sb.append(clean(e.getKey())).append('\t').append(clean(e.getValue())).append('\n');
        }
        atomicWrite(contestState, contest.toString().getBytes(StandardCharsets.UTF_8));
        atomicWrite(userState, users.toString().getBytes(StandardCharsets.UTF_8));
        atomicWrite(punishState, punish.toString().getBytes(StandardCharsets.UTF_8));
        atomicWrite(stateFile, legacy.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 先写同目录的 .tmp 再原子替换。
     * 直接 Files.write 到目标文件时，一旦写到一半进程被杀或磁盘满，
     * 整张成绩表 / 讨论区会被截成半截，等于本届数据全丢。
     */
    private static void atomicWrite(Path target, byte[] bytes) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // 某些文件系统不支持原子替换，退回普通覆盖，至少内容已经完整落盘
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void buildUsers() {
        for (int i = 1; i <= USER_COUNT; i++) {
            String username = ID_PREFIX + String.format("%02d", i);
            users.put(username, new User(username, DEFAULT_PASSWORD, i));
        }
    }

    public User findUser(String username) {
        return users.get(username);
    }

    public Collection<User> allUsers() {
        return users.values();
    }

    /** 所有成绩，启动时用来找出「正确但效率没算完」的记录补跑 */
    public synchronized Collection<ResultRecord> allResults() {
        return new ArrayList<>(results.values());
    }

    public synchronized ResultRecord getResult(String username) {
        return results.get(username);
    }

    public synchronized void saveResult(ResultRecord r) throws IOException {
        results.put(r.username, r);
        writeAll();
    }

    /** 删掉单个选手的成绩（裁判同意重考时用），其余人不受影响 */
    public synchronized boolean deleteResult(String username) throws IOException {
        if (results.remove(username) == null) {
            return false;
        }
        writeAll();
        return true;
    }

    /** 成绩表整体重写：tsv 全量 + csv 视图 */
    private void writeAll() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("#v2\tusername\tno\tscore\tcorrect\tcodingMinutes\tefficiencyScore\tcaseOk\tcaseTotal\tfinishedAt\tdetail\tefficiencyReady\tcodeSnapshot\tcodingSeconds\trunMillis\n");
        for (ResultRecord x : results.values()) {
            sb.append(clean(x.username)).append('\t')
                    .append(x.no).append('\t')
                    .append(String.format(Locale.ROOT, "%.1f", x.score)).append('\t')
                    .append(x.correct).append('\t')
                    .append(String.format(Locale.ROOT, "%.2f", x.codingMinutes)).append('\t')
                    .append(String.format(Locale.ROOT, "%.1f", x.efficiencyScore)).append('\t')
                    .append(x.caseOk).append('\t')
                    .append(x.caseTotal).append('\t')
                    .append(clean(x.finishedAt)).append('\t')
                    .append(clean(x.detail)).append('\t')
                    .append(x.efficiencyReady).append('\t')
                    .append(clean(x.codeSnapshot)).append('\t')
                    .append(x.codingSeconds).append('\t')
                    .append(String.format(Locale.ROOT, "%.1f", x.runMillis))
                    .append('\n');
        }
        atomicWrite(resultFile, sb.toString().getBytes(StandardCharsets.UTF_8));
        writeCsvView();
    }

    // ---------------- 讨论区 ----------------

    /** 发一条评论：只保留最近 KEEP_COMMENTS 条，前端每次拉最近 N 条做差集 */
    public synchronized Comment addComment(String role, String username, int no, String text) throws IOException {
        return addComment(role, username, no, text, "comment");
    }

    /**
     * 发一条消息。
     * kind = comment 进讨论区列表；kind = danmaku 只在弹幕轨道上飘，两者互不混排。
     */
    public synchronized Comment addComment(String role, String username, int no, String text, String kind) throws IOException {
        String k = "danmaku".equals(kind) ? "danmaku" : "comment";
        Comment c = new Comment(nextCommentId++, role, username, no, text, now(), k);
        comments.add(c);
        if (comments.size() > KEEP_COMMENTS) {
            comments.subList(0, comments.size() - KEEP_COMMENTS).clear();
        }
        writeComments();
        return c;
    }

    /** 管理员删一条评论；删不掉说明 id 不存在 */
    public synchronized boolean deleteComment(long id) throws IOException {
        boolean removed = comments.removeIf(c -> c.id == id);
        if (!removed) {
            return false;
        }
        writeComments();
        return true;
    }

    /** 取最近的若干条：前端每次拉全量做差集，管理员删掉的消息才能在各端一起消失 */
    public synchronized List<Comment> recentComments(int limit) {
        if (limit <= 0 || comments.isEmpty()) {
            return new ArrayList<>();
        }
        int n = Math.min(limit, comments.size());
        return new ArrayList<>(comments.subList(comments.size() - n, comments.size()));
    }



    public synchronized long lastCommentId() {
        return comments.isEmpty() ? 0L : comments.get(comments.size() - 1).id;
    }

    private void loadComments() throws IOException {
        if (!Files.exists(commentFile)) {
            return;
        }
        for (String line : Files.readAllLines(commentFile, StandardCharsets.UTF_8)) {
            if (line.startsWith("#") || line.trim().isEmpty()) {
                continue;
            }
            String[] p = line.split("\t", -1);
            if (p.length < 5) {
                continue;
            }
            // 兼容：旧格式没有 role 列（id time username no text），一律当作学生发言
            long id = parseLong(p[0]);
            String time = p[1];
            Comment c;
            if (p.length >= 7) {
                c = new Comment(id, p[2], p[3], parseInt(p[4]), p[5], time, p[6]);
            } else if (p.length >= 6) {
                c = new Comment(id, p[2], p[3], parseInt(p[4]), p[5], time);
            } else {
                c = new Comment(id, "student", p[2], parseInt(p[3]), p[4], time);
            }
            comments.add(c);
            if (id >= nextCommentId) {
                nextCommentId = id + 1;
            }
        }
        if (comments.size() > KEEP_COMMENTS) {
            comments.subList(0, comments.size() - KEEP_COMMENTS).clear();
        }
    }

    private void writeComments() throws IOException {
        StringBuilder sb = new StringBuilder("#id\ttime\trole\tusername\tno\ttext\tkind\n");
        for (Comment c : comments) {
            sb.append(c.id).append('\t')
                    .append(clean(c.time)).append('\t')
                    .append(clean(c.role)).append('\t')
                    .append(clean(c.username)).append('\t')
                    .append(c.no).append('\t')
                    .append(clean(c.text)).append('\t')
                    .append(clean(c.kind)).append('\n');
        }
        atomicWrite(commentFile, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 顺手导一份 CSV，方便老师直接用 Excel 打开排序 */
    private void writeCsvView() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("选手号,账号,总分,是否正确,编码用时(分钟),效率分,通过用例,总用例,提交时间,编码时长(秒),运行耗时(毫秒)\n");
        for (ResultRecord x : results.values()) {
            sb.append(x.no).append(',')
                    .append(x.username).append(',')
                    .append(String.format(Locale.ROOT, "%.1f", x.score)).append(',')
                    .append(x.correct ? "是" : "否").append(',')
                    .append(String.format(Locale.ROOT, "%.2f", x.codingMinutes)).append(',')
                    .append(String.format(Locale.ROOT, "%.1f", x.efficiencyScore)).append(',')
                    .append(x.caseOk).append(',')
                    .append(x.caseTotal).append(',')
                    .append(x.finishedAt).append(',')
                    .append(x.codingSeconds).append(',')
                    .append(String.format(Locale.ROOT, "%.1f", x.runMillis)).append('\n');
        }
        atomicWrite(dataDir.resolve("results.csv"), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void loadResults() throws IOException {
        if (!Files.exists(resultFile)) {
            return;
        }
        List<String> lines = Files.readAllLines(resultFile, StandardCharsets.UTF_8);
        boolean v2 = false;
        for (String line : lines) {
            if (line.startsWith("#")) {
                // #v2 表示文件里只有百分制记录；没有这个标记的才是可升级的旧文件
                if (line.startsWith("#v2")) {
                    v2 = true;
                }
                continue;
            }
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] p = line.split("\t", -1);
            if (p.length < 10) {
                continue;
            }
            ResultRecord r = new ResultRecord();
            r.username = p[0];
            r.no = parseInt(p[1]);
            r.score = parseDouble(p[2]);
            r.correct = "true".equalsIgnoreCase(p[3]);
            r.codingMinutes = parseDouble(p[4]);
            r.efficiencyScore = parseDouble(p[5]);
            r.caseOk = parseInt(p[6]);
            r.caseTotal = parseInt(p[7]);
            r.finishedAt = p[8];
            r.detail = p[9];
            // 兼容旧文件：列不够就给默认值
            r.efficiencyReady = p.length >= 11 && "true".equalsIgnoreCase(p[10]);
            r.codeSnapshot = p.length >= 12 ? p[11] : "";
            // 兼容旧文件：这两列是后加的，缺了就按 0 处理
            r.codingSeconds = p.length >= 13 ? (long) parseDouble(p[12]) : 0L;
            r.runMillis = p.length >= 14 ? parseDouble(p[13]) : 0.0;
            // 旧制 25 分记录混进 100 分表时，排行榜会把「25 分」当成 25 分排在 60 分后面。
            // 没有 codingSeconds 这一列、且分数/效率都没超过 25 的行，判为旧制，×4 折算成百分制。
            if (!v2 && p.length < 13 && r.score > 0 && r.score <= 25 && r.efficiencyScore <= 25) {
                r.score *= 4;
                r.efficiencyScore *= 4;
                r.detail = "[旧制25分制已折算×4] " + r.detail;
            }
            if (r.score < 0 || r.score > 100) {
                r.detail = "[分数越界，排行榜已忽略] " + r.detail;
            }
            results.put(r.username, r);
        }
    }

    /**
     * 清空全部成绩：内存清掉，tsv / csv 重写成只剩表头。
     * 给管理员后台的「清空所有成绩」按钮用。
     */
    public synchronized void clearAll() throws IOException {
        results.clear();
        StringBuilder sb = new StringBuilder();
        sb.append(String.join("\t",
                "#v2", "username", "no", "score", "correct", "codingMinutes", "efficiencyScore",
                "caseOk", "caseTotal", "finishedAt", "detail", "efficiencyReady", "codeSnapshot",
                "codingSeconds", "runMillis"));
        sb.append('\n');
        atomicWrite(resultFile, sb.toString().getBytes(StandardCharsets.UTF_8));
        writeCsvView();
    }

    /** 排行榜：按总分降序，同分比编码用时，再比选手号 */
    public synchronized List<ResultRecord> leaderboard() {
        List<ResultRecord> list = new ArrayList<>(results.values());
        list.sort((a, b) -> {
            int c = Double.compare(b.score, a.score);
            if (c != 0) {
                return c;
            }
            // 用秒做同分比较：codingMinutes 是向下取整的整分钟，
            // 40 分 01 秒和 40 分 59 秒会被当成同一档，名次只能靠学号碰运气
            c = Long.compare(codingSecondsOf(a), codingSecondsOf(b));
            if (c != 0) {
                return c;
            }
            return Integer.compare(a.no, b.no);
        });
        return list;
    }

    /** 编码用时（秒）：新记录直接有 codingSeconds，旧记录按分钟折算 */
    public static long codingSecondsOf(ResultRecord r) {
        if (r.codingSeconds > 0) {
            return r.codingSeconds;
        }
        return Math.round(r.codingMinutes * 60.0);
    }

    public static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    // ---------------- 实体 ----------------

    public static class User {
        public final String username;
        public final String password;
        public final int no;

        public User(String username, String password, int no) {
            this.username = username;
            this.password = password;
            this.no = no;
        }
    }

    public static class ResultRecord {
        public String username;
        public int no;
        public double score;
        public boolean correct;
        public double codingMinutes;
        public double efficiencyScore;
        public int caseOk;
        public int caseTotal;
        public String finishedAt;
        public String detail = "";
        // 新增：效率是否算完 + 交卷时的代码快照
        public boolean efficiencyReady;
        public String codeSnapshot = "";
        // 原始数值：编码时长（秒，与页面倒计时一致）、效率评测实际运行耗时（毫秒）
        public long codingSeconds;
        public double runMillis;
    }

    /** 举报或申诉的一条记录 */
    public static class CaseRecord {
        public long id;
        /** report=举报，appeal=申诉 */
        public String kind = "";
        /** violation=违规，speech=不当言论，mute=禁言申诉，ban=封号申诉，staff=裁判/管理员违规上报 */
        public String type = "";
        public String target = "";
        public int targetNo;
        /** 举报人 / 申诉人身份与称呼 */
        public String reporterRole = "";
        public String reporter = "";
        public int reporterNo;
        public String reason = "";
        /** pending / approved / rejected */
        public String status = "pending";
        public String handler = "";
        public String handledAt = "";
        public String note = "";
        public String createdAt = "";
    }

    /** 讨论区的一条消息 */
    public static class Comment {
        public final long id;
        /** student / admin / viewer（观众），前端据此决定显示前缀与配色 */
        public final String role;
        public final String username;
        public final int no;
        public final String text;
        public final String time;
        /** comment 进讨论区列表；danmaku 只走弹幕轨道 */
        public final String kind;

        public Comment(long id, String role, String username, int no, String text, String time) {
            this(id, role, username, no, text, time, "comment");
        }

        public Comment(long id, String role, String username, int no, String text, String time, String kind) {
            this.id = id;
            this.role = role;
            this.username = username;
            this.no = no;
            this.text = text;
            this.time = time;
            this.kind = "danmaku".equals(kind) ? "danmaku" : "comment";
        }
    }
}
