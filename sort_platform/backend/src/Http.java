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

/** HTTP 层：读请求、统一 JSON 响应、CORS、参数解析。所有接口类都从这里继承这些工具方法。 */
class Http {

    static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int len;
        while ((len = is.read(buf)) != -1) {
            bos.write(buf, 0, len);
        }
        return bos.toByteArray();
    }

    static String tokenOf(HttpExchange ex) {
        String token = ex.getRequestHeaders().getFirst("X-Token");
        return token == null ? "" : token;
    }

    /** 最常见的一种成功响应 {"ok":true} */
    static void okTrue(HttpExchange ex) throws IOException {
        ok(ex, "{" + Json.bool("ok", true) + "}");
    }

    /** 从 query string 里取一个 long 参数，取不到就用默认值 */
    static String query(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getQuery();
        if (q == null) {
            return "";
        }
        for (String kv : q.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0 && key.equals(kv.substring(0, i))) {
                try {
                    return URLDecoder.decode(kv.substring(i + 1), "UTF-8");
                } catch (Exception ignore) {
                    return kv.substring(i + 1);
                }
            }
        }
        return "";
    }

    static long queryLong(HttpExchange ex, String key, long def) {
        String q = ex.getRequestURI().getQuery();
        if (q == null) {
            return def;
        }
        for (String kv : q.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0 && key.equals(kv.substring(0, i))) {
                try {
                    return Long.parseLong(kv.substring(i + 1));
                } catch (Exception ignore) {
                    return def;
                }
            }
        }
        return def;
    }

    /** 表单字段取值并去掉首尾空白，省掉满屏的 getOrDefault + trim */
    static String field(Map<String, String> f, String key) {
        return f.getOrDefault(key, "").trim();
    }

    static void ok(HttpExchange ex, String json) throws IOException {
        cors(ex);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    static void error(HttpExchange ex, int code, String msg) throws IOException {
        cors(ex);
        byte[] bytes = ("{" + Json.str("error", msg) + "}").getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** 带业务码的错误，前端靠 code 区分「被顶号」和「普通登录过期」 */
    static void errorCode(HttpExchange ex, int status, String bizCode, String msg) throws IOException {
        cors(ex);
        byte[] bytes = ("{" + Json.str("error", msg) + "," + Json.str("code", bizCode) + "}")
                .getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    static void cors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "X-Token, Content-Type");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    }

    static Map<String, String> form(HttpExchange ex) throws IOException {
        String body = new String(readAllBytes(ex.getRequestBody()), "UTF-8");
        Map<String, String> map = new HashMap<>();
        if (body.isEmpty()) {
            return map;
        }
        for (String pair : body.split("&")) {
            int i = pair.indexOf('=');
            if (i < 0) {
                continue;
            }
            String k = java.net.URLDecoder.decode(pair.substring(0, i), "UTF-8");
            String v = java.net.URLDecoder.decode(pair.substring(i + 1), "UTF-8");
            map.put(k, v);
        }
        return map;
    }
}
