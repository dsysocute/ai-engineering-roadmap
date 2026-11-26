import java.util.Scanner;

/**
 * 第二阶段 · Java 入门 代码向导
 * ------------------------------------------------------------------
 * 用法：先把本文件从上到下看一遍，记住每种语法长什么样、坑在哪，
 *      再回到对应练习文件，照着 TODO 自己手写代码，做到验收线。
 *
 * 练习文件对应关系：
 *   VariablesAndTypes.java  -> 第一节 变量与类型
 *   OperatorsScanner.java   -> 第二节 运算符与输入
 *   FlowControl.java        -> 第三节 流程控制
 *   ArraysPractice.java      -> 第四节 数组
 *   SortSearch.java         -> 第五节 排序与查找
 *   MethodsPractice.java    -> 第六节 方法
 *   Recursion.java          -> 第七节 递归
 *   ConsoleShop.java        -> 第八节 综合项目
 * ------------------------------------------------------------------
 */
public class JavaGuide {

    public static void main(String[] args) {
        // 想看哪个示例就打开对应方法的注释调用，或直接读方法里的写法
        // demoVariables();
        // demoScanner();
        // demoFlowControl();
        // demoArray();
        // demoMethod();
        // demoRecursion();
    }

    // ==================================================================
    // 第一节 变量、常量、数据类型、类型转换
    // ==================================================================
    private static void demoVariables() {
        // 1. 八种基本类型（取值范围由大到小记忆，注意字面量后缀）
        byte    b = 1;          // 1 字节， -128 ~ 127
        short   s = 2;          // 2 字节
        int     i = 3;          // 默认整数类型，最常用
        long    l = 4L;         // 长整数字面量要加 L
        float   f = 1.5F;       // 单精度，字面量要加 F
        double  d = 2.5;        // 默认小数类型，最常用
        char    c = 'A';        // 单个字符，单引号；本质是一个 Unicode 整数
        boolean flag = true;     // 只有 true / false

        // 引用类型：字符串（不是基本类型）
        String str = "hello";

        // 2. 常量：final 修饰，赋值后不可改，名字通常全大写下划线分隔
        final int MAX_AGE = 120;

        // 3. 类型转换
        // 自动提升（小 -> 大，不丢精度）：byte/short/char -> int -> long -> float -> double
        int up = b;             // byte 自动转 int
        double up2 = i;         // int 自动转 double

        // 强制转换（大 -> 小，可能丢精度或溢出），括号手动转
        int down = (int) 3.99;  // 截断成 3，不是四舍五入
        byte b2 = (byte) 200;   // 超出 byte 范围会溢出，结果异常

        System.out.println("" + b + s + i + l + f + d + c + flag + str + MAX_AGE + up + up2 + down + b2);
    }

    // ==================================================================
    // 第二节 运算符 + 控制台输入输出
    // ==================================================================
    private static void demoScanner() {
        // 输出：System.out.println(...) 换行打印；print 不换行；printf 格式化
        System.out.println("普通输出");

        // 输入：先 import java.util.Scanner; 再 new
        Scanner sc = new Scanner(System.in);

        // nextInt() 读整数；nextDouble() 读小数；next() 读一个单词；nextLine() 读一整行
        System.out.print("请输入一个整数：");
        int n = sc.nextInt();

        // 运算符
        int sum = n + 1;        // 算术 + - * / %
        int rem = 10 % 3;       // 取模（余数）
        boolean gt = n > 0;     // 比较 > < >= <= == !=
        boolean logic = gt && n < 100;  // 逻辑与 &&，或 ||，非 !

        // 自增自减：++i 先加后用；i++ 先用后加
        int x = 5;
        int y = x++;            // y=5，x 再变 6
        int z = ++x;            // x 先加，z 拿到加后的值

        // 逻辑短路：&& 左侧 false，右侧不再执行；|| 左侧 true，右侧不再执行
        // 例：先判空再调用，避免空指针
        String text = null;
        if (text != null && text.length() > 0) {
            System.out.println(text);
        }

        System.out.println(sum + " " + rem + " " + logic + " " + y + " " + z);
        sc.close();
    }

    // ==================================================================
    // 第三节 流程控制
    // ==================================================================
    private static void demoFlowControl() {
        // 1. if / else if / else
        int score = 85;
        if (score >= 90) {
            System.out.println("优秀");
        } else if (score >= 60) {
            System.out.println("及格");
        } else {
            System.out.println("不及格");
        }

        // 2. switch：匹配某个确定值；case 不写 break 会穿透到下一个 case
        int day = 3;
        switch (day) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                System.out.println("工作日");
                break;
            case 6:
            case 7:
                System.out.println("周末");
                break;
            default:
                System.out.println("输入非法");
        }

        // 3. while：先判断后执行，可能一次都不跑
        int i = 1;
        while (i <= 100) {
            i++;
        }

        // 4. do-while：先执行后判断，至少跑一次
        int k = 1;
        do {
            k++;
        } while (k <= 0);

        // 5. for：知道循环次数最常用
        for (int j = 0; j < 3; j++) {
            System.out.println(j);
        }

        // 6. 增强 for（foreach）：遍历数组/集合，拿不到下标
        int[] arr = {10, 20, 30};
        for (int v : arr) {
            System.out.println(v);
        }

        // break：跳出当前整个循环；continue：跳过本次，进入下一次
        for (int m = 0; m < 5; m++) {
            if (m == 2) continue;
            if (m == 4) break;
            System.out.println(m);
        }
    }

    // ==================================================================
    // 第四节 数组
    // ==================================================================
    private static void demoArray() {
        // 静态初始化：直接给值
        int[] a = {1, 2, 3, 4};
        // 动态初始化：先定长度，元素给默认值（int 默认 0）
        int[] b = new int[5];

        // 长度用 length；下标从 0 开始，最大下标 length-1，越界抛 ArrayIndexOutOfBoundsException
        System.out.println(a.length);
        System.out.println(a[0]);

        // 遍历
        for (int j = 0; j < a.length; j++) {
            System.out.println(a[j]);
        }

        // 二维数组：数组里套数组
        int[][] grid = new int[3][4];
        grid[0][0] = 1;
        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[row].length; col++) {
                System.out.println(grid[row][col]);
            }
        }
    }

    // ==================================================================
    // 第五节 排序与查找（思路先讲清，代码回 SortSearch.java 手写）
    // ==================================================================
    // 冒泡排序：相邻两个两两比较，大的往后换；每轮把当前最大的"冒"到末尾。
    //   时间复杂度 O(n²)，空间 O(1)，稳定排序。
    // 选择排序：每轮在未排序区间里找到最小值下标，和区间第一个位置交换。
    //   时间复杂度 O(n²)，空间 O(1)，不稳定。
    // 二分查找：前提是数组已有序；左右指针不断折半缩小范围。
    //   时间复杂度 O(log n)；无序数组不能用。

    // ==================================================================
    // 第六节 方法
    // ==================================================================
    private static int maxOf(int a, int b) {
        // 定义格式：修饰符 返回值类型 方法名(形参列表) { 方法体; return 返回值; }
        return a > b ? a : b;
    }

    private static void demoMethod() {
        // 调用：方法名(实参)；实参把值传给形参
        int m = maxOf(3, 9);
        System.out.println(m);

        // 基本类型参数是"值传递"：方法内改形参，不影响外面的实参
        int n = 10;
        change(n);
        System.out.println(n); // 仍是 10

        // 方法重载：同一个类里，方法名相同、参数列表不同（个数/类型/顺序），与返回值无关
        // 调用时编译器按传入参数匹配对应重载
    }

    private static void change(int x) {
        x = 999;
    }

    // ==================================================================
    // 第七节 递归
    // ==================================================================
    // 递归 = 方法自己调用自己。两要素缺一不可：
    //   1. 终止条件（出口）：到某个值不再往下递归；
    //   2. 递归公式：把大问题拆成规模更小的同类问题。
    // 没有终止条件会一直压栈，最终 StackOverflowError。
    private static int factorial(int n) {
        if (n <= 1) return 1;          // 终止条件
        return n * factorial(n - 1);  // 递归公式
    }

    private static int fib(int n) {
        if (n == 1 || n == 2) return 1;
        return fib(n - 1) + fib(n - 2);
    }

    private static void demoRecursion() {
        System.out.println(factorial(5));
        System.out.println(fib(10));
    }

    // ==================================================================
    // 易错点速查（写代码时对照）
    // ==================================================================
    // 1. Java 文件名必须和 public class 类名完全一致；类名不能数字开头、不能含 -、不用中文。
    // 2. 语句末尾分号不能漏；括号成对。
    // 3. 基本类型比较用 ==；字符串比较内容用 a.equals(b)，别用 ==。
    // 4. 两个整数相除是整数除法，直接截断小数；要小数就先转 double。
    // 5. 数组下标从 0 开始，长度是 length，最大下标 length - 1。
    // 6. Scanner 混用 nextInt() 和 nextLine() 时，nextInt 留下的换行可能被 nextLine 立刻读空，
    //    必要时补一次 sc.nextLine() 吃掉换行。
    // 7. 成员变量有默认值，局部变量没有默认值，必须先赋值再使用。
}
