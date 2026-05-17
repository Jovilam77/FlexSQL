package cn.vonce.sql.android.helper;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.List;
import java.util.logging.Logger;

import android.util.Log;
import cn.vonce.sql.android.util.PackageUtil;
import cn.vonce.sql.annotation.SqlTable;
import cn.vonce.sql.bean.Create;
import cn.vonce.sql.bean.Table;
import cn.vonce.sql.config.SqlBeanConfig;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.enumerate.DbType;
import cn.vonce.sql.helper.SqlHelper;
import cn.vonce.sql.uitls.SqlBeanUtil;
import cn.vonce.sql.uitls.StringUtil;

/**
 * 初始化数据库
 *
 * @author Jovi
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final Logger logger = Logger.getLogger(DatabaseHelper.class.getName());

    private String dbName;
    private Class<?> clazz;
    private Context context;

    public DatabaseHelper(Class<?> clazz, Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
        this.dbName = name;
        this.clazz = clazz;
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (newVersion > oldVersion) {
            createTable(db);
        }
    }

    /**
     * 创建表
     *
     * @param db
     */
    public void createTable(SQLiteDatabase db) {
        List<String> classNames = PackageUtil.getClasses(context, clazz.getPackage().getName());
        try {
            Create create;
            SqlBeanMeta sqlBeanMeta = new SqlBeanMeta();
            sqlBeanMeta.setDbType(DbType.SQLite);
            sqlBeanMeta.setSqlBeanConfig(new SqlBeanConfig());
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                Table table = SqlBeanUtil.getTable(clazz);
                SqlTable sqlTable = clazz.getAnnotation(SqlTable.class);
                if (StringUtil.isEmpty(table.getSchema()) || table.getSchema().equals(dbName)) {
                    if (sqlTable != null && sqlTable.autoCreate()) {
                        create = new Create();
                        create.setSqlBeanMeta(sqlBeanMeta);
                        create.setBeanClass(clazz);
                        create.setTable(clazz);
                        String sql = SqlHelper.buildCreateSql(create);
                        db.execSQL("DROP TABLE IF EXISTS " + SqlBeanUtil.getTableName(create.getTable(), create));
                        db.execSQL(sql);
                        Log.d("flexsql", sql);
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            logger.warning("Failed to load class or create table: " + e.getMessage());
            Log.e("flexsql", e.getMessage(), e);
        }
    }

}
