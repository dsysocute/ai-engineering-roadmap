import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 举报 / 申诉事务台：所有「谁被举报、谁来处理、通过后怎么执行」的逻辑都收在这里。
 *
 * 路由仍然注册在 Main，但处理函数与权限判断搬出 Main，
 * 避免 Main 继续膨胀成一个三千行的杂物间。
 *
 * 分派规则：
 *   违规 violation          → 裁判 + 总管
 *   不当言论 speech         → 管理员 + 总管
 *   禁言申诉 mute           → 管理员 + 总管
 *   封号申诉 ban            → 总管
 *   工作人员违规 staff      → 总管
 */
public final class CaseCenter {

    private CaseCenter() {
    }

    /** 控制台身份（管理员/裁判/总管），没有就是空串 */
    static String staffRole(HttpExchange ex) {
        if (Main.isSuper(ex)) {
            return "super";
        }
        if (Main.isJudge(ex)) {
            return "judge";
        }
        if (Main.isAdmin(ex)) {
            return "admin";
        }
        return "";
    }

    /** 处理人署名，写进案件记录给人看 */
    static String staffLabel(HttpExchange ex) {
        if (Main.isSuper(ex)) {
            return "总管";
        }
        if (Main.isJudge(ex)) {
            String ju = Main.judgeUser();
            return ju.isEmpty() ? "裁判" : "裁判·" + labelOf(ju);
        }
        if (Main.isAdmin(ex)) {
            String who = Main.adminTokenUsers.get(Main.tokenOf(ex));
            return who == null || "master".equals(who) ? "主管理员" : "管理员·" + labelOf(who);
        }
        return "";
    }

    /** 页面上称呼某账号：选手N / 观众N，再加昵称 */
    static String labelOf(String username) {
        Store.User u = Main.store.findUser(username);
        if (u == null) {
            return username;
        }
        String base = Main.playerList().contains(username) ? ("选手" + u.no) : ("观众" + u.no);
        String nick = nickOf(username);
        return nick.isEmpty() ? base : base + "·" + nick;
    }

    static String nickOf(String username) {
        return Main.store.state("nick." + username, "");
    }

    /** 哪类事务归谁处理 */
    static boolean canHandle(String role, String type) {
        if ("super".equals(role)) {
            return true;
        }
        if ("judge".equals(role)) {
            return "violation".equals(type);
        }
        if ("admin".equals(role)) {
            return "speech".equals(type) || "mute".equals(type);
        }
        return false;
    }

    static String caseTypeLabel(String type) {
        switch (type) {
            case "violation":
                return "违规";
            case "speech":
                return "不当言论";
            case "mute":
                return "禁言申诉";
            case "ban":
                return "封号申诉";
            case "staff":
                return "工作人员违规";
            default:
                return type;
        }
    }

    /**
     * 举报：选手/观众举报他人违规或不当言论，管理员举报裁判/管理员违规。
     * 违规进裁判 + 总管，不当言论进管理员 + 总管，工作人员违规只进总管。
     */
    static void report(HttpExchange ex) throws IOException {
        String role = staffRole(ex);
        Main.Session s = null;
        String reporter;
        int reporterNo;
        if (role.isEmpty()) {
            s = Main.auth(ex);
            if (s == null) {
                return;
            }
            if (!"player".equals(s.role) && !"viewer".equals(s.role)) {
                Main.error(ex, 403, "只有选手与观众可以举报");
                return;
            }
            reporter = labelOf(s.username);
            reporterNo = s.no;
        } else {
            reporter = staffLabel(ex);
            reporterNo = 0;
        }
        Map<String, String> f = Main.form(ex);
        String target = Main.field(f, "username");
        String type = Main.field(f, "type");
        String reason = Main.cleanCommentText(f.getOrDefault("reason", ""));
        Store.User u = Main.store.findUser(target);
        if (u == null) {
            Main.error(ex, 404, "没有这个账号");
            return;
        }
        if (s != null && target.equals(s.username)) {
            Main.error(ex, 400, "不能举报自己");
            return;
        }
        if (reason.isEmpty()) {
            Main.error(ex, 400, "请填写举报理由");
            return;
        }
        boolean targetStaff = Main.isAdminAccount(target) || target.equals(Main.judgeUser());
        if (targetStaff && s != null) {
            // 观众/选手只能举报同为观众或选手的人；工作人员违规只能由管理员上报给总管
            Main.error(ex, 403, "观众和选手只能举报其他观众或选手");
            return;
        }
        if (targetStaff) {
            type = "staff";
        } else if (!"speech".equals(type) && !"violation".equals(type)) {
            Main.error(ex, 400, "举报类型只能是违规或不当言论");
            return;
        }
        for (Store.CaseRecord c : Main.store.cases()) {
            if ("pending".equals(c.status) && "report".equals(c.kind)
                    && c.target.equals(target) && c.reason.equals(reason) && c.reporterNo == reporterNo) {
                Main.error(ex, 400, "同样的举报已经在处理中");
                return;
            }
        }
        Store.CaseRecord c = Main.store.addCase("report", type, target, u.no,
                s == null ? "staff" : s.role, reporter, reporterNo, reason);
        Main.ok(ex, "{" + Json.bool("ok", true) + "," + Json.inum("id", c.id) + ","
                + Json.str("status", c.status) + "}");
    }

    /** 申诉：被判违规 / 禁言 / 封号的人对处罚提出申诉，按处罚类型分派处理人 */
    static void appeal(HttpExchange ex) throws IOException {
        Main.Session s = Main.auth(ex);
        if (s == null) {
            return;
        }
        if (!"player".equals(s.role) && !"viewer".equals(s.role)) {
            Main.error(ex, 403, "只有选手与观众可以申诉");
            return;
        }
        Map<String, String> f = Main.form(ex);
        String reason = Main.cleanCommentText(f.getOrDefault("reason", ""));
        if (reason.isEmpty()) {
            Main.error(ex, 400, "请填写申诉理由");
            return;
        }
        String type = Main.field(f, "type");
        if (!"ban".equals(type) && !"mute".equals(type) && !"violation".equals(type)) {
            type = "";
        }
        if ("ban".equals(type) && !Main.isBanned(s.username)) {
            type = "";
        }
        if ("mute".equals(type) && Main.muteRemainMs(s.username) <= 0) {
            type = "";
        }
        if ("violation".equals(type) && Main.store.state("violation." + s.username, "").isEmpty()) {
            type = "";
        }
        if (type.isEmpty()) {
            if (Main.isBanned(s.username)) {
                type = "ban";
            } else if (Main.muteRemainMs(s.username) > 0) {
                type = "mute";
            } else if (!Main.store.state("violation." + s.username, "").isEmpty()) {
                type = "violation";
            } else {
                Main.error(ex, 403, "你当前没有可申诉的处罚（禁言、封号、违规记录都没有）");
                return;
            }
        }
        for (Store.CaseRecord c : Main.store.cases()) {
            if ("pending".equals(c.status) && "appeal".equals(c.kind)
                    && c.reporterNo == s.no && c.type.equals(type)) {
                Main.error(ex, 400, "同类申诉已经在处理中，请等待处理结果");
                return;
            }
        }
        Store.CaseRecord c = Main.store.addCase("appeal", type, s.username, s.no,
                s.role, labelOf(s.username), s.no, reason);
        Main.ok(ex, "{" + Json.bool("ok", true) + "," + Json.inum("id", c.id) + ","
                + Json.str("type", type) + "," + Json.str("status", c.status) + "}");
    }

    /** 我自己的处罚现状 + 我提交过的举报/申诉（头像菜单里的「我要申诉」用） */
    static void mine(HttpExchange ex) throws IOException {
        Main.Session s = Main.auth(ex);
        if (s == null) {
            return;
        }
        StringBuilder sb = new StringBuilder("{");
        sb.append(Json.bool("banned", Main.isBanned(s.username))).append(',');
        sb.append(Json.inum("muteRemainMs", Main.muteRemainMs(s.username))).append(',');
        sb.append(Json.str("violation", Main.store.state("violation." + s.username, ""))).append(',');
        sb.append(Json.esc("items")).append(":[");
        int i = 0;
        for (Store.CaseRecord c : Main.store.cases()) {
            if (c.reporterNo != s.no || s.no == 0) {
                continue;
            }
            if (i++ > 0) {
                sb.append(',');
            }
            sb.append(caseJson(c, true));
        }
        sb.append("]}");
        Main.ok(ex, sb.toString());
    }

    /** 举报时可以选的对象：观众/选手只列出同身份的人；工作人员另走 staff 上报 */
    static void targets(HttpExchange ex) throws IOException {
        // 先判断是不是工作人员：Main.auth 对控制台令牌会直接写 401，
        // 以前无条件先调 auth，导致管理员/裁判打开举报框必然失败
        String role = staffRole(ex);
        Main.Session s = null;
        if (role.isEmpty()) {
            s = Main.auth(ex);
            if (s == null) {
                return;
            }
        }
        String self = role.isEmpty() ? (s == null ? "" : s.username)
                : ("judge".equals(role) ? Main.judgeUser() : Main.adminTokenUsers.get(Main.tokenOf(ex)));
        List<String> players = Main.playerList();
        List<Store.User> list = new java.util.ArrayList<>();
        for (Store.User u : Main.store.allUsers()) {
            boolean adminAcc = Main.isAdminAccount(u.username);
            boolean judgeAcc = u.username.equals(Main.judgeUser());
            if (role.isEmpty() && (adminAcc || judgeAcc)) {
                continue;   // 观众/选手不能把管理员或裁判列进可举报对象
            }
            if (u.username.equals(self)) {
                continue;   // 不能举报自己
            }
            list.add(u);
        }
        // 正在参赛的排前面，其余按学号；这样名单里一眼能看到「选手」
        list.sort((a, b) -> {
            boolean pa = players.contains(a.username);
            boolean pb = players.contains(b.username);
            if (pa != pb) {
                return pa ? -1 : 1;
            }
            return Integer.compare(a.no, b.no);
        });
        StringBuilder sb = new StringBuilder("{" + Json.esc("items") + ":[");
        int i = 0;
        for (Store.User u : list) {
            if (i++ > 0) {
                sb.append(',');
            }
            boolean adminAcc = Main.isAdminAccount(u.username);
            boolean judgeAcc = u.username.equals(Main.judgeUser());
            sb.append('{').append(Json.inum("no", u.no)).append(',')
                    .append(Json.str("username", u.username)).append(',')
                    .append(Json.str("label", labelOf(u.username))).append(',')
                    .append(Json.bool("player", players.contains(u.username))).append(',')
                    .append(Json.bool("online", Main.isOnline(u.username))).append(',')
                    .append(Json.bool("admin", adminAcc)).append(',')
                    .append(Json.bool("judge", judgeAcc)).append('}');
        }
        sb.append("]}");
        Main.ok(ex, sb.toString());
    }

    static void list(HttpExchange ex) throws IOException {
        String role = staffRole(ex);
        if (role.isEmpty()) {
            Main.error(ex, 401, "没有权限");
            return;
        }
        String status = Main.query(ex, "status");
        String kind = Main.query(ex, "kind");
        StringBuilder sb = new StringBuilder("{" + Json.esc("role") + ":" + Json.esc(role) + ',');
        sb.append(Json.esc("who")).append(':').append(Json.esc(staffLabel(ex))).append(',');
        sb.append(Json.esc("items")).append(":[");
        List<Store.CaseRecord> all = Main.store.cases();
        all.sort((a, b) -> Long.compare(b.id, a.id));
        int i = 0;
        for (Store.CaseRecord c : all) {
            // 不属于该身份处理范围的，直接不下发：裁判看不到不当言论，管理员看不到违规
            if (!canHandle(role, c.type)) {
                continue;
            }
            if (!status.isEmpty() && !"all".equals(status) && !status.equals(c.status)) {
                continue;
            }
            if (!kind.isEmpty() && !"all".equals(kind) && !kind.equals(c.kind)) {
                continue;
            }
            if (i++ > 0) {
                sb.append(',');
            }
            sb.append(caseJson(c, true));
        }
        sb.append("]}");
        Main.ok(ex, sb.toString());
    }

    /** 处理一条举报/申诉：通过即生效（申诉自动撤罚，举报核实后判罚） */
    static void op(HttpExchange ex) throws IOException {
        String role = staffRole(ex);
        if (role.isEmpty()) {
            Main.error(ex, 401, "没有权限");
            return;
        }
        Map<String, String> f = Main.form(ex);
        long id = 0L;
        try {
            id = Long.parseLong(Main.field(f, "id"));
        } catch (Exception ignore) {
        }
        String action = Main.field(f, "action");
        String note = Main.cleanCommentText(f.getOrDefault("note", ""));
        Store.CaseRecord c = Main.store.caseById(id);
        if (c == null) {
            Main.error(ex, 404, "没有这条记录");
            return;
        }
        if (!"pending".equals(c.status)) {
            Main.error(ex, 400, "这条已经处理过了");
            return;
        }
        if (!canHandle(role, c.type)) {
            Main.error(ex, 403, "这条不在你的处理范围");
            return;
        }
        if (!"approve".equals(action) && !"reject".equals(action) && !"approve_ban".equals(action)) {
            Main.error(ex, 400, "只能是通过、驳回，或通过并封号");
            return;
        }
        boolean banToo = "approve_ban".equals(action);
        if (banToo) {
            // 「通过并封号」只给总管，且只对工作人员违规生效；仍然由人主动点，不是自动封
            if (!"super".equals(role)) {
                Main.error(ex, 403, "只有总管可以通过并封号");
                return;
            }
            if (!"report".equals(c.kind) || !"staff".equals(c.type)) {
                Main.error(ex, 400, "只有工作人员违规案件支持通过并封号");
                return;
            }
        }
        if ("approve".equals(action) || banToo) {
            apply(c, note);
        }
        String status = "reject".equals(action) ? "rejected" : "approved";
        if (banToo) {
            Main.store.setState("ban." + c.target, String.valueOf(System.currentTimeMillis() + 86_400_000L * 3650L));
            String why = c.reason + (note.isEmpty() ? "" : "；" + note);
            Main.store.setState("banreason." + c.target, why.length() > 60 ? why.substring(0, 60) : why);
            Main.store.setState("bancase." + c.target, String.valueOf(c.id));
            Main.demoteStaffOnBan(c.target);
            Main.killSessionsOf(c.target);
        }
        Main.store.updateCase(id, status, staffLabel(ex),
                banToo ? (note.isEmpty() ? "通过并封号" : note + "；通过并封号") : note);
        Main.ok(ex, caseJson(Main.store.caseById(id), false));
    }

    /** 通过之后真正落到处罚上的动作 */
    private static void apply(Store.CaseRecord c, String note) throws IOException {
        if ("appeal".equals(c.kind)) {
            switch (c.type) {
                case "mute":
                    Main.unmuteUser(c.target);
                    break;
                case "ban":
                    Main.store.removeState("ban." + c.target);
                    break;
                case "violation":
                    // 交过卷：恢复判罚前成绩；没交过卷（人还在答题）：放回比赛区并给独立倒计时
                    Main.onViolationAppealApproved(c.target);
                    break;
                default:
                    break;
            }
            return;
        }
        if ("speech".equals(c.type)) {
            Main.punishViolation(c.target);
        } else if ("violation".equals(c.type) && !Main.isAdminAccount(c.target)
                && !c.target.equals(Main.judgeUser())) {
            String why = c.reason + (note.isEmpty() ? "" : "；" + note);
            Main.penalizeViolation(c.target, why.length() > 60 ? why.substring(0, 60) : why, "经举报核实判违规");
        }
        // staff 类型：只把处理结论留档，撤职/封号由总管另行在总控台操作
    }

    static String caseJson(Store.CaseRecord c, boolean canHandle) {
        return "{" + Json.inum("id", c.id) + ","
                + Json.str("kind", c.kind) + ","
                + Json.str("kindLabel", "report".equals(c.kind) ? "举报" : "申诉") + ","
                + Json.str("type", c.type) + ","
                + Json.str("typeLabel", caseTypeLabel(c.type)) + ","
                + Json.str("target", c.target) + ","
                + Json.inum("targetNo", c.targetNo) + ","
                + Json.str("targetLabel", labelOf(c.target)) + ","
                + Json.str("reporterRole", c.reporterRole) + ","
                + Json.str("reporter", c.reporter) + ","
                + Json.inum("reporterNo", c.reporterNo) + ","
                + Json.str("reason", c.reason) + ","
                + Json.str("status", c.status) + ","
                + Json.str("handler", c.handler) + ","
                + Json.str("handledAt", c.handledAt) + ","
                + Json.str("note", c.note) + ","
                + Json.str("createdAt", c.createdAt) + ","
                + Json.bool("canHandle", canHandle) + "}";
    }

    // ---------------- 旧接口兼容 ----------------

    /** 旧的「管理员检举选手」：直接落成一条违规举报，不再另走一套收件箱 */
    static void legacyAdminReport(HttpExchange ex) throws IOException {
        if (!Main.isAdmin(ex) && !Main.isSuper(ex)) {
            Main.error(ex, 401, "没有权限");
            return;
        }
        Map<String, String> f = Main.form(ex);
        String target = Main.field(f, "username");
        String reason = Main.cleanCommentText(f.getOrDefault("reason", ""));
        Store.User u = Main.store.findUser(target);
        if (u == null) {
            Main.error(ex, 404, "没有这个账号");
            return;
        }
        if (reason.isEmpty()) {
            Main.error(ex, 400, "请填写检举理由");
            return;
        }
        boolean targetStaff = Main.isAdminAccount(target) || target.equals(Main.judgeUser());
        Store.CaseRecord c = Main.store.addCase("report", targetStaff ? "staff" : "violation",
                target, u.no, "staff", staffLabel(ex), 0, reason);
        Main.ok(ex, "{" + Json.bool("ok", true) + "," + Json.inum("id", c.id) + "}");
    }

    /** 旧的裁判收件箱：读的就是新案件表里的待处理违规举报 */
    static void legacyJudgeReports(HttpExchange ex) throws IOException {
        if (!Main.isJudge(ex) && !Main.isSuper(ex)) {
            Main.error(ex, 401, "没有裁判权限");
            return;
        }
        StringBuilder sb = new StringBuilder("{" + Json.esc("items") + ":[");
        int i = 0;
        for (Store.CaseRecord c : Main.store.cases()) {
            if (!"pending".equals(c.status) || !"report".equals(c.kind) || !"violation".equals(c.type)) {
                continue;
            }
            if (i++ > 0) {
                sb.append(',');
            }
            sb.append('{').append(Json.inum("id", c.id)).append(',')
                    .append(Json.inum("no", c.targetNo)).append(',')
                    .append(Json.str("username", c.target)).append(',')
                    .append(Json.str("reason", c.reason)).append(',')
                    .append(Json.str("reporter", c.reporter)).append(',')
                    .append(Json.inum("at", 0L)).append(',')
                    .append(Json.str("status", "new")).append('}');
        }
        sb.append("]}");
        Main.ok(ex, sb.toString());
    }

    /** 旧的「已读」按钮：新体系里由 /api/case/op 真正处理，这里只回成功 */
    static void legacyJudgeReportAck(HttpExchange ex) throws IOException {
        if (!Main.isJudge(ex) && !Main.isSuper(ex)) {
            Main.error(ex, 401, "没有裁判权限");
            return;
        }
        Main.okTrue(ex);
    }
}
