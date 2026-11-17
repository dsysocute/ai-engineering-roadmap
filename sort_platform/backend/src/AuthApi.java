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

/** 登录 / 登出 / 当前身份 / 学生改密与密保 / 昵称。 */
class AuthApi extends App {

    static void login(HttpExchange ex) throws IOException {
        Map<String, String> f = form(ex);
        String username = field(f, "username");
        String password = f.getOrDefault("password", "").trim();
        // 登录时必须选身份：选手 或 观众
        String role = "viewer".equals(f.getOrDefault("role", "player")) ? "viewer" : "player";

        Store.User u = store.findUser(username);
        if (u == null || !passwordOf(u.username).equals(password)) {
            error(ex, 401, "账号或密码不对");
            return;
        }
        // 被封号的人仍然可以登录：降为观众，只能观赛和申诉，不能参赛、不能发言
        boolean banned = isBanned(username);
        if (banned) {
            role = "viewer";
        }
        // 裁判/管理员本身就是同学，但被设定后自动归到观众一栏，不能参赛
        boolean staffAccount = isAdminAccount(username) || username.equals(judgeUser());
        if (staffAccount) {
            role = "viewer";
        }
        // 比赛已经开跑才进来的：不再给本场参赛资格，直接按观众身份进观赛页
        boolean latePlayer = false;
        if (!banned && !staffAccount && "player".equals(role) && "running".equals(contestStatus())
                && store.state("player." + username, "").isEmpty()) {
            role = "viewer";
            latePlayer = true;
        }
        if ("player".equals(role)) {
            // 选手名单是动态的：谁选了选手身份登录，谁就出现在观赛列表里
            if (store.state("player." + username, "").isEmpty()) {
                store.setState("player." + username, Store.now());
            }
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        String activeKey = username + "#" + role;
        String old = activeToken.put(activeKey, token);
        boolean kickedPrevious = old != null && !old.equals(token);
        if (kickedPrevious) {
            sessions.remove(old);
            killedTokens.put(old, System.currentTimeMillis());
        }

        Session s = new Session();
        s.username = u.username;
        s.no = u.no;
        s.role = role;
        Long st = startTimes.get(u.username);
        s.startMillis = st == null ? 0L : st;
        s.finished = store.getResult(u.username) != null;
        s.lastSeenMillis = System.currentTimeMillis();
        sessions.put(token, s);
        lastSeen.put(u.username, System.currentTimeMillis());

        StringBuilder sb = new StringBuilder("{");
        sb.append(Json.str("token", token)).append(',');
        sb.append(Json.str("username", u.username)).append(',');
        sb.append(Json.inum("no", u.no)).append(',');
        sb.append(Json.str("role", role)).append(',');
        sb.append(Json.str("contest", contestStatus())).append(',');
        sb.append(Json.inum("muteUntil", muteUntil(username)));
        if (staffAccount && !banned) {
            sb.append(',').append(Json.str("notice",
                    "你已被设为裁判/管理员：学生账号只能以观众身份观赛，不参与本场排名"));
        } else if (latePlayer) {
            sb.append(',').append(Json.str("notice", "比赛已经开始，你本场未报名，现按观众身份进入观赛，请静候下场比赛"));
        } else if (banned) {
            sb.append(',').append(Json.str("notice", "该账号已被封禁，只能观赛与提交申诉，不能参赛或发言"));
        }
        sb.append(',').append(Json.bool("kickedPrevious", kickedPrevious));
        sb.append('}');
        ok(ex, sb.toString());
    }

    /** 退出登录：把这个 token 主动作废，免得下次别人登录时它还算「被顶」 */
    static void logout(HttpExchange ex) throws IOException {
        String token = ex.getRequestHeaders().getFirst("X-Token");
        if (token != null) {
            Session s = sessions.remove(token);
            if (s != null && token.equals(activeToken.get(s.username + "#" + s.role))) {
                activeToken.remove(s.username + "#" + s.role);
            }
        }
        okTrue(ex);
    }

    static void me(HttpExchange ex) throws IOException {
        Session s = auth(ex);
        if (s == null) {
            return;
        }
        StringBuilder sb = new StringBuilder("{");
        sb.append(Json.str("username", s.username)).append(',');
        sb.append(Json.inum("no", s.no)).append(',');
        sb.append(Json.str("nick", CaseCenter.nickOf(s.username))).append(',');
        sb.append(Json.str("role", s.role)).append(',');
        String status;
        if (!"player".equals(s.role)) {
            status = "VIEWER";
        } else {
            status = s.finished ? "FINISHED" : (s.startMillis == 0 ? "NOT_STARTED" : "CODING");
        }
        sb.append(Json.str("status", status)).append(',');
        sb.append(Json.str("contest", contestStatus())).append(',');
        sb.append(Json.inum("muteUntil", muteUntil(s.username))).append(',');
        sb.append(Json.inum("elapsedSeconds", elapsedSeconds(s)));
        long reinst = store.stateLong("reinst." + s.username, 0L);
        long personalDeadline = store.stateLong("reinstDeadline." + s.username, 0L);
        sb.append(',').append(Json.bool("reinstated", reinst > 0));
        sb.append(',').append(Json.inum("personalDeadline", personalDeadline));
        sb.append(',').append(Json.inum("personalRemainMillis",
                personalDeadline > 0 ? Math.max(0L, personalDeadline - System.currentTimeMillis()) : 0L));
        Store.ResultRecord r = store.getResult(s.username);
        if (r != null) {
            sb.append(',').append(Json.esc("result")).append(':').append(resultJson(r));
        }
        sb.append('}');
        ok(ex, sb.toString());
    }

    /**
     * 观赛页头像用：按令牌返回工作人员的真实身份。
     * 裁判 = 被指定的那个学号；总管/管理员 = 头衔（总管不在 65 个学号里）。
     */
    static void staffMe(HttpExchange ex) throws IOException {
        String token = tokenOf(ex);
        String role = "";
        String label = "";
        String avatar = "";
        int no = 0;
        if (judgeTokens.contains(token)) {
            role = "judge";
            label = "裁判";
            Store.User u = store.findUser(judgeUser());
            if (u != null) {
                no = u.no;
                avatar = String.format("%02d", no);
            } else {
                avatar = "裁判";
            }
        } else if (superTokens.contains(token)) {
            role = "super";
            label = "总管";
            avatar = "总管";
        } else if (isAdmin(ex)) {
            role = "admin";
            label = "管理员";
            // 管理员本身是同学的话，头像用他的学号；主管理员（教师）没有学号，显示「管理员」
            String who = adminTokenUsers.get(token);
            Store.User au = who == null || "master".equals(who) ? null : store.findUser(who);
            if (au != null) {
                no = au.no;
                avatar = String.format("%02d", no);
            } else {
                avatar = "管理员";
            }
        }
        if (role.isEmpty()) {
            error(ex, 401, "没有权限");
            return;
        }
        ok(ex, "{" + Json.str("role", role) + "," + Json.str("label", label) + ","
                + Json.str("avatar", avatar) + "," + Json.inum("no", no) + "}");
    }

    /** 学生自己改密码；第一次改必须顺手把两道密保设上 */
    static void userPassChange(HttpExchange ex) throws IOException {
        Map<String, String> f = form(ex);
        String header = ex.getRequestHeaders().getFirst("X-Token");
        Session s = header == null ? null : sessions.get(header);
        String username;
        if (s != null) {
            username = s.username;
        } else {
            // 登录页也能改：直接给账号 + 原密码，验过就允许
            username = field(f, "username");
            Store.User u = store.findUser(username);
            if (u == null) {
                error(ex, 404, "没有这个账号");
                return;
            }
        }
        String oldPass = f.getOrDefault("oldPass", "").trim();
        String newPass = f.getOrDefault("newPass", "").trim();
        if (!passwordOf(username).equals(oldPass)) {
            error(ex, 400, "原密码不对");
            return;
        }
        if (!validPass(newPass)) {
            error(ex, 400, "新密码请控制在 4 ~ 20 位");
            return;
        }
        String q1 = cleanCommentText(f.getOrDefault("q1", ""));
        String a1 = f.getOrDefault("a1", "").trim();
        String q2 = cleanCommentText(f.getOrDefault("q2", ""));
        String a2 = f.getOrDefault("a2", "").trim();
        if (!hasSec(username)) {
            if (q1.isEmpty() || a1.isEmpty() || q2.isEmpty() || a2.isEmpty()) {
                error(ex, 400, "首次修改密码需要同时设置两道密保问题");
                return;
            }
            if (q1.equals(q2)) {
                error(ex, 400, "两道密保问题不能一样");
                return;
            }
            store.setState("sec." + username + ".q1", q1);
            store.setState("sec." + username + ".a1", a1);
            store.setState("sec." + username + ".q2", q2);
            store.setState("sec." + username + ".a2", a2);
        }
        store.setState("pass." + username, newPass);
        ok(ex, "{" + Json.bool("ok", true) + "," + Json.bool("hasSec", true) + "}");
    }

    /** 当前账号的密保情况：只回问题，不回答案 */
    static void userSec(HttpExchange ex) throws IOException {
        Session s = auth(ex);
        if (s == null) {
            return;
        }
        StringBuilder sb = new StringBuilder("{");
        sb.append(Json.bool("hasSec", hasSec(s.username))).append(',');
        sb.append(Json.str("q1", store.state("sec." + s.username + ".q1", ""))).append(',');
        sb.append(Json.str("q2", store.state("sec." + s.username + ".q2", "")));
        sb.append('}');
        ok(ex, sb.toString());
    }

    /** 忘记密码第一步：按账号取出两道密保问题 */
    static void resetQuiz(HttpExchange ex) throws IOException {
        String username = query(ex, "username");
        Store.User u = store.findUser(username);
        if (u == null) {
            error(ex, 404, "没有这个账号");
            return;
        }
        if (!hasSec(username)) {
            error(ex, 400, "该账号还没设置密保，请联系管理员重置");
            return;
        }
        ok(ex, "{" + Json.str("username", username) + ","
                + Json.str("q1", store.state("sec." + username + ".q1", "")) + ","
                + Json.str("q2", store.state("sec." + username + ".q2", "")) + "}");
    }

    /** 忘记密码第二步：两道密保答对就能换新密码 */
    static void resetPass(HttpExchange ex) throws IOException {
        Map<String, String> f = form(ex);
        String username = field(f, "username");
        Store.User u = store.findUser(username);
        if (u == null) {
            error(ex, 404, "没有这个账号");
            return;
        }
        if (!hasSec(username)) {
            error(ex, 400, "该账号还没设置密保，请联系管理员重置");
            return;
        }
        String a1 = f.getOrDefault("a1", "").trim();
        String a2 = f.getOrDefault("a2", "").trim();
        if (!store.state("sec." + username + ".a1", "").equals(a1)
                || !store.state("sec." + username + ".a2", "").equals(a2)) {
            error(ex, 400, "密保答案不对");
            return;
        }
        String newPass = f.getOrDefault("newPass", "").trim();
        if (!validPass(newPass)) {
            error(ex, 400, "新密码请控制在 4 ~ 20 位");
            return;
        }
        store.setState("pass." + username, newPass);
        okTrue(ex);
    }

    /** 头像菜单里的「修改ID」：只改显示昵称，65 个学号账户名不可改 */
    static void userNick(HttpExchange ex) throws IOException {
        Session s = auth(ex);
        if (s == null) {
            return;
        }
        Map<String, String> f = form(ex);
        String nick = cleanCommentText(f.getOrDefault("nick", ""));
        if (nick.length() > 10) {
            nick = nick.substring(0, 10);
        }
        if (nick.isEmpty()) {
            store.removeState("nick." + s.username);
        } else {
            store.setState("nick." + s.username, nick);
        }
        okTrue(ex);
    }
}
