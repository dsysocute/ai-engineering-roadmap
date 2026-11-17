import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 判题核心。
 *
 * 流程：静态查作弊 → 编译 → 起子进程跑用例（超时可 kill）→ 解析结果 → 算分。
 * 选手代码始终在子进程里跑，主服务不会被拖死。
 *
 * 交卷分两段：
 *   judgeCorrectOnly()    只跑正确性五组，交卷时立刻返回，编码计时在这里定格；
 *   judgeEfficiencyOnly() 只跑 10 万规模三组，由 Main 丢进后台线程池，跑完回填分数。
 */
public class JudgeEngine {

    /** 「测试运行」用小样例：只求快、只求暴露典型错误，和正式判题完全隔离。 */
    private static final String TEST_CASES =
            "t_single|1|random|0\n" +
            "t_odd|7|reversed|0\n" +
            "t_dup|20|duplicate|0\n" +
            "t_200|200|random|0\n";

    /** 测试运行的分组说明，顺序与上面用例一一对应。 */
    private static final String[][] TEST_META = {
            {"t_single", "单元素数组"},
            {"t_odd", "7 个完全逆序元素"},
            {"t_dup", "20 个大量重复元素"},
            {"t_200", "200 个随机元素"},
    };

    /** 正式判题的正确性用例：先验对错，再量效率。 */
    private static final String CORRECTNESS_CASES =
            "c_1k_random|1000|random|0\n" +
            "c_10k_random|10000|random|0\n" +
            "c_10k_sorted|10000|sorted|0\n" +
            "c_10k_reversed|10000|reversed|0\n" +
            "c_10k_duplicate|10000|duplicate|0\n";

    /** 正确性用例条数：判罚、强制结束时记分要保持一致，别再各处写死 5 */
    public static int correctnessCaseCount() {
        return CORRECTNESS_META.length;
    }
    private static final String[][] CORRECTNESS_META = {
            {"c_1k_random", "1000 个随机数"},
            {"c_10k_random", "10000 个随机数"},
            {"c_10k_sorted", "10000 个已排序"},
            {"c_10k_reversed", "10000 个完全逆序"},
            {"c_10k_duplicate", "10000 个大量重复"},
    };

    /** 效率用例：10 万规模，和平台归并排序比耗时。 */
    private static final String EFFICIENCY_CASES =
            "e_100k_random|100000|random|1\n" +
            "e_100k_sorted|100000|sorted|1\n" +
            "e_100k_reversed|100000|reversed|1\n";

    private static final String[] EFFICIENCY_NAMES = {
            "e_100k_random", "e_100k_sorted", "e_100k_reversed"
    };

    private static final String[] FORBIDDEN = {
            "Arrays.sort", "Collections.sort", ".sorted(", "System.exit",
            "Runtime.getRuntime", "ProcessBuilder", "new Thread",
            "new File(", "Socket", "URL(", "Class.forName", "reflect"
    };

    /** Java 的 Unicode 转义：反斜杠 u 加四位十六进制，允许连写多个 u（JLS 3.3） */
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u+([0-9a-fA-F]{4})");

    /** 单个用例的结果，字段与 JudgeRunner 的输出协议对应。 */
    public static class CaseResult {
        public String name;
        public boolean ok;
        public long timeNano;
        public long refNano;
        /** 测试运行回传的数组预览，正式判题时为空。 */
        public String before = "";
        public String after = "";
    }

    /** 一次判题的总体结果。 */
    public static class Outcome {
        public boolean correct;
        public int caseOk;
        public int caseTotal;
        public double codingScore;
        public double efficiencyScore;
        public double score;
        public String detail = "";
        public String terminal = "";
    }

    // ---------------- 对外接口 ----------------

    /** 「测试运行」：编译 + 跑 4 组小样例，不计分，逐组给通过/失败和数组回显。 */
    public Outcome testRun(String code) {
        Outcome out = new Outcome();
        String cheat = staticScan(code);
        if (cheat != null) {
            out.terminal = "检测到不允许的写法：" + cheat;
            out.detail = "违规：" + cheat;
            return out;
        }

        CompileService cs = new CompileService();
        CompileService.Result cr = cs.compile(code);
        if (!cr.ok) {
            out.terminal = cr.message;
            out.detail = "编译失败";
            deleteQuietly(cr.workDir);
            return out;
        }

        List<CaseResult> cases = runChild(cr.workDir, TEST_CASES, 20);
        deleteQuietly(cr.workDir);

        if (hasLoadError(cases)) {
            out.correct = false;
            out.detail = "找不到 sort 方法";
            out.terminal = "加载失败：未找到可调用的 sort 方法。\n"
                    + "   请检查类名是否为 Solution、方法是否为 public void sort(int[] arr)、是否误加 static 或 private 修饰符。\n"
                    + "   参照页面模板编写：public class Solution { public void sort(int[] arr) { ... } }\n"
                    + "   禁止编写 main 方法、Scanner 输入及 System.out 输出。";
            return out;
        }

        if (cases.isEmpty()) {
            out.correct = false;
            out.detail = "超时或崩溃";
            out.terminal = "运行超时或程序异常退出（每组限时 20 秒）。\n"
                    + "   常见原因：死循环、递归缺少终止条件、数组下标越界导致程序异常终止。";
            return out;
        }

        out.caseTotal = TEST_META.length;
        int okCount = 0;
        for (CaseResult c : cases) {
            if (c.ok) {
                okCount++;
            }
        }
        out.caseOk = okCount;
        out.correct = okCount == TEST_META.length;
        out.terminal = buildTestReport(cases);
        return out;
    }

    /** 交卷第一段：只跑正确性五组。编码分在这里算好，效率分留给后台。 */
    public Outcome judgeCorrectOnly(String code, long codingSeconds) {
        Outcome out = new Outcome();
        String cheat = staticScan(code);
        if (cheat != null) {
            out.correct = false;
            out.codingScore = 0;
            out.efficiencyScore = 0;
            out.score = 0;
            out.detail = "违规：" + cheat;
            out.terminal = "检测到不允许的写法：" + cheat;
            return out;
        }

        CompileService cs = new CompileService();
        CompileService.Result cr = cs.compile(code);
        if (!cr.ok) {
            out.correct = false;
            out.codingScore = 0;
            out.efficiencyScore = 0;
            out.score = 0;
            out.detail = "编译失败：" + firstLine(cr.message);
            out.terminal = cr.message;
            deleteQuietly(cr.workDir);
            return out;
        }

        List<CaseResult> cases = runChild(cr.workDir, CORRECTNESS_CASES, 40);
        deleteQuietly(cr.workDir);

        if (hasLoadError(cases)) {
            out.correct = false;
            out.codingScore = 0;
            out.efficiencyScore = 0;
            out.score = 0;
            out.caseTotal = CORRECTNESS_META.length;
            out.detail = "找不到 sort 方法";
            out.terminal = "加载失败：未找到 sort 方法。请参照页面模板编写 public void sort(int[] arr)，禁止编写 main、Scanner。";
            return out;
        }

        Map<String, CaseResult> byName = new HashMap<>();
        for (CaseResult c : cases) {
            byName.put(c.name, c);
        }

        int okCount = 0;
        StringBuilder detail = new StringBuilder();
        for (String[] meta : CORRECTNESS_META) {
            CaseResult c = byName.get(meta[0]);
            boolean ok = c != null && c.ok;
            if (ok) {
                okCount++;
            }
            detail.append(meta[0]).append('=')
                    .append(c == null ? "超时" : (ok ? "通过" : "错误"))
                    .append(';');
        }

        out.caseOk = okCount;
        out.caseTotal = CORRECTNESS_META.length;
        out.correct = okCount == CORRECTNESS_META.length;
        out.codingScore = ScoreCalculator.codingScoreSeconds(codingSeconds);
        out.efficiencyScore = 0;

        if (!out.correct) {
            out.score = 0;
            out.detail = "正确性未通过（" + okCount + "/" + CORRECTNESS_META.length + "）；" + detail;
            return out;
        }

        out.score = 60 + out.codingScore;
        out.detail = "正确性 5 组通过，效率评测后台执行中；" + detail;
        return out;
    }

    /** 效率评测的结果：得分 + 三组实际运行耗时的平均值（毫秒），后者只用于排行榜展示。 */
    public static class EfficiencyResult {
        public final double score;
        public final double avgMillis;

        public EfficiencyResult(double score, double avgMillis) {
            this.score = score;
            this.avgMillis = avgMillis;
        }
    }

    /** 交卷第二段：只跑 10 万规模效率三组，返回三组平均效率分（0~20，1 位小数）与平均耗时。 */
    public EfficiencyResult judgeEfficiencyOnly(String code) {
        if (staticScan(code) != null) {
            return new EfficiencyResult(0.0, 0.0);
        }

        CompileService cs = new CompileService();
        CompileService.Result cr = cs.compile(code);
        if (!cr.ok) {
            deleteQuietly(cr.workDir);
            return new EfficiencyResult(0.0, 0.0);
        }

        List<CaseResult> cases = runChild(cr.workDir, EFFICIENCY_CASES, 90);
        deleteQuietly(cr.workDir);

        Map<String, CaseResult> byName = new HashMap<>();
        for (CaseResult c : cases) {
            byName.put(c.name, c);
        }

        double sum = 0.0;
        double nanos = 0.0;
        int counted = 0;
        int timed = 0;
        for (String name : EFFICIENCY_NAMES) {
            CaseResult c = byName.get(name);
            boolean missing = c == null || !c.ok || c.timeNano <= 0;
            sum += ScoreCalculator.efficiencyScore(
                    missing ? 0L : c.timeNano,
                    missing ? 0L : c.refNano,
                    missing);
            counted++;
            if (!missing) {
                nanos += (double) c.timeNano;
                timed++;
            }
        }
        if (counted == 0) {
            return new EfficiencyResult(0.0, 0.0);
        }
        double avgMillis = timed == 0 ? 0.0 : (nanos / timed) / 1_000_000.0;
        return new EfficiencyResult(Math.round((sum / counted) * 10.0) / 10.0, avgMillis);
    }

    /** 一次跑完两段，给本地调试或老接口用。 */
    public Outcome judge(String code, long codingSeconds) {
        Outcome out = judgeCorrectOnly(code, codingSeconds);
        if (!out.correct) {
            return out;
        }
        EfficiencyResult eff = judgeEfficiencyOnly(code);
        out.efficiencyScore = eff.score;
        out.score = 60.0 + out.codingScore + eff.score;
        out.detail = out.detail + " 效率评测完成：" + ScoreCalculator.format(eff.score) + "/20";
        return out;
    }

    // ---------------- 内部实现 ----------------

    /** 测试运行报告：逐组 ✅/❌ + 排序前后数组 + 「测试通过≠正式通过」的提醒。 */
    private String buildTestReport(List<CaseResult> cases) {
        Map<String, CaseResult> byName = new HashMap<>();
        for (CaseResult c : cases) {
            byName.put(c.name, c);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("测试运行：").append(TEST_META.length).append(" 组样例\n");

        int okCount = 0;
        for (int i = 0; i < TEST_META.length; i++) {
            String name = TEST_META[i][0];
            String desc = TEST_META[i][1];
            CaseResult c = byName.get(name);
            boolean ok = c != null && c.ok;
            if (ok) {
                okCount++;
            }

            sb.append(ok ? "✅ " : "❌ ")
                    .append(i + 1).append(". ").append(desc)
                    .append(ok ? "：通过" : "：未通过")
                    .append('\n');

            if (c == null) {
                sb.append("     该组无结果，可能超时或程序异常退出。\n");
                continue;
            }
            if (!c.before.isEmpty()) {
                sb.append("     排序前 ").append(c.before).append('\n');
            }
            if (!c.after.isEmpty()) {
                sb.append("     排序后 ").append(c.after).append('\n');
            }
            if (!ok) {
                sb.append("     排序结果为非升序，或元素与原始数组不一致，判定未通过。\n");
            }
        }

        sb.append("结果：").append(okCount).append(" / ").append(TEST_META.length).append(" 组通过\n");
        sb.append("上述样例规模较小，全部通过不代表正式评测可以通过")
                .append("（正式评测含 1000 至 10000 规模 5 组及十万级效率 3 组）。");
        return sb.toString();
    }

    private static boolean hasLoadError(List<CaseResult> cases) {
        for (CaseResult c : cases) {
            if ("__LOADERROR__".equals(c.name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 静态查作弊。
     *
     * 先把 Unicode 转义还原、把注释和字符串字面量抠掉，再去掉所有空白后做包含匹配。
     * 只按原文 contains 是不够的：Java 允许在记号之间随意换行或插注释，
     * 把 Arrays 和 .sort 拆成两行照样能编译通过，却能绕过按原文匹配的扫描；
     * 用 Unicode 转义写点号更是连字符都不同。归一化之后这些写法都会还原成
     * Arrays.sort，一并拦下。
     *
     * 代价：极少数情况会误伤（例如自己定义了一个叫 newFile() 的方法，会被当成 new File(）。
     * 这类名字在本赛题里没有合理用途，取舍上宁可误伤。
     */
    public static String staticScan(String code) {
        if (code == null) {
            return null;
        }
        String stripped = decodeUnicodeEscapes(code)
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("//[^\\n]*", " ")
                .replaceAll("(?s)\"(?:\\\\.|[^\"\\\\])*\"", "\"\"");
        String compact = stripped.replaceAll("\\s+", "");
        for (String bad : FORBIDDEN) {
            if (compact.contains(bad.replaceAll("\\s+", ""))) {
                return bad;
            }
        }
        return null;
    }

    /** 把 Java 源码里的 Unicode 转义还原成字符（javac 在词法分析前就做这一步）。 */
    private static String decodeUnicodeEscapes(String s) {
        if (s.indexOf("\\u") < 0) {
            return s;
        }
        Matcher m = UNICODE_ESCAPE.matcher(s);
        StringBuffer out = new StringBuffer(s.length());
        while (m.find()) {
            char ch = (char) Integer.parseInt(m.group(1), 16);
            m.appendReplacement(out, Matcher.quoteReplacement(String.valueOf(ch)));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * 在子进程里跑一批用例。
     *
     * 用例清单写进 workDir/cases.txt，JudgeRunner 按协议往 stdout 吐 READY/REF/CASE/DEBUG/LOADERROR，
     * 超时就强杀，然后把已经吐出来的结果解析掉 —— 超时前跑通的组仍然算数。
     */
    private List<CaseResult> runChild(Path workDir, String caseText, int timeoutSeconds) {
        List<CaseResult> list = new ArrayList<>();
        try {
            Path caseFile = workDir.resolve("cases.txt");
            Files.write(caseFile, caseText.getBytes(StandardCharsets.UTF_8));

            String cp = new File(JudgeEngine.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getAbsolutePath()
                    + File.pathSeparator + workDir.toAbsolutePath();

            Path outFile = workDir.resolve("out.txt");
            Path errFile = workDir.resolve("err.txt");

            ProcessBuilder pb = new ProcessBuilder(
                    CompileService.tool("java"),
                    "-Xmx256m", "-Dfile.encoding=UTF-8",
                    "-cp", cp,
                    "JudgeRunner",
                    caseFile.toString());
            pb.directory(workDir.toFile());
            pb.redirectOutput(outFile.toFile());
            pb.redirectError(errFile.toFile());

            Process p = pb.start();
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                p.waitFor(5, TimeUnit.SECONDS);
            }

            if (!Files.exists(outFile)) {
                return list;
            }

            List<String> lines = Files.readAllLines(outFile, StandardCharsets.UTF_8);
            String pendingBefore = "";
            String pendingAfter = "";
            Map<String, Long> refMap = new HashMap<>();

            for (String line : lines) {
                if (line.startsWith("LOADERROR|")) {
                    CaseResult errTag = new CaseResult();
                    errTag.name = "__LOADERROR__";
                    list.add(errTag);
                    return list;
                } else if (line.startsWith("DEBUG|before|")) {
                    pendingBefore = line.substring("DEBUG|before|".length()).trim();
                } else if (line.startsWith("DEBUG|after|")) {
                    pendingAfter = line.substring("DEBUG|after|".length()).trim();
                } else if (line.startsWith("REF|")) {
                    String[] s = line.split("\\|", -1);
                    if (s.length >= 3) {
                        refMap.put(s[1], parseLong(s[2]));
                    }
                } else if (line.startsWith("CASE|")) {
                    String[] s = line.split("\\|", -1);
                    if (s.length >= 5) {
                        CaseResult c = new CaseResult();
                        c.name = s[1];
                        c.ok = "1".equals(s[2]);
                        c.timeNano = parseLong(s[3]);
                        c.refNano = parseLong(s[4]);
                        if (c.refNano <= 0) {
                            Long ref = refMap.get(c.name);
                            c.refNano = ref == null ? 0L : ref;
                        }
                        c.before = pendingBefore;
                        c.after = pendingAfter;
                        pendingBefore = "";
                        pendingAfter = "";
                        list.add(c);
                    }
                }
                // READY / NOTE / DONE 不影响判分，忽略
            }
        } catch (Exception e) {
            // 出错就当没拿到结果，外层按超时/失败处理
        }
        return list;
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String firstLine(String s) {
        if (s == null) {
            return "";
        }
        int i = s.indexOf('\n');
        String line = i < 0 ? s : s.substring(0, i);
        line = line.replace('\r', ' ').trim();
        return line.length() > 120 ? line.substring(0, 120) + "…" : line;
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    /** 日志里的耗时展示。 */
    static String ms(long nano) {
        return String.format(Locale.ROOT, "%.1f", nano / 1e6);
    }
}
