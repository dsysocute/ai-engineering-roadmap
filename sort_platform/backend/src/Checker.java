import java.util.Arrays;

/**
 * 判卷规则：光"升序"不够，还要保证排序前后元素一个不多一个不少。
 * 否则交个写死的有序数组也能混成满分。
 */
public final class Checker {

    private Checker() {
    }

    public static boolean isCorrect(int[] origin, int[] result) {
        if (origin == null || result == null) {
            return false;
        }
        if (origin.length != result.length) {
            return false;
        }
        for (int i = 1; i < result.length; i++) {
            if (result[i - 1] > result[i]) {
                return false;   // 不是升序
            }
        }
        int[] a = origin.clone();
        int[] b = result.clone();
        Arrays.sort(a);
        Arrays.sort(b);
        return Arrays.equals(a, b);   // 元素集合必须完全一致
    }
}
