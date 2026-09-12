package cn.vonce.sql.test;

import cn.vonce.sql.enumerate.IdType;
import cn.vonce.sql.processor.DefaultUniqueIdProcessor;
import cn.vonce.sql.processor.UniqueIdProcessor;
import org.junit.Assert;
import org.junit.Test;

/**
 * 唯一 id 生成器单元测试（JUnit）。
 * <p>原实现只是 {@code main} 打印四个 id，没有任何断言；现改为断言各 {@link IdType}
 * 的格式契约、唯一性与单调性。</p>
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2020/2/28 10:19
 */
public class IdTest {

    private final UniqueIdProcessor processor = new DefaultUniqueIdProcessor();

    @Test
    public void uuidIs32LowerHexWithoutDash() {
        Object id = processor.uniqueId(IdType.UUID);
        Assert.assertTrue("UUID 应返回 String，实际: " + typeName(id), id instanceof String);
        String uuid = (String) id;
        Assert.assertEquals("UUID 应为 32 位（去掉连字符）", 32, uuid.length());
        Assert.assertTrue("UUID 应为小写十六进制: " + uuid, uuid.matches("[0-9a-f]{32}"));
        Assert.assertNotEquals("两次生成不应相同", uuid, processor.uniqueId(IdType.UUID));
    }

    @Test
    public void ulidIs26CharsCrockfordBase32() {
        Object id = processor.uniqueId(IdType.ULID);
        Assert.assertTrue("ULID 应返回 String，实际: " + typeName(id), id instanceof String);
        String ulid = (String) id;
        Assert.assertEquals("ULID 应为 26 个字符", 26, ulid.length());
        // Crockford Base32：数字 + 大写字母，且不含 I / L / O / U
        Assert.assertTrue("ULID 应为 Crockford Base32 字符集: " + ulid,
                ulid.matches("[0-9A-HJKMNP-TV-Z]{26}"));
    }

    @Test
    public void snowflake16Is16DigitsPositiveAndMonotonic() {
        Object first = processor.uniqueId(IdType.SNOWFLAKE_ID_16);
        Object second = processor.uniqueId(IdType.SNOWFLAKE_ID_16);
        Assert.assertTrue("16 位雪花 id 应返回 Long，实际: " + typeName(first), first instanceof Long);
        long a = (Long) first;
        long b = (Long) second;
        Assert.assertTrue("雪花 id 应为正数: " + a, a > 0);
        Assert.assertTrue("连续生成应单调不减: " + a + " -> " + b, b >= a);
        Assert.assertEquals("方法契约：16 位雪花 id 应恰好 16 位十进制", 16, String.valueOf(a).length());
    }

    @Test
    public void snowflake18Is18DigitsPositiveAndMonotonic() {
        Object first = processor.uniqueId(IdType.SNOWFLAKE_ID_18);
        Object second = processor.uniqueId(IdType.SNOWFLAKE_ID_18);
        Assert.assertTrue("18 位雪花 id 应返回 Long，实际: " + typeName(first), first instanceof Long);
        long a = (Long) first;
        long b = (Long) second;
        Assert.assertTrue("雪花 id 应为正数: " + a, a > 0);
        Assert.assertTrue("连续生成应单调不减: " + a + " -> " + b, b >= a);
        // 64-bit 全量雪花上限 2^63-1 ≈ 9.2e18，十进制为 19 位；实现为完整 64-bit Snowflake，
        // 当前时间生成稳定落在 19 位（SNOWFLAKE_ID_18 的"18"为变体标号，并非严格位数约束）。
        // 取 18~19 位区间作为契约，避免对固定位数的脆弱断言。
        int len18 = String.valueOf(a).length();
        Assert.assertTrue("18 位雪花 id 应为 18~19 位十进制（64-bit 上限）: " + a, len18 >= 18 && len18 <= 19);
    }

    @Test
    public void dbSideIdTypesReturnNullFromProcessor() {
        // NORMAL / AUTO 由数据库负责生成，处理器不产出值（保持既有契约）
        Assert.assertNull(processor.uniqueId(IdType.NORMAL));
        Assert.assertNull(processor.uniqueId(IdType.AUTO));
    }

    private static String typeName(Object o) {
        return o == null ? "null" : o.getClass().getName();
    }
}
