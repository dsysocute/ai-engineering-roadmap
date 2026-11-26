import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

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
 * 后端入口：登录、开始计时、测试运行、交卷判题、后台效率评测、排行榜。
 *
 * 交卷分两段：/api/finish 只跑正确性，立刻返回；
 * 正确性通过后由前端再调 /api/judge-efficiency，10 万规模的效率测试在后台线程池跑完回填。
 */
public class Main extends App {

    public static void main(String[] args) throws Exception {
        Path dataDir = Paths.get("data");
        store = new Store(dataDir);
        // 重启会把内存里的效率评测队列清空，已交卷但没算完的下次打开页面会永远「测算中」，
        // 启动时按成绩表补跑一遍。
        PlayApi.recoverPendingEfficiency();
        // 重启后把落盘的开始时刻读回来，比赛中途重启不影响选手计时
        for (String key : store.keysWithPrefix("start.")) {
            long v = store.stateLong(key, 0L);
            if (v > 0) {
                startTimes.put(key.substring("start.".length()), v);
            }
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newFixedThreadPool(16));

        server.createContext("/api/login", h(AuthApi::login));
        server.createContext("/api/logout", h(AuthApi::logout));
        server.createContext("/api/start", h(PlayApi::start));
        server.createContext("/api/test", h(PlayApi::test));
        server.createContext("/api/finish", h(PlayApi::finish));
        server.createContext("/api/judge-efficiency", h(PlayApi::judgeEfficiency));
        server.createContext("/api/me", h(AuthApi::me));
        server.createContext("/api/problem", h(PlayApi::problem));
        server.createContext("/api/leaderboard", h(WatchApi::leaderboard));
        server.createContext("/api/admin/login", h(AdminApi::adminLogin));
        server.createContext("/api/admin/results", h(AdminApi::adminResults));
        server.createContext("/api/admin/clear-all", h(AdminApi::adminClearAll));
        server.createContext("/api/admin/delete-one", h(AdminApi::adminDeleteOne));
        server.createContext("/api/comments", h(CommentApi::commentsApi));
        server.createContext("/api/watch", h(WatchApi::watchApi));
        server.createContext("/api/watch/code", h(WatchApi::watchCodeApi));
        server.createContext("/api/watch/danmaku", h(WatchApi::watchDanmaku));
        server.createContext("/api/editor", h(PlayApi::editorApi));
        server.createContext("/api/admin/comment-post", h(CommentApi::adminCommentPost));
        server.createContext("/api/admin/comment-delete", h(CommentApi::adminCommentDelete));
        server.createContext("/api/admin/users", h(AdminApi::adminUsers));
        server.createContext("/api/admin/user-op", h(AdminApi::adminUserOp));
        server.createContext("/api/contest", h(WatchApi::contestApi));
        server.createContext("/api/judge/login", h(JudgeApi::judgeLogin));
        server.createContext("/api/super/login", h(SuperApi::superLogin));
        server.createContext("/api/super/pass", h(SuperApi::superPassChange));
        server.createContext("/api/staff/comment", h(CommentApi::staffComment));
        server.createContext("/api/staff/me", h(AuthApi::staffMe));
        server.createContext("/api/judge/pass", h(JudgeApi::judgePassChange));
        server.createContext("/api/judge/start-contest", h(JudgeApi::judgeStartContest));
        server.createContext("/api/judge/force-end", h(JudgeApi::judgeForceEnd));
        server.createContext("/api/judge/violate", h(JudgeApi::judgeViolate));
        server.createContext("/api/judge/user-op", h(JudgeApi::judgeUserOp));
        server.createContext("/api/judge/reports", h(CaseCenter::legacyJudgeReports));
        server.createContext("/api/judge/report-ack", h(CaseCenter::legacyJudgeReportAck));
        server.createContext("/api/admin/pass", h(AdminApi::adminPassChange));
        server.createContext("/api/admin/report", h(CaseCenter::legacyAdminReport));
        server.createContext("/api/user/pass", h(AuthApi::userPassChange));
        server.createContext("/api/user/sec", h(AuthApi::userSec));
        server.createContext("/api/reset/quiz", h(AuthApi::resetQuiz));
        server.createContext("/api/reset/pass", h(AuthApi::resetPass));
        server.createContext("/api/quick", h(PlayApi::quickApi));
        server.createContext("/api/user/nick", h(AuthApi::userNick));
        server.createContext("/api/case/report", h(CaseCenter::report));
        server.createContext("/api/case/appeal", h(CaseCenter::appeal));
        server.createContext("/api/case/mine", h(CaseCenter::mine));
        server.createContext("/api/case/targets", h(CaseCenter::targets));
        server.createContext("/api/case/list", h(CaseCenter::list));
        server.createContext("/api/case/op", h(CaseCenter::op));
        server.createContext("/", h(Main::staticFile));

        server.start();

        String ip = localIp();
        System.out.println("========================================");
        System.out.println("  排序算法测评平台 已启动");
        System.out.println("  本机访问: http://localhost:" + PORT);
        System.out.println("  同学访问: http://" + ip + ":" + PORT);
        System.out.println("  后台看榜: http://" + ip + ":" + PORT + "/admin");
        System.out.println("  账号 202530126001 ~ 202530126065，密码 123456");
        System.out.println("========================================");
    }

    // ---------------- 接口 ----------------

    // ---------------- 观赛端（观众） ----------------

    // ---------------- 赛事状态 / 权限 ----------------

    // ---------------- 账号口令 / 密保 ----------------

    // ---------------- 总管（开发者） ----------------

    // ---------------- 裁判 ----------------

    // ---------------- 举报 / 申诉 ----------------

    // ---------------- 管理员：账号权限 ----------------

    private interface Handler {
        void handle(HttpExchange ex) throws IOException;
    }

    private static com.sun.net.httpserver.HttpHandler h(Handler inner) {
        return ex -> {
            try {
                if ("OPTIONS".equals(ex.getRequestMethod())) {
                    cors(ex);
                    ex.sendResponseHeaders(204, -1);
                    ex.close();
                    return;
                }
                inner.handle(ex);
            } catch (Exception e) {
                try {
                    error(ex, 500, "服务异常：" + e.getMessage());
                } catch (Exception ignored) {
                }
            } finally {
                ex.close();
            }
        };
    }

    private static void staticFile(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }
        if (path.equals("/admin")) {
            path = "/admin.html";
        }
        if (path.equals("/watch")) {
            path = "/watch.html";
        }
        if (path.equals("/judge")) {
            path = "/judge.html";
        }
        if (path.equals("/super")) {
            path = "/super.html";
        }
        if (path.equals("/case")) {
            path = "/case.html";
        }
        // 只允许 frontend 目录内的文件：先归一化再判断前缀，
        // 否则 /../data/state.tsv 这类请求会把口令、成绩直接读走
        Path base = Paths.get("frontend").toAbsolutePath().normalize();
        Path file;
        try {
            file = base.resolve(path.substring(1)).normalize();
        } catch (Exception badPath) {
            error(ex, 404, "not found");
            return;
        }
        if (!file.startsWith(base) || !Files.isRegularFile(file)) {
            error(ex, 404, "not found");
            return;
        }
        byte[] bytes = Files.readAllBytes(file);
        String type = "text/plain; charset=utf-8";
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".html")) {
            type = "text/html; charset=utf-8";
        } else if (name.endsWith(".css")) {
            type = "text/css; charset=utf-8";
        } else if (name.endsWith(".js")) {
            type = "application/javascript; charset=utf-8";
        } else if (name.endsWith(".png")) {
            type = "image/png";
        } else if (name.endsWith(".ico")) {
            type = "image/x-icon";
        }
        ex.getResponseHeaders().set("Content-Type", type);
        // 本地平台改完就要立刻生效：禁止浏览器对静态文件做启发式缓存，
        // 否则用户每次进页面都得 Ctrl+F5 才能看到新版本
        ex.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        ex.getResponseHeaders().set("Pragma", "no-cache");
        ex.getResponseHeaders().set("Expires", "0");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String localIp() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof java.net.Inet4Address) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "localhost";
    }
}

