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

/** 讨论区与发言：学生/观众发言、管理员发帖与删帖、裁判/总管发言。 */
class CommentApi extends App {

    /** 讨论区：GET 拉最新一页，POST 发一条。选手本人完赛前对讨论区不可见、不可发 */
    static void commentsApi(HttpExchange ex) throws IOException {
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            // 裁判、总管、管理员都可以进观赛页/后台看讨论，但只读不写（发言走专门接口）
            boolean judgeViewer = isJudge(ex) || isSuper(ex) || isAdmin(ex);
            Session s = judgeViewer ? null : auth(ex);
            if (!judgeViewer && s == null) {
                return;
            }
            String q = ex.getRequestURI().getQuery();
            boolean onlyDanmaku = q != null && q.contains("kind=danmaku");
            String cs = contestStatus();
            boolean duringContest = "running".equals(cs) || "countdown".equals(cs);
            // 弹幕：观众一直能看；选手赛前能看、赛中未交卷看不到、交卷后解锁
            boolean danmakuLocked = !judgeViewer && s != null && "player".equals(s.role)
                    && duringContest && !canReadDiscussion(s);
            // 选手本人未完赛时可以看弹幕，但看不到讨论内容
            if (!judgeViewer && !onlyDanmaku && !canReadDiscussion(s)) {
                error(ex, 403, "本人完赛前讨论区关闭，提交代码后自动解锁");
                return;
            }
            int limit = (int) queryLong(ex, "limit", 60L);
            if (limit <= 0 || limit > 200) {
                limit = 60;
            }
            List<Store.Comment> list = danmakuLocked
                    ? new ArrayList<>()
                    : store.recentComments(onlyDanmaku ? 200 : limit);
            if (onlyDanmaku) {
                List<Store.Comment> dm = new ArrayList<>();
                for (Store.Comment c : list) {
                    if ("danmaku".equals(c.kind)) {
                        dm.add(c);
                    }
                }
                if (dm.size() > limit) {
                    dm = new ArrayList<>(dm.subList(dm.size() - limit, dm.size()));
                }
                list = dm;
            }
            StringBuilder sb = new StringBuilder("{");
            sb.append(Json.inum("last", store.lastCommentId())).append(',');
            sb.append(Json.bool("locked", danmakuLocked)).append(',');
            sb.append(Json.esc("items")).append(":[");
            int i = 0;
            for (Store.Comment c : list) {
                if (i++ > 0) {
                    sb.append(',');
                }
                sb.append(commentJson(c));
            }
            sb.append("]}");
            ok(ex, sb.toString());
            return;
        }

        Session s = auth(ex);
        if (s == null) {
            return;
        }
        Map<String, String> f = form(ex);
        String text = cleanCommentText(f.getOrDefault("text", ""));
        if (text.isEmpty()) {
            error(ex, 400, "内容不能为空");
            return;
        }
        String kind = "danmaku".equals(f.getOrDefault("kind", "comment")) ? "danmaku" : "comment";
        String cs = contestStatus();
        boolean duringContest = "running".equals(cs) || "countdown".equals(cs);
        if ("danmaku".equals(kind)) {
            // 观众一直可以发；选手必须交卷后（赛前请用快捷消息）；工作人员走各自接口
            if ("player".equals(s.role) && !canReadDiscussion(s)) {
                error(ex, 403, duringContest
                        ? "比赛进行中，选手交卷后才能发弹幕"
                        : "选手交卷后才能发弹幕，赛前请用上方快捷消息");
                return;
            }
        } else if (!canReadDiscussion(s)) {
            // 讨论内容对未完赛选手锁定，弹幕不受此限
            error(ex, 403, "本人完赛前讨论区关闭，提交代码后自动解锁");
            return;
        }
        if (!speechAllowed(ex, s.username, text)) {
            return;
        }
        // 赛中防递答案：比赛进行中才拦提示词；赛前/赛后的正常讨论不拦
        if ("danmaku".equals(kind) && duringContest && promptBlockedWord(text) != null) {
            error(ex, 400, "弹幕含有比赛提示内容，已拦截");
            return;
        }
        long cd = "danmaku".equals(kind) ? DANMAKU_CD_MS : COMMENT_CD_MS;
        if (tooFast(s.username, cd)) {
            error(ex, 429, "发送太快了，缓一下再发");
            return;
        }
        String role = "player".equals(s.role) ? "student" : "viewer";
        String who = "player".equals(s.role) ? s.username : ("观众" + s.no);
        Store.Comment c = store.addComment(role, who, s.no, text, kind);
        ok(ex, "{" + Json.bool("ok", true) + ","
                + Json.esc("comment") + ":" + commentJson(c) + "}");
    }

    /** 讨论区是否对本人开放：观众一直可看，选手只有自己完赛后才能看 */
    static boolean canReadDiscussion(Session s) {
        if (!"player".equals(s.role)) {
            return true;
        }
        return s.finished || store.getResult(s.username) != null;
    }

    /** 管理员（或总管）在后台发言：统一打上身份，学生端会显示成「管理员：内容」 */
    static void adminCommentPost(HttpExchange ex) throws IOException {
        if (!requireAdminOrSuper(ex)) {
            return;
        }
        boolean superUser = isSuper(ex);
        Map<String, String> f = form(ex);
        String text = cleanCommentText(f.getOrDefault("text", ""));
        if (text.isEmpty()) {
            error(ex, 400, "内容不能为空");
            return;
        }
        String kind = "danmaku".equals(f.getOrDefault("kind", "comment")) ? "danmaku" : "comment";
        String cs = contestStatus();
        boolean duringContest = "running".equals(cs) || "countdown".equals(cs);
        // 管理员/总管是“高级权限的观众”：弹幕评论都能发，但赛中同样不许递提示
        if ("danmaku".equals(kind) && duringContest && promptBlockedWord(text) != null) {
            error(ex, 400, "弹幕含有比赛提示内容，已拦截");
            return;
        }
        String who = superUser ? "总管" : "管理员";
        long cd = "danmaku".equals(kind) ? DANMAKU_CD_MS : COMMENT_CD_MS;
        if (tooFast("staff:" + who, cd)) {
            error(ex, 429, "发送太快了，缓一下再发");
            return;
        }
        Store.Comment c = store.addComment(superUser ? "super" : "admin", who, 0, text, kind);
        ok(ex, "{" + Json.bool("ok", true) + ","
                + Json.esc("comment") + ":" + commentJson(c) + "}");
    }

    /** 管理员删除一条讨论区消息 */
    static void adminCommentDelete(HttpExchange ex) throws IOException {
        if (!requireAdminOrSuper(ex)) {
            return;
        }
        Map<String, String> f = form(ex);
        long id = queryLong(ex, "id", -1L);
        if (id < 0) {
            String raw = f.getOrDefault("id", "").trim();
            try {
                id = Long.parseLong(raw);
            } catch (Exception ignore) {
                id = -1L;
            }
        }
        if (id < 0) {
            error(ex, 400, "缺少消息编号");
            return;
        }
        boolean removed = store.deleteComment(id);
        if (!removed) {
            error(ex, 404, "该消息已被删除");
            return;
        }
        ok(ex, "{" + Json.bool("ok", true) + ","
                + Json.str("msg", "已删除该条发言") + "}");
    }

    /**
     * 裁判 / 总管发言：发到全局讨论区，也可以发一条弹幕。
     * 身份直接由 token 决定，所以前端伪造不了头衔。
     */
    static void staffComment(HttpExchange ex) throws IOException {
        String token = ex.getRequestHeaders().getFirst("X-Token");
        String role;
        String who;
        if (token != null && judgeTokens.contains(token)) {
            role = "judge";
            who = "裁判";
        } else if (token != null && superTokens.contains(token)) {
            role = "super";
            who = "总管";
        } else {
            error(ex, 401, "没有权限");
            return;
        }
        Map<String, String> f = form(ex);
        String kind = f.getOrDefault("kind", "comment").trim();
        if (!"danmaku".equals(kind)) {
            kind = "comment";
        }
        String text = cleanCommentText(f.getOrDefault("text", ""));
        if (text.isEmpty()) {
            error(ex, 400, "内容不能为空");
            return;
        }
        if (text.length() > COMMENT_MAX_LEN) {
            error(ex, 400, "内容过长");
            return;
        }
        if ("danmaku".equals(kind)) {
            // 裁判/总管是“高级权限的观众”：任何时候都能发弹幕，只在赛中拦提示词
            String st = contestStatus();
            boolean duringContest = "running".equals(st) || "countdown".equals(st);
            if (duringContest && promptBlockedWord(text) != null) {
                error(ex, 400, "弹幕含有比赛提示内容，已拦截");
                return;
            }
        }
        long cd = "danmaku".equals(kind) ? DANMAKU_CD_MS : COMMENT_CD_MS;
        if (tooFast("staff:" + role, cd)) {
            error(ex, 429, "发送太快了，缓一下再发");
            return;
        }
        Store.Comment c = store.addComment(role, who, 0, text, kind);
        ok(ex, "{" + Json.bool("ok", true) + ","
                + Json.esc("comment") + ":" + commentJson(c) + "}");
    }
}
