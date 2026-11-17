import java.util.Locale;

/**
 * 评分规则。
 *
 * 总分 100 = 正确性 60 + 编码用时 20 + 算法效率 20，两项细分都保留 1 位小数。
 *
 * 效率和用时都不用绝对毫秒/绝对分钟硬切档，而是分段线性折算：
 * 效率比 = 选手耗时 / 同机基准耗时，换一台机器不用重新调档位；
 * 段内连续变化，不会出现「多花 1 毫秒掉一整档」的断崖。
 */
public final class ScoreCalculator {

    /** 编码用时、算法效率各自满分 */
    public static final double CODING_MAX = 20.0;
    public static final double EFFICIENCY_MAX = 20.0;

    private ScoreCalculator() {
    }

    /**
     * 编码用时（秒）→ 分（满分 20，1 位小数）。
     *
     * 40 分钟内（含 40:00）满分；之后按秒分段线性递减，120:00 正好 0 分，超过 120 分钟记 0。
     * 以前用「整分钟向下取整」，40 分 59 秒仍算 40 分钟拿满分；而且 120 分钟还给 4 分、
     * 一过 120 分钟立刻 0 分，中间有 4 分的断崖。改成按秒后两条都不存在。
     */
    public static double codingScoreSeconds(long seconds) {
        if (seconds <= 40L * 60) {
            return CODING_MAX;
        }
        if (seconds <= 60L * 60) {
            return round1(lerp(seconds, 40 * 60, 60 * 60, 20.0, 16.0));
        }
        if (seconds <= 80L * 60) {
            return round1(lerp(seconds, 60 * 60, 80 * 60, 16.0, 12.0));
        }
        if (seconds <= 100L * 60) {
            return round1(lerp(seconds, 80 * 60, 100 * 60, 12.0, 8.0));
        }
        if (seconds <= 120L * 60) {
            return round1(lerp(seconds, 100 * 60, 120 * 60, 8.0, 0.0));
        }
        return 0.0;
    }

    /**
     * 效率比值 → 分（满分 20，1 位小数）。
     *
     * 每一段的落点都对应原整数档位（25/22/19/16/12/8/4/2 换算到 20 分制），
     * 段内线性过渡，所以同样耗时不会比原标准更低，只是不再一过边界就猛掉一整档。
     */
    public static double efficiencyScore(double ratio) {
        if (ratio <= 1.20) {
            return EFFICIENCY_MAX;
        }
        if (ratio <= 2.00) {
            return round1(lerp(ratio, 1.20, 2.00, 20.0, 17.6));
        }
        if (ratio <= 3.00) {
            return round1(lerp(ratio, 2.00, 3.00, 17.6, 15.2));
        }
        if (ratio <= 5.00) {
            return round1(lerp(ratio, 3.00, 5.00, 15.2, 12.8));
        }
        if (ratio <= 10.00) {
            return round1(lerp(ratio, 5.00, 10.00, 12.8, 9.6));
        }
        if (ratio <= 20.00) {
            return round1(lerp(ratio, 10.00, 20.00, 9.6, 6.4));
        }
        if (ratio <= 50.00) {
            return round1(lerp(ratio, 20.00, 50.00, 6.4, 3.2));
        }
        if (ratio <= 100.00) {
            return round1(lerp(ratio, 50.00, 100.00, 3.2, 1.6));
        }
        return 1.6;
    }

    /** 一组效率用例的得分；超时或没有数据按 0 算。 */
    public static double efficiencyScore(long solutionNano, long refNano, boolean missing) {
        if (missing || solutionNano <= 0 || refNano <= 0) {
            return 0.0;
        }
        return efficiencyScore((double) solutionNano / (double) refNano);
    }

    /** 区间内线性插值：x 从 x0 走到 x1 时，返回值从 y0 变到 y1。 */
    private static double lerp(double x, double x0, double x1, double y0, double y1) {
        double t = (x - x0) / (x1 - x0);
        t = Math.max(0.0, Math.min(1.0, t));
        return y0 + (y1 - y0) * t;
    }

    /** 统一保留 1 位小数（四舍五入）。 */
    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    public static String format(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
