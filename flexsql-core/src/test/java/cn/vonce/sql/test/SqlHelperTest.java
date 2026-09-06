package cn.vonce.sql.test;

import cn.vonce.sql.bean.*;
import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.define.SqlFun;
import cn.vonce.sql.enumerate.*;
import cn.vonce.sql.helper.Cond;
import cn.vonce.sql.helper.SqlHelper;
import cn.vonce.sql.helper.Wrapper;
import cn.vonce.sql.model.AuditBean;
import cn.vonce.sql.model.Essay;
import cn.vonce.sql.model.TenantBean;
import cn.vonce.sql.model.User;
import cn.vonce.sql.model.union.EssayUnion;
import cn.vonce.sql.provider.DynSchemaContextHolder;
import cn.vonce.sql.provider.TenantContextHolder;
import cn.vonce.sql.provider.SqlBeanProvider;
import cn.vonce.sql.uitls.SqlBeanUtil;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Date;
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

        // selectLockVariants（行锁变体 FOR SHARE / NOWAIT / OF）
        selectLockVariants();

        // selectCte（CTE / WITH 子句）
        selectCte();

        // upsertTest（UPSERT / MERGE 支持）
        upsertTest();

        // windowFunTest（窗口函数 OVER (PARTITION BY .. ORDER BY ..)）
        windowFunTest();

        // auditTest（审计自动注入：操作人注入 + 只读字段保护）
        auditInjectionTest(sqlBeanMeta);

        // dynSchemaTest（动态Schema 注入安全校验）
        dynSchemaTest();

        // tenantTest（行级多租户隔离：tenant_id 自动注入 + WHERE 强制过滤）
        tenantInjectionTest(sqlBeanMeta);
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

        // H2 2.x（FOR UPDATE / FOR SHARE / NOWAIT / SKIP LOCKED 全支持）
        SqlBeanMeta h2 = new SqlBeanMeta();
        h2.setDbType(DbType.H2);
        h2.setDatabaseMajorVersion(2);
        h2.setSqlBeanConfig(new SqlBeanConfig());

        // 14) H2 2.x + FOR UPDATE
        Select s14 = new Select();
        s14.setSqlBeanMeta(h2);
        s14.setBeanClass(User.class);
        s14.column(User::getId);
        s14.setTable(User.class);
        s14.forUpdate();
        System.out.println("---selectLock H2 FOR UPDATE---");
        System.out.println(SqlHelper.buildSelectSql(s14));

        // 15) H2 2.x + FOR UPDATE SKIP LOCKED
        Select s15 = new Select();
        s15.setSqlBeanMeta(h2);
        s15.setBeanClass(User.class);
        s15.column(User::getId);
        s15.setTable(User.class);
        s15.forUpdateSkipLocked();
        System.out.println("---selectLock H2 FOR UPDATE SKIP LOCKED---");
        System.out.println(SqlHelper.buildSelectSql(s15));

        // 16) H2 2.x + FOR SHARE NOWAIT
        Select s16 = new Select();
        s16.setSqlBeanMeta(h2);
        s16.setBeanClass(User.class);
        s16.column(User::getId);
        s16.setTable(User.class);
        s16.forShare().nowait();
        System.out.println("---selectLock H2 FOR SHARE NOWAIT---");
        System.out.println(SqlHelper.buildSelectSql(s16));

        // HSQLDB 2.3.3（仅 FOR UPDATE / FOR UPDATE NOWAIT；无 FOR SHARE / SKIP LOCKED）
        SqlBeanMeta hsql = new SqlBeanMeta();
        hsql.setDbType(DbType.Hsql);
        hsql.setDatabaseMajorVersion(2);
        hsql.setDatabaseMinorVersion(3);
        hsql.setSqlBeanConfig(new SqlBeanConfig());

        // 17) HSQLDB + FOR UPDATE
        Select s17 = new Select();
        s17.setSqlBeanMeta(hsql);
        s17.setBeanClass(User.class);
        s17.column(User::getId);
        s17.setTable(User.class);
        s17.forUpdate();
        System.out.println("---selectLock HSQLDB FOR UPDATE---");
        System.out.println(SqlHelper.buildSelectSql(s17));

        // 18) HSQLDB + FOR UPDATE NOWAIT
        Select s18 = new Select();
        s18.setSqlBeanMeta(hsql);
        s18.setBeanClass(User.class);
        s18.column(User::getId);
        s18.setTable(User.class);
        s18.forUpdate().nowait();
        System.out.println("---selectLock HSQLDB FOR UPDATE NOWAIT---");
        System.out.println(SqlHelper.buildSelectSql(s18));

        // 19) HSQLDB + FOR SHARE（应告警并忽略，无锁子句）
        Select s19 = new Select();
        s19.setSqlBeanMeta(hsql);
        s19.setBeanClass(User.class);
        s19.column(User::getId);
        s19.setTable(User.class);
        s19.forShare();
        System.out.println("---selectLock HSQLDB FOR SHARE（应无锁）---");
        System.out.println(SqlHelper.buildSelectSql(s19));

        // 20) HSQLDB + FOR UPDATE SKIP LOCKED（应告警并忽略，无锁子句）
        Select s20 = new Select();
        s20.setSqlBeanMeta(hsql);
        s20.setBeanClass(User.class);
        s20.column(User::getId);
        s20.setTable(User.class);
        s20.forUpdateSkipLocked();
        System.out.println("---selectLock HSQLDB FOR UPDATE SKIP LOCKED（应无锁）---");
        System.out.println(SqlHelper.buildSelectSql(s20));

        // DB2 11.5（FOR UPDATE；NOWAIT / SKIP LOCKED 需 11.5+，SKIP LOCKED 写作 SKIP LOCKED DATA）
        SqlBeanMeta db2 = new SqlBeanMeta();
        db2.setDbType(DbType.DB2);
        db2.setDatabaseMajorVersion(11);
        db2.setDatabaseMinorVersion(5);
        db2.setSqlBeanConfig(new SqlBeanConfig());

        // 21) DB2 + FOR UPDATE
        Select s21 = new Select();
        s21.setSqlBeanMeta(db2);
        s21.setBeanClass(User.class);
        s21.column(User::getId);
        s21.setTable(User.class);
        s21.forUpdate();
        System.out.println("---selectLock DB2 FOR UPDATE---");
        System.out.println(SqlHelper.buildSelectSql(s21));

        // 22) DB2 + FOR UPDATE SKIP LOCKED（应转写为 SKIP LOCKED DATA）
        Select s22 = new Select();
        s22.setSqlBeanMeta(db2);
        s22.setBeanClass(User.class);
        s22.column(User::getId);
        s22.setTable(User.class);
        s22.forUpdateSkipLocked();
        System.out.println("---selectLock DB2 FOR UPDATE SKIP LOCKED（-> SKIP LOCKED DATA）---");
        System.out.println(SqlHelper.buildSelectSql(s22));

        // 23) DB2 + FOR UPDATE NOWAIT
        Select s23 = new Select();
        s23.setSqlBeanMeta(db2);
        s23.setBeanClass(User.class);
        s23.column(User::getId);
        s23.setTable(User.class);
        s23.forUpdate().nowait();
        System.out.println("---selectLock DB2 FOR UPDATE NOWAIT---");
        System.out.println(SqlHelper.buildSelectSql(s23));

        // 24) DB2 + FOR SHARE（应告警并忽略，无锁子句）
        Select s24 = new Select();
        s24.setSqlBeanMeta(db2);
        s24.setBeanClass(User.class);
        s24.column(User::getId);
        s24.setTable(User.class);
        s24.forShare();
        System.out.println("---selectLock DB2 FOR SHARE（应无锁）---");
        System.out.println(SqlHelper.buildSelectSql(s24));
    }

    /**
     * 行锁变体（FOR SHARE / NOWAIT / OF）生成测试
     */
    private static void selectLockVariants() {
        // MySQL 8.0
        SqlBeanMeta mysql8 = new SqlBeanMeta();
        mysql8.setDbType(DbType.MySQL);
        mysql8.setDatabaseMajorVersion(8);
        mysql8.setDatabaseMinorVersion(0);
        mysql8.setSqlBeanConfig(new SqlBeanConfig());

        // MySQL 5.7（不支持 FOR SHARE / NOWAIT / SKIP LOCKED）
        SqlBeanMeta mysql57 = new SqlBeanMeta();
        mysql57.setDbType(DbType.MySQL);
        mysql57.setDatabaseMajorVersion(5);
        mysql57.setDatabaseMinorVersion(7);
        mysql57.setSqlBeanConfig(new SqlBeanConfig());

        // PostgreSQL 13
        SqlBeanMeta pg13 = new SqlBeanMeta();
        pg13.setDbType(DbType.Postgresql);
        pg13.setDatabaseMajorVersion(13);
        pg13.setDatabaseMinorVersion(0);
        pg13.setSqlBeanConfig(new SqlBeanConfig());

        // PostgreSQL 9.4（不支持 NOWAIT / SKIP LOCKED）
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

        // SQL Server 2019
        SqlBeanMeta sqlServer = new SqlBeanMeta();
        sqlServer.setDbType(DbType.SQLServer);
        sqlServer.setDatabaseMajorVersion(15);
        sqlServer.setSqlBeanConfig(new SqlBeanConfig());

        // 1) MySQL 8.0 + FOR SHARE
        Select v1 = new Select();
        v1.setSqlBeanMeta(mysql8);
        v1.setBeanClass(User.class);
        v1.column(User::getId);
        v1.setTable(User.class);
        v1.forShare();
        System.out.println("---variants MySQL8 FOR SHARE---");
        System.out.println(SqlHelper.buildSelectSql(v1));

        // 2) MySQL 8.0 + FOR SHARE NOWAIT（链式）
        Select v2 = new Select();
        v2.setSqlBeanMeta(mysql8);
        v2.setBeanClass(User.class);
        v2.column(User::getId);
        v2.setTable(User.class);
        v2.forShare().nowait();
        System.out.println("---variants MySQL8 FOR SHARE NOWAIT---");
        System.out.println(SqlHelper.buildSelectSql(v2));

        // 3) MySQL 8.0 + FOR UPDATE NOWAIT（链式）
        Select v3 = new Select();
        v3.setSqlBeanMeta(mysql8);
        v3.setBeanClass(User.class);
        v3.column(User::getId);
        v3.setTable(User.class);
        v3.forUpdate().nowait();
        System.out.println("---variants MySQL8 FOR UPDATE NOWAIT---");
        System.out.println(SqlHelper.buildSelectSql(v3));

        // 4) MySQL 8.0 + FOR UPDATE OF t1, t2（限定锁定表）
        Select v4 = new Select();
        v4.setSqlBeanMeta(mysql8);
        v4.setBeanClass(User.class);
        v4.column(User::getId);
        v4.setTable(User.class);
        v4.forUpdate().of("d_user", "d_role");
        System.out.println("---variants MySQL8 FOR UPDATE OF d_user, d_role---");
        System.out.println(SqlHelper.buildSelectSql(v4));

        // 5) MySQL 8.0 + FOR SHARE OF t1 SKIP LOCKED（组合）
        Select v5 = new Select();
        v5.setSqlBeanMeta(mysql8);
        v5.setBeanClass(User.class);
        v5.column(User::getId);
        v5.setTable(User.class);
        v5.forShare().of("d_user").skipLocked();
        System.out.println("---variants MySQL8 FOR SHARE OF d_user SKIP LOCKED---");
        System.out.println(SqlHelper.buildSelectSql(v5));

        // 6) MySQL 5.7 + FOR SHARE（应忽略并告警，无锁子句）
        Select v6 = new Select();
        v6.setSqlBeanMeta(mysql57);
        v6.setBeanClass(User.class);
        v6.column(User::getId);
        v6.setTable(User.class);
        v6.forShare();
        System.out.println("---variants MySQL5.7 FOR SHARE（应无锁）---");
        System.out.println(SqlHelper.buildSelectSql(v6));

        // 7) PostgreSQL 13 + FOR SHARE NOWAIT
        Select v7 = new Select();
        v7.setSqlBeanMeta(pg13);
        v7.setBeanClass(User.class);
        v7.column(User::getId);
        v7.setTable(User.class);
        v7.forShare().nowait();
        System.out.println("---variants PG13 FOR SHARE NOWAIT---");
        System.out.println(SqlHelper.buildSelectSql(v7));

        // 8) PostgreSQL 13 + FOR UPDATE OF t1, t2
        Select v8 = new Select();
        v8.setSqlBeanMeta(pg13);
        v8.setBeanClass(User.class);
        v8.column(User::getId);
        v8.setTable(User.class);
        v8.forUpdate().of("d_user", "d_role");
        System.out.println("---variants PG13 FOR UPDATE OF d_user, d_role---");
        System.out.println(SqlHelper.buildSelectSql(v8));

        // 9) PostgreSQL 9.4 + FOR UPDATE NOWAIT（应忽略并告警，无锁子句）
        Select v9 = new Select();
        v9.setSqlBeanMeta(pg94);
        v9.setBeanClass(User.class);
        v9.column(User::getId);
        v9.setTable(User.class);
        v9.forUpdate().nowait();
        System.out.println("---variants PG9.4 FOR UPDATE NOWAIT（应无锁）---");
        System.out.println(SqlHelper.buildSelectSql(v9));

        // 10) Oracle 19c + FOR UPDATE NOWAIT
        Select v10 = new Select();
        v10.setSqlBeanMeta(oracle19);
        v10.setBeanClass(User.class);
        v10.column(User::getId);
        v10.setTable(User.class);
        v10.forUpdate().nowait();
        System.out.println("---variants Oracle19 FOR UPDATE NOWAIT---");
        System.out.println(SqlHelper.buildSelectSql(v10));

        // 11) Oracle 19c + FOR SHARE（应告警并忽略，无锁子句）
        Select v11 = new Select();
        v11.setSqlBeanMeta(oracle19);
        v11.setBeanClass(User.class);
        v11.column(User::getId);
        v11.setTable(User.class);
        v11.forShare();
        System.out.println("---variants Oracle19 FOR SHARE（应无锁）---");
        System.out.println(SqlHelper.buildSelectSql(v11));

        // 12) Oracle 19c + FOR UPDATE OF t1 SKIP LOCKED（分页，锁注入最内层）
        Select v12 = new Select();
        v12.setSqlBeanMeta(oracle19);
        v12.setBeanClass(User.class);
        v12.column(User::getId);
        v12.setTable(User.class);
        v12.orderByDesc(User::getId);
        v12.page(0, 10);
        v12.forUpdate().of("d_user").skipLocked();
        System.out.println("---variants Oracle19 FOR UPDATE OF d_user SKIP LOCKED + page（锁在最内层）---");
        System.out.println(SqlHelper.buildSelectSql(v12));

        // 13) SQL Server 2019 + FOR UPDATE NOWAIT（表提示 UPDLOCK, NOWAIT）
        Select v13 = new Select();
        v13.setSqlBeanMeta(sqlServer);
        v13.setBeanClass(User.class);
        v13.column(User::getId);
        v13.setTable(User.class);
        v13.forUpdate().nowait();
        System.out.println("---variants SQLServer2019 FOR UPDATE NOWAIT（表提示 UPDLOCK, NOWAIT）---");
        System.out.println(SqlHelper.buildSelectSql(v13));

        // 14) SQL Server 2019 + FOR SHARE（映射为 UPDLOCK 表提示）
        Select v14 = new Select();
        v14.setSqlBeanMeta(sqlServer);
        v14.setBeanClass(User.class);
        v14.column(User::getId);
        v14.setTable(User.class);
        v14.forShare();
        System.out.println("---variants SQLServer2019 FOR SHARE（表提示 UPDLOCK）---");
        System.out.println(SqlHelper.buildSelectSql(v14));
    }

    /**
     * CTE（WITH 子句）生成测试
     */
    private static void selectCte() {
        // MySQL 8.0
        SqlBeanMeta mysql8 = new SqlBeanMeta();
        mysql8.setDbType(DbType.MySQL);
        mysql8.setDatabaseMajorVersion(8);
        mysql8.setDatabaseMinorVersion(0);
        mysql8.setSqlBeanConfig(new SqlBeanConfig());

        // PostgreSQL 13
        SqlBeanMeta pg13 = new SqlBeanMeta();
        pg13.setDbType(DbType.Postgresql);
        pg13.setDatabaseMajorVersion(13);
        pg13.setDatabaseMinorVersion(0);
        pg13.setSqlBeanConfig(new SqlBeanConfig());

        // Oracle 19c
        SqlBeanMeta oracle19 = new SqlBeanMeta();
        oracle19.setDbType(DbType.Oracle);
        oracle19.setDatabaseMajorVersion(19);
        oracle19.setDatabaseMinorVersion(0);
        oracle19.setSqlBeanConfig(new SqlBeanConfig());

        // SQL Server 2019
        SqlBeanMeta sqlServer = new SqlBeanMeta();
        sqlServer.setDbType(DbType.SQLServer);
        sqlServer.setSqlBeanConfig(new SqlBeanConfig());

        // 1) MySQL 8.0 + 多个（非递归）CTE，子查询为 Select 形式
        Select cteA = new Select();
        cteA.setSqlBeanMeta(mysql8);
        cteA.setBeanClass(User.class);
        cteA.column(User::getId);
        cteA.setTable(User.class);
        cteA.where().gt(User::getId, 0);

        Select cteB = new Select();
        cteB.setSqlBeanMeta(mysql8);
        cteB.setBeanClass(User.class);
        cteB.column(User::getId);
        cteB.setTable(User.class);
        cteB.where().lt(User::getId, 100);

        Select c1 = new Select();
        c1.setSqlBeanMeta(mysql8);
        c1.setBeanClass(User.class);
        c1.column(User::getId);
        c1.setTable("cteA");
        c1.with("cteA", cteA).with("cteB", cteB);
        System.out.println("---selectCte MySQL8 多CTE（子查询为 Select）---");
        System.out.println(SqlHelper.buildSelectSql(c1));

        // 2) MySQL 8.0 + 原生 SQL 子查询的 CTE（含列定义）
        Cte rawCte = new Cte("t", "SELECT id, name FROM d_user WHERE status = 1");
        rawCte.column("id").column("name");
        Select c2 = new Select();
        c2.setSqlBeanMeta(mysql8);
        c2.setBeanClass(User.class);
        c2.column(User::getId);
        c2.setTable(User.class);
        c2.with(rawCte);
        System.out.println("---selectCte MySQL8 原生SQL子查询CTE（含列定义）---");
        System.out.println(SqlHelper.buildSelectSql(c2));

        // 3) MySQL 8.0 + 递归 CTE（builder UNION ALL）
        // 锚点：SELECT 1 AS n（无 FROM）；递归：SELECT n + 1 FROM counter WHERE n < 5
        Select anchor3 = new Select();
        anchor3.setSqlBeanMeta(mysql8);
        anchor3.column(new Column("1 AS n"));
        anchor3.noFrom();
        Select recursive3 = new Select();
        recursive3.setSqlBeanMeta(mysql8);
        recursive3.column(new Column("n + 1"));
        recursive3.setTable("counter");
        recursive3.where().lt("n", 5);
        Select counterCte3 = anchor3.unionAll(recursive3);
        Select c3 = new Select();
        c3.setSqlBeanMeta(mysql8);
        c3.setBeanClass(User.class);
        c3.column("n");
        c3.setTable("counter");
        c3.recursive();
        c3.with("counter", counterCte3);
        System.out.println("---selectCte MySQL8 递归CTE（builder UNION ALL）---");
        System.out.println(SqlHelper.buildSelectSql(c3));

        // 4) PostgreSQL 13 + 递归 CTE（builder UNION ALL）
        Select anchor4 = new Select();
        anchor4.setSqlBeanMeta(pg13);
        anchor4.column(new Column("1 AS n"));
        anchor4.noFrom();
        Select recursive4 = new Select();
        recursive4.setSqlBeanMeta(pg13);
        recursive4.column(new Column("n + 1"));
        recursive4.setTable("counter");
        recursive4.where().lt("n", 5);
        Select counterCte4 = anchor4.unionAll(recursive4);
        Select c4 = new Select();
        c4.setSqlBeanMeta(pg13);
        c4.setBeanClass(User.class);
        c4.column("n");
        c4.setTable("counter");
        c4.recursive();
        c4.with("counter", counterCte4);
        System.out.println("---selectCte PG13 递归CTE（builder UNION ALL）---");
        System.out.println(SqlHelper.buildSelectSql(c4));

        // 5) Oracle 19c + 递归 CTE（builder UNION ALL，无 RECURSIVE 关键字，锚点需 FROM dual）
        Select anchor5 = new Select();
        anchor5.setSqlBeanMeta(oracle19);
        anchor5.column(new Column("1 AS n"));
        anchor5.setTable("dual");
        Select recursive5 = new Select();
        recursive5.setSqlBeanMeta(oracle19);
        recursive5.column(new Column("n + 1"));
        recursive5.setTable("counter");
        recursive5.where().lt("n", 5);
        Select counterCte5 = anchor5.unionAll(recursive5);
        Select c5 = new Select();
        c5.setSqlBeanMeta(oracle19);
        c5.setBeanClass(User.class);
        c5.column("n");
        c5.setTable("counter");
        c5.recursive();
        c5.with("counter", counterCte5);
        System.out.println("---selectCte Oracle19 递归CTE（builder UNION ALL）---");
        System.out.println(SqlHelper.buildSelectSql(c5));

        // 6) SQL Server 2019 + 非递归 CTE（无 RECURSIVE 关键字）
        Select cteS = new Select();
        cteS.setSqlBeanMeta(sqlServer);
        cteS.setBeanClass(User.class);
        cteS.column(User::getId);
        cteS.setTable(User.class);
        cteS.where().gt(User::getId, 0);
        Select c6 = new Select();
        c6.setSqlBeanMeta(sqlServer);
        c6.setBeanClass(User.class);
        c6.column(User::getId);
        c6.setTable("cteS");
        c6.with("cteS", cteS);
        System.out.println("---selectCte SQLServer 非递归CTE（无 RECURSIVE 关键字）---");
        System.out.println(SqlHelper.buildSelectSql(c6));

        // 7) MySQL 8.0 + 组合：CTE + 分页 + SKIP LOCKED
        Select c7sub = new Select();
        c7sub.setSqlBeanMeta(mysql8);
        c7sub.setBeanClass(User.class);
        c7sub.column(User::getId);
        c7sub.setTable(User.class);
        c7sub.where().eq(User::getUsername, "jovi");
        Select c7 = new Select();
        c7.setSqlBeanMeta(mysql8);
        c7.setBeanClass(User.class);
        c7.column(User::getId);
        c7.setTable("active_user");
        c7.with("active_user", c7sub);
        c7.orderByDesc(User::getId);
        c7.page(0, 10);
        c7.forUpdateSkipLocked();
        System.out.println("---selectCte MySQL8 组合：CTE + 分页 + SKIP LOCKED---");
        System.out.println(SqlHelper.buildSelectSql(c7));

        // 8) SQL Server 2019 + CTE + 分页（验证 CTE 位于分页外层包裹之外）
        Select cteSP = new Select();
        cteSP.setSqlBeanMeta(sqlServer);
        cteSP.setBeanClass(User.class);
        cteSP.column(User::getId);
        cteSP.setTable(User.class);
        cteSP.where().gt(User::getId, 0);
        Select c8 = new Select();
        c8.setSqlBeanMeta(sqlServer);
        c8.setBeanClass(User.class);
        c8.column(User::getId);
        c8.setTable("cteSP");
        c8.with("cteSP", cteSP);
        c8.orderByAsc(User::getId);
        c8.page(0, 10);
        System.out.println("---selectCte SQLServer CTE + 分页（CTE 在包裹外）---");
        System.out.println(SqlHelper.buildSelectSql(c8));

        // 9) MySQL 8.0 + 顶层 UNION（普通合并，去重）
        Select u1 = new Select();
        u1.setSqlBeanMeta(mysql8);
        u1.setBeanClass(User.class);
        u1.column(User::getId);
        u1.setTable(User.class);
        u1.where().gt(User::getId, 0);
        Select u2 = new Select();
        u2.setSqlBeanMeta(mysql8);
        u2.setBeanClass(User.class);
        u2.column(User::getId);
        u2.setTable(User.class);
        u2.where().lt(User::getId, 100);
        Select unionSel = u1.union(u2);
        System.out.println("---selectCte MySQL8 顶层 UNION（去重）---");
        System.out.println(SqlHelper.buildSelectSql(unionSel));

        // 10) MySQL 8.0 + 顶层 UNION ALL（不去重，性能更优）
        Select a1 = new Select();
        a1.setSqlBeanMeta(mysql8);
        a1.setBeanClass(User.class);
        a1.column(User::getId);
        a1.setTable(User.class);
        a1.where().eq(User::getGender, 1);
        Select a2 = new Select();
        a2.setSqlBeanMeta(mysql8);
        a2.setBeanClass(User.class);
        a2.column(User::getId);
        a2.setTable(User.class);
        a2.where().eq(User::getGender, 0);
        Select unionAllSel = a1.unionAll(a2);
        System.out.println("---selectCte MySQL8 顶层 UNION ALL（不去重）---");
        System.out.println(SqlHelper.buildSelectSql(unionAllSel));

        // 11) MySQL 8.0 + CTE + UNION ALL 组合（CTE 内联 UNION，主查询引用）
        Select activeA = new Select();
        activeA.setSqlBeanMeta(mysql8);
        activeA.setBeanClass(User.class);
        activeA.column(User::getId);
        activeA.setTable(User.class);
        activeA.where().eq(User::getGender, 1);
        Select activeB = new Select();
        activeB.setSqlBeanMeta(mysql8);
        activeB.setBeanClass(User.class);
        activeB.column(User::getId);
        activeB.setTable(User.class);
        activeB.where().eq(User::getGender, 0);
        Select activeCte = activeA.unionAll(activeB);
        Select c9 = new Select();
        c9.setSqlBeanMeta(mysql8);
        c9.setBeanClass(User.class);
        c9.column(User::getId);
        c9.setTable("active_users");
        c9.with("active_users", activeCte);
        System.out.println("---selectCte MySQL8 CTE + UNION ALL 组合---");
        System.out.println(SqlHelper.buildSelectSql(c9));
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

    /**
     * UPSERT / MERGE 生成测试（覆盖 4 主方言 + SQLite、单行/多行、setAll / 字面量 / doNothing）
     */
    private static void upsertTest() {
        // 构造各方言 SqlBeanMeta（默认 toUpperCase=false）
        SqlBeanMeta mysql = meta(DbType.MySQL);
        SqlBeanMeta pg = meta(DbType.Postgresql);
        SqlBeanMeta sqlite = meta(DbType.SQLite);
        SqlBeanMeta oracle = meta(DbType.Oracle);
        SqlBeanMeta sqlServer = meta(DbType.SQLServer);
        SqlBeanMeta h2 = meta(DbType.H2);
        SqlBeanMeta hsql = meta(DbType.Hsql);
        SqlBeanMeta db2 = meta(DbType.DB2);

        // 1) MySQL 单行：onConflict(id) + setAll()
        Upsert<User> u1 = new Upsert<>();
        u1.setSqlBeanMeta(mysql);
        u1.setBeanClass(User.class);
        u1.setBean(sampleUser("10000", "jovi", "麻花疼"));
        u1.onConflict(User::getId).setAll();
        System.out.println("---upsert MySQL 单行 setAll---");
        System.out.println(SqlHelper.buildUpsertSql(u1));

        // 2) MySQL 单行：onConflict(id) + doNothing()（MySQL 用 INSERT IGNORE）
        Upsert<User> u2 = new Upsert<>();
        u2.setSqlBeanMeta(mysql);
        u2.setBeanClass(User.class);
        u2.setBean(sampleUser("10001", "vicky", "薇琪"));
        u2.onConflict(User::getId).doNothing();
        System.out.println("---upsert MySQL 单行 doNothing---");
        System.out.println(SqlHelper.buildUpsertSql(u2));

        // 3) MySQL 单行：字面量 set + 引用待插入值 set
        Upsert<User> u3 = new Upsert<>();
        u3.setSqlBeanMeta(mysql);
        u3.setBeanClass(User.class);
        u3.setBean(sampleUser("10002", "tom", "汤姆"));
        u3.onConflict(User::getId)
                .set(User::getNickname, "固定昵称")      // 字面量
                .set(User::getHeadPortrait);            // 引用待插入值 → VALUES(headPortrait)
        System.out.println("---upsert MySQL 字面量+引用---");
        System.out.println(SqlHelper.buildUpsertSql(u3));

        // 4) PostgreSQL 单行：onConflict(id) + setAll()
        Upsert<User> u4 = new Upsert<>();
        u4.setSqlBeanMeta(pg);
        u4.setBeanClass(User.class);
        u4.setBean(sampleUser("10003", "lily", "莉莉"));
        u4.onConflict(User::getId).setAll();
        System.out.println("---upsert PG 单行 setAll---");
        System.out.println(SqlHelper.buildUpsertSql(u4));

        // 5) PostgreSQL 单行：onConflict(id) + doNothing()
        Upsert<User> u5 = new Upsert<>();
        u5.setSqlBeanMeta(pg);
        u5.setBeanClass(User.class);
        u5.setBean(sampleUser("10004", "lucy", "露西"));
        u5.onConflict(User::getId).doNothing();
        System.out.println("---upsert PG 单行 doNothing---");
        System.out.println(SqlHelper.buildUpsertSql(u5));

        // 6) SQLite 单行：onConflict(id) + setAll()
        Upsert<User> u6 = new Upsert<>();
        u6.setSqlBeanMeta(sqlite);
        u6.setBeanClass(User.class);
        u6.setBean(sampleUser("10005", "jack", "杰克"));
        u6.onConflict(User::getId).setAll();
        System.out.println("---upsert SQLite 单行 setAll---");
        System.out.println(SqlHelper.buildUpsertSql(u6));

        // 7) Oracle 单行：onConflict(id) + setAll()（MERGE INTO）
        Upsert<User> u7 = new Upsert<>();
        u7.setSqlBeanMeta(oracle);
        u7.setBeanClass(User.class);
        u7.setBean(sampleUser("10006", "rose", "萝丝"));
        u7.onConflict(User::getId).setAll();
        System.out.println("---upsert Oracle 单行 setAll（MERGE）---");
        System.out.println(SqlHelper.buildUpsertSql(u7));

        // 8) Oracle 单行：onConflict(id) + doNothing()（MERGE 仅保留插入分支）
        Upsert<User> u8 = new Upsert<>();
        u8.setSqlBeanMeta(oracle);
        u8.setBeanClass(User.class);
        u8.setBean(sampleUser("10007", "bob", "鲍勃"));
        u8.onConflict(User::getId).doNothing();
        System.out.println("---upsert Oracle 单行 doNothing（MERGE）---");
        System.out.println(SqlHelper.buildUpsertSql(u8));

        // 9) SQL Server 单行：onConflict(id) + setAll()（MERGE INTO）
        Upsert<User> u9 = new Upsert<>();
        u9.setSqlBeanMeta(sqlServer);
        u9.setBeanClass(User.class);
        u9.setBean(sampleUser("10008", "kate", "凯特"));
        u9.onConflict(User::getId).setAll();
        System.out.println("---upsert SQLServer 单行 setAll（MERGE）---");
        System.out.println(SqlHelper.buildUpsertSql(u9));

        // 10) SQL Server 单行：onConflict(id) + doNothing()（MERGE 仅保留插入分支）
        Upsert<User> u10 = new Upsert<>();
        u10.setSqlBeanMeta(sqlServer);
        u10.setBeanClass(User.class);
        u10.setBean(sampleUser("10009", "neo", "尼奥"));
        u10.onConflict(User::getId).doNothing();
        System.out.println("---upsert SQLServer 单行 doNothing（MERGE）---");
        System.out.println(SqlHelper.buildUpsertSql(u10));

        // 11) MySQL 多行（Column 模式）：onConflict(id) + setAll()
        Upsert<User> u11 = new Upsert<>();
        u11.setSqlBeanMeta(mysql);
        u11.setBeanClass(User.class);
        u11.column(User::getId, User::getUsername, User::getNickname, User::getHeadPortrait, User::getGender, User::getIntegral)
                .values("20001", "a", "A", "a.png", 1, 10)
                .values("20002", "b", "B", "b.png", 0, 20);
        u11.onConflict(User::getId).setAll();
        System.out.println("---upsert MySQL 多行 setAll---");
        System.out.println(SqlHelper.buildUpsertSql(u11));

        // 12) Oracle 多行（Column 模式）：onConflict(id) + setAll()（MERGE USING 多行 UNION ALL）
        Upsert<User> u12 = new Upsert<>();
        u12.setSqlBeanMeta(oracle);
        u12.setBeanClass(User.class);
        u12.column(User::getId, User::getUsername, User::getNickname, User::getHeadPortrait, User::getGender, User::getIntegral)
                .values("20003", "c", "C", "c.png", 1, 30)
                .values("20004", "d", "D", "d.png", 0, 40);
        u12.onConflict(User::getId).setAll();
        System.out.println("---upsert Oracle 多行 setAll（MERGE）---");
        System.out.println(SqlHelper.buildUpsertSql(u12));

        // 13) SQL Server 多行（Column 模式）：onConflict(id) + setAll()（MERGE USING 表值构造器）
        Upsert<User> u13 = new Upsert<>();
        u13.setSqlBeanMeta(sqlServer);
        u13.setBeanClass(User.class);
        u13.column(User::getId, User::getUsername, User::getNickname, User::getHeadPortrait, User::getGender, User::getIntegral)
                .values("20005", "e", "E", "e.png", 1, 50)
                .values("20006", "f", "F", "f.png", 0, 60);
        u13.onConflict(User::getId).setAll();
        System.out.println("---upsert SQLServer 多行 setAll（MERGE）---");
        System.out.println(SqlHelper.buildUpsertSql(u13));

        // 14) H2 单行：onConflict(id) + setAll()（MERGE INTO）
        Upsert<User> u14 = new Upsert<>();
        u14.setSqlBeanMeta(h2);
        u14.setBeanClass(User.class);
        u14.setBean(sampleUser("10010", "h2a", "H2甲"));
        u14.onConflict(User::getId).setAll();
        System.out.println("---upsert H2 单行 setAll（MERGE）---");
        System.out.println(SqlHelper.buildUpsertSql(u14));

        // 15) H2 单行：onConflict(id) + doNothing()（MERGE 仅保留插入分支）
        Upsert<User> u15 = new Upsert<>();
        u15.setSqlBeanMeta(h2);
        u15.setBeanClass(User.class);
        u15.setBean(sampleUser("10011", "h2b", "H2乙"));
        u15.onConflict(User::getId).doNothing();
        System.out.println("---upsert H2 单行 doNothing（MERGE）---");
        System.out.println(SqlHelper.buildUpsertSql(u15));

        // 16) H2 多行（Column 模式）：onConflict(id) + setAll()（MERGE USING 表值构造器）
        Upsert<User> u16 = new Upsert<>();
        u16.setSqlBeanMeta(h2);
        u16.setBeanClass(User.class);
        u16.column(User::getId, User::getUsername, User::getNickname, User::getHeadPortrait, User::getGender, User::getIntegral)
                .values("20007", "g", "G", "g.png", 1, 70)
                .values("20008", "h", "H", "h.png", 0, 80);
        u16.onConflict(User::getId).setAll();
        System.out.println("---upsert H2 多行 setAll（MERGE）---");
        System.out.println(SqlHelper.buildUpsertSql(u16));

        // 17) HSQLDB 单行：onConflict(id) + setAll()（MERGE INTO）
        Upsert<User> u17 = new Upsert<>();
        u17.setSqlBeanMeta(hsql);
        u17.setBeanClass(User.class);
        u17.setBean(sampleUser("10012", "hs1", "HS甲"));
        u17.onConflict(User::getId).setAll();
        System.out.println("---upsert HSQLDB 单行 setAll（MERGE）---");
        System.out.println(SqlHelper.buildUpsertSql(u17));

        // 18) HSQLDB 单行：onConflict(id) + doNothing()（MERGE 仅保留插入分支）
        Upsert<User> u18 = new Upsert<>();
        u18.setSqlBeanMeta(hsql);
        u18.setBeanClass(User.class);
        u18.setBean(sampleUser("10013", "hs2", "HS乙"));
        u18.onConflict(User::getId).doNothing();
        System.out.println("---upsert HSQLDB 单行 doNothing（MERGE）---");
        System.out.println(SqlHelper.buildUpsertSql(u18));

        // 19) HSQLDB 多行（Column 模式）：onConflict(id) + setAll()（MERGE USING 表值构造器）
        Upsert<User> u19 = new Upsert<>();
        u19.setSqlBeanMeta(hsql);
        u19.setBeanClass(User.class);
        u19.column(User::getId, User::getUsername, User::getNickname, User::getHeadPortrait, User::getGender, User::getIntegral)
                .values("20009", "i", "I", "i.png", 1, 90)
                .values("20010", "j", "J", "j.png", 0, 100);
        u19.onConflict(User::getId).setAll();
        System.out.println("---upsert HSQLDB 多行 setAll（MERGE）---");
        System.out.println(SqlHelper.buildUpsertSql(u19));

        // 20) DB2 单行：onConflict(id) + setAll()（MERGE INTO）
        Upsert<User> u20 = new Upsert<>();
        u20.setSqlBeanMeta(db2);
        u20.setBeanClass(User.class);
        u20.setBean(sampleUser("10014", "db1", "DB甲"));
        u20.onConflict(User::getId).setAll();
        System.out.println("---upsert DB2 单行 setAll（MERGE）---");
        System.out.println(SqlHelper.buildUpsertSql(u20));

        // 21) DB2 单行：onConflict(id) + doNothing()（MERGE 仅保留插入分支）
        Upsert<User> u21 = new Upsert<>();
        u21.setSqlBeanMeta(db2);
        u21.setBeanClass(User.class);
        u21.setBean(sampleUser("10015", "db2", "DB乙"));
        u21.onConflict(User::getId).doNothing();
        System.out.println("---upsert DB2 单行 doNothing（MERGE）---");
        System.out.println(SqlHelper.buildUpsertSql(u21));

        // 22) DB2 多行（Column 模式）：onConflict(id) + setAll()（MERGE USING 表值构造器）
        Upsert<User> u22 = new Upsert<>();
        u22.setSqlBeanMeta(db2);
        u22.setBeanClass(User.class);
        u22.column(User::getId, User::getUsername, User::getNickname, User::getHeadPortrait, User::getGender, User::getIntegral)
                .values("20011", "k", "K", "k.png", 1, 110)
                .values("20012", "l", "L", "l.png", 0, 120);
        u22.onConflict(User::getId).setAll();
        System.out.println("---upsert DB2 多行 setAll（MERGE）---");
        System.out.println(SqlHelper.buildUpsertSql(u22));
    }

    /**
     * 窗口函数（OVER (PARTITION BY .. ORDER BY ..)）测试
     */
    private static void windowFunTest() {
        // 1) ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY create_time DESC)
        Select s1 = new Select();
        s1.setSqlBeanMeta(meta(DbType.MySQL));
        s1.setBeanClass(Essay.class);
        s1.column(SqlFun.rowNumber().over().partitionBy("user_id").orderByDesc("create_time"), "rn");
        s1.setTable(Essay.class);
        System.out.println("---windowFun 1: ROW_NUMBER() OVER (PARTITION BY .. ORDER BY ..)---");
        System.out.println(SqlHelper.buildSelectSql(s1));

        // 2) SUM(amount) OVER (PARTITION BY dept_id) 聚合窗口
        Select s2 = new Select();
        s2.setSqlBeanMeta(meta(DbType.MySQL));
        s2.setBeanClass(Essay.class);
        s2.column(SqlFun.sum(new Column("amount")).over().partitionBy("dept_id"));
        s2.setTable(Essay.class);
        System.out.println("---windowFun 2: SUM() OVER (PARTITION BY ..)---");
        System.out.println(SqlHelper.buildSelectSql(s2));

        // 3) RANK() OVER (ORDER BY score DESC) 仅排序、无分区
        Select s3 = new Select();
        s3.setSqlBeanMeta(meta(DbType.MySQL));
        s3.setBeanClass(Essay.class);
        s3.column(SqlFun.rank().over().orderByDesc("score"));
        s3.setTable(Essay.class);
        System.out.println("---windowFun 3: RANK() OVER (ORDER BY ..) 无分区---");
        System.out.println(SqlHelper.buildSelectSql(s3));

        // 4) LAG(nickname, 1) OVER (PARTITION BY dept ORDER BY id ASC) Lambda 列
        Select s4 = new Select();
        s4.setSqlBeanMeta(meta(DbType.MySQL));
        s4.setBeanClass(User.class);
        s4.column(SqlFun.lag(User::getNickname, 1).over().partitionBy("dept").orderByAsc(User::getId));
        s4.setTable(User.class);
        System.out.println("---windowFun 4: LAG() OVER (PARTITION BY .. ORDER BY ..) Lambda 列---");
        System.out.println(SqlHelper.buildSelectSql(s4));

        // 5) SUM(amount) OVER (ORDER BY create_time ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) 帧子句
        Select s5 = new Select();
        s5.setSqlBeanMeta(meta(DbType.MySQL));
        s5.setBeanClass(Essay.class);
        s5.column(SqlFun.sum(new Column("amount")).over().orderByAsc("create_time")
                .frame("ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW"));
        s5.setTable(Essay.class);
        System.out.println("---windowFun 5: 帧子句 ROWS BETWEEN ... AND CURRENT ROW---");
        System.out.println(SqlHelper.buildSelectSql(s5));
    }

    /**
     * 构造测试的 SqlBeanMeta（默认 toUpperCase=false）
     */
    private static SqlBeanMeta meta(DbType dbType) {
        SqlBeanMeta m = new SqlBeanMeta();
        m.setDbType(dbType);
        m.setSqlBeanConfig(new SqlBeanConfig());
        return m;
    }

    /**
     * 构造一个带完整字段的测试用户对象
     */
    private static User sampleUser(String id, String username, String nickname) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(nickname);
        user.setHeadPortrait(username + ".png");
        user.setGender(id.hashCode() % 2 == 0 ? 0 : 1);
        user.setIntegral(100);
        return user;
    }

    /**
     * 审计自动注入测试：当前操作人注入 + 只读字段保护
     */
    private static void auditInjectionTest(SqlBeanMeta sqlBeanMeta) {
        // 注册当前操作人解析器（模拟从登录上下文取 userId）
        SqlBeanUtil.setCurrentUserSupplier(type -> "operator_001");

        // ---- INSERT：createBy/createTime 应被自动填充；updateBy/updateTime 不填（with=UPDATE_EVERYTIME 仅更新时生效）----
        AuditBean insertBean = new AuditBean();
        insertBean.setName("测试审计");
        Insert<AuditBean> insert = new Insert<>();
        insert.setSqlBeanMeta(sqlBeanMeta);
        insert.table(AuditBean.class);
        insert.setBean(insertBean);
        String insertSql = SqlHelper.buildInsertSql(insert);
        System.out.println("---audit INSERT---");
        System.out.println(insertSql);
        System.out.println("[断言] create_by 含 operator_001 => " + insertSql.contains("'operator_001'"));
        System.out.println("[断言] create_time 被填充（非 NULL）=> " + !insertSql.contains("create_time = NULL"));
        System.out.println("[断言] bean.createBy 已回填 => " + "operator_001".equals(insertBean.getCreateBy()));

        // ---- UPDATE：readonly 的 createBy/createTime 不应出现在 SET 中；updateBy/updateTime 应刷新 ----
        AuditBean updateBean = new AuditBean();
        updateBean.setId(1L);
        updateBean.setName("更新审计");
        updateBean.setCreateBy("hacker");      // 业务层误设，应被 readonly 忽略
        updateBean.setCreateTime(new Date(0)); // 业务层误设，应被 readonly 忽略
        Update<AuditBean> update = new Update<>();
        update.setSqlBeanMeta(sqlBeanMeta);
        update.table(AuditBean.class);
        update.bean(updateBean);
        update.where().eq(AuditBean::getId, 1L);
        String updateSql = SqlHelper.buildUpdateSql(update);
        System.out.println("---audit UPDATE---");
        System.out.println(updateSql);
        System.out.println("[断言] readonly 的 create_by 不在 SET => " + !containsSetColumn(updateSql, "create_by"));
        System.out.println("[断言] readonly 的 create_time 不在 SET => " + !containsSetColumn(updateSql, "create_time"));
        System.out.println("[断言] update_by 被刷新为 operator_001 => " + updateSql.contains("'operator_001'"));
    }

    /**
     * 判断 UPDATE 的 SET 子句中是否包含指定列（只检查 SET 区，避免误判 WHERE 条件中的同名列）
     */
    private static boolean containsSetColumn(String updateSql, String column) {
        int setIdx = updateSql.indexOf("SET");
        int whereIdx = updateSql.indexOf("WHERE");
        int end = (whereIdx > setIdx && whereIdx >= 0) ? whereIdx : updateSql.length();
        String setPart = setIdx >= 0 ? updateSql.substring(setIdx, end) : updateSql;
        return setPart.contains(column + " =") || setPart.contains(column + " = ");
    }

    /**
     * 动态 Schema 注入安全校验测试
     */
    private static void dynSchemaTest() {
        SqlBeanMeta sqlBeanMeta = new SqlBeanMeta();
        SqlBeanConfig sqlBeanConfig = new SqlBeanConfig();
        sqlBeanConfig.setToUpperCase(false);
        sqlBeanMeta.setDbType(DbType.MySQL);
        sqlBeanMeta.setSqlBeanConfig(sqlBeanConfig);

        // 1) 合法 schema：应正常拼入 SQL（走 SqlBeanProvider 高层 API 才会触发 setSchema 注入）
        DynSchemaContextHolder.setSchema("tenant_a");
        Select select = new Select();
        select.setSqlBeanMeta(sqlBeanMeta);
        select.setBeanClass(AuditBean.class);
        select.setTable(AuditBean.class);
        select.where().eq(AuditBean::getId, 1L);
        String safeSql = SqlBeanProvider.selectSql(sqlBeanMeta, AuditBean.class, AuditBean.class, select);
        DynSchemaContextHolder.clearSchema();
        System.out.println("---dynSchema 合法---");
        System.out.println(safeSql);
        System.out.println("[断言] 合法 schema 拼入 => " + safeSql.contains("tenant_a"));

        // 2) 非法 schema（含注入字符）：应抛 SqlBeanException 而非拼入
        DynSchemaContextHolder.setSchema("a'; DROP TABLE t_audit; --");
        Select evil = new Select();
        evil.setSqlBeanMeta(sqlBeanMeta);
        evil.setBeanClass(AuditBean.class);
        evil.setTable(AuditBean.class);
        evil.where().eq(AuditBean::getId, 1L);
        boolean threw = false;
        try {
            SqlBeanProvider.selectSql(sqlBeanMeta, AuditBean.class, AuditBean.class, evil);
        } catch (cn.vonce.sql.exception.SqlBeanException e) {
            threw = true;
            System.out.println("[断言] 非法 schema 被拦截 => " + e.getMessage());
        } finally {
            DynSchemaContextHolder.clearSchema();
        }
        System.out.println("[断言] 非法 schema 抛异常 => " + threw);
    }

    /**
     * 行级多租户隔离测试：tenant_id 自动注入 + WHERE 强制过滤
     */
    private static void tenantInjectionTest(SqlBeanMeta sqlBeanMeta) {
        // 设置当前租户上下文（模拟拦截器/切面从登录信息注入）
        TenantContextHolder.setTenantId("t-abc");
        try {
            // ---- INSERT：tenant_id 被上下文覆盖，业务层设置的 "evil" 被忽略 ----
            TenantBean insertBean = new TenantBean();
            insertBean.setName("租户数据");
            insertBean.setTenantId("evil"); // 业务层越权指定，应被忽略
            Insert<TenantBean> insert = new Insert<>();
            insert.setSqlBeanMeta(sqlBeanMeta);
            insert.table(TenantBean.class);
            insert.setBean(insertBean);
            String insertSql = SqlHelper.buildInsertSql(insert);
            System.out.println("---tenant INSERT---");
            System.out.println(insertSql);
            System.out.println("[断言] INSERT 含 tenant_id 列 => " + insertSql.contains("tenant_id"));
            System.out.println("[断言] INSERT 写入上下文租户 t-abc => " + insertSql.contains("'t-abc'"));
            System.out.println("[断言] INSERT 忽略业务层 evil => " + !insertSql.contains("'evil'"));

            // ---- SELECT（无显式 where）：自动追加 tenant_id 过滤 ----
            Select selectNoWhere = new Select();
            selectNoWhere.setSqlBeanMeta(sqlBeanMeta);
            selectNoWhere.setBeanClass(TenantBean.class);
            selectNoWhere.setTable(TenantBean.class);
            String selectNoWhereSql = SqlHelper.buildSelectSql(selectNoWhere);
            System.out.println("---tenant SELECT（无 where）---");
            System.out.println(selectNoWhereSql);
            System.out.println("[断言] SELECT 无 where 时追加租户过滤 => " + (selectNoWhereSql.contains("tenant_id") && selectNoWhereSql.contains("'t-abc'")));

            // ---- SELECT（有显式 where）：租户过滤始终生效（AND 连接） ----
            Select selectWithWhere = new Select();
            selectWithWhere.setSqlBeanMeta(sqlBeanMeta);
            selectWithWhere.setBeanClass(TenantBean.class);
            selectWithWhere.setTable(TenantBean.class);
            selectWithWhere.where().eq(TenantBean::getName, "租户数据");
            String selectWithWhereSql = SqlHelper.buildSelectSql(selectWithWhere);
            System.out.println("---tenant SELECT（有 where）---");
            System.out.println(selectWithWhereSql);
            System.out.println("[断言] SELECT 有 where 时仍强制租户过滤 => " + (selectWithWhereSql.contains("'租户数据'") && selectWithWhereSql.contains("tenant_id") && selectWithWhereSql.contains("'t-abc'")));

            // ---- UPDATE（bean 模式）：SET 跳过 tenant_id，WHERE 强制租户过滤 ----
            TenantBean updateBean = new TenantBean();
            updateBean.setId(1L);
            updateBean.setName("更新");
            updateBean.setTenantId("evil"); // 业务层误设，SET 应跳过
            Update<TenantBean> update = new Update<>();
            update.setSqlBeanMeta(sqlBeanMeta);
            update.setBeanClass(TenantBean.class);
            update.table(TenantBean.class);
            update.bean(updateBean);
            update.where().eq(TenantBean::getId, 1L);
            String updateSql = SqlHelper.buildUpdateSql(update);
            System.out.println("---tenant UPDATE---");
            System.out.println(updateSql);
            System.out.println("[断言] UPDATE 的 SET 跳过 tenant_id => " + !containsSetColumn(updateSql, "tenant_id"));
            System.out.println("[断言] UPDATE 的 WHERE 强制租户过滤 => " + (updateSql.contains("tenant_id") && updateSql.contains("'t-abc'")));

            // ---- DELETE（有 where）：WHERE 强制租户过滤 ----
            Delete delete = new Delete();
            delete.setSqlBeanMeta(sqlBeanMeta);
            delete.setBeanClass(TenantBean.class);
            delete.setTable(TenantBean.class);
            delete.where().eq(TenantBean::getId, 1L);
            String deleteSql = SqlHelper.buildDeleteSql(delete);
            System.out.println("---tenant DELETE---");
            System.out.println(deleteSql);
            System.out.println("[断言] DELETE 的 WHERE 强制租户过滤 => " + (deleteSql.contains("tenant_id") && deleteSql.contains("'t-abc'")));
        } finally {
            TenantContextHolder.clearTenantId();
        }
    }

}