package cn.vonce.sql.uitls;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字符串工具类
 */
public class StringUtil {

    /**
     * 占位符前缀: "${"
     */
    public static final String PLACEHOLDER_PREFIX = "${";
    /**
     * 占位符后缀: "}"
     */
    public static final String PLACEHOLDER_SUFFIX = "}";

    /**
     * 下划线
     */
    private final static String UNDERLINE = "_";

    /**
     * 连字符
     */
    private final static String HYPHEN = "-";

    public static boolean isEmpty(Object str) {
        return str == null || "".equals(str);
    }

    public static boolean isNotEmpty(Object str) {
        return !isEmpty(str);
    }

    public static boolean isBlank(Object str) {
        if (isEmpty(str)) {
            return true;
        }
        if (str instanceof CharSequence) {
            CharSequence cs = (CharSequence) str;
            int strLen;
            if ((strLen = cs.length()) != 0) {
                for (int i = 0; i < strLen; ++i) {
                    if (!Character.isWhitespace(cs.charAt(i))) {
                        return false;
                    }
                }

            }
            return true;
        }
        return false;
    }

    public static boolean isNotBlank(Object str) {
        return !isBlank(str);
    }

    /**
     * 生成UUID
     */
    public static String getUUid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成一个几位的随机数
     *
     * @param length 随机数的长度
     */
    public static String randomCode(int length) {
        String base = "1234567890";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int number = random.nextInt(base.length());
            sb.append(base.charAt(number));
        }
        return sb.toString();
    }

    /**
     * 将一段文本的占位符替换 如：${name} to zhangsan
     *
     * @param text   要替换的文本
     * @param params map.key = name,map.value = zhangsan
     * @return 替换后的文本
     */
    public static String resolvePlaceholders(String text, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return text;
        }
        StringBuilder buf = new StringBuilder(text);
        int startIndex = buf.indexOf(PLACEHOLDER_PREFIX);
        while (startIndex != -1) {
            int endIndex = buf.indexOf(PLACEHOLDER_SUFFIX, startIndex + PLACEHOLDER_PREFIX.length());
            if (endIndex != -1) {
                String placeholder = buf.substring(startIndex + PLACEHOLDER_PREFIX.length(), endIndex);
                int nextIndex = endIndex + PLACEHOLDER_SUFFIX.length();
                try {
                    String propVal = params.get(placeholder);
                    if (propVal != null) {
                        buf.replace(startIndex, endIndex + PLACEHOLDER_SUFFIX.length(), propVal);
                        nextIndex = startIndex + propVal.length();
                    } else {
                        //LOG.warn("Could not resolve placeholder '" + placeholder + "' in [" + text + "] ");
                    }
                } catch (Exception ex) {
                    //LOG.warn("Could not resolve placeholder '" + placeholder + "' in [" + text + "]: " + ex);
                }
                startIndex = buf.indexOf(PLACEHOLDER_PREFIX, nextIndex);
            } else {
                startIndex = -1;
            }
        }
        return buf.toString();
    }

    /***
     * 下划线命名转为驼峰命名
     *
     * @param para
     *        下划线命名的字符串
     */

    public static String underlineToHump(String para) {
        StringBuilder result = new StringBuilder();
        String a[] = para.split(UNDERLINE);
        if (a.length == 1) {
            return a[0];
        }
        for (String s : a) {
            if (!para.contains(UNDERLINE)) {
                result.append(s.toLowerCase());
                continue;
            }
            if (StringUtil.isEmpty(s)) {
                continue;
            }
            if (result.length() == 0) {
                result.append(s.toLowerCase());
            } else {
                result.append(s.substring(0, 1).toUpperCase());
                result.append(s.substring(1).toLowerCase());
            }
        }
        return result.toString();
    }

    /***
     * 驼峰命名转为下划线命名
     *
     * @param hump 驼峰命名的字符串
     *
     */
    public static String humpToUnderline(String hump) {
        return humpTo(hump, UNDERLINE);
    }

    /***
     * 驼峰命名转为连字符命名
     *
     * @param hump 驼峰命名的字符串
     *
     */
    public static String humpToHyphen(String hump) {
        return humpTo(hump, HYPHEN);
    }

    /***
     * 驼峰命名转为符号分割命名
     *
     * @param hump 驼峰命名的字符串
     * @param symbol 符号
     */
    public static String humpTo(String hump, String symbol) {
        StringBuilder sb = new StringBuilder(hump);
        int temp = 0;
        if (!hump.contains(symbol)) {
            for (int i = 0; i < hump.length(); i++) {
                if (Character.isUpperCase(hump.charAt(i)) && (i - 1 >= 0 ? Character.isLowerCase(hump.charAt(i - 1)) : false)) {
                    sb.insert(i + temp, symbol);
                    temp += 1;
                }
            }
        }
        return sb.toString().toLowerCase();
    }

    /**
     * 首字母转大写
     * @param str
     * @return
     */
    public static String firstToUpperCase(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * 获取指定字符串中括号内的内容，返回字符串数组
     *
     * @param content
     * @return
     */
    public static String getBracketContent(String content) {
        String[] arr = new String[0];
        Pattern p = Pattern.compile("(?<=\\()[^\\)]+");
        Matcher m = p.matcher(content);
        while (m.find()) {
            arr = Arrays.copyOf(arr, arr.length + 1);
            arr[arr.length - 1] = m.group();
        }
        return arr.length == 0 ? "" : arr[0];
    }

    /**
     * 排除括号及括号内的数据
     *
     * @param content
     * @return
     */
    public static String getWord(String content) {
        return content.replaceAll("[\\[][^\\[\\]]+[\\]]|[\\(][^\\(\\)]+[\\)]", "");
    }


}
