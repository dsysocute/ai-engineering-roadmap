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

/** 管理员后台：登录、成绩、清空/删除、账号列表与操作、口令。 */
class AdminApi extends App {

    /**
     * 管理员登录：带账号的就是被裁判授予权限的学生（默认口令是他的学号），
     * 不带账号的走主口令，给老师自己留一条后路。
     */
    static void adminLogin(HttpExchange ex) throws IOException {
        if (System.currentTimeMillis() < adminLockUntil) {
            error(ex, 429, "口令错误次数太多，先等 1 分钟");
            return;
        }
        Map<String, String> f = form(ex);
        String username = field(f, "username");
        String pass = f.getOrDefault("password", "").trim();
        String who;
        if (username.isEmpty()) {
            if (!ADMIN_PASS.equals(pass)) {
                adminLoginFail(ex);
                return;
            }
            who = "master";
        } else {
            Store.User u = store.findUser(username);
            if (u == null || !isAdminAccount(username) || !adminPassOf(username).equals(pass)) {
                adminLoginFail(ex);
                return;
            }
            who = username;
        }
        adminFails.set(0);
        String token = UUID.randomUUID().toString().replace("-", "");
        adminTokens.add(token);
        adminTokenUsers.put(token, who);
        ok(ex, "{" + Json.str("token", token) + ","
                + Json.str("role", "admin") + ","
                + Json.str("who", who) + ","
                + Json.str("name", "master".equals(who) ? "主管理员" : who) + "}");
    }

    static void adminLoginFail(HttpExchange ex) throws IOException {
        if (adminFails.incrementAndGet() >= ADMIN_MAX_FAILS) {
            adminFails.set(0);
            adminLockUntil = System.currentTimeMillis() + ADMIN_LOCK_MS;
        }
        error(ex, 401, "口令不对");
    }

    static void adminResults(HttpExchange ex) throws IOException {
        if (!requireAdminOrSuper(ex)) {
            return;
        }
        List<Store.ResultRecord> list = store.leaderboard();
        StringBuilder sb = new StringBuilder("{");
        sb.append(Json.inum("total", list.size())).append(',');
        sb.append(Json.inum("users", store.allUsers().size())).append(',');
        sb.append(Json.esc("results")).append(":[");
        int i = 0;
        for (Store.ResultRecord r : list) {
            if (i++ > 0) {
                sb.append(',');
            }
            sb.append(resultJson(r));
        }
        sb.append("]}");
        ok(ex, sb.toString());
    }

    /** 管理员清空全部成绩：内存和 data 下的 tsv/csv 一起清掉 */
    static void adminClearAll(HttpExchange ex) throws IOException {
        if (!requireAdminOrSuper(ex)) {
            return;
        }
        store.clearAll();
        ok(ex, "{" + Json.bool("ok", true) + ","
                + Json.str("msg", "提交记录已清空") + "}");
    }

    /**
     * 删除单个选手的成绩：有人中途出了状况、裁判同意重考时用，
     * 其他人的成绩保持不动。顺带把该账号的会话和计时一并重置，
     * 让他能重新点【开始】从头来一遍。
     */
    static void adminDeleteOne(HttpExchange ex) throws IOException {
        if (!requireAdminOrSuper(ex)) {
            return;
        }
        Map<String, String> f = form(ex);
        String username = field(f, "username");
        if (username.isEmpty()) {
            error(ex, 400, "缺少选手账号");
            return;
        }
        synchronized (store) {
            boolean removed = store.deleteResult(username);
            if (!removed) {
                error(ex, 404, "该选手没有成绩记录");
                return;
            }
            activeToken.remove(username + "#player");
            activeToken.remove(username + "#viewer");
            sessions.entrySet().removeIf(e -> username.equals(e.getValue().username));
            startTimes.remove(username);
            store.removeState("start." + username);
            store.removeState("violation." + username);
            store.removeState("violprev." + username);
            finishingAccounts.remove(username);
            lastCommentAt.remove(username);
            efficiencyQueued.remove(username);
        }
        ok(ex, "{" + Json.bool("ok", true) + ","
                + Json.str("msg", "已删除 " + username + " 的成绩，该账号可重新作答") + "}");
    }

    /** 管理员改口令：输入原口令再输新口令 */
    static void adminPassChange(HttpExchange ex) throws IOException {
        String token = ex.getRequestHeaders().getFirst("X-Token");
        if (token == null || !adminTokens.contains(token)) {
            error(ex, 401, "没有权限");
            return;
        }
        String who = adminTokenUsers.getOrDefault(token, "master");
        if ("master".equals(who)) {
            error(ex, 400, "主管理员口令请改 start.bat 里的 ADMIN_PASS");
            return;
        }
        Map<String, String> f = form(ex);
        if (!adminPassOf(who).equals(f.getOrDefault("oldPass", "").trim())) {
            error(ex, 400, "原口令不对");
            return;
        }
        String np = f.getOrDefault("newPass", "").trim();
        if (!validPass(np)) {
            error(ex, 400, "新口令请控制在 4 ~ 20 位");
            return;
        }
        store.setState("adminpass." + who, np);
        okTrue(ex);
    }

    static void adminUsers(HttpExchange ex) throws IOException {
        if (!isAdminOrSuper(ex) && !isJudge(ex)) {
            error(ex, 401, "没有权限");
            return;
        }
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder("{\"items\":[");
        int i = 0;
        for (Store.User u : store.allUsers()) {
            boolean isJudge = u.username.equals(judgeUser());
            String role = isJudge ? "judge"
                    : (store.state("admin." + u.username, "").isEmpty() ? "student" : "admin");
            long banUntil = store.stateLong("ban." + u.username, 0L);
            boolean banned = banUntil > now;
            long mute = muteRemainMs(u.username);
            boolean player = !store.state("player." + u.username, "").isEmpty();
            boolean finished = store.getResult(u.username) != null;
            if (i++ > 0) {
                sb.append(',');
            }
            sb.append('{').append(Json.inum("no", u.no)).append(',')
                    .append(Json.str("username", u.username)).append(',')
                    .append(Json.str("role", role)).append(',')
                    .append(Json.bool("judge", isJudge)).append(',')
                    .append(Json.bool("online", isOnline(u.username))).append(',')
                    .append(Json.bool("player", player)).append(',')
                    .append(Json.bool("finished", finished)).append(',')
                    .append(Json.bool("banned", banned)).append(',')
                    .append(Json.str("banReason", banned ? store.state("banreason." + u.username, "") : "")).append(',')
                    .append(Json.inum("muteUntil", mute > 0 ? muteUntil(u.username) : 0L)).append(',')
                    .append(Json.inum("violations", (long) store.stateLong("mute." + u.username + ".count", 0)))
                    .append('}');
        }
        sb.append("]}");
        ok(ex, sb.toString());
    }

    static void adminUserOp(HttpExchange ex) throws IOException {
        if (!requireAdminOrSuper(ex)) {
            return;
        }
        Map<String, String> f = form(ex);
        String target = field(f, "username");
        String action = field(f, "action");
        Store.User u = store.findUser(target);
        if (u == null) {
            error(ex, 404, "没有这个账号");
            return;
        }
        long now = System.currentTimeMillis();
        switch (action) {
            case "resetPass":
                // 重置学生密码只给总管：留空即回到初始密码 123456
                if (!isSuper(ex)) {
                    error(ex, 403, "只有总管才能重置密码");
                    return;
                }
                String np = field(f, "newPass");
                if (np.isEmpty()) {
                    store.removeState("pass." + target);
                } else {
                    if (!validPass(np)) {
                        error(ex, 400, "新密码要 4 ~ 20 位");
                        return;
                    }
                    store.setState("pass." + target, np);
                }
                break;
            case "grantJudge":
            case "revokeJudge":
                // 裁判 = 同学里的高级管理员，全平台只有一个，只能由总管指定或撤销
                if (!isSuper(ex)) {
                    error(ex, 403, "只有总管才能设置裁判");
                    return;
                }
                if ("grantJudge".equals(action)) {
                    store.setState("judge.user", target);
                    // 没单独改过裁判口令时，默认就用这名同学的学号
                    store.removeState("judge.pass");
                    // 裁判本身是同学：设定后退出选手名单，只能以观众身份观赛
                    demoteToViewer(target);
                } else {
                    if (!target.equals(judgeUser())) {
                        error(ex, 400, "这位同学不是当前裁判");
                        return;
                    }
                    store.removeState("judge.user");
                }
                // 换裁判或撤裁判，旧口令签发的裁判令牌立即作废
                judgeTokens.clear();
                break;
            case "resetStaffPass":
                // 重置管理员 / 裁判的控制台口令（与学生登录密码是两套）
                if (!isSuper(ex)) {
                    error(ex, 403, "只有总管才能重置控制台口令");
                    return;
                }
                String kind = field(f, "kind");
                String newPass2 = field(f, "newPass");
                if (!newPass2.isEmpty() && !validPass(newPass2)) {
                    error(ex, 400, "新口令要 4 ~ 20 位");
                    return;
                }
                if ("admin".equals(kind)) {
                    if (!isAdminAccount(target)) {
                        error(ex, 400, "这位同学不是管理员");
                        return;
                    }
                    resetStaffPassword("adminpass." + target, newPass2);
                    adminTokens.removeIf(t -> target.equals(adminTokenUsers.get(t)));
                    adminTokenUsers.entrySet().removeIf(e -> target.equals(e.getValue()));
                } else if ("judge".equals(kind)) {
                    if (!target.equals(judgeUser())) {
                        error(ex, 400, "这位同学不是当前裁判");
                        return;
                    }
                    resetStaffPassword("judge.pass", newPass2);
                    judgeTokens.clear();
                } else {
                    error(ex, 400, "未知的控制台身份");
                    return;
                }
                break;
            case "grantAdmin":
            case "revokeAdmin":
                // 管理员身份只能由总管设置，管理员自己和裁判都没有这个按钮
                if (!isSuper(ex)) {
                    error(ex, 403, "只有总管才能设置管理员");
                    return;
                }
                if ("grantAdmin".equals(action)) {
                    store.setState("admin." + target, "1");
                    // 新授予的管理员，口令默认就是他的学号
                    store.removeState("adminpass." + target);
                    // 管理员本身是同学：设定后退出选手名单，只能以观众身份观赛
                    demoteToViewer(target);
                } else {
                    store.removeState("admin." + target);
                    store.removeState("adminpass." + target);
                    // 撤销后立刻作废已发放的后台 token，不能等他自然过期
                    adminTokens.removeIf(t -> target.equals(adminTokenUsers.get(t)));
                    adminTokenUsers.entrySet().removeIf(e -> target.equals(e.getValue()));
                }
                break;
            case "mute":
            case "unmute":
                // 禁言归管理员：管理员可以管观众和选手，但不能动管理员/裁判
                if (!isSuper(ex) && (isAdminAccount(target) || target.equals(judgeUser()))) {
                    error(ex, 403, "管理员不能处理管理员或裁判");
                    return;
                }
                if ("mute".equals(action)) {
                    muteFor(target, minutesOf(f));
                } else {
                    unmuteUser(target);
                }
                break;
            case "ban":
                // 封号只有总管能做；封掉管理员/裁判时同时降级成观众，不能再参赛
                if (!isSuper(ex)) {
                    error(ex, 403, "只有总管才能封号");
                    return;
                }
                store.setState("ban." + target, String.valueOf(now + 86_400_000L * 3650L));
                String banWhy = cleanCommentText(f.getOrDefault("reason", ""));
                if (banWhy.isEmpty()) {
                    banWhy = "总管手动封号";
                }
                store.setState("banreason." + target, banWhy.length() > 60 ? banWhy.substring(0, 60) : banWhy);
                String banCase = field(f, "caseId");
                if (!banCase.isEmpty()) {
                    store.setState("bancase." + target, banCase);
                }
                demoteStaffOnBan(target);
                killSessionsOf(target);
                break;
            case "unban":
                if (!isSuper(ex)) {
                    error(ex, 403, "只有总管才能解封");
                    return;
                }
                store.removeState("ban." + target);
                store.removeState("banreason." + target);
                store.removeState("bancase." + target);
                break;
                default:
                error(ex, 400, "未知操作");
                return;
        }
        okTrue(ex);
    }

    /** 重置控制台口令：留空就清掉，回到该身份的默认口令 */
    static void resetStaffPassword(String key, String newPass) throws IOException {
        if (newPass.isEmpty()) {
            store.removeState(key);
        } else {
            store.setState(key, newPass);
        }
    }
}
