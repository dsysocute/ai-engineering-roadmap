import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * 跑在子进程里的判题执行器。
 *
 * 输出协议（每行一条，JudgeEngine 按前缀解析）：
 *   READY                         子进程起来了
 *   LOADERROR|原因                 反射加载 Solution / sort 方法失败
 *   REF|用例名|基准纳秒             平台归并排序的基准耗时
 *   CASE|用例名|是否通过|纳秒|基准纳秒
 *   DEBUG|before|数组内容          仅测试运行(t_ 开头的用例)会带，方便选手自查
 *   DEBUG|after|数组内容
 *   NOTE|原因                      效率用例里选手算法抛异常
 *   DONE                          全部跑完
 *
 * 数据生成用固定种子（用例名参与种子），所以同一用例每次都是同一组数据，
 * 既保证选手之间公平，也让"测试运行"和"正式判题"看到的是同一份数据。
 */
public class JudgeRunner {

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, "UTF-8");
        out.println("READY");

        List<String[]> cases = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(args[0]), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                cases.add(line.split("\\|"));
            }
        }

        SortStrategy solution;
        try {
            solution = loadSolution();
        } catch (Throwable t) {
            // 类名不对、没有 public void sort(int[] arr)、构造失败，都走这里
            out.println("LOADERROR|" + oneLine(t));
            out.println("DONE");
            out.flush();
            return;
        }

        MergeSortRef reference = new MergeSortRef();

        for (String[] c : cases) {
            String name = c[0];
            int n = Integer.parseInt(c[1]);
            String type = c[2];
            boolean efficiency = c.length > 3 && "1".equals(c[3]);

            int[] data = makeData(name, n, type);

            if (!efficiency) {
                boolean ok = false;
                int[] result = null;
                try {
                    result = data.clone();
                    solution.sort(result);
                    ok = Checker.isCorrect(data, result);
                } catch (Throwable t) {
                    ok = false;
                }
                if (name.startsWith("t_")) {
                    // 只有"测试运行"的小样例才回传数组内容，正式判题不回传，省得刷屏
                    out.println("DEBUG|before|" + preview(data));
                    out.println("DEBUG|after|" + preview(result));
                }
                out.println("CASE|" + name + "|" + (ok ? "1" : "0") + "|0|0");
            } else {
                long refNano = 0;
                try {
                    refNano = measureOne(reference, data);
                } catch (Throwable t) {
                    refNano = 0;
                }
                out.println("REF|" + name + "|" + refNano);

                long timeNano = 0;
                boolean ok = true;
                try {
                    timeNano = measureOne(solution, data);
                } catch (Throwable t) {
                    ok = false;
                    out.println("NOTE|" + oneLine(t));
                }
                out.println("CASE|" + name + "|" + (ok ? "1" : "0") + "|" + timeNano + "|" + refNano);
            }
        }

        out.println("DONE");
        out.flush();
    }

    /** 正确性检查：跑一次，clone 原数组再交给选手，避免选手改动影响比对。 */
    private static boolean runOnce(SortStrategy s, int[] data) {
        try {
            int[] copy = data.clone();
            s.sort(copy);
            return Checker.isCorrect(data, copy);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 计时：先热身 3 次，再跑 5 次取中位数；每次都用 clone 的干净数组。 */
    private static long measureOne(SortStrategy s, int[] data) throws Exception {
        for (int i = 0; i < 3; i++) {
            int[] copy = data.clone();
            s.sort(copy);
        }
        long[] times = new long[5];
        for (int i = 0; i < 5; i++) {
            int[] copy = data.clone();
            long t0 = System.nanoTime();
            s.sort(copy);
            long t1 = System.nanoTime();
            times[i] = t1 - t0;
            if (!Checker.isCorrect(data, copy)) {
                throw new IllegalStateException("排序结果不正确");
            }
        }
        Arrays.sort(times);
        return times[2];
    }

    /** 加载 Solution：优先用 SortStrategy 接口，其次退回按名字找 public void sort(int[])。 */
    private static SortStrategy loadSolution() throws Exception {
        Class<?> cls = Class.forName("Solution");
        final Object inst = cls.getDeclaredConstructor().newInstance();

        if (inst instanceof SortStrategy) {
            return (SortStrategy) inst;
        }

        final Method m = cls.getMethod("sort", int[].class);
        return new SortStrategy() {
            @Override
            public void sort(int[] arr) {
                try {
                    m.invoke(inst, arr);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    /** 生成用例数据：种子 = 固定值 + 用例名哈希，保证同一用例数据固定。 */
    private static int[] makeData(String name, int n, String type) {
        int[] a = new int[n];
        Random r = new Random(20251110L + (long) name.hashCode());
        if ("sorted".equals(type)) {
            for (int i = 0; i < n; i++) {
                a[i] = i;
            }
        } else if ("reversed".equals(type)) {
            for (int i = 0; i < n; i++) {
                a[i] = n - i;
            }
        } else if ("duplicate".equals(type)) {
            for (int i = 0; i < n; i++) {
                a[i] = r.nextInt(10);
            }
        } else {
            for (int i = 0; i < n; i++) {
                a[i] = r.nextInt(n);
            }
        }
        return a;
    }

    /** 数组预览：短数组全给，长数组掐头去尾，避免终端被 1 万个数刷满。 */
    private static String preview(int[] a) {
        if (a == null) {
            return "（选手代码没有回传结果）";
        }
        if (a.length <= 20) {
            return Arrays.toString(a);
        }
        int[] head = Arrays.copyOfRange(a, 0, 10);
        int[] tail = Arrays.copyOfRange(a, a.length - 5, a.length);
        return "共 " + a.length + " 个，开头 " + Arrays.toString(head)
                + " … 末尾 " + Arrays.toString(tail);
    }

    /** 异常压成一行，太长就截断，避免把整段堆栈糊进 out.txt。 */
    private static String oneLine(Throwable t) {
        String msg = t.getClass().getSimpleName() + ": " + t.getMessage();
        msg = msg.replace('\n', ' ').replace('\r', ' ');
        return msg.length() > 200 ? msg.substring(0, 200) + "…" : msg;
    }
}
