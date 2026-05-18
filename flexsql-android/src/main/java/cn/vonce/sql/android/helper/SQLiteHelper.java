package cn.vonce.sql.android.helper;

import android.content.Context;
import android.util.Log;
import cn.vonce.sql.android.util.PackageUtil;
import cn.vonce.sql.annotation.SqlTable;
import cn.vonce.sql.bean.Table;
import cn.vonce.sql.uitls.SqlBeanUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 数据库连接助手
 *
 * @author Jovi
 */
public class SQLiteHelper {

    private static final Logger logger = Logger.getLogger(SQLiteHelper.class.getName());

    private volatile static SQLiteHelper defaultSqLiteHelper;
    private final static Map<String, SQLiteHelper> sqLiteHelperMap = new ConcurrentHashMap<>();
    private final Map<Class<?>, SqlBeanHelper> sqlBeanHelperMap = new ConcurrentHashMap<>();

    private Context context;
    private String name;
    private int version;
    private DatabaseHelper databaseHelper;

    private SQLiteHelper(Context context, String name, int version) {
        this.context = context;
        this.name = name;
        this.version = version;
    }

    /**
     * 初始化默认数据库参数
     *
     * @param context 上下文
     * @param name    数据库名称
     * @param version 数据库版本
     */
    public static void init(Context context, String name, int version) {
        if (defaultSqLiteHelper == null) {
            synchronized (SQLiteHelper.class) {
                if (defaultSqLiteHelper == null) {
                    defaultSqLiteHelper = new SQLiteHelper(context, name, version);
                }
            }
        }
    }

    /**
     * 动态设置数据库参数
     *
     * @param context 上下文
     * @param name    数据库名称
     * @param version 数据库版本
     * @return
     */
    public static SQLiteHelper db(Context context, String name, int version) {
        SQLiteHelper sqLiteHelper = sqLiteHelperMap.get(name);
        if (sqLiteHelper == null) {
            sqLiteHelper = new SQLiteHelper(context, name, version);
            sqLiteHelperMap.put(name, sqLiteHelper);
        } else {
            if (sqLiteHelper.version != version) {
                sqLiteHelper.version = version;
                sqLiteHelper.sqlBeanHelperMap.clear();
            }
        }
        return sqLiteHelper;
    }

    /**
     * 获取默认数据库参数
     *
     * @return
     */
    public static SQLiteHelper db() {
        SqlBeanUtil.isNull(defaultSqLiteHelper, "请初始化默认数据库");
        return defaultSqLiteHelper;
    }

    /**
     * 获得数据库连接
     *
     * @param clazz
     * @return
     */
    public <T, ID> SqlBeanHelper<T, ID> get(Class<T> clazz) {
        SqlBeanHelper<T, ID> sqlBeanHelper = sqlBeanHelperMap.get(clazz);
        if (sqlBeanHelper == null) {
            if (databaseHelper == null) {
                databaseHelper = new DatabaseHelper(clazz, context, name, null, version);
                this.initSqlBeanHelper(clazz);
            }
            sqlBeanHelper = sqlBeanHelperMap.get(clazz);
            if (sqlBeanHelper == null) {
                sqlBeanHelper = new SqlBeanHelper<>(clazz, databaseHelper);
                sqlBeanHelperMap.put(clazz, sqlBeanHelper);
            }
        }
        return sqlBeanHelper;
    }

    private void initSqlBeanHelper(Class beanClazz) {
        new Thread(() -> {
            List<String> classNames = PackageUtil.getClasses(context, beanClazz.getPackage().getName());
            try {
                for (String className : classNames) {
                    Class<?> clazz = Class.forName(className);
                    SqlBeanHelper sqlBeanHelper = new SqlBeanHelper<>(clazz, databaseHelper);
                    sqlBeanHelperMap.put(clazz, sqlBeanHelper);
                    SqlTable sqlTable = SqlBeanUtil.getSqlTable(clazz);
                    //更新表结构
                    if (sqlTable != null && sqlTable.autoAlter()) {
                        Table table = SqlBeanUtil.getTable(clazz);
                        sqlBeanHelper.alter(table, sqlBeanHelper.getColumnInfoList(table.getName()));
                    }
                }
            } catch (ClassNotFoundException e) {
                logger.warning("Failed to load class during table initialization: " + e.getMessage());
                Log.e("flexsql", e.getMessage(), e);
            }
        }).start();
    }

}