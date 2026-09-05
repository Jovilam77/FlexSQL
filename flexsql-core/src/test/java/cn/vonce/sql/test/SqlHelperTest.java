package cn.vonce.sql.test;

import cn.vonce.sql.bean.*;
import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.define.SqlFun;
import cn.vonce.sql.enumerate.*;
import cn.vonce.sql.helper.Cond;
import cn.vonce.sql.helper.SqlHelper;
import cn.vonce.sql.helper.Wrapper;
import cn.vonce.sql.model.Essay;
import cn.vonce.sql.model.User;
import cn.vonce.sql.model.union.EssayUnion;
import cn.vonce.sql.uitls.SqlBeanUtil;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * sql语句生成测试
 * 测试中写的sql，可能某些字段的条件写的不符合现实逻辑，不要在意，只是为了测试语法支持。
 * 实例仅供参考，可自由发挥写出符合自己业务需要的sql语句
 */
public class SqlHelperTest {

    public static void main(String[] args) throws InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException {
        SqlBeanMeta sqlBeanMeta = new SqlBeanMeta();
        SqlBeanConfig sqlBeanConfig = new SqlBeanConfig();
        sqlBeanConfig.setToUpperCase(false);
        sqlBeanMeta.setDbType(DbType.MySQL);
        sqlBeanMeta.setSqlBeanConfig(sqlBeanConfig);

        long startTime = System.currentTimeMillis();

        // select1
        select1(sqlBeanMeta);

        // select2
        select2(sqlBeanMeta);

        // select3
        select3(sqlBeanMeta);

        // selectLock（行锁 FOR UPDATE / SKIP LOCKED）
        selectLock();
//
//        // select4
//        select4(sqlBeanMeta);
//
//        // select5
//        select5(sqlBeanMeta);
//
//        // insert1
//        insert1(sqlBeanMeta);
//
//        // insert2
//        insert2(sqlBeanMeta);
//
//        // update
//        update(sqlBeanMeta);
//
//        // update2
//        update2(sqlBeanMeta);
//
//        // delete
//        delete(sqlBeanMeta);

        float excTime = (float) (System.currentTimeMillis() - startTime) / 1000;
        System.out.println("耗时：" + excTime + "秒");
    }

    /**
     * 查询1
     *
     * @param sqlBeanMeta
     */
    private static void select1(SqlBeanMeta sqlBeanMeta) {
        Select select = new Select();
        select.setSqlBeanMeta(sqlBeanMeta);
        select.setBeanClass(Essay.class);
        select.column(Essay::getClass);
        select.column(User::getHeadPortrait, "头像");
        select.column(User::getNickname, "昵称");
        select.setTable(Essay.class);
        select.innerJoin(User.class).on(User::getId, Essay::getUserId);
        select.where().eq(Essay::getUserId, "1111").or(condition -> condition.eq(User::getNickname, "vicky").and(condition1 -> {
            condition1.eq(User::getHeadPortrait, "2222").or().eq(User::getHeadPortrait, "3333");
        }));
        //value 直接输入字符串 会当作字符串处理，sql中会带''，如果希望不被做处理则使用Original
        select.where().eq(SqlFun.date_format(Essay::getCreationTime, "%Y-%m-%d"), SqlFun.date_format(SqlFun.now(), "%Y-%m-%d"));
        select.orderByDesc(Essay::getId);
        System.out.println("---select1---");
        System.out.println(SqlHelper.buildSelectSql(select));
    }

    /**
     * 查询2
     *
     * @param sqlBeanMeta
     */
    private static void select2(SqlBeanMeta sqlBeanMeta) {
        Select select2 = new Select();
        select2.setSqlBeanMeta(sqlBeanMeta);
        select2.setBeanClass(Essay.class);
        select2.column(Essay::getId, "序号")
                .column(Essay::getContent, "文章内容")
                .column(Essay::getCreationTime, "创建时间")
                .column(User::getNickname, "用户昵称");
        select2.setTable(Essay.class);
//        select2.join(JoinType.INNER_JOIN, SqlUser._tableAlias, SqlUser.id.getName(), Essay.userId.getName());
//        select2.innerJoin(User.class).on(SqlUser.id$, Essay.userId$);
        select2.innerJoin(User.class).on().eq(User::getId, Essay::getUserId).and().gt(User::getId, 1);
        select2.where().gt(SqlFun.date_format(Essay::getCreationTime, "%Y-%m-%d"), "2020-01-01").and().eq(User::getNickname, "vicky");
        System.out.println("---select2---");
        System.out.println(SqlHelper.buildSelectSql(select2));
    }


    /**
     * 查询3
     *
     * @param sqlBeanMeta
     */
    private static void select3(SqlBeanMeta sqlBeanMeta) throws InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException {
        Select select3 = new Select();
        select3.setSqlBeanMeta(sqlBeanMeta);
        select3.setBeanClass(Essay.class);
        select3.column(SqlFun.count(Essay::getId), "count")
                .column(Essay::getCategoryId);
        select3.setTable(Essay.class);
        SqlBeanUtil.setJoin(select3, EssayUnion.class);
        select3.groupBy(Essay::getCategoryId);
        select3.having().eq("count", 5);
        System.out.println("---select3---");
        System.out.println(SqlHelper.buildSelectSql(select3));
    }

    /**
     * 行锁查询（FOR UPDATE / FOR UPDATE SKIP LOCKED）
     *
     * @param
     */
    private static void selectLock() {
        // MySQL 8.0
        SqlBeanMeta mysql8 = new SqlBeanMeta();
        mysql8.setDbType(DbType.MySQL);
        mysql8.setDatabaseMajorVersion(8);
        mysql8.setDatabaseMinorVersion(0);
        mysql8.setSqlBeanConfig(new SqlBeanConfig());

        // MySQL 5.7（不支持 SKIP LOCKED）
        SqlBeanMeta mysql57 = new SqlBeanMeta();
        mysql57.setDbType(DbType.MySQL);
        mysql57.setDatabaseMajorVersion(5);
        mysql57.setDatabaseMinorVersion(7);
        mysql57.setSqlBeanConfig(new SqlBeanConfig());

        // MySQL 未探测版本（major=0，按已支持处理）
        SqlBeanMeta mysqlUnknown = new SqlBeanMeta();
        mysqlUnknown.setDbType(DbType.MySQL);
        mysqlUnknown.setSqlBeanConfig(new SqlBeanConfig());

        // PostgreSQL 13
        SqlBeanMeta pg13 = new SqlBeanMeta();
        pg13.setDbType(DbType.Postgresql);
        pg13.setDatabaseMajorVersion(13);
        pg13.setDatabaseMinorVersion(0);
        pg13.setSqlBeanConfig(new SqlBeanConfig());

        // PostgreSQL 9.4（不支持 SKIP LOCKED）
        SqlBeanMeta pg94 = new SqlBeanMeta();
        pg94.setDbType(DbType.Postgresql);
        pg94.setDatabaseMajorVersion(9);
        pg94.setDatabaseMinorVersion(4);
        pg94.setSqlBeanConfig(new SqlBeanConfig());

        // Oracle 19c
        SqlBeanMeta oracle19 = new SqlBeanMeta();
        oracle19.setDbType(DbType.Oracle);
        oracle19.setDatabaseMajorVersion(19);
        oracle19.setDatabaseMinorVersion(0);
        oracle19.setSqlBeanConfig(new SqlBeanConfig());

        // Oracle 10g（不支持 SKIP LOCKED）
        SqlBeanMeta oracle10 = new SqlBeanMeta();
        oracle10.setDbType(DbType.Oracle);
        oracle10.setDatabaseMajorVersion(10);
        oracle10.setDatabaseMinorVersion(2);
        oracle10.setSqlBeanConfig(new SqlBeanConfig());

        // 1) MySQL 8.0 + SKIP LOCKED + 分页
        Select s1 = new Select();
        s1.setSqlBeanMeta(mysql8);
        s1.setBeanClass(User.class);
        s1.column(User::getId);
        s1.setTable(User.class);
        s1.where().eq(User::getUsername, "jovi");
        s1.orderByDesc(User::getId);
        s1.page(0, 10);
        s1.forUpdateSkipLocked();
        System.out.println("---selectLock MySQL8 SKIP LOCKED + page---");
        System.out.println(SqlHelper.buildSelectSql(s1));

        // 2) MySQL 5.7 + SKIP LOCKED（应忽略并告警，无锁子句）
        Select s2 = new Select();
        s2.setSqlBeanMeta(mysql57);
        s2.setBeanClass(User.class);
        s2.column(User::getId);
        s2.setTable(User.class);
        s2.where().eq(User::getUsername, "jovi");
        s2.forUpdateSkipLocked();
        System.out.println("---selectLock MySQL5.7 SKIP LOCKED（应无锁）---");
        System.out.println(SqlHelper.buildSelectSql(s2));

        // 3) MySQL 未知版本 + SKIP LOCKED（按已支持处理）
        Select s3 = new Select();
        s3.setSqlBeanMeta(mysqlUnknown);
        s3.setBeanClass(User.class);
        s3.column(User::getId);
        s3.setTable(User.class);
        s3.forUpdateSkipLocked();
        System.out.println("---selectLock MySQL未知版本 SKIP LOCKED---");
        System.out.println(SqlHelper.buildSelectSql(s3));

        // 4) MySQL 8.0 + 普通 FOR UPDATE
        Select s4 = new Select();
        s4.setSqlBeanMeta(mysql8);
        s4.setBeanClass(User.class);
        s4.column(User::getId);
        s4.setTable(User.class);
        s4.forUpdate();
        System.out.println("---selectLock MySQL8 FOR UPDATE---");
        System.out.println(SqlHelper.buildSelectSql(s4));

        // 5) PostgreSQL 13 + SKIP LOCKED
        Select s5 = new Select();
        s5.setSqlBeanMeta(pg13);
        s5.setBeanClass(User.class);
        s5.column(User::getId);
        s5.setTable(User.class);
        s5.orderByDesc(User::getId);
        s5.page(0, 10);
        s5.forUpdateSkipLocked();
        System.out.println("---selectLock PG13 SKIP LOCKED + page---");
        System.out.println(SqlHelper.buildSelectSql(s5));

        // 6) PostgreSQL 9.4 + SKIP LOCKED（应忽略并告警，无锁子句）
        Select s6 = new Select();
        s6.setSqlBeanMeta(pg94);
        s6.setBeanClass(User.class);
        s6.column(User::getId);
        s6.setTable(User.class);
        s6.forUpdateSkipLocked();
        System.out.println("---selectLock PG9.4 SKIP LOCKED（应无锁）---");
        System.out.println(SqlHelper.buildSelectSql(s6));

        // 7) Oracle 19c + SKIP LOCKED + 分页（锁注入最内层）
        Select s7 = new Select();
        s7.setSqlBeanMeta(oracle19);
        s7.setBeanClass(User.class);
        s7.column(User::getId);
        s7.setTable(User.class);
        s7.where().eq(User::getUsername, "jovi");
        s7.orderByDesc(User::getId);
        s7.page(0, 10);
        s7.forUpdateSkipLocked();
        System.out.println("---selectLock Oracle19 SKIP LOCKED + page（锁在最内层）---");
        System.out.println(SqlHelper.buildSelectSql(s7));

        // 8) Oracle 10g + SKIP LOCKED（应忽略并告警，无锁子句）
        Select s8 = new Select();
        s8.setSqlBeanMeta(oracle10);
        s8.setBeanClass(User.class);
        s8.column(User::getId);
        s8.setTable(User.class);
        s8.forUpdateSkipLocked();
        System.out.println("---selectLock Oracle10 SKIP LOCKED（应无锁）---");
        System.out.println(SqlHelper.buildSelectSql(s8));

        // 9) Oracle 19c + 普通 FOR UPDATE（无分页，锁在末尾）
        Select s9 = new Select();
        s9.setSqlBeanMeta(oracle19);
        s9.setBeanClass(User.class);
        s9.column(User::getId);
        s9.setTable(User.class);
        s9.forUpdate();
        System.out.println("---selectLock Oracle19 FOR UPDATE（无分页）---");
        System.out.println(SqlHelper.buildSelectSql(s9));

        // 10) Oracle 19c + SKIP LOCKED（无分页，锁在末尾）
        Select s10 = new Select();
        s10.setSqlBeanMeta(oracle19);
        s10.setBeanClass(User.class);
        s10.column(User::getId);
        s10.setTable(User.class);
        s10.forUpdateSkipLocked();
        System.out.println("---selectLock Oracle19 SKIP LOCKED（无分页）---");
        System.out.println(SqlHelper.buildSelectSql(s10));

        // SQL Server 2019 元信息（UPDLOCK / READPAST 表提示全版本支持，无需版本门控）
        SqlBeanMeta sqlServer = new SqlBeanMeta();
        sqlServer.setDbType(DbType.SQLServer);
        sqlServer.setDatabaseMajorVersion(15); // SQL Server 2019
        sqlServer.setSqlBeanConfig(new SqlBeanConfig());

        // 11) SQL Server 2019 + SKIP LOCKED + 分页（提示注入到内层 FROM，即 key-preserving 位置）
        Select s11 = new Select();
        s11.setSqlBeanMeta(sqlServer);
        s11.setBeanClass(User.class);
        s11.column(User::getId);
        s11.setTable(User.class);
        s11.where().eq(User::getUsername, "jovi");
        s11.orderByDesc(User::getId);
        s11.page(0, 10);
        s11.forUpdateSkipLocked();
        System.out.println("---selectLock SQLServer2019 SKIP LOCKED + page（提示在最内层 FROM）---");
        System.out.println(SqlHelper.buildSelectSql(s11));

        // 12) SQL Server 2019 + 普通 FOR UPDATE（无分页，提示在主表名之后）
        Select s12 = new Select();
        s12.setSqlBeanMeta(sqlServer);
        s12.setBeanClass(User.class);
        s12.column(User::getId);
        s12.setTable(User.class);
        s12.where().eq(User::getUsername, "jovi");
        s12.forUpdate();
        System.out.println("---selectLock SQLServer2019 FOR UPDATE（无分页）---");
        System.out.println(SqlHelper.buildSelectSql(s12));

        // 13) SQL Server 2019 + SKIP LOCKED + JOIN（提示同时作用于主表与 join 表）
        Select s13 = new Select();
        s13.setSqlBeanMeta(sqlServer);
        s13.setBeanClass(User.class);
        s13.column(User::getId);
        s13.setTable(User.class);
        s13.join("d_role", "d_user.id = d_role.user_id");
        s13.forUpdateSkipLocked();
        System.out.println("---selectLock SQLServer2019 SKIP LOCKED + JOIN（两表均有提示）---");
        System.out.println(SqlHelper.buildSelectSql(s13));
    }

    /**
     * 查询4
     *
     * @param sqlBeanMeta
     */
    private static void select4(SqlBeanMeta sqlBeanMeta) {
        Select select4 = new Select();
        select4.setSqlBeanMeta(sqlBeanMeta);
        select4.setBeanClass(Essay.class);
        select4.column(User::getClass);
        select4.setTable(User.class);
        Integer[] gender = {0, 1};
        select4.where(
                Wrapper.where(Cond.between(User::getId, 2, 6)).
                        and(Wrapper.where(Cond.eq(User::getNickname, "vicky")).or(Cond.in(User::getGender, gender))));
        System.out.println("---select4---");
        System.out.println(SqlHelper.buildSelectSql(select4));
    }

    /**
     * 查询5
     *
     * @param sqlBeanMeta
     */
    private static void select5(SqlBeanMeta sqlBeanMeta) {
        Select select5 = new Select();
        select5.setSqlBeanMeta(sqlBeanMeta);
        select5.setBeanClass(Essay.class);
        select5.column(User::getClass);
        select5.setTable(User.class);
        select5.where(Wrapper.where(Cond.eq(User::getId, 1)).and(Wrapper.where(Cond.eq(User::getGender, "1")).or(Cond.eq(User::getNickname, 1))));
        System.out.println("---select5---");
        System.out.println(SqlHelper.buildSelectSql(select5));
    }

    /**
     * 插入1
     *
     * @param sqlBeanMeta
     */
    private static void insert1(SqlBeanMeta sqlBeanMeta) {
        Insert insert = new Insert();
        insert.setBeanClass(User.class);
        insert.setSqlBeanMeta(sqlBeanMeta);
        User user = new User();
        user.setId("10000");
        user.setUsername("10000");
        user.setNickname("麻花疼");
        user.setHeadPortrait("logo.png");
        user.setGender(0);
        insert.setBean(user);
        System.out.println("---insert1---");
        System.out.println(SqlHelper.buildInsertSql(insert));
    }

    /**
     * 插入2
     *
     * @param sqlBeanMeta
     */
    private static void insert2(SqlBeanMeta sqlBeanMeta) {
        Insert insert = new Insert();
        insert.setSqlBeanMeta(sqlBeanMeta);
        insert.setBeanClass(User.class);
        List<User> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            User user = new User();
            user.setId("id" + i);
            user.setUsername("username" + i);
            user.setNickname("nickname" + i);
            user.setHeadPortrait("logo.png");
            user.setGender(i % 2);
            list.add(user);
        }
        insert.setBean(list);
        System.out.println("---insert2---");
        System.out.println(SqlHelper.buildInsertSql(insert));
    }

    /**
     * 插入3
     *
     * @param sqlBeanMeta
     */
    private static void insert3(SqlBeanMeta sqlBeanMeta) {
        Insert<User> insert = new Insert<>();
        insert.setSqlBeanMeta(sqlBeanMeta);
        insert.setBeanClass(User.class);
        insert.column(User::getId, User::getGender, User::getNickname).values(1, 2, "Jovi").values(2, 1, "Vicky");
        System.out.println("---insert3---");
        System.out.println(SqlHelper.buildInsertSql(insert));
    }

    /**
     * 更新1
     *
     * @param sqlBeanMeta
     */
    private static void update(SqlBeanMeta sqlBeanMeta) {
        User user = new User();
        user.setHeadPortrait("logo.png");
        user.setUsername("123");
        user.setGender(1);
        Update<User> update = new Update();
        update.setTable(User.class);
        update.setSqlBeanMeta(sqlBeanMeta);
        update.filterFields("username").bean(user).notNull(true);
        update.where().gt(User::getId, 0).and().lt(User::getId, 10);
        System.out.println("---update---");
        System.out.println(SqlHelper.buildUpdateSql(update));


    }

    /**
     * 更新2
     *
     * @param sqlBeanMeta
     */
    private static void update2(SqlBeanMeta sqlBeanMeta) {
        Update<User> update = new Update();
        update.setTable(User.class);
        update.setSqlBeanMeta(sqlBeanMeta);
        update.set(User::getId, 1).
                set(User::getNickname, "jovi").
                setAdd(User::getIntegral, User::getIntegral, new RawValue(User::getIntegral)).
                setSub(User::getGender, User::getGender, 1).
                where().gt(User::getId, 0).and().lt(User::getId, 10);
        System.out.println("---update2---");
        System.out.println(SqlHelper.buildUpdateSql(update));
    }

    /**
     * 删除
     *
     * @param sqlBeanMeta
     */
    private static void delete(SqlBeanMeta sqlBeanMeta) {
        Delete delete = new Delete();
        delete.setTable(User.class);
        delete.setSqlBeanMeta(sqlBeanMeta);
        delete.where().gt(User::getId, 1).and().eq(User::getNickname, "jovi");
        System.out.println("---delete---");
        System.out.println(SqlHelper.buildDeleteSql(delete));
    }

}