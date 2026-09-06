package cn.vonce.sql.define;

import cn.vonce.sql.bean.Column;
import cn.vonce.sql.bean.Order;
import cn.vonce.sql.bean.RawValue;
import cn.vonce.sql.enumerate.SqlSort;
import cn.vonce.sql.enumerate.TimeUnit;
import cn.vonce.sql.uitls.LambdaUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
/**
 * Sql函数
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2022/12/8 15:15
 */
public class SqlFun extends Column {

    private String funName;
    private Object[] values;

    /**
     * 方言支持元数据（{@code null} = 通用 SQL，所有方言通过校验）。
     * <p>工厂方法内部用 {@link #dialect(DialectSupport)} 标注；用户自定义函数也可用此字段。</p>
     */
    private DialectSupport dialectSupport;

    private SqlFun(String funName, Object[] values) {
        this.funName = funName;
        this.values = values;
    }

    public String getFunName() {
        return funName;
    }

    public Object[] getValues() {
        return values;
    }

    @Override
    public String toString() {
        return "SqlFun{" + "funName" + funName + '\'' + ", values=" + Arrays.toString(values) + '}';
    }

    /**
     * 是否作为窗口函数使用（OVER 子句）
     */
    private boolean windowMode = false;

    /**
     * 窗口分区列 PARTITION BY
     */
    private List<Column> partitionByList = new ArrayList<>();

    /**
     * 窗口排序列 ORDER BY
     */
    private List<Order> orderByList = new ArrayList<>();

    /**
     * 窗口帧子句，如 "ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW"
     */
    private String frame;

    /**
     * 标记为窗口函数，后续接 partitionBy / orderBy / frame。
     * 例：SqlFun.sum("amount").over().partitionBy("user_id").orderByDesc("create_time")
     *
     * @return this
     */
    public SqlFun over() {
        this.windowMode = true;
        return this;
    }

    /**
     * 添加窗口分区列 PARTITION BY
     *
     * @param columns 列
     * @return this
     */
    public SqlFun partitionBy(Column... columns) {
        if (columns != null) {
            this.partitionByList.addAll(Arrays.asList(columns));
            this.windowMode = true;
        }
        return this;
    }

    /**
     * 添加窗口分区列 PARTITION BY（Lambda 形式）
     *
     * @param columnFuns 列
     * @return this
     */
    public <T, R> SqlFun partitionBy(ColumnFun<T, R>... columnFuns) {
        if (columnFuns != null) {
            for (ColumnFun<T, R> columnFun : columnFuns) {
                this.partitionByList.add(LambdaUtil.getColumn(columnFun));
            }
            this.windowMode = true;
        }
        return this;
    }

    /**
     * 添加窗口分区列 PARTITION BY（列名形式）
     *
     * @param columNames 列名
     * @return this
     */
    public SqlFun partitionBy(String... columNames) {
        if (columNames != null) {
            for (String name : columNames) {
                this.partitionByList.add(new Column(name));
            }
            this.windowMode = true;
        }
        return this;
    }

    /**
     * 添加窗口排序列 ORDER BY
     *
     * @param orders 排序列
     * @return this
     */
    public SqlFun orderBy(Order... orders) {
        if (orders != null) {
            this.orderByList.addAll(Arrays.asList(orders));
            this.windowMode = true;
        }
        return this;
    }

    /**
     * 添加窗口排序列 ORDER BY ASC
     *
     * @param column 列
     * @return this
     */
    public SqlFun orderByAsc(Column column) {
        this.orderByList.add(new Order(column, SqlSort.ASC));
        this.windowMode = true;
        return this;
    }

    /**
     * 添加窗口排序列 ORDER BY ASC（Lambda 形式）
     *
     * @param columnFun 列
     * @return this
     */
    public <T, R> SqlFun orderByAsc(ColumnFun<T, R> columnFun) {
        this.orderByList.add(new Order(LambdaUtil.getColumn(columnFun), SqlSort.ASC));
        this.windowMode = true;
        return this;
    }

    /**
     * 添加窗口排序列 ORDER BY DESC
     *
     * @param column 列
     * @return this
     */
    public SqlFun orderByDesc(Column column) {
        this.orderByList.add(new Order(column, SqlSort.DESC));
        this.windowMode = true;
        return this;
    }

    /**
     * 添加窗口排序列 ORDER BY DESC（Lambda 形式）
     *
     * @param columnFun 列
     * @return this
     */
    public <T, R> SqlFun orderByDesc(ColumnFun<T, R> columnFun) {
        this.orderByList.add(new Order(LambdaUtil.getColumn(columnFun), SqlSort.DESC));
        this.windowMode = true;
        return this;
    }

    /**
     * 添加窗口排序列 ORDER BY ASC（列名形式）
     *
     * @param columName 列名
     * @return this
     */
    public SqlFun orderByAsc(String columName) {
        this.orderByList.add(new Order(columName, SqlSort.ASC));
        this.windowMode = true;
        return this;
    }

    /**
     * 添加窗口排序列 ORDER BY DESC（列名形式）
     *
     * @param columName 列名
     * @return this
     */
    public SqlFun orderByDesc(String columName) {
        this.orderByList.add(new Order(columName, SqlSort.DESC));
        this.windowMode = true;
        return this;
    }

    /**
     * 设置窗口帧子句（如 "ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW"）
     *
     * @param frameClause 帧子句
     * @return this
     */
    public SqlFun frame(String frameClause) {
        this.frame = frameClause;
        this.windowMode = true;
        return this;
    }

    /**
     * 是否包含窗口定义
     *
     * @return 是否窗口函数
     */
    public boolean hasWindow() {
        return windowMode;
    }

    public List<Column> getPartitionByList() {
        return partitionByList;
    }

    public List<Order> getOrderByList() {
        return orderByList;
    }

    public String getFrame() {
        return frame;
    }

    /**
     * 行号（无参数窗口函数）
     *
     * @return SqlFun
     */
    public static SqlFun rowNumber() {
        return new SqlFun("row_number", null);
    }

    /**
     * 排名（并列同名次，后续跳号）
     *
     * @return SqlFun
     */
    public static SqlFun rank() {
        return new SqlFun("rank", null);
    }

    /**
     * 密集排名（并列同名次，后续不跳号）
     *
     * @return SqlFun
     */
    public static SqlFun denseRank() {
        return new SqlFun("dense_rank", null);
    }

    /**
     * 分桶（将有序窗口均分为 n 个分组）
     *
     * @param n 桶数
     * @return SqlFun
     */
    public static SqlFun ntile(int n) {
        return new SqlFun("ntile", new Object[]{n});
    }

    /**
     * 访问当前行之前第 offset 行的 expr 值
     *
     * @param expr 表达式
     * @return SqlFun
     */
    public static SqlFun lag(Object expr) {
        return new SqlFun("lag", new Object[]{expr});
    }

    public static SqlFun lag(Object expr, int offset) {
        return new SqlFun("lag", new Object[]{expr, offset});
    }

    public static SqlFun lag(Object expr, int offset, Object defaultValue) {
        return new SqlFun("lag", new Object[]{expr, offset, defaultValue});
    }

    public static <T, R> SqlFun lag(ColumnFun<T, R> expr) {
        return new SqlFun("lag", new Object[]{expr});
    }

    public static <T, R> SqlFun lag(ColumnFun<T, R> expr, int offset) {
        return new SqlFun("lag", new Object[]{expr, offset});
    }

    public static <T, R> SqlFun lag(ColumnFun<T, R> expr, int offset, Object defaultValue) {
        return new SqlFun("lag", new Object[]{expr, offset, defaultValue});
    }

    /**
     * 访问当前行之后第 offset 行的 expr 值
     *
     * @param expr 表达式
     * @return SqlFun
     */
    public static SqlFun lead(Object expr) {
        return new SqlFun("lead", new Object[]{expr});
    }

    public static SqlFun lead(Object expr, int offset) {
        return new SqlFun("lead", new Object[]{expr, offset});
    }

    public static SqlFun lead(Object expr, int offset, Object defaultValue) {
        return new SqlFun("lead", new Object[]{expr, offset, defaultValue});
    }

    public static <T, R> SqlFun lead(ColumnFun<T, R> expr) {
        return new SqlFun("lead", new Object[]{expr});
    }

    public static <T, R> SqlFun lead(ColumnFun<T, R> expr, int offset) {
        return new SqlFun("lead", new Object[]{expr, offset});
    }

    public static <T, R> SqlFun lead(ColumnFun<T, R> expr, int offset, Object defaultValue) {
        return new SqlFun("lead", new Object[]{expr, offset, defaultValue});
    }

    /**
     * 窗口内第一行 expr 值
     *
     * @param expr 表达式
     * @return SqlFun
     */
    public static SqlFun firstValue(Object expr) {
        return new SqlFun("first_value", new Object[]{expr});
    }

    public static <T, R> SqlFun firstValue(ColumnFun<T, R> expr) {
        return new SqlFun("first_value", new Object[]{expr});
    }

    /**
     * 窗口内最后一行 expr 值
     *
     * @param expr 表达式
     * @return SqlFun
     */
    public static SqlFun lastValue(Object expr) {
        return new SqlFun("last_value", new Object[]{expr});
    }

    public static <T, R> SqlFun lastValue(ColumnFun<T, R> expr) {
        return new SqlFun("last_value", new Object[]{expr});
    }

    /**
     * 窗口内第 n 行 expr 值
     *
     * @param expr 表达式
     * @param n    行号
     * @return SqlFun
     */
    public static SqlFun nthValue(Object expr, int n) {
        return new SqlFun("nth_value", new Object[]{expr, n});
    }

    public static <T, R> SqlFun nthValue(ColumnFun<T, R> expr, int n) {
        return new SqlFun("nth_value", new Object[]{expr, n});
    }

    /**
     * 版本（mysql）
     *
     * @return
     */
    public static SqlFun version() {
        return new SqlFun("version", null);
    }

    /**
     * 当前数据库（mysql）
     *
     * @return
     */
    public static SqlFun database() {
        return new SqlFun("database", null);
    }

    /**
     * 当前用户（mysql）
     *
     * @return
     */
    public static SqlFun user() {
        return new SqlFun("user", null);
    }

    /**
     * 返回的结果集中的行数。
     *
     * @param value
     * @return
     */
    public static SqlFun count(Object value) {
        return new SqlFun("count", new Object[]{value});
    }

    /**
     * 返回的结果集中的行数。
     *
     * @param value
     * @return
     */
    public static <T, R> SqlFun count(ColumnFun<T, R> value) {
        return new SqlFun("count", new Object[]{value});
    }

    /**
     * 用于计算一组值或表达式的平均值。
     *
     * @param value
     * @return
     */
    public static SqlFun avg(Object value) {
        return new SqlFun("avg", new Object[]{value});
    }

    /**
     * 用于计算一组值或表达式的平均值。
     *
     * @param value
     * @return
     */
    public static <T, R> SqlFun avg(ColumnFun<T, R> value) {
        return new SqlFun("avg", new Object[]{value});
    }

    /**
     * 返回一组值中的最大值。
     *
     * @param value
     * @return
     */
    public static SqlFun max(Object value) {
        return new SqlFun("max", new Object[]{value});
    }

    /**
     * 返回一组值中的最大值。
     *
     * @param value
     * @return
     */
    public static <T, R> SqlFun max(ColumnFun<T, R> value) {
        return new SqlFun("max", new Object[]{value});
    }

    /**
     * 返回一组值中的最小值。
     *
     * @param value
     * @return
     */
    public static SqlFun min(Object value) {
        return new SqlFun("min", new Object[]{value});
    }

    /**
     * 返回一组值中的最小值。
     *
     * @param value
     * @return
     */
    public static <T, R> SqlFun min(ColumnFun<T, R> value) {
        return new SqlFun("min", new Object[]{value});
    }

    /**
     * 用于计算一组值或表达式的总和。
     *
     * @param value
     * @return
     */
    public static SqlFun sum(Object value) {
        return new SqlFun("sum", new Object[]{value});
    }

    /**
     * 用于计算一组值或表达式的总和。
     *
     * @param value
     * @return
     */
    public static <T, R> SqlFun sum(ColumnFun<T, R> value) {
        return new SqlFun("sum", new Object[]{value});
    }

    /**
     * 提取日期或日期时间表达式expr中的日期部分。
     *
     * @return
     */
    public static SqlFun date(Object date) {
        return new SqlFun("date", new Object[]{date});
    }

    /**
     * 提取日期或日期时间表达式expr中的日期部分。
     *
     * @return
     */
    public static <T, R> SqlFun date(ColumnFun<T, R> date) {
        return new SqlFun("date", new Object[]{date});
    }


    /**
     * 返回以"YYYY-MM-DD HH:MM:DD"或"YYYYMMDDHHMMSS.uuuuuuu"格式的字符串或数字返回配置的时区中的当前日期和时间。
     *
     * @return
     */
    public static SqlFun now() {
        return new SqlFun("now", null);
    }

    /**
     * 返回当前日期为'YYYY-MM-DD“或YYYYMMDD格式的值。
     *
     * @return
     */
    public static SqlFun curDate() {
        return new SqlFun("curDate", null);
    }

    /**
     * 返回当前时间以“HH:MM:SS'或'HHMMSS' 格式的值。
     *
     * @return
     */
    public static SqlFun curTime() {
        return new SqlFun("curTime", null);
    }

    /**
     * 返回一个指定日期的年份值，范围为1000到9999，如果日期为零，YEAR()函数返回0。
     *
     * @param date 合法的日期
     * @return
     */
    public static SqlFun year(Object date) {
        return new SqlFun("year", new Object[]{date});
    }

    /**
     * 返回一个指定日期的年份值，范围为1000到9999，如果日期为零，YEAR()函数返回0。
     *
     * @param date 合法的日期
     * @return
     */
    public static <T, R> SqlFun year(ColumnFun<T, R> date) {
        return new SqlFun("year", new Object[]{date});
    }

    /**
     * 返回日期的月份，取值范围为0〜12。
     *
     * @param date 合法的日期
     * @return
     */
    public static SqlFun month(Object date) {
        return new SqlFun("month", new Object[]{date});
    }

    /**
     * 返回日期的月份，取值范围为0〜12。
     *
     * @param date 合法的日期
     * @return
     */
    public static <T, R> SqlFun month(ColumnFun<T, R> date) {
        return new SqlFun("month", new Object[]{date});
    }

    /**
     * 返回日期的月份全名。
     *
     * @param date 合法的日期
     * @return
     */
    public static SqlFun monthName(Object date) {
        return new SqlFun("monthName", new Object[]{date});
    }

    /**
     * 返回日期的月份全名。
     *
     * @param date 合法的日期
     * @return
     */
    public static <T, R> SqlFun monthName(ColumnFun<T, R> date) {
        return new SqlFun("monthName", new Object[]{date});
    }

    /**
     * 返回给定日期的月份的日期部分。
     *
     * @param date 它是您要获取月份日期的日期值。如果date参数为零，例如'0000-00-00'，则DAY函数返回0，如果日期为NULL，则DAY函数返回NULL值。
     * @return
     */
    public static SqlFun day(Object date) {
        return new SqlFun("day", new Object[]{date});
    }

    /**
     * 返回给定日期的月份的日期部分。
     *
     * @param date 它是您要获取月份日期的日期值。如果date参数为零，例如'0000-00-00'，则DAY函数返回0，如果日期为NULL，则DAY函数返回NULL值。
     * @return
     * @return
     */
    public static <T, R> SqlFun day(ColumnFun<T, R> date) {
        return new SqlFun("day", new Object[]{date});
    }

    /**
     * 返回时间的小时部分。返回值的范围为0至23的小时值。然而，TIME值的范围实际上要大得多，所以HOUR可以返回大于23的值。
     *
     * @param time 合法的时间
     * @return
     */
    public static SqlFun hour(Object time) {
        return new SqlFun("hour", new Object[]{time});
    }

    /**
     * 返回时间的小时部分。返回值的范围为0至23的小时值。然而，TIME值的范围实际上要大得多，所以HOUR可以返回大于23的值。
     *
     * @param time 合法的时间
     */
    public static <T, R> SqlFun hour(ColumnFun<T, R> time) {
        return new SqlFun("hour", new Object[]{time});
    }

    /**
     * 返回时间的分钟，范围为0至59。
     *
     * @return
     * @return
     * @@param time 合法的时间
     */
    public static SqlFun minute(Object time) {
        return new SqlFun("minute", new Object[]{time});
    }

    /**
     * 返回时间的分钟，范围为0至59。
     *
     * @return
     * @@param time 合法的时间
     */
    public static <T, R> SqlFun minute(ColumnFun<T, R> time) {
        return new SqlFun("minute", new Object[]{time});
    }

    /**
     * 返回时间秒值，范围为0〜59。
     *
     * @param time 合法的时间
     * @return
     */
    public static SqlFun second(Object time) {
        return new SqlFun("second", new Object[]{time});
    }

    /**
     * 返回时间的秒，取值范围为0〜59。
     *
     * @param time 合法的时间
     * @return
     */
    public static <T, R> SqlFun second(ColumnFun<T, R> time) {
        return new SqlFun("second", new Object[]{time});
    }

    /**
     * 计算两个DATE，DATETIME或TIMESTAMP值之间的天数。
     *
     * @param unit      表示差值的单位，可以是以下值之一：MICROSECOND（微秒）、SECOND（秒）、MINUTE（分）、HOUR（小时）、DAY（天）、WEEK（周）、MONTH（月）、QUARTER（季度）或 YEAR（年）
     * @param startDate 表示时间段的起始时间
     * @param endDate   表示时间段的结束时间
     * @return
     */
    public static SqlFun timestampDiff(TimeUnit unit, Object startDate, Object endDate) {
        return new SqlFun("timestampDiff", new Object[]{unit.name(), startDate, endDate});
    }

    /**
     * 计算两个DATE，DATETIME或TIMESTAMP值之间的天数。
     *
     * @param unit      表示差值的单位，可以是以下值之一：MICROSECOND（微秒）、SECOND（秒）、MINUTE（分）、HOUR（小时）、DAY（天）、WEEK（周）、MONTH（月）、QUARTER（季度）或 YEAR（年）
     * @param startDate 表示时间段的起始时间
     * @param endDate   表示时间段的结束时间
     * @return
     */
    public static <T, R> SqlFun timestampDiff(TimeUnit unit, ColumnFun<T, R> startDate, Object endDate) {
        return new SqlFun("timestampDiff", new Object[]{unit.name(), startDate, endDate});
    }

    /**
     * 计算两个DATE，DATETIME或TIMESTAMP值之间的天数。
     *
     * @param unit      表示差值的单位，可以是以下值之一：MICROSECOND（微秒）、SECOND（秒）、MINUTE（分）、HOUR（小时）、DAY（天）、WEEK（周）、MONTH（月）、QUARTER（季度）或 YEAR（年）
     * @param startDate 表示时间段的起始时间
     * @param endDate   表示时间段的结束时间
     * @return
     */
    public static <T, R> SqlFun timestampDiff(TimeUnit unit, ColumnFun<T, R> startDate, ColumnFun<T, R> endDate) {
        return new SqlFun("timestampDiff", new Object[]{unit.name(), startDate, endDate});
    }

    /**
     * 计算两个DATE，DATETIME或TIMESTAMP值之间的天数。
     *
     * @param startDate 表示时间段的起始时间
     * @param endDate   表示时间段的结束时间
     * @return
     */
    public static SqlFun dateDiff(Object startDate, Object endDate) {
        return new SqlFun("dateDiff", new Object[]{startDate, endDate});
    }

    /**
     * 计算两个DATE，DATETIME或TIMESTAMP值之间的天数。
     *
     * @param startDate 表示时间段的起始时间
     * @param endDate   表示时间段的结束时间
     * @return
     */
    public static <T, R> SqlFun dateDiff(ColumnFun<T, R> startDate, Object endDate) {
        return new SqlFun("dateDiff", new Object[]{startDate, endDate});
    }

    /**
     * 计算两个DATE，DATETIME或TIMESTAMP值之间的天数。
     *
     * @param startDate 表示时间段的起始时间
     * @param endDate   表示时间段的结束时间
     * @return
     */
    public static <T, R> SqlFun dateDiff(ColumnFun<T, R> startDate, ColumnFun<T, R> endDate) {
        return new SqlFun("dateDiff", new Object[]{startDate, endDate});
    }

    /**
     * 时间增加
     *
     * @param date 是DATE或DATETIME的起始值。
     * @param num  是一个字符串，用于确定从起始日期增加的间隔值。
     * @param unit 是expr可解析的间隔单位，例如DAY，HOUR等.
     * @return
     */
    public static SqlFun dateAdd(Object date, int num, TimeUnit unit) {
        return new SqlFun("date_add", new Object[]{date, new RawValue("interval " + num + " " + unit.name())});
    }

    /**
     * 时间增加
     *
     * @param date 是DATE或DATETIME的起始值。
     * @param num  是一个字符串，用于确定从起始日期增加的间隔值。
     * @param unit 是expr可解析的间隔单位，例如DAY，HOUR等.
     * @return
     */
    public static <T, R> SqlFun dateAdd(ColumnFun<T, R> date, int num, TimeUnit unit) {
        return new SqlFun("date_add", new Object[]{date, new RawValue("interval " + num + " " + unit.name())});
    }

    /**
     * 时间减少
     *
     * @param date 是DATE或DATETIME的起始值。
     * @param num  是一个字符串，用于确定从起始日期减去的间隔值。
     * @param unit 是expr可解析的间隔单位，例如DAY，HOUR等.
     * @return
     */
    public static SqlFun dateSub(Object date, int num, TimeUnit unit) {
        return new SqlFun("date_sub", new Object[]{date, new RawValue("interval " + num + " " + unit.name())});
    }

    /**
     * 时间减少
     *
     * @param date 是DATE或DATETIME的起始值。
     * @param num  是一个字符串，用于确定从起始日期减去的间隔值。
     * @param unit 是expr可解析的间隔单位，例如DAY，HOUR等.
     * @return
     */
    public static <T, R> SqlFun dateSub(ColumnFun<T, R> date, int num, TimeUnit unit) {
        return new SqlFun("date_sub", new Object[]{date, new RawValue("interval " + num + " " + unit.name())});
    }

    /**
     * 日期类型格式化成指定格式的字符串
     *
     * @param date   合法的日期
     * @param format 规定日期/时间的输出格式
     * @return
     */
    public static SqlFun date_format(Object date, String format) {
        return new SqlFun("date_format", new Object[]{date, format});
    }

    /**
     * 日期类型格式化成指定格式的字符串
     *
     * @param date   合法的日期
     * @param format 规定日期/时间的输出格式
     * @return
     */
    public static <T, R> SqlFun date_format(ColumnFun<T, R> date, String format) {
        return new SqlFun("date_format", new Object[]{date, format});
    }

    /**
     * 根据format格式字符串将str字符串转换为日期值。
     *
     * @param str    合法的字符串日期
     * @param format 规定日期/时间的格式
     * @return
     */
    public static SqlFun str_to_date(Object str, String format) {
        return new SqlFun("str_to_date", new Object[]{str, format});
    }

    /**
     * 根据format格式字符串将str字符串转换为日期值。
     *
     * @param str    合法的字符串日期
     * @param format 规定日期/时间的格式
     * @return
     */
    public static <T, R> SqlFun str_to_date(ColumnFun<T, R> str, String format) {
        return new SqlFun("str_to_date", new Object[]{str, format});
    }

    /**
     * 判断，类似于三目运算
     *
     * @param bool        布尔值
     * @param trueResult  true返回的结果
     * @param falseResult false返回到结果
     * @return
     */
    public static SqlFun iF(Object bool, Object trueResult, Object falseResult) {
        return new SqlFun("if", new Object[]{bool, trueResult, falseResult});
    }

    /**
     * 统计字符串的字节数（取决于编码方式，utf8汉字3字节，gbk汉字2字节）
     *
     * @param str 字符串
     * @return
     */
    public static SqlFun length(Object str) {
        return new SqlFun("length", new Object[]{str});
    }

    /**
     * 统计字符串的字节数（取决于编码方式，utf8汉字3字节，gbk汉字2字节）
     *
     * @param str 字符串
     * @return
     */
    public static <T, R> SqlFun length(ColumnFun<T, R> str) {
        return new SqlFun("length", new Object[]{str});
    }

    /**
     * 拼接字符
     *
     * @param str 字符串数组
     * @return
     */
    public static SqlFun concat(Object... str) {
        return new SqlFun("concat", str);
    }

    /**
     * 拼接字符
     *
     * @param separator 分隔符
     * @param str       字符串数组
     * @return
     */
    public static SqlFun concat_ws(String separator, Object... str) {
        List<Object> objectList = new ArrayList<>();
        objectList.add(separator);
        objectList.addAll(Arrays.asList(str));
        return new SqlFun("concat_ws", objectList.toArray());
    }

    /**
     * 切割字符，startIndex起始位置（mysql下标从1开始）
     *
     * @param str        字符串
     * @param startIndex startIndex起始位置
     * @return
     */
    public static SqlFun substring(Object str, int startIndex) {
        return new SqlFun("substring", new Object[]{str, startIndex});
    }

    /**
     * 切割字符，startIndex起始位置（mysql下标从1开始）
     *
     * @param str        字符串
     * @param startIndex startIndex起始位置
     * @return
     */
    public static <T, R> SqlFun substring(ColumnFun<T, R> str, int startIndex) {
        return new SqlFun("substring", new Object[]{str, startIndex});
    }

    /**
     * 切割字符，start起始位置（mysql下标从1开始），end结束位置，表示切割长度
     *
     * @param str        字符串
     * @param startIndex startIndex起始位置
     * @param endIndex   startIndex结束位置
     * @return
     */
    public static SqlFun substring(Object str, int startIndex, int endIndex) {
        return new SqlFun("substring", new Object[]{str, startIndex, endIndex});
    }

    /**
     * 切割字符，start起始位置（mysql下标从1开始），end结束位置，表示切割长度
     *
     * @param str        字符串
     * @param startIndex startIndex起始位置
     * @param endIndex   startIndex结束位置
     * @return
     */
    public static <T, R> SqlFun substring(ColumnFun<T, R> str, int startIndex, int endIndex) {
        return new SqlFun("substring", new Object[]{str, startIndex, endIndex});
    }

    /**
     * 返回str2在str1中首次出现的位置；如果没有找到，则返回0。不区分大小写
     *
     * @param str1 字符串1
     * @param str2 字符串2
     * @return
     */
    public static SqlFun instr(Object str1, Object str2) {
        return new SqlFun("instr", new Object[]{str1, str2});
    }

    /**
     * 返回str2在str1中首次出现的位置；如果没有找到，则返回0。不区分大小写
     *
     * @param str1 字符串1
     * @param str2 字符串2
     * @return
     */
    public static <T, R> SqlFun instr(ColumnFun<T, R> str1, Object str2) {
        return new SqlFun("instr", new Object[]{str1, str2});
    }

    /**
     * 返回str2在str1中首次出现的位置；如果没有找到，则返回0。不区分大小写
     *
     * @param str1 字符串1
     * @param str2 字符串2
     * @return
     */
    public static <T, R> SqlFun instr(ColumnFun<T, R> str1, ColumnFun<T, R> str2) {
        return new SqlFun("instr", new Object[]{str1, str2});
    }

    /**
     * 字母变大写
     *
     * @param str 字符串
     * @return
     */
    public static SqlFun upper(Object str) {
        return new SqlFun("upper", new Object[]{str});
    }

    /**
     * 字母变大写
     *
     * @param str 字符串
     * @return
     */
    public static <T, R> SqlFun upper(ColumnFun<T, R> str) {
        return new SqlFun("upper", new Object[]{str});
    }

    /**
     * 字母变小写
     *
     * @param str 字符串
     * @return
     */
    public static SqlFun lower(Object str) {
        return new SqlFun("lower", new Object[]{str});
    }

    /**
     * 字母变小写
     *
     * @param str 字符串
     * @return
     */
    public static <T, R> SqlFun lower(ColumnFun<T, R> str) {
        return new SqlFun("lower", new Object[]{str});
    }

    /**
     * 其中str1是第一个字符串，length是结果字符串的长度，str2是一个填充字符串。如果str1的长度没有length那么长，则使用str2往左边填充；如果str1的长度大于length，则截断
     *
     * @param str1   字符串
     * @param length 字符串
     * @param str2   字符串
     * @return
     */
    public static SqlFun lPad(Object str1, int length, Object str2) {
        return new SqlFun("lPad", new Object[]{str1, length, str2});
    }


    /**
     * 其中str1是第一个字符串，length是结果字符串的长度，str2是一个填充字符串。如果str1的长度没有length那么长，则使用str2往左边填充；如果str1的长度大于length，则截断
     *
     * @param str1   字符串
     * @param length 字符串
     * @param str2   字符串
     * @return
     */
    public static <T, R> SqlFun lPad(ColumnFun<T, R> str1, int length, Object str2) {
        return new SqlFun("lPad", new Object[]{str1, length, str2});
    }

    /**
     * 其中str1是第一个字符串，length是结果字符串的长度，str2是一个填充字符串。如果str1的长度没有length那么长，则使用str2往左边填充；如果str1的长度大于length，则截断
     *
     * @param str1   字符串
     * @param length 字符串
     * @param str2   字符串
     * @return
     */
    public static <T, R> SqlFun lPad(ColumnFun<T, R> str1, int length, ColumnFun<T, R> str2) {
        return new SqlFun("lPad", new Object[]{str1, length, str2});
    }

    /**
     * 其中str1是第一个字符串，length是结果字符串的长度，str2是一个填充字符串。如果str1的长度没有length那么长，则使用str2往右边填充；如果str1的长度大于length，则截断
     *
     * @param str1   字符串
     * @param length 字符串
     * @param str2   字符串
     * @return
     */
    public static SqlFun rPad(Object str1, int length, Object str2) {
        return new SqlFun("rPad", new Object[]{str1, length, str2});
    }

    /**
     * 其中str1是第一个字符串，length是结果字符串的长度，str2是一个填充字符串。如果str1的长度没有length那么长，则使用str2往右边填充；如果str1的长度大于length，则截断
     *
     * @param str1   字符串
     * @param length 字符串
     * @param str2   字符串
     * @return
     */
    public static <T, R> SqlFun rPad(ColumnFun<T, R> str1, int length, Object str2) {
        return new SqlFun("rPad", new Object[]{str1, length, str2});
    }

    /**
     * 其中str1是第一个字符串，length是结果字符串的长度，str2是一个填充字符串。如果str1的长度没有length那么长，则使用str2往右边填充；如果str1的长度大于length，则截断
     *
     * @param str1   字符串
     * @param length 字符串
     * @param str2   字符串
     * @return
     */
    public static <T, R> SqlFun rPad(ColumnFun<T, R> str1, int length, ColumnFun<T, R> str2) {
        return new SqlFun("rPad", new Object[]{str1, length, str2});
    }

    /**
     * 清除字符左边的空格
     *
     * @param str 字符串
     * @return
     */
    public static SqlFun ltrim(Object str) {
        return new SqlFun("ltrim", new Object[]{str});
    }

    /**
     * 清除字符左边的空格
     *
     * @param str 字符串
     * @return
     */
    public static <T, R> SqlFun ltrim(ColumnFun<T, R> str) {
        return new SqlFun("ltrim", new Object[]{str});
    }

    /**
     * 清除字符右边的空格
     *
     * @param str 字符串
     * @return
     */
    public static SqlFun rtrim(Object str) {
        return new SqlFun("rtrim", new Object[]{str});
    }

    /**
     * 清除字符右边的空格
     *
     * @param str 字符串
     * @return
     */
    public static <T, R> SqlFun rtrim(ColumnFun<T, R> str) {
        return new SqlFun("rtrim", new Object[]{str});
    }


    /**
     * 把object对象中出现的的search全部替换成replace
     *
     * @param str     字符串
     * @param search  查找的字符串
     * @param replace 将要替换的新字符串
     * @return
     */
    public static SqlFun replace(Object str, Object search, Object replace) {
        return new SqlFun("replace", new Object[]{str, search, replace});
    }

    /**
     * 把object对象中出现的的search全部替换成replace
     *
     * @param str     字符串
     * @param search  查找的字符串
     * @param replace 将要替换的新字符串
     * @return
     */
    public static <T, R> SqlFun replace(ColumnFun<T, R> str, Object search, Object replace) {
        return new SqlFun("replace", new Object[]{str, search, replace});
    }

    /************Sqlserver************/

    /**
     * 查找字符对应的下标
     *
     * @param query 查找的内容
     * @param str   数据源字符串
     * @return
     */
    public static SqlFun charIndex(Object query, Object str) {
        return new SqlFun("charIndex", new Object[]{query, str});
    }

    /**
     * 查找字符对应的下标
     *
     * @param query 查找的内容
     * @param str   数据源字符串
     * @return
     */
    public static <T, R> SqlFun charIndex(Object query, ColumnFun<T, R> str) {
        return new SqlFun("charIndex", new Object[]{query, str});
    }

    /**
     * 查找字符对应的下标
     *
     * @param query 查找的内容
     * @param str   数据源字符串
     * @param index 指定位置开始查找
     * @return
     */
    public static SqlFun charIndex(Object query, Object str, int index) {
        return new SqlFun("charIndex", new Object[]{query, str, index});
    }

    /**
     * 查找字符对应的下标
     *
     * @param query 查找的内容
     * @param str   数据源字符串
     * @param index 指定位置开始查找
     * @return
     */
    public static <T, R> SqlFun charIndex(Object query, ColumnFun<T, R> str, int index) {
        return new SqlFun("charIndex", new Object[]{query, str, index});
    }

    /**
     * 字符串长度
     *
     * @param str 字符串
     * @return
     */
    public static SqlFun len(Object str) {
        return new SqlFun("len", new Object[]{str});
    }

    /**
     * 字符串长度
     *
     * @param str 字符串
     * @return
     */
    public static <T, R> SqlFun len(ColumnFun<T, R> str) {
        return new SqlFun("len", new Object[]{str});
    }

    /**
     * 从左边开始截取指定长度的字符串
     *
     * @param str    字符串
     * @param length 长度
     * @return
     */
    public static SqlFun left(Object str, int length) {
        return new SqlFun("left", new Object[]{str, length});
    }

    /**
     * 从左边开始截取指定长度的字符串
     *
     * @param str    字符串
     * @param length 长度
     * @return
     */
    public static <T, R> SqlFun left(ColumnFun<T, R> str, int length) {
        return new SqlFun("left", new Object[]{str, length});
    }

    /**
     * 从右边开始截取指定长度的字符串
     *
     * @param str    字符串
     * @param length 长度
     * @return
     */
    public static SqlFun right(Object str, int length) {
        return new SqlFun("right", new Object[]{str, length});
    }

    /**
     * 从右边开始截取指定长度的字符串
     *
     * @param str    字符串
     * @param length 长度
     * @return
     */
    public static <T, R> SqlFun right(ColumnFun<T, R> str, int length) {
        return new SqlFun("right", new Object[]{str, length});
    }

    /**
     * 从字符串的某个位置删除指定长度的字符串之后插入新字符串
     *
     * @param str    数据源字符串
     * @param index  开始位置
     * @param length 要删除的字符串长度
     * @param newStr 新字符串
     * @return
     */
    public static SqlFun stuff(Object str, int index, int length, Object newStr) {
        return new SqlFun("stuff", new Object[]{str, index, length, newStr});
    }

    /**
     * 从字符串的某个位置删除指定长度的字符串之后插入新字符串
     *
     * @param str    数据源字符串
     * @param index  开始位置
     * @param length 要删除的字符串长度
     * @param newStr 新字符串
     * @return
     */
    public static <T, R> SqlFun stuff(ColumnFun<T, R> str, int index, int length, Object newStr) {
        return new SqlFun("stuff", new Object[]{str, index, length, newStr});
    }

    /**
     * 当前登录的计算机名称
     *
     * @return
     */
    public static SqlFun host_name() {
        return new SqlFun("host_name", null);
    }

    /**
     * 指定用户名Id的用户名
     *
     * @return
     */
    public static SqlFun user_name(Object id) {
        return new SqlFun("user_name", new Object[]{id});
    }

    /**
     * 把日期转换为新数据类型的通用函数。
     *
     * @param type  规定目标数据类型（带有可选的长度）。
     * @param value 规定需要转换的值。
     * @return
     */
    public static SqlFun convert(Object type, Object value) {
        return new SqlFun("convert", new Object[]{type, value});
    }

    /**
     * 把日期转换为新数据类型的通用函数。
     *
     * @param type  规定目标数据类型（带有可选的长度）。
     * @param value 规定需要转换的值。
     * @return
     */
    public static <T, R> SqlFun convert(Object type, ColumnFun<T, R> value) {
        return new SqlFun("convert", new Object[]{type, value});
    }

    /**
     * 把日期转换为新数据类型的通用函数。
     *
     * @param type  规定目标数据类型（带有可选的长度）。
     * @param value 规定需要转换的值。
     * @param style 规定日期/时间的输出格式。
     * @return
     */
    public static SqlFun convert(Object type, Object value, Object style) {
        return new SqlFun("convert", new Object[]{type, value, style});
    }

    /**
     * 把日期转换为新数据类型的通用函数。
     *
     * @param type  规定目标数据类型（带有可选的长度）。
     * @param value 规定需要转换的值。
     * @param style 规定日期/时间的输出格式。
     * @return
     */
    public static <T, R> SqlFun convert(Object type, ColumnFun<T, R> value, Object style) {
        return new SqlFun("convert", new Object[]{type, value, style});
    }

    /**
     * 返回表达式的长度（以字节为单位）
     *
     * @param value 返回长度的数据类型。
     * @return
     */
    public static SqlFun dataLength(Object value) {
        return new SqlFun("dataLength", new Object[]{value});
    }

    /**
     * 返回表达式的长度（以字节为单位）
     *
     * @param value 返回长度的数据类型。
     * @return
     */
    public static <T, R> SqlFun dataLength(ColumnFun<T, R> value) {
        return new SqlFun("dataLength", new Object[]{value});
    }

    /**
     * 获取当前系统日期
     *
     * @return
     */
    public static SqlFun getDate() {
        return new SqlFun("getDate", null);
    }

    /**
     * 添加指定日期后的日期(SqlServer)
     *
     * @param datePart 年份(yy . yyyy . year)、季度(qq , q . quarter)、月份(mm , m , month)、年中的日(dy. y)、日(dd ,d ,day)、周(wk ,ww , week)、星期(dw.w)、小时(hh , hour)、分钟(mi , n . minute)、秒(ss , s , second)、毫秒(ms)、微秒(mcs)、纳秒(ns)
     * @param num      添加的间隔数，最好是整数，对于未来的时间，此数是正数，对于过去的时间，此数是负数
     * @param date     合法的日期表达式，类型可以是datetime、smalldatetime、char
     * @return
     */
    public static SqlFun dateAdd(Object datePart, int num, Object date) {
        return new SqlFun("dateAdd", new Object[]{datePart, num, date});
    }

    /**
     * 添加指定日期后的日期(SqlServer)
     *
     * @param datePart 年份(yy . yyyy . year)、季度(qq , q . quarter)、月份(mm , m , month)、年中的日(dy. y)、日(dd ,d ,day)、周(wk ,ww , week)、星期(dw.w)、小时(hh , hour)、分钟(mi , n . minute)、秒(ss , s , second)、毫秒(ms)、微秒(mcs)、纳秒(ns)
     * @param num      添加的间隔数，最好是整数，对于未来的时间，此数是正数，对于过去的时间，此数是负数
     * @param date     合法的日期表达式，类型可以是datetime、smalldatetime、char
     * @return
     */
    public static <T, R> SqlFun dateAdd(Object datePart, int num, ColumnFun<T, R> date) {
        return new SqlFun("dateAdd", new Object[]{datePart, num, date});
    }

    /**
     * 获取时差
     *
     * @param datePart  年份(yy . yyyy . year)、季度(qq , q . quarter)、月份(mm , m , month)、年中的日(dy. y)、日(dd ,d ,day)、周(wk ,ww , week)、星期(dw.w)、小时(hh , hour)、分钟(mi , n . minute)、秒(ss , s , second)、毫秒(ms)、微秒(mcs)、纳秒(ns)
     * @param startDate 开始时间,合法的日期表达式，类型可以是datetime、smalldatetime、char
     * @param endDate   结束时间,合法的日期表达式，类型可以是datetime、smalldatetime、char
     * @return
     */
    public static SqlFun dateDiff(Object datePart, Object startDate, Object endDate) {
        return new SqlFun("dateDiff", new Object[]{datePart, startDate, endDate});
    }

    /**
     * 获取时差
     *
     * @param datePart  年份(yy . yyyy . year)、季度(qq , q . quarter)、月份(mm , m , month)、年中的日(dy. y)、日(dd ,d ,day)、周(wk ,ww , week)、星期(dw.w)、小时(hh , hour)、分钟(mi , n . minute)、秒(ss , s , second)、毫秒(ms)、微秒(mcs)、纳秒(ns)
     * @param startDate 开始时间,合法的日期表达式，类型可以是datetime、smalldatetime、char
     * @param endDate   结束时间,合法的日期表达式，类型可以是datetime、smalldatetime、char
     * @return
     */
    public static <T, R> SqlFun dateDiff(Object datePart, ColumnFun<T, R> startDate, Object endDate) {
        return new SqlFun("dateDiff", new Object[]{datePart, startDate, endDate});
    }

    /**
     * 获取时差
     *
     * @param datePart  年份(yy . yyyy . year)、季度(qq , q . quarter)、月份(mm , m , month)、年中的日(dy. y)、日(dd ,d ,day)、周(wk ,ww , week)、星期(dw.w)、小时(hh , hour)、分钟(mi , n . minute)、秒(ss , s , second)、毫秒(ms)、微秒(mcs)、纳秒(ns)
     * @param startDate 开始时间,合法的日期表达式，类型可以是datetime、smalldatetime、char
     * @param endDate   结束时间,合法的日期表达式，类型可以是datetime、smalldatetime、char
     * @return
     */
    public static <T, R> SqlFun dateDiff(Object datePart, ColumnFun<T, R> startDate, ColumnFun<T, R> endDate) {
        return new SqlFun("dateDiff", new Object[]{datePart, startDate, endDate});
    }

    /**
     * 获取指定日期部分的字符串形式
     *
     * @param datePart 年份(yy . yyyy . year)、季度(qq , q . quarter)、月份(mm , m , month)、年中的日(dy. y)、日(dd ,d ,day)、周(wk ,ww , week)、星期(dw.w)、小时(hh , hour)、分钟(mi , n . minute)、秒(ss , s , second)、毫秒(ms)、微秒(mcs)、纳秒(ns)
     * @param date     合法的日期表达式，类型可以是datetime、smalldatetime、char
     * @return
     */
    public static SqlFun dateName(Object datePart, Object date) {
        return new SqlFun("dateName", new Object[]{datePart, date});
    }

    /**
     * 获取指定日期部分的字符串形式
     *
     * @param datePart 年份(yy . yyyy . year)、季度(qq , q . quarter)、月份(mm , m , month)、年中的日(dy. y)、日(dd ,d ,day)、周(wk ,ww , week)、星期(dw.w)、小时(hh , hour)、分钟(mi , n . minute)、秒(ss , s , second)、毫秒(ms)、微秒(mcs)、纳秒(ns)
     * @param date     合法的日期表达式，类型可以是datetime、smalldatetime、char
     * @return
     */
    public static <T, R> SqlFun dateName(Object datePart, ColumnFun<T, R> date) {
        return new SqlFun("dateName", new Object[]{datePart, date});
    }

    /**
     * 获取指定日期部分的整数形式
     *
     * @param datePart 年份(yy . yyyy . year)、季度(qq , q . quarter)、月份(mm , m , month)、年中的日(dy. y)、日(dd ,d ,day)、周(wk ,ww , week)、星期(dw.w)、小时(hh , hour)、分钟(mi , n . minute)、秒(ss , s , second)、毫秒(ms)、微秒(mcs)、纳秒(ns)
     * @param date     合法的日期表达式，类型可以是datetime、smalldatetime、char
     * @return
     */
    public static SqlFun datePart(Object datePart, Object date) {
        return new SqlFun("datePart", new Object[]{datePart, date});
    }

    /**
     * 获取指定日期部分的整数形式
     *
     * @param datePart 年份(yy . yyyy . year)、季度(qq , q . quarter)、月份(mm , m , month)、年中的日(dy. y)、日(dd ,d ,day)、周(wk ,ww , week)、星期(dw.w)、小时(hh , hour)、分钟(mi , n . minute)、秒(ss , s , second)、毫秒(ms)、微秒(mcs)、纳秒(ns)
     * @param date     合法的日期表达式，类型可以是datetime、smalldatetime、char
     * @return
     */
    public static <T, R> SqlFun datePart(Object datePart, ColumnFun<T, R> date) {
        return new SqlFun("datePart", new Object[]{datePart, date});
    }

    /**
     * 四舍五入，保留两位小数
     *
     * @param num 数值
     * @return
     */
    public static SqlFun round(Object num) {
        return new SqlFun("round", new Object[]{num});
    }

    /**
     * 四舍五入，保留两位小数
     *
     * @param num 数值
     * @return
     */
    public static <T, R> SqlFun round(ColumnFun<T, R> num) {
        return new SqlFun("round", new Object[]{num});
    }

    /**
     * 向上取整
     *
     * @param num 数值
     * @return
     */
    public static SqlFun ceil(Object num) {
        return new SqlFun("ceil", new Object[]{num});
    }

    /**
     * 向上取整
     *
     * @param num 数值
     * @return
     */
    public static <T, R> SqlFun ceil(ColumnFun<T, R> num) {
        return new SqlFun("ceil", new Object[]{num});
    }

    /**
     * 向上取整
     *
     * @param num 数值
     * @return
     */
    public static SqlFun ceiling(Object num) {
        return new SqlFun("ceiling", new Object[]{num});
    }

    /**
     * 向上取整
     *
     * @param num 数值
     * @return
     */
    public static <T, R> SqlFun ceiling(ColumnFun<T, R> num) {
        return new SqlFun("ceiling", new Object[]{num});
    }

    /**
     * 向下取整
     *
     * @param num 数值
     * @return
     */
    public static SqlFun floor(Object num) {
        return new SqlFun("floor", new Object[]{num});
    }

    /**
     * 向下取整
     *
     * @param num 数值
     * @return
     */
    public static <T, R> SqlFun floor(ColumnFun<T, R> num) {
        return new SqlFun("floor", new Object[]{num});
    }

    /**
     * 保留几位小数
     *
     * @param num    数值
     * @param length 保留的小数长度
     * @return
     */
    public static SqlFun truncate(Object num, int length) {
        return new SqlFun("truncate", new Object[]{num, length});
    }

    /**
     * 保留几位小数
     *
     * @param num    数值
     * @param length 保留的小数长度
     * @return
     */
    public static <T, R> SqlFun truncate(ColumnFun<T, R> num, int length) {
        return new SqlFun("truncate", new Object[]{num, length});
    }

    /**
     * 求余数 num % 2
     *
     * @param num1 数值
     * @param num2 被%数
     * @return
     */
    public static SqlFun mod(Object num1, Object num2) {
        return new SqlFun("mod", new Object[]{num1, num2});
    }

    /**
     * 求余数 num % 2
     *
     * @param num1 数值
     * @param num2 被%数
     * @return
     */
    public static <T, R> SqlFun mod(ColumnFun<T, R> num1, Object num2) {
        return new SqlFun("mod", new Object[]{num1, num2});
    }

    /**
     * 返回符号或0
     *
     * @param num 数值
     * @return
     */
    public static SqlFun sign(Object num) {
        return new SqlFun("sign", new Object[]{num});
    }

    /**
     * 返回符号或0
     *
     * @param num 数值
     * @return
     */
    public static <T, R> SqlFun sign(ColumnFun<T, R> num) {
        return new SqlFun("sign", new Object[]{num});
    }

    /**
     * 返回平方根
     *
     * @param num 数值
     * @return
     */
    public static SqlFun sqrt(Object num) {
        return new SqlFun("sqrt", new Object[]{num});
    }

    /**
     * 返回平方根
     *
     * @param num 数值
     * @return
     */
    public static <T, R> SqlFun sqrt(ColumnFun<T, R> num) {
        return new SqlFun("sqrt", new Object[]{num});
    }

    /**
     * 获取随机数
     *
     * @return
     */
    public static SqlFun rand() {
        return new SqlFun("rand", null);
    }

    // ============================================================
    //  方言支持元数据（DialectSupport）
    // ============================================================

    /**
     * 当前函数的方言支持元数据。{@code null} = 通用 SQL（所有方言通过校验）。
     */
    public DialectSupport getDialectSupport() {
        return dialectSupport;
    }

    /**
     * 标注方言支持（工厂方法内部使用 / 用户自定义函数）。返回 this 便于链式。
     *
     * <pre>
     *   SqlFun.userDefined = new SqlFun("MY_FUNC", args);
     *   userDefined.dialect(DialectSupport.builder()
     *           .support(DbType.MySQL, DbVersion.from(5, 0))
     *           .build());
     * </pre>
     */
    public SqlFun dialect(DialectSupport support) {
        this.dialectSupport = support;
        return this;
    }

    /**
     * 用户扩展入口：给自定义函数标注方言支持。
     *
     * @param f       函数实例
     * @param support 方言支持元数据
     * @return 同 {@code f}（便于链式）
     */
    public static SqlFun dialect(SqlFun f, DialectSupport support) {
        return f.dialect(support);
    }

    /**
     * 校验当前函数对给定方言与版本是否兼容。
     * <p><b>不缓存结果</b> —— 同一个 SqlFun 实例可能在不同 SQL 上下文对应不同方言
     * （多数据源场景）。校验本身是常数级查表（{@code DialectSupport.supports} 为 O(1)
     * HashMap 查询），可忽略。</p>
     *
     * <p>返回值：
     * <ul>
     *   <li>{@code null}：方言兼容通过</li>
     *   <li>非空字符串：不兼容的清晰错误说明（可直接抛 {@link cn.vonce.sql.exception.UnsupportedDialectException}）</li>
     * </ul>
     *
     * @param sqlBeanMeta 当前数据库方言/版本元数据。{@code null} 表示未连接任何方言（放行）
     */
    public String checkDialect(cn.vonce.sql.config.SqlBeanMeta sqlBeanMeta) {
        if (dialectSupport == null || dialectSupport.isUniversal()) {
            return null;   // 通用 SQL，永远通过
        }
        if (sqlBeanMeta == null) {
            return null;   // 没有元数据，放行
        }
        cn.vonce.sql.enumerate.DbType dbType = sqlBeanMeta.getDbType();
        DbVersion version = (sqlBeanMeta.getDatabaseMajorVersion() == 0 && sqlBeanMeta.getDatabaseMinorVersion() == 0)
                ? null
                : DbVersion.from(sqlBeanMeta.getDatabaseMajorVersion(), sqlBeanMeta.getDatabaseMinorVersion());
        if (dialectSupport.supports(dbType, version)) {
            return null;
        }
        // 返回非 null 标识失败。renderer 端 STRICT 模式抛异常；WARN 模式输出日志。
        return this.funName;
    }

    /**
     * 内部使用：构造 {@link cn.vonce.sql.exception.UnsupportedDialectException} 用于异常抛出。
     */
    public cn.vonce.sql.exception.UnsupportedDialectException buildException(cn.vonce.sql.config.SqlBeanMeta sqlBeanMeta) {
        cn.vonce.sql.enumerate.DbType dbType = sqlBeanMeta.getDbType();
        DbVersion version = (sqlBeanMeta.getDatabaseMajorVersion() == 0 && sqlBeanMeta.getDatabaseMinorVersion() == 0)
                ? null
                : DbVersion.from(sqlBeanMeta.getDatabaseMajorVersion(), sqlBeanMeta.getDatabaseMinorVersion());
        return new cn.vonce.sql.exception.UnsupportedDialectException(
                this.funName,
                dbType,
                version,
                dialectSupport.getSupported(),
                dialectSupport.getUnsupported());
    }

    // ============================================================
    //  P0 函数补强（2026-09-06）—— trim / coalesce / ifNull / nvl / nullIf /
    //                            countDistinct / dateFormat / dateTrunc /
    //                            groupConcat / greatest / least
    //  详见 doc/CACHE.md 与 SqlFunDialectTest
    // ============================================================

    // ---------------- A. SQL 标准函数（无需标注 = 通用） ----------------

    /**
     * 去前后空格（标准 SQL TRIM，全方言支持）。
     * <p>MySQL: TRIM('  x  ')='x' / PG / Oracle / SQL Server 同；SQLite 用 RTRIM(LTRIM())。</p>
     */
    public static SqlFun trim(Object str) {
        return new SqlFun("trim", new Object[]{str});
    }

    public static <T, R> SqlFun trim(ColumnFun<T, R> str) {
        return new SqlFun("trim", new Object[]{str});
    }

    /**
     * 返回第一个非空值（SQL 标准 COALESCE，全方言支持）。
     * <p>MySQL: COALESCE(a, b, c) / PG / Oracle / SQL Server 同 / SQLite 同。</p>
     */
    public static SqlFun coalesce(Object... values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("coalesce 至少需要一个参数");
        }
        return new SqlFun("coalesce", values);
    }

    /**
     * 空值替换（SQL 标准 IFNULL；Oracle 旧版叫 NVL）。
     * <p>MySQL / SQLite / H2 / HSQL / Derby / DB2: IFNULL(c, v)。Oracle 9i+: NVL；PG: COALESCE 兜底。
     * 推荐 PG/Oracle 用户用 {@link #nvl} 别名。</p>
     */
    public static SqlFun ifNull(Object value, Object defaultValue) {
        return new SqlFun("ifnull", new Object[]{value, defaultValue});
    }

    public static <T, R> SqlFun ifNull(ColumnFun<T, R> value, Object defaultValue) {
        return new SqlFun("ifnull", new Object[]{value, defaultValue});
    }

    /**
     * 空值替换（Oracle NVL 别名；其它方言内部等价于 IFNULL）。
     */
    public static SqlFun nvl(Object value, Object defaultValue) {
        return new SqlFun("nvl", new Object[]{value, defaultValue});
    }

    public static <T, R> SqlFun nvl(ColumnFun<T, R> value, Object defaultValue) {
        return new SqlFun("nvl", new Object[]{value, defaultValue});
    }

    /**
     * 两值相等返回 NULL（标准 SQL，全方言支持）。
     */
    public static SqlFun nullIf(Object expr1, Object expr2) {
        return new SqlFun("nullif", new Object[]{expr1, expr2});
    }

    public static <T, R> SqlFun nullIf(ColumnFun<T, R> expr1, Object expr2) {
        return new SqlFun("nullif", new Object[]{expr1, expr2});
    }

    /**
     * 去重计数（SQL 标准 COUNT(DISTINCT c)，全方言支持）。
     */
    public static SqlFun countDistinct(Object value) {
        return new SqlFun("count_distinct", new Object[]{value});
    }

    public static <T, R> SqlFun countDistinct(ColumnFun<T, R> value) {
        return new SqlFun("count_distinct", new Object[]{value});
    }

    // ---------------- B. 跨方言函数（带 dialectSupport 标注） ----------------

    /**
     * 日期格式化（方言差异：MySQL {@code DATE_FORMAT} / SQLite {@code STRFTIME（参数顺序反向）}）。
     * <p><b>注意</b>：{@code fmt} 格式串方言特有 —— MySQL 用 {@code '%Y-%m-%d'}，PG/Oracle 用 {@code 'YYYY-MM-DD'}，
     * SQL Server 用 {@code 'yyyy-MM-dd'}。Oracle / SQL Server / PostgreSQL 用户请改用
     * {@link cn.vonce.sql.bean.RawValue} 自写方言 SQL。</p>
     *
     * <p>框架标注：本 API 仅对 MySQL / SQLite 显式登记为支持（其它方言抛清晰异常）。
     * 若需要 PG/Oracle 等价，请直接拷工厂方法照搬 TO_CHAR 模板。</p>
     */
    public static SqlFun dateFormat(Object col, String format) {
        SqlFun f = new SqlFun("date_format", new Object[]{col, format});
        f.dialect(DialectSupport.builder()
                .support(cn.vonce.sql.enumerate.DbType.MySQL, DbVersion.from(5, 0))
                .support(cn.vonce.sql.enumerate.DbType.MariaDB, DbVersion.from(5, 0))
                .unsupport(cn.vonce.sql.enumerate.DbType.Oracle)
                .unsupport(cn.vonce.sql.enumerate.DbType.SQLServer)
                .unsupport(cn.vonce.sql.enumerate.DbType.Postgresql)
                .unsupport(cn.vonce.sql.enumerate.DbType.DB2)
                .unsupport(cn.vonce.sql.enumerate.DbType.Derby)
                .unsupport(cn.vonce.sql.enumerate.DbType.H2)
                .unsupport(cn.vonce.sql.enumerate.DbType.Hsql)
                .build());
        return f;
    }

    /**
     * 日期截断到指定精度（方言差异）。
     * <p>参数语义：{@code unit} 表示精度（如 {@code 'day'} / {@code 'month'} / {@code 'year'}）；
     * MySQL 5.6 之前的版本不支持 DATE_TRUNC。
     * PG/Oracle/SQL Server 用户请改用 {@link cn.vonce.sql.bean.RawValue}。</p>
     */
    public static SqlFun dateTrunc(Object col, String unit) {
        SqlFun f = new SqlFun("date_trunc", new Object[]{col, unit});
        f.dialect(DialectSupport.builder()
                .support(cn.vonce.sql.enumerate.DbType.Postgresql, DbVersion.from(9, 0))
                .support(cn.vonce.sql.enumerate.DbType.MySQL, DbVersion.from(8, 0))
                .support(cn.vonce.sql.enumerate.DbType.MariaDB, DbVersion.from(10, 0))
                .unsupport(cn.vonce.sql.enumerate.DbType.Oracle)
                .unsupport(cn.vonce.sql.enumerate.DbType.SQLServer)
                .unsupport(cn.vonce.sql.enumerate.DbType.DB2)
                .unsupport(cn.vonce.sql.enumerate.DbType.Derby)
                .unsupport(cn.vonce.sql.enumerate.DbType.H2)
                .unsupport(cn.vonce.sql.enumerate.DbType.Hsql)
                .unsupport(cn.vonce.sql.enumerate.DbType.SQLite)
                .build());
        return f;
    }

    /**
     * 行内字符串聚合（方言差异）。
     * <p>MySQL 用 {@code GROUP_CONCAT} / PG 与 SQL Server 用 {@code STRING_AGG} / Oracle 用 {@code LISTAGG}。
     * 函数名与分隔符调用形式各异；本 API 仅登记为 MySQL 支持，跨方言用户改用 RawValue。</p>
     */
    public static SqlFun groupConcat(Object col) {
        SqlFun f = new SqlFun("group_concat", new Object[]{col});
        f.dialect(DialectSupport.builder()
                .support(cn.vonce.sql.enumerate.DbType.MySQL, DbVersion.from(5, 0))
                .support(cn.vonce.sql.enumerate.DbType.MariaDB, DbVersion.from(5, 0))
                .support(cn.vonce.sql.enumerate.DbType.SQLite, DbVersion.from(3, 0))
                .unsupport(cn.vonce.sql.enumerate.DbType.Oracle)
                .unsupport(cn.vonce.sql.enumerate.DbType.SQLServer)
                .unsupport(cn.vonce.sql.enumerate.DbType.Postgresql)
                .unsupport(cn.vonce.sql.enumerate.DbType.DB2)
                .unsupport(cn.vonce.sql.enumerate.DbType.Derby)
                .unsupport(cn.vonce.sql.enumerate.DbType.H2)
                .unsupport(cn.vonce.sql.enumerate.DbType.Hsql)
                .build());
        return f;
    }

    /**
     * 多值取最大（标准 SQL GREATEST，MySQL 8.0+ 起支持）。
     * <p>Oracle / PG / SQL Server / SQLite / DB2 / Derby 长期支持。MySQL 8.0 之前需用 {@code IF(...)} 链模拟。</p>
     */
    public static SqlFun greatest(Object... values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("greatest 至少需要一个参数");
        }
        SqlFun f = new SqlFun("greatest", values);
        f.dialect(DialectSupport.builder()
                .support(cn.vonce.sql.enumerate.DbType.Oracle)
                .support(cn.vonce.sql.enumerate.DbType.Postgresql)
                .support(cn.vonce.sql.enumerate.DbType.SQLServer, DbVersion.from(2008, 0))
                .support(cn.vonce.sql.enumerate.DbType.DB2)
                .support(cn.vonce.sql.enumerate.DbType.Derby)
                .support(cn.vonce.sql.enumerate.DbType.H2)
                .support(cn.vonce.sql.enumerate.DbType.Hsql)
                .support(cn.vonce.sql.enumerate.DbType.SQLite, DbVersion.from(3, 0))
                .support(cn.vonce.sql.enumerate.DbType.MySQL, DbVersion.from(8, 0))
                .support(cn.vonce.sql.enumerate.DbType.MariaDB, DbVersion.from(10, 3))
                .build());
        return f;
    }

    /**
     * 多值取最小（同 {@link #greatest} 兼容性）。
     */
    public static SqlFun least(Object... values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("least 至少需要一个参数");
        }
        SqlFun f = new SqlFun("least", values);
        f.dialect(DialectSupport.builder()
                .support(cn.vonce.sql.enumerate.DbType.Oracle)
                .support(cn.vonce.sql.enumerate.DbType.Postgresql)
                .support(cn.vonce.sql.enumerate.DbType.SQLServer, DbVersion.from(2008, 0))
                .support(cn.vonce.sql.enumerate.DbType.DB2)
                .support(cn.vonce.sql.enumerate.DbType.Derby)
                .support(cn.vonce.sql.enumerate.DbType.H2)
                .support(cn.vonce.sql.enumerate.DbType.Hsql)
                .support(cn.vonce.sql.enumerate.DbType.SQLite, DbVersion.from(3, 0))
                .support(cn.vonce.sql.enumerate.DbType.MySQL, DbVersion.from(8, 0))
                .support(cn.vonce.sql.enumerate.DbType.MariaDB, DbVersion.from(10, 3))
                .build());
        return f;
    }

}
