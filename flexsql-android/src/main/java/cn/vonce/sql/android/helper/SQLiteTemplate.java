package cn.vonce.sql.android.helper;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import cn.vonce.sql.android.mapper.RowMapper;
import cn.vonce.sql.mapper.ResultSetDelegate;

/**
 * SQLite 执行sql模板
 *
 * @author Jovi
 */
public class SQLiteTemplate {

    private SQLiteDatabase db;

    public SQLiteTemplate(SQLiteDatabase db) {
        this.db = db;
    }

    /**
     * 查询某个对象列表
     *
     * @param sql
     * @param rowMapper
     * @param <T>
     * @return
     */
    public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
        List<T> list = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, null);
            Log.d("flexsql", "query: " + sql);
            if (cursor != null && cursor.getCount() > 0) {
                ResultSetDelegate<Cursor> resultSetDelegate = new ResultSetDelegate<>(cursor);
                for (int i = 0; i < cursor.getCount(); i++) {
                    cursor.moveToPosition(i);
                    list.add(rowMapper.mapRow(resultSetDelegate, i));
                }
            }
        } catch (Exception e) {
            Log.e("flexsql", "query error: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return list;
    }

    /**
     * 查询返回某个对象类型
     *
     * @param sql
     * @param rowMapper
     * @param <T>
     * @return
     */
    public <T> T queryForObject(String sql, RowMapper<T> rowMapper) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, null);
            Log.d("flexsql", "queryForObject: " + sql);
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                return rowMapper.mapRow(new ResultSetDelegate<>(cursor), 0);
            }
            return null;
        } catch (Exception e) {
            Log.e("flexsql", "queryForObject error: " + e.getMessage(), e);
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * insert
     *
     * @param sql
     * @return
     */
    public int insert(final String sql) {
        Log.d("flexsql", "updateSQL: " + sql);
        return (int) db.compileStatement(sql).executeInsert();
    }

    /**
     * update
     *
     * @param sql
     * @return
     */
    public int update(final String sql) {
        Log.d("flexsql", "updateSQL: " + sql);
        return db.compileStatement(sql).executeUpdateDelete();
    }

    /**
     * 执行sql 无返回
     *
     * @param sql
     */
    public void execSQL(final String sql) {
        Log.d("flexsql", "execSQL: " + sql);
        db.execSQL(sql);
    }

}
