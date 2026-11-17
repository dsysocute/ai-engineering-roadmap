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

/** 总管：登录与改口令（其余事务走 CaseCenter）。 */
class SuperApi extends App {

    /** 总管登录：账号 + 口令，跟学生账号、裁判口令都分开 */
    static void superLogin(HttpExchange ex) throws IOException {
        Map<String, String> f = form(ex);
        String user = field(f, "username");
        String pass = f.getOrDefault("password", "").trim();
        if (!SUPER_USER.equals(user) || !superPass().equals(pass)) {
            error(ex, 401, "总管账号或口令不正确");
            return;
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        superTokens.add(token);
        ok(ex, "{" + Json.str("token", token) + "," + Json.str("role", "super")
                + "," + Json.str("who", SUPER_USER) + "}");
    }

    /** 总管改自己的口令 */
    static void superPassChange(HttpExchange ex) throws IOException {
        if (!isSuper(ex)) {
            error(ex, 401, "没有总管权限");
            return;
        }
        Map<String, String> f = form(ex);
        if (!superPass().equals(f.getOrDefault("oldPass", "").trim())) {
            error(ex, 400, "原口令不对");
            return;
        }
        String np = f.getOrDefault("newPass", "").trim();
        if (!validPass(np)) {
            error(ex, 400, "新口令请控制在 4 ~ 20 位");
            return;
        }
        store.setState("super.pass", np);
        okTrue(ex);
    }
}
