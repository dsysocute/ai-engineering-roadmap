import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/**
 * 编译服务：把选手交上来的源码落盘，调 javac 编译。
 * 编译失败就把 javac 的原始报错回给前端，学生自己看得到哪一行错了。
 */
public class CompileService {

    /** javac 报错只留前 12 万字符，避免超长输出把内存和前端都拖垮 */
    private static final int MAX_OUTPUT_CHARS = 120_000;

    public static class Result {
        public boolean ok;
        public String message = "";
        public Path workDir;
    }

    public Result compile(String code) {
        Result r = new Result();
        try {
            r.workDir = Files.createTempDirectory("judge-");
            Path src = r.workDir.resolve("Solution.java");
            Files.write(src, code.getBytes(StandardCharsets.UTF_8));

            ProcessBuilder pb = new ProcessBuilder(
                    tool("javac"),
                    "-encoding", "UTF-8",
                    "-cp", platformClasses(),
                    "-d", r.workDir.toString(),
                    src.toString());
            pb.directory(r.workDir.toFile());
            pb.redirectErrorStream(true);

            Process p = pb.start();
            // 必须先并发读输出、再等超时：如果先 readAll，读流会一直阻塞到 javac 自己退出，
            // 20 秒超时永远等不到，javac 卡死会把 HTTP 线程一起拖死。
            final StringBuilder output = new StringBuilder();
            Thread drain = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    char[] buf = new char[4096];
                    int n;
                    while ((n = br.read(buf)) != -1) {
                        synchronized (output) {
                            if (output.length() < MAX_OUTPUT_CHARS) {
                                output.append(buf, 0, Math.min(n, MAX_OUTPUT_CHARS - output.length()));
                            }
                        }
                    }
                } catch (IOException ignored) {
                }
            }, "javac-drain");
            drain.setDaemon(true);
            drain.start();

            if (!p.waitFor(20, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                p.waitFor(5, TimeUnit.SECONDS);
                r.ok = false;
                r.message = "编译超时（20 秒）";
                return r;
            }
            drain.join(2000);
            String text;
            synchronized (output) {
                text = output.toString();
            }
            if (p.exitValue() == 0) {
                r.ok = true;
            } else {
                r.ok = false;
                r.message = text.trim();
            }
        } catch (Exception e) {
            r.ok = false;
            r.message = "编译环境异常：" + e.getMessage();
        }
        return r;
    }

    /** 当前后端自己所在的 classes 目录，选手编译时要能看到 SortStrategy。 */
    private String platformClasses() throws Exception {
        return new File(CompileService.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).getAbsolutePath();
    }

    /** 优先用当前 JVM 同目录下的 javac，找不到再退回 PATH 里的 javac。 */
    public static String tool(String name) {
        String exe = isWindows() ? name + ".exe" : name;
        File f = new File(new File(System.getProperty("java.home"), "bin"), exe);
        if (f.exists()) {
            return f.getAbsolutePath();
        }
        return exe;
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
