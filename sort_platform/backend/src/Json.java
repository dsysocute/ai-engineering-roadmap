/**
 * 极简 JSON 输出工具。只负责把字符串安全地转义成 JSON 字面量，
 * 整个后端不依赖任何第三方库。
 */
public final class Json {

    private Json() {
    }

    public static String esc(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /** 拼一个键值对，value 由调用方自行决定是否已经转义。 */
    public static String pair(String key, String rawValue) {
        return esc(key) + ":" + rawValue;
    }

    public static String str(String key, String value) {
        return esc(key) + ":" + esc(value);
    }

    public static String num(String key, double value) {
        return esc(key) + ":" + value;
    }

    /** 整数字段单独出口，避免输出成 1.0、65.0 这种别扭的样子。 */
    public static String inum(String key, long value) {
        return esc(key) + ":" + value;
    }

    public static String bool(String key, boolean value) {
        return esc(key) + ":" + value;
    }

    public static String arr(String... items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(items[i]);
        }
        return sb.append(']').toString();
    }
}
