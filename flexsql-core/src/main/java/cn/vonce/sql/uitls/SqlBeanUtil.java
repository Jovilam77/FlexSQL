package cn.vonce.sql.uitls;

import cn.vonce.sql.annotation.*;
import cn.vonce.sql.bean.*;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.constant.SqlConstant;
import cn.vonce.sql.define.*;
import cn.vonce.sql.enumerate.*;
import cn.vonce.sql.exception.SqlBeanException;
import cn.vonce.sql.helper.Cond;
import cn.vonce.sql.helper.Wrapper;
import cn.vonce.sql.json.JSONConvertImpl;
import cn.vonce.sql.json.JSONConvert;

import java.beans.Introspector;
import java.io.*;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * SqlBean 工具类 Created by Jovi on 2018/6/17.
 */
public class SqlBeanUtil {

    private static final Logger logger = Logger.getLogger(SqlBeanUtil.class.getName());
    // 字段列表缓存
    private static final Map<Class<?>, List<Field>> beanAllFieldCache = new ConcurrentHashMap<>();
    // 是否是Android环境
    private static boolean isAndroidEnv;

    static {
        try {
            Class.forName("dalvik.system.BaseDexClassLoader");
            isAndroidEnv = true;
        } catch (Exception e) {
            isAndroidEnv = false;
        }
    }

    public static boolean isAndroidEnv() {
        return isAndroidEnv;
    }

    /**
     * 保存SqlTable缓存
     */
    private static final ConcurrentHashMap<Class<?>, SqlTable> sqlTableMap = new ConcurrentHashMap<>();

    /**
     * 获取SqlTable注解
     *
     * @param clazz
     * @return
     */
    public static SqlTable getSqlTable(Class<?> clazz) {
        return sqlTableMap.computeIfAbsent(clazz, key -> {
            SqlTable sqlTable = key.getAnnotation(SqlTable.class);
            if (sqlTable == null) {
                Class<?> superClass = key.getSuperclass();
                if (superClass != null) {
                    do {
                        if (superClass != java.lang.Object.class) {
                            sqlTable = superClass.getAnnotation(SqlTable.class);
                            if (sqlTable != null) {
                                break;
                            }
                            superClass = superClass.getSuperclass();
                        }
                        if (superClass == java.lang.Object.class) {
                            break;
                        }
                    } while (superClass != java.lang.Object.class);
                }
            }
            return sqlTable;
        });
    }

    /**
     * 校验两个SqlTable是否一致
     *
     * @param clazz1
     * @param clazz2
     * @return
     */
    public static boolean sqlTableIsConsistent(Class<?> clazz1, Class<?> clazz2) {
        SqlTable sqlTable = getSqlTable(clazz1);
        SqlTable sqlTable2 = getSqlTable(clazz2);
        if (sqlTable != null && sqlTable2 != null && sqlTable.schema().equals(sqlTable2.schema()) && sqlTable.value().equals(sqlTable2.value())) {
            return true;
        }
        return false;
    }

    /**
     * 根据类名获取表名信息
     *
     * @param clazz
     * @return
     */
    public static Table getTable(Class<?> clazz) {
        SqlTable sqlTable = getSqlTable(clazz);
        if (sqlTable != null) {
            return new Table(sqlTable.schema(), sqlTable.value(), StringUtil.isEmpty(sqlTable.alias()) ? sqlTable.value() : sqlTable.alias());
        }
        String tableName = clazz.getSimpleName();
        return new Table("", tableName, tableName);
    }

    /**
     * 优先根据@SqlBeanJoin注解获取，获取不到则从类获取
     *
     * @param clazz
     * @return
     */
    public static Table getTable(Class<?> clazz, SqlJoin sqlJoin) {
        Table table = new Table();
        if (sqlJoin != null) {
            table.setSchema(sqlJoin.schema());
            table.setName(sqlJoin.table());
            table.setAlias(sqlJoin.tableAlias());
        }
        Table classTable = getTable(clazz);
        if (StringUtil.isEmpty(table.getSchema())) {
            table.setSchema(classTable.getSchema());
        }
        if (StringUtil.isEmpty(table.getName())) {
            table.setName(classTable.getName());
        }
        if (StringUtil.isEmpty(table.getAlias())) {
            table.setAlias(classTable.getAlias());
        }
        if (StringUtil.isEmpty(table.getAlias())) {
            table.setAlias(classTable.getName());
        }
        return table;
    }

    /**
     * 返回带转义表名
     *
     * @param table
     * @param common
     * @return
     */
    public static String getTableName(Table table, Common common) {
        String tableName = getTableName(common, table.getSchema(), table.getName());
        return SqlBeanUtil.isToUpperCase(common) ? tableName.toUpperCase() : tableName;
    }

    /**
     * 返回带schema表名
     *
     * @param schema
     * @param tableName
     * @return
     */
    public static String getTableName(Common common, String schema, String tableName) {
        String escape = getEscape(common);
        if (StringUtil.isNotEmpty(schema)) {
            tableName = escape + schema + escape + SqlConstant.POINT + escape + tableName + escape;
        }
        return tableName;
    }


    /**
     * 获取Bean字段中实际对于的表字段(加数据库转义符)
     *
     * @param common
     * @param field
     * @param sqlTable
     * @return
     */
    public static String getTableFieldName(Common common, Field field, SqlTable sqlTable) {
        return getTableFieldName(common, getTableFieldName(field, sqlTable));
    }

    /**
     * 获取Bean字段中实际对于的表字段(加数据库转义符)
     *
     * @param common
     * @param name
     * @return
     */
    public static String getTableFieldName(Common common, String name) {
        if (SqlBeanUtil.isToUpperCase(common)) {
            name = name.toUpperCase();
        }
        String escape = SqlBeanUtil.getEscape(common);
        return escape + name + escape;
    }

    /**
     * 获取Bean字段中实际对于的表字段
     *
     * @param field
     * @param sqlTable
     * @return
     */
    public static String getTableFieldName(Field field, SqlTable sqlTable) {
        SqlColumn sqlColumn = field.getAnnotation(SqlColumn.class);
        String name = field.getName();
        if (sqlColumn != null && StringUtil.isNotBlank(sqlColumn.value())) {
            name = sqlColumn.value();
        } else {
            if (sqlTable == null || sqlTable.mapUsToCc()) {
                name = StringUtil.humpToUnderline(name);
            }
        }
        return name;
    }

    /**
     * 获取Bean字段中实际对于的表字段
     *
     * @param field
     * @param table
     * @param sqlTable
     * @return
     */
    public static Column getTableColumn(Field field, Table table, SqlTable sqlTable) {
        return new Column(table.getAlias(), getTableFieldName(field, sqlTable), "");
    }

    /**
     * 获得新的表字段名
     *
     * @param common
     * @param tableAlias
     * @param tableFieldName
     * @return
     */
    public static String getTableFieldFullName(Common common, String tableAlias, String tableFieldName) {
        if (common == null) {
            throw new IllegalArgumentException("common cannot be null");
        }
        if (tableFieldName == null) {
            throw new IllegalArgumentException("tableFieldName cannot be null");
        }
        String escape = getEscape(common);
        StringBuilder fullName = new StringBuilder();
        if (StringUtil.isNotEmpty(tableAlias)) {
            fullName.append(escape);
            fullName.append(tableAlias);
            fullName.append(escape);
            fullName.append(SqlConstant.POINT);
        }
        fullName.append(escape);
        fullName.append(SqlBeanUtil.isToUpperCase(common) ? tableFieldName.toUpperCase() : tableFieldName);
        fullName.append(escape);
        return fullName.toString();
    }

    /**
     * 获得新的表字段名
     *
     * @param common
     * @param column
     * @return
     */
    public static String getTableFieldFullName(Common common, Column column) {
        if (common == null) {
            throw new IllegalArgumentException("common cannot be null");
        }
        if (column == null) {
            throw new IllegalArgumentException("column cannot be null");
        }
        String escape = getEscape(common);
        StringBuilder fullName = new StringBuilder();
        if (StringUtil.isNotEmpty(column.getTableAlias())) {
            fullName.append(escape);
            fullName.append(column.getTableAlias());
            fullName.append(escape);
            fullName.append(SqlConstant.POINT);
        }
        fullName.append(column.isNameEscape() ? escape : "");
        fullName.append(column.getName(SqlBeanUtil.isToUpperCase(common)));
        fullName.append(column.isNameEscape() ? escape : "");
        return fullName.toString();
    }

    /**
     * 获得新的表字段名
     *
     * @param common
     * @param tableAlias
     * @param field
     * @param sqlTable
     * @return
     */
    public static String getTableFieldFullName(Common common, String tableAlias, Field field, SqlTable sqlTable) {
        return getTableFieldFullName(common, tableAlias, getTableFieldName(field, sqlTable));
    }

    /**
     * 返回from的表名包括别名
     *
     * @param schema
     * @param tableName
     * @param tableAlias
     * @param common
     * @return
     */
    public static String fromFullName(String schema, String tableName, String tableAlias, Common common) {
        String escape = SqlBeanUtil.getEscape(common);
        StringBuilder fromSql = new StringBuilder();
        if (SqlBeanUtil.isToUpperCase(common)) {
            tableName = tableName.toUpperCase();
            schema = schema.toUpperCase();
        }
        if (StringUtil.isNotEmpty(schema)) {
            fromSql.append(schema);
            fromSql.append(SqlConstant.POINT);
        }
        fromSql.append(tableName);
        fromSql.append(SqlConstant.SPACES);
        fromSql.append(escape);
        fromSql.append(tableAlias);
        fromSql.append(escape);
        return fromSql.toString();
    }

    /**
     * 获取id标识字段
     *
     * @param clazz
     * @return
     */
    public static Field getIdField(Class<?> clazz) throws SqlBeanException {
        if (clazz == null) {
            throw new IllegalArgumentException("clazz cannot be null");
        }
        List<Field> fieldList = getBeanAllField(clazz);
        Field idField = null;
        int existId = 0;
        for (Field field : fieldList) {
            SqlId sqlBeanField = field.getAnnotation(SqlId.class);
            if (sqlBeanField != null) {
                idField = field;
                existId++;
            }
            if (existId > 1) {
                throw new SqlBeanException("请正确标识@SqlId注解，id字段只能标识一个，但我们在《" + field.getDeclaringClass().getName() + "》此实体类或其父类找到了不止一处");
            }
        }
        if (existId == 0) {
            throw new SqlBeanException("请检查实体类《" + clazz.getName() + "》的id字段是否正确标识@SqlId注解");
        }
        return idField;
    }

    /**
     * 获取逻辑删除标识字段
     *
     * @param clazz
     * @return
     */
    public static Field getLogicallyField(Class<?> clazz) throws SqlBeanException {
        if (clazz == null) {
            throw new IllegalArgumentException("clazz cannot be null");
        }
        List<Field> fieldList = getBeanAllField(clazz);
        Field logicallyField = null;
        int existLogicallyField = 0;
        for (Field field : fieldList) {
            SqlLogically sqlLogically = field.getAnnotation(SqlLogically.class);
            if (sqlLogically != null) {
                logicallyField = field;
                existLogicallyField++;
            }
            if (existLogicallyField > 1) {
                throw new SqlBeanException("请正确标识@SqlLogically注解，逻辑删除字段只能标识一个，但我们在'" + field.getDeclaringClass().getName() + "'此实体类或其父类找到了不止一处");
            }
        }
        if (existLogicallyField == 0) {
            throw new SqlBeanException("请检查实体类申明逻辑删除的字段是否正确标识@SqlLogically注解");
        }
        return logicallyField;
    }

    /**
     * 获取列信息
     *
     * @param sqlBeanMeta
     * @param field
     * @return
     */
    public static ColumnInfo buildColumnInfo(SqlBeanMeta sqlBeanMeta, Field field) {
        return buildColumnInfo(sqlBeanMeta, field, field.getDeclaringClass().getAnnotation(SqlTable.class), field.getAnnotation(SqlColumn.class), field.getDeclaringClass());
    }

    /**
     * 获取列信息
     *
     * @param sqlBeanMeta
     * @param field
     * @param sqlTable
     * @param sqlColumn
     * @param constantClass
     * @return
     */
    public static ColumnInfo buildColumnInfo(SqlBeanMeta sqlBeanMeta, Field field, SqlTable sqlTable, SqlColumn sqlColumn, Class constantClass) {
        String columnName = SqlBeanUtil.getTableFieldName(field, sqlTable);
        ColumnInfo columnInfo = new ColumnInfo();
        columnInfo.setName(SqlBeanUtil.isToUpperCase(sqlBeanMeta) ? columnName.toUpperCase() : columnName);
        //是否主键 是否自增
        SqlId sqlId = field.getAnnotation(SqlId.class);
        columnInfo.setPk(sqlId != null);
        columnInfo.setAutoIncr(sqlId == null ? false : sqlId.type() == IdType.AUTO);
        JdbcType jdbcType;
        if (sqlColumn != null && sqlColumn.type() != JdbcType.NOTHING) {
            jdbcType = sqlColumn.type();
        } else {
            jdbcType = sqlBeanMeta.getDbType().getSqlDialect().getJdbcType(field);
        }
        columnInfo.setType(jdbcType.name());
        if (sqlColumn != null) {
            if (columnInfo.getPk()) {
                columnInfo.setNotnull(true);
            } else {
                columnInfo.setNotnull(sqlColumn.notNull());
            }
        }
        if (sqlColumn != null && sqlColumn.length() != 0) {
            columnInfo.setLength(sqlColumn.length());
            columnInfo.setScale(sqlColumn.scale());
        } else {
            columnInfo.setLength(jdbcType.getLength() > 0 ? jdbcType.getLength() : null);
        }
        if (sqlColumn != null && sqlColumn.scale() != 0) {
            columnInfo.setScale(sqlColumn.scale());
        } else {
            columnInfo.setScale(jdbcType.getScale() > 0 ? jdbcType.getScale() : null);
        }
        if (sqlColumn != null && StringUtil.isNotEmpty(sqlColumn.def())) {
            columnInfo.setDfltValue(sqlColumn.def());
        }
        if (sqlColumn != null && StringUtil.isNotEmpty(sqlColumn.remarks())) {
            columnInfo.setRemarks(sqlColumn.remarks());
        } else {
            columnInfo.setRemarks(SqlBeanUtil.getFiledRemarks(constantClass, columnInfo.getName()));
        }
        return columnInfo;
    }

    /**
     * 比较两个字段信息是否一致
     *
     * @param sqlBeanMeta
     * @param columnInfo
     * @param toColumnInfo
     * @return
     */
    public static List<AlterDifference> columnInfoCompare(SqlBeanMeta sqlBeanMeta, ColumnInfo columnInfo, ColumnInfo toColumnInfo) {
        List<AlterDifference> alterDifferenceList = new ArrayList<>();
        if (!columnInfo.getPk().equals(toColumnInfo.getPk())) {
            alterDifferenceList.add(AlterDifference.PK);
        }
        if (!columnInfo.getName().equals(toColumnInfo.getName())) {
            alterDifferenceList.add(AlterDifference.NAME);
        }
        if (!columnInfo.getType().equalsIgnoreCase(toColumnInfo.getType())) {
            alterDifferenceList.add(AlterDifference.TYPE);
        }
        if (columnInfo.getNotnull() != null && !columnInfo.getNotnull().equals(toColumnInfo.getNotnull())) {
            alterDifferenceList.add(AlterDifference.NOT_NULL);
        }
        if (sqlBeanMeta.getDbType() != DbType.SQLServer) {
            if ((columnInfo.getDfltValue() == null && columnInfo.getDfltValue() != toColumnInfo.getDfltValue()) || (columnInfo.getDfltValue() != null /*&& toColumnInfo.getDfltValue() != null*/ && !columnInfo.getDfltValue().equals(toColumnInfo.getDfltValue()))) {
                alterDifferenceList.add(AlterDifference.DFLT_VALUE);
            }
        }
        if (columnInfo.getLength() != null && !columnInfo.getLength().equals(toColumnInfo.getLength())) {
            //MySql8之后整数类型不支持设置长度
            if (sqlBeanMeta.getDbType() != DbType.MySQL || (sqlBeanMeta.getDbType() == DbType.MySQL && sqlBeanMeta.getDatabaseMajorVersion() < 8) || (sqlBeanMeta.getDbType() == DbType.MySQL && sqlBeanMeta.getDatabaseMajorVersion() >= 8 && !columnInfo.getType().equalsIgnoreCase("tinyint") && !columnInfo.getType().equalsIgnoreCase("smallint") && !columnInfo.getType().equalsIgnoreCase("mediumint") && !columnInfo.getType().equalsIgnoreCase("int") && !columnInfo.getType().equalsIgnoreCase("bigint"))) {
                alterDifferenceList.add(AlterDifference.LENGTH);
            }
        }
        if (columnInfo.getScale() != null && !columnInfo.getScale().equals(toColumnInfo.getScale())) {
            alterDifferenceList.add(AlterDifference.SCALE);
        }
        if (sqlBeanMeta.getDbType() != DbType.SQLite && sqlBeanMeta.getDbType() != DbType.Derby) {
            if (StringUtil.isNotBlank(columnInfo.getRemarks()) && !columnInfo.getRemarks().equals(toColumnInfo.getRemarks())) {
                alterDifferenceList.add(AlterDifference.REMARKS);
            }
        }
        if (sqlBeanMeta.getDbType() == DbType.MySQL || sqlBeanMeta.getDbType() == DbType.MariaDB) {
            if (!columnInfo.getAutoIncr().equals(toColumnInfo.getAutoIncr())) {
                alterDifferenceList.add(AlterDifference.AUTO_INCR);
            }
        }
        return alterDifferenceList;
    }

    /**
     * 是否使用逻辑删除
     *
     * @param clazz
     * @return
     */
    public static boolean checkLogically(Class<?> clazz) {
        List<Field> fieldList = getBeanAllField(clazz);
        for (Field field : fieldList) {
            SqlLogically sqlLogically = field.getAnnotation(SqlLogically.class);
            if (sqlLogically != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取乐观锁标识字段
     *
     * @param clazz
     * @return
     */
    public static Field getVersionField(Class<?> clazz) throws SqlBeanException {
        List<Field> fieldList = getBeanAllField(clazz);
        Field versionField = null;
        int existVersionField = 0;
        for (Field field : fieldList) {
            SqlVersion sqlVersion = field.getAnnotation(SqlVersion.class);
            if (sqlVersion != null) {
                versionField = field;
                existVersionField++;
            }
            if (existVersionField > 1) {
                throw new SqlBeanException("请正确标识SqlVersion注解，version字段只能标识一个，但我们在'" + field.getDeclaringClass().getName() + "'此实体类或其父类找到了不止一处");
            }
        }
        return versionField;
    }

    /**
     * 是否忽略该字段
     *
     * @param field
     * @return
     */
    public static boolean isIgnore(Field field) {
        if (Modifier.isStatic(field.getModifiers())) {
            return true;
        }
        SqlJoin sqlJoin = field.getAnnotation(SqlJoin.class);
        if (sqlJoin != null) {
            return true;
        }
        SqlColumn sqlColumn = field.getAnnotation(SqlColumn.class);
        if (sqlColumn != null) {
            return sqlColumn.ignore();
        }
        return false;
    }

    /**
     * 是否过滤该字段
     *
     * @param filterTableFields
     * @param tableFieldName
     * @return
     */
    public static boolean isFilter(String[] filterTableFields, String tableFieldName) {
        if (filterTableFields != null) {
            for (String filter : filterTableFields) {
                if (tableFieldName.equals(filter)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 是否过滤该字段
     *
     * @param filterColumns
     * @param column
     * @return
     */
    public static boolean isFilter(List<Column> filterColumns, Column column) {
        if (filterColumns != null) {
            for (Column item : filterColumns) {
                if ((StringUtil.isNotBlank(item.getTableAlias()) && column.getTableAlias().equals(item.getTableAlias()) && column.getName().equals(item.getName())) || (StringUtil.isBlank(item.getTableAlias()) && column.getName().equals(item.getName()))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 获取该bean所有字段（包括父类）
     *
     * @param clazz
     * @return
     */
    public static List<Field> getBeanAllField(Class<?> clazz) {
        if (clazz == null) {
            return Collections.emptyList();
        }
        List<Field> cached = beanAllFieldCache.get(clazz);
        if (cached != null) {
            return cached;
        }
        List<Field> fieldList = new ArrayList<>();
        Class<?> superClass = clazz.getSuperclass();
        do {
            if (superClass != null && superClass != java.lang.Object.class) {
                fieldList.addAll(Arrays.asList(superClass.getDeclaredFields()));
                superClass = superClass.getSuperclass();
            }
            if (superClass != null && superClass == java.lang.Object.class) {
                break;
            }
        } while (superClass != null && superClass != java.lang.Object.class);
        fieldList.addAll(Arrays.asList(clazz.getDeclaredFields()));
        List<Field> unmodifiable = Collections.unmodifiableList(fieldList);
        beanAllFieldCache.put(clazz, unmodifiable);
        return unmodifiable;
    }

    /**
     * 根据表字段获取java中对应的java字段
     *
     * @param fieldList
     * @param tableFieldName
     * @return
     */
    public static Field getFieldByTableFieldName(List<Field> fieldList, String tableFieldName) {
        Field thisField = null;
        for (Field field : fieldList) {
            SqlColumn sqlColumn = field.getAnnotation(SqlColumn.class);
            if (sqlColumn != null && sqlColumn.value().equals(tableFieldName) || StringUtil.humpToUnderline(field.getName()).equals(tableFieldName) || field.getName().equals(tableFieldName)) {
                thisField = field;
            }
        }
        return thisField;
    }

    /**
     * 返回查询的字段
     *
     * @param clazz
     * @param filterColumns
     * @param columnList
     * @return
     * @throws SqlBeanException
     */
    public static List<Column> getSelectColumns(Class<?> clazz, List<Column> filterColumns, List<Column> columnList) throws SqlBeanException {
        if (clazz == null) {
            return null;
        }
        Set<Column> columnSet = new LinkedHashSet<>();
        SqlTable sqlTable = getSqlTable(clazz);
        Table table = getTable(clazz);
        String tableAlias = table.getAlias();
        List<Field> fieldList = getBeanAllField(clazz);
        for (Field field : fieldList) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            SqlColumn sqlColumn = field.getAnnotation(SqlColumn.class);
            if (sqlColumn != null && sqlColumn.ignore()) {
                continue;
            }
            if (isFilter(filterColumns, getTableColumn(field, table, sqlTable))) {
                continue;
            }
            SqlJoin sqlJoin = field.getAnnotation(SqlJoin.class);
            boolean isBean = !SqlBeanUtil.isBaseType(field.getType());
            if (sqlJoin != null && isBean) {
                Class<?> subBeanClazz = field.getType();
                if (sqlJoin.from() != null && sqlJoin.from() != void.class) {
                    subBeanClazz = sqlJoin.from();
                }
                SqlTable subSqlTable = getSqlTable(subBeanClazz);
                //如果有指定查询的字段
                List<Field> subBeanFieldList = getBeanAllField(subBeanClazz);
                if (sqlJoin.value().length > 0 && !sqlJoin.value()[0].equals("")) {
                    for (String fieldName : sqlJoin.value()) {
                        Field javaField = getFieldByTableFieldName(subBeanFieldList, fieldName);
                        if (javaField == null) {
                            throw new SqlBeanException("该表连接查询的字段未与java字段关联：" + subBeanClazz.getName() + "类中找不到" + fieldName);
                        }
                        //表名、别名优先从@SqlBeanJoin注解中取，如果不存在则从类注解中取，再其次是类名
                        Table subTable = getTable(subBeanClazz, sqlJoin);
                        columnSet.add(new Column(subSqlTable == null ? "" : subTable.getAlias(), fieldName, getColumnAlias(subSqlTable == null ? "" : subTable.getAlias(), javaField.getName())));
                    }
                }
                //没有指定查询的字段则查询所有字段
                else {
                    //表名、别名优先从@SqlBeanJoin注解中取，如果不存在则从类注解中取，再其次是类名
                    Table subTable = getTable(subBeanClazz, sqlJoin);
                    for (Field subBeanField : subBeanFieldList) {
                        if (Modifier.isStatic(subBeanField.getModifiers())) {
                            continue;
                        }
                        SqlColumn subSqlColumn = subBeanField.getAnnotation(SqlColumn.class);
                        if (subSqlColumn != null && subSqlColumn.ignore()) {
                            continue;
                        }
                        if (isFilter(filterColumns, getTableColumn(subBeanField, subTable, subSqlTable))) {
                            continue;
                        }
                        columnSet.add(new Column(subTable.getAlias(), getTableFieldName(subBeanField, subSqlTable), getColumnAlias(subTable.getAlias(), subBeanField.getName())));
                    }
                }
            } else if (sqlJoin != null) {
                //获取SqlBeanJoin 注解中的查询字段
                String tableFieldName = sqlJoin.value()[0];
                if (StringUtil.isEmpty(tableFieldName)) {
                    throw new SqlBeanException("需指定连接查询的字段：@SqlJoin > value = {“xxx”}");
                }
                //可能会连同一个表，但连接条件不一样（这时表需要区分别名），所以查询的字段可能是同一个，但属于不同表别名下，所以用java字段名当sql字段别名不会出错
                String subTableAlias = StringUtil.isEmpty(sqlJoin.tableAlias()) ? sqlJoin.table() : sqlJoin.tableAlias();
                columnSet.add(new Column(subTableAlias, tableFieldName, getColumnAlias(subTableAlias, field.getName())));
            } else if (columnList == null || columnList.size() == 0) {
                columnSet.add(new Column(tableAlias, getTableFieldName(field, sqlTable), field.getName()));
            }
        }
        return new ArrayList<>(columnSet);
    }


    /**
     * 设置表连接
     *
     * @param clazz
     * @return
     */
    public static Map<String, Join> setJoin(Select select, Class<?> clazz) throws SqlBeanException, InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        SqlTable sqlTable = getSqlTable(clazz);
        List<Field> fieldList = getBeanAllField(clazz);
        Map<String, Join> joinFieldMap = new HashMap<>();
        for (Field field : fieldList) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            SqlJoin sqlJoin = field.getAnnotation(SqlJoin.class);
            if (sqlJoin == null) {
                continue;
            }
            //key是唯一的，作用是为了去重复，因为可能连接相同的表取不同的字段，但连接相同的表，连接条件不同是可以允许的
            String key = null;
            Class<?> subClazz = field.getType();
            Table table = null;
            //如果字段是bean
            boolean isBean = !SqlBeanUtil.isBaseType(field.getType());
            if (isBean) {
                if (sqlJoin.from() != null && sqlJoin.from() != void.class) {
                    subClazz = sqlJoin.from();
                }
                //表名、别名优先从@SqlBeanJoin注解中取，如果不存在则从类注解中取，再其次是类名
                table = getTable(subClazz, sqlJoin);
            }
            Join join = new Join();
            join.setJoinType(sqlJoin.type());
            join.setSchema(table != null ? table.getSchema() : sqlJoin.schema());
            join.setTableName(table != null ? table.getName() : sqlJoin.table());
            join.setTableAlias(table != null ? table.getAlias() : StringUtil.isEmpty(sqlJoin.tableAlias()) ? sqlJoin.table() : sqlJoin.tableAlias());

            //如果指定了条件对象
            if (sqlJoin.on() != null && sqlJoin.on() != void.class) {
                key = Md5Util.encode(sqlJoin.on() + sqlJoin.on().getClassLoader().toString());
                if (joinFieldMap.containsKey(key)) {
                    continue;
                }
                select.addJoin(join);
                JoinOn joinOn;
                //如果是非静态内部类
                if (sqlJoin.on().getEnclosingClass() != null && !Modifier.isStatic(sqlJoin.on().getModifiers())) {
                    Object enclosingInstance = sqlJoin.on().getEnclosingClass().newInstance();
                    Constructor<?> constructor = sqlJoin.on().getDeclaredConstructor(sqlJoin.on().getEnclosingClass());
                    joinOn = (JoinOn) constructor.newInstance(enclosingInstance);
                } else {
                    joinOn = (JoinOn) sqlJoin.on().newInstance();
                }
                Condition condition = new Condition();
                joinOn.on(condition);
                join.on().setDataList(condition.getDataList());
            } else {
                if (!isBean) {
                    key = Md5Util.encode(sqlJoin.table().toLowerCase() + sqlJoin.tableKeyword().toLowerCase() + sqlJoin.mainKeyword().toLowerCase());
                    if (joinFieldMap.containsKey(key)) {
                        continue;
                    }
                    select.addJoin(join);
                    join.on(join.getTableAlias(), sqlJoin.tableKeyword(), new RawValue(SqlBeanUtil.getTableFieldFullName(select, select.getTable().getAlias(), sqlJoin.mainKeyword())));
                } else {
                    String tableKeyword = getTableFieldName(getIdField(subClazz), sqlTable);
                    key = Md5Util.encode(join.getTableName().toLowerCase() + tableKeyword.toLowerCase() + sqlJoin.mainKeyword().toLowerCase());
                    if (joinFieldMap.containsKey(key)) {
                        continue;
                    }
                    select.addJoin(join);
                    join.on(join.getTableAlias(), tableKeyword, new RawValue(SqlBeanUtil.getTableFieldFullName(select, select.getTable().getAlias(), sqlJoin.mainKeyword())));
                }
            }
            joinFieldMap.put(key, join);
        }
        return joinFieldMap;
    }

    /**
     * 获取字段别名
     *
     * @param tableAlias
     * @param fieldName
     * @return
     */
    public static String getColumnAlias(String tableAlias, String fieldName) {
        return tableAlias + SqlConstant.UNDERLINE + fieldName;
    }

    /**
     * 获取处理后的where语句
     *
     * @param common
     * @param where
     * @param args
     * @return
     */
    public static String getCondition(Common common, String where, Object[] args) {
        if (where == null || where.equals("")) {
            return "";
        }
        StringBuilder conditionSql = new StringBuilder();
        int index = 0;
        for (char c : where.toCharArray()) {
            if ('?' == c) {
                StringBuilder value = new StringBuilder();
                Object[] objects = null;
                if (args[index] == null) {
                    objects = null;
                } else if (args[index].getClass().isArray()) {
                    objects = (Object[]) args[index];
                } else if (args[index] instanceof Collection) {
                    objects = ((Collection) args[index]).toArray();
                } else {
                    objects = new Object[]{args[index]};
                }
                if (objects != null) {
                    for (Object object : objects) {
                        value.append(getActualValue(common, object));
                        value.append(SqlConstant.COMMA);
                    }
                    value.delete(value.length() - SqlConstant.COMMA.length(), value.length());
                }
                conditionSql.append(value);
                index++;
            } else if ('&' == c) {
                conditionSql.append(getColumn(common, args[index]));
                index++;
            } else {
                conditionSql.append(c);
            }
        }
        return conditionSql.toString();
    }

    /**
     * 获取列
     *
     * @param common
     * @param columnObject
     * @return
     */
    public static String getColumn(Common common, Object columnObject) {
        StringBuilder value = new StringBuilder();
        Column column = null;
        if (columnObject instanceof Column) {
            column = (Column) columnObject;
        }
        if (column != null) {
            String escape = getEscape(common);
            if (StringUtil.isNotEmpty(column.getTableAlias())) {
                value.append(escape);
                value.append(column.getTableAlias());
                value.append(escape);
                value.append(SqlConstant.POINT);
            }
            value.append(column.getName());
        } else {
            value.append(columnObject);
        }
        return value.toString();
    }

    /**
     * 获取实际类型值
     *
     * @param common
     * @param value
     * @return
     */
    public static Object getActualValue(Common common, Object value) {
        if (value instanceof SqlFun) {
            return getSqlFunction(common, (SqlFun) value);
        } else if (value instanceof Column) {
            Column column = (Column) value;
            return SqlBeanUtil.getTableFieldFullName(common, column);
        } else if (value instanceof ColumnFun) {
            Column column = LambdaUtil.getColumn((ColumnFun) value);
            return SqlBeanUtil.getTableFieldFullName(common, column);
        } else if (value instanceof RawValue) {
            Object raw = ((RawValue) value).getValue();
            if (raw instanceof SqlFun) {
                return getSqlFunction(common, (SqlFun) raw);
            }
            return raw;
        }
        return getSqlValue(common, value);
    }

    /**
     * 获取Sql函数内容
     *
     * @param common
     * @param sqlFun
     * @return
     */
    public static String getSqlFunction(Common common, SqlFun sqlFun) {
        StringBuilder fun = new StringBuilder();
        fun.append(sqlFun.getFunName());
        fun.append(SqlConstant.BEGIN_BRACKET);
        if (sqlFun.getValues() != null && sqlFun.getValues().length > 0) {
            for (Object value : sqlFun.getValues()) {
                fun.append(SqlBeanUtil.getActualValue(common, value));
                fun.append(SqlConstant.COMMA);
            }
            fun.deleteCharAt(fun.length() - SqlConstant.COMMA.length());
        }
        fun.append(SqlConstant.END_BRACKET);
        return fun.toString();
    }

    /**
     * 获取处理后的where语句
     *
     * @param common
     * @param where
     * @param bean
     * @return
     */
    public static String getCondition(Common common, String where, Object bean) {
        if (where == null || bean == null) {
            return "";
        }
        String prefix = "${";
        String suffix = "}";
        StringBuilder conditionSql = new StringBuilder(where);
        int startIndex = conditionSql.indexOf(prefix);
        while (startIndex != -1) {
            int endIndex = conditionSql.indexOf(suffix, startIndex + prefix.length());
            if (endIndex != -1) {
                String name = conditionSql.substring(startIndex + prefix.length(), endIndex);
                int nextIndex = endIndex + suffix.length();
                String value = getSqlValue(common, ReflectJdkUtil.instance().get(bean.getClass(), bean, name));
                conditionSql.replace(startIndex, endIndex + suffix.length(), value);
                nextIndex = startIndex + value.length();
                startIndex = conditionSql.indexOf(prefix, nextIndex);
            } else {
                startIndex = -1;
            }
        }
        return conditionSql.toString();
    }

    /**
     * 获取字段类型
     *
     * @param type
     * @return
     */
    public static WhatType whatType(Class<?> type) {
        if (type == String.class || type == char.class || type == Character.class || type == byte.class || type == Byte.class) {
            return WhatType.STRING_TYPE;
        } else if (type == boolean.class || type == Boolean.class) {
            return WhatType.BOOL_TYPE;
        } else if (type == short.class || type == Short.class || type == int.class || type == Integer.class || type == long.class || type == Long.class || type == float.class || type == Float.class || type == double.class || type == Double.class) {
            return WhatType.VALUE_TYPE;
        }
        return jdk8DateType(type);
    }

    private static WhatType jdk8DateType(Class<?> type) {
        if (isAndroidEnv) {
            if (type == Date.class || type == java.sql.Date.class || type == java.sql.Timestamp.class || type == java.sql.Timestamp.class || type == java.sql.Time.class) {
                return WhatType.DATE_TYPE;
            }
        } else {
            if (type == Date.class || type == java.sql.Date.class || type == java.sql.Timestamp.class || type == java.sql.Timestamp.class || type == java.sql.Time.class || type == java.time.LocalDate.class || type == java.time.LocalTime.class || type == java.time.LocalDateTime.class) {
                return WhatType.DATE_TYPE;
            }
        }
        return WhatType.OBJECT_TYPE;
    }

    /**
     * 获取字段类型
     *
     * @param jdbcType
     * @return
     */
    public static WhatType whatType(JdbcType jdbcType) {
        WhatType whatType;
        switch (jdbcType) {
            case CHAR:
            case NCHAR:
            case VARCHAR:
            case VARCHAR2:
            case NVARCHAR:
            case TINYTEXT:
            case TEXT:
            case NTEXT:
            case LONGTEXT:
                whatType = WhatType.STRING_TYPE;
                break;
            case DATE:
            case TIME:
            case DATETIME:
            case DATETIME2:
            case TIMESTAMP:
                whatType = WhatType.DATE_TYPE;
                break;
            case BIT:
                whatType = WhatType.BOOL_TYPE;
                break;
            case TINYINT:
            case SMALLINT:
            case INTEGER:
            case BIGINT:
            case LONG:
            case FLOAT:
            case DOUBLE:
            case DECIMAL:
            case NUMERIC:
            case MONEY:
            case SMALLMONEY:
                whatType = WhatType.VALUE_TYPE;
                break;
            default:
                whatType = WhatType.OBJECT_TYPE;
                break;
        }
        return whatType;
    }

    /**
     * 该类型是否为基本类型
     *
     * @param type
     * @return
     */
    public static boolean isBaseType(Class<?> type) {
        if (type == String.class || type == char.class || type == Character.class || type == boolean.class || type == Boolean.class || type == byte.class || type == Byte.class || type == short.class || type == Short.class || type == int.class || type == Integer.class || type == long.class || type == Long.class || type == float.class || type == Float.class || type == double.class || type == Double.class || type == Date.class || type == java.sql.Date.class || type == java.sql.Timestamp.class || type == java.sql.Timestamp.class || type == java.sql.Time.class || type == BigDecimal.class || jdk8IsBaseType(type)) {
            return true;
        }
        return false;
    }

    private static boolean jdk8IsBaseType(Class<?> type) {
        if (!isAndroidEnv) {
            return type == java.time.LocalDate.class || type == java.time.LocalTime.class || type == java.time.LocalDateTime.class;
        }
        return false;
    }

    /**
     * 该类型是否为map
     *
     * @param type
     * @return
     */
    public static boolean isMap(Class<?> type) {
        if (type == Map.class || type == HashMap.class) {
            return true;
        }
        return false;
    }

    /**
     * 获取sql实际值(过滤sql注入)
     *
     * @param value
     * @return
     */
    public static String getSqlValue(Common common, Object value) {
        return getSqlValue(common, value, null);
    }

    /**
     * 获取sql实际值(过滤sql注入)
     *
     * @param value
     * @return
     */
    public static String getSqlValue(Common common, Object value, JdbcType jdbcType) {
        if (value == null) {
            return SqlConstant.NULL_VALUE;
        }
        String single_quotation_mark = SqlConstant.SINGLE_QUOTATION_MARK;
        String sqlValue;
        WhatType whatType;
        if (jdbcType == null) {
            whatType = whatType(value.getClass());
        } else {
            whatType = whatType(jdbcType);
        }
        switch (whatType) {
            case VALUE_TYPE:
                if (jdk8DateType(value.getClass()) == WhatType.DATE_TYPE) {
                    sqlValue = DateUtil.unifyDateToTimestamp(value).toString();
                } else {
                    sqlValue = value.toString();
                }
                break;
            case BOOL_TYPE:
                DbType dbType = common.getSqlBeanMeta().getDbType();
                if (dbType == DbType.Postgresql) {
                    sqlValue = Boolean.parseBoolean(value.toString()) ? "'1'" : "'0'";
                } else if (dbType == DbType.H2 || dbType == DbType.Hsql) {
                    sqlValue = Boolean.parseBoolean(value.toString()) ? "true" : "false";
                } else {
                    sqlValue = Boolean.parseBoolean(value.toString()) ? "1" : "0";
                }
                break;
            case DATE_TYPE:
                if (Objects.requireNonNull(common.getSqlBeanMeta().getDbType()) == DbType.Oracle) {
                    sqlValue = "to_timestamp(" + single_quotation_mark + DateUtil.unifyDateToString(value) + single_quotation_mark + ", 'syyyy-mm-dd hh24:mi:ss.ff')";
                } else {
                    sqlValue = single_quotation_mark + DateUtil.unifyDateToString(value) + single_quotation_mark;
                }
                break;
            default:
                if (value instanceof SqlEnum<?>) {
                    sqlValue = ((SqlEnum<?>) value).getCode().toString();
                } else if (value instanceof Date) {
                    sqlValue = single_quotation_mark + DateUtil.unifyDateToString(value) + single_quotation_mark;
                } else {
                    sqlValue = single_quotation_mark + filterSQLInject(value.toString()) + single_quotation_mark;
                }
                break;
        }
        return sqlValue;
    }

    /**
     * 是否使用分页
     *
     * @param select
     * @return
     */
    public static boolean isUsePage(Select select) {
        if (select.getPage() != null && select.getPage().getPagenum() != null && select.getPage().getPagesize() != null) {
            return true;
        }
        return false;
    }

    /**
     * 过滤sql注入
     *
     * @param str
     * @return
     */
    public static String filterSQLInject(String str) {
        if (str == null) {
            return "";
        }
        // 过滤常见SQL注入字符
        return str.replace("\\", "\\\\")
                  .replace("'", "\\'")
                  .replace("\"", "\\\"")
                  .replace("\0", "\\0")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t")
                  .replace("\b", "\\b")
                  .replace("\u001a", "\\u001a");
    }

    /**
     * 过滤LIKE查询中的通配符，防止通配符注入
     *
     * @param str
     * @return
     */
    public static String filterLikeWildcard(String str) {
        if (str == null) {
            return "";
        }
        // 先进行SQL注入过滤
        str = filterSQLInject(str);
        // 转义LIKE通配符：% 和 _
        return str.replace("%", "\\%")
                  .replace("_", "\\_");
    }

    /**
     * 获取不同数据库的转义符号
     *
     * @param common
     * @return
     */
    public static String getEscape(Common common) {
        String escape = SqlConstant.DOUBLE_ESCAPE_CHARACTER;
        DbType dbType = common.getSqlBeanMeta().getDbType();
        if (dbType == DbType.MySQL || dbType == DbType.MariaDB) {
            escape = SqlConstant.SINGLE_ESCAPE_CHARACTER;
        }
        return escape;
    }

    /**
     * 是否需要转大写
     *
     * @param common
     * @return
     */
    public static boolean isToUpperCase(Common common) {
        if (common.getSqlBeanMeta().getSqlBeanConfig().getToUpperCase() != null && common.getSqlBeanMeta().getSqlBeanConfig().getToUpperCase()) {
            return true;
        }
        return false;
    }

    /**
     * 是否需要转大写
     *
     * @param sqlBeanMeta
     * @return
     */
    public static boolean isToUpperCase(SqlBeanMeta sqlBeanMeta) {
        if (sqlBeanMeta.getSqlBeanConfig().getToUpperCase() != null && sqlBeanMeta.getSqlBeanConfig().getToUpperCase()) {
            return true;
        }
        return false;
    }

    /**
     * 更新乐观锁版本
     *
     * @param type
     * @param value
     * @return
     */
    public static Object updateVersion(Class<?> type, Object value) {
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class) {
            long val = 0;
            if (value != null) {
                val = Long.parseLong(value.toString());
            }
            val += 1;
            if (type == int.class || type == Integer.class) {
                return (int) val;
            }
            return val;
        }
        if (type == Date.class || type == java.sql.Timestamp.class) {
            return new Date();
        }
        return jdk8UpdateVersion(type, value);
    }

    private static Object jdk8UpdateVersion(Class<?> type, Object value) {
        if (!isAndroidEnv) {
            if (type == java.time.LocalDateTime.class) {
                return java.time.LocalDateTime.now();
            }
        }
        return null;
    }

    /**
     * 乐观锁字段是否有效
     *
     * @param type
     * @return
     */
    public static boolean versionEffectiveness(Class<?> type) {
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class || type == Date.class || jdk8VersionEffectiveness(type)) {
            return true;
        }
        return false;
    }

    public static boolean jdk8VersionEffectiveness(Class<?> type) {
        if (!isAndroidEnv) {
            return type == java.time.LocalDateTime.class;
        }
        return false;
    }

    /**
     * 获取object数组
     *
     * @param bean
     * @return
     */
    public static Object[] getObjectArray(Object bean) {
        Object[] objects;
        if (bean.getClass().isArray()) {
            objects = (Object[]) bean;
        } else if (bean instanceof Collection) {
            Collection<Object> list = (Collection<Object>) bean;
            objects = list.toArray();
        } else {
            objects = new Object[]{bean};
        }
        return objects;
    }

    /**
     * 赋予初始值
     *
     * @param type
     * @return
     */
    public static Object assignInitialValue(Class<?> type) {
        if (type == Byte.class || type == byte.class) {
            return new Byte("0");
        }
        if (type == Short.class || type == short.class) {
            return new Short("0");
        }
        if (type == Integer.class || type == int.class) {
            return 0;
        }
        if (type == Long.class || type == long.class) {
            return 0L;
        }
        if (type == Float.class || type == float.class) {
            return 0F;
        }
        if (type == Double.class || type == double.class) {
            return 0D;
        }
        if (type == Character.class || type == char.class) {
            return '\u0000';
        }
        if (type == Boolean.class || type == boolean.class) {
            return false;
        }
        if (type == String.class) {
            return "";
        }
        if (type == Date.class) {
            return new Date();
        }
        if (type == Timestamp.class) {
            return new Timestamp(System.currentTimeMillis());
        }
        if (type == BigDecimal.class) {
            return new BigDecimal(0);
        }
        return jdk8AssignInitialValue(type);
    }

    private static Object jdk8AssignInitialValue(Class<?> type) {
        if (!isAndroidEnv) {
            if (type == java.time.LocalDate.class) {
                return java.time.LocalDate.now();
            }
            if (type == java.time.LocalTime.class) {
                return java.time.LocalTime.now();
            }
            if (type == java.time.LocalDateTime.class) {
                return java.time.LocalDateTime.now();
            }
        }
        return null;
    }

    /**
     * 获取类型的默认值
     *
     * @param type
     * @return
     */
    public static Object getDefaultValue(Class<?> type) {
        if (type == byte.class) {
            return new Byte("0");
        }
        if (type == short.class) {
            return new Short("0");
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == char.class) {
            return '\u0000';
        }
        if (type == boolean.class) {
            return false;
        }
        return null;
    }

    /**
     * 获取转换后的值
     *
     * @param type
     * @param value
     * @return
     */
    public static Object getValueConvert(Class<?> type, Object value) {
        if (type == Byte.class || type == byte.class) {
            return Byte.parseByte(value.toString());
        }
        if (type == Short.class || type == short.class) {
            return Short.parseShort(value.toString());
        }
        if (type == Integer.class || type == int.class) {
            return Integer.parseInt(value.toString());
        }
        if (type == Long.class || type == long.class) {
            return Long.parseLong(value.toString());
        }
        if (type == Float.class || type == float.class) {
            return Float.parseFloat(value.toString());
        }
        if (type == Double.class || type == double.class) {
            return Double.parseDouble(value.toString());
        }
        if (type == Character.class || type == char.class) {
            return value.toString().charAt(0);
        }
        if (type == Boolean.class || type == boolean.class) {
            return Boolean.parseBoolean(value.toString());
        }
        if (type == String.class) {
            return value.toString();
        }
        if (type == Date.class || type == Timestamp.class) {
            return DateUtil.stringToDate(value.toString());
        }
        if (type == BigDecimal.class) {
            return new BigDecimal(value.toString());
        }
        return jdk8DateValueConvert(type, value);
    }

    private static Object jdk8DateValueConvert(Class<?> type, Object value) {
        try {
            Class.forName("android.content.Context");
        } catch (ClassNotFoundException e) {
            if (type == java.time.LocalDate.class) {
                java.time.LocalDateTime localDateTime = DateUtil.strTimeToLocalDateTime(value.toString());
                return localDateTime.toLocalDate();
            }
            if (type == java.time.LocalTime.class) {
                java.time.LocalDateTime localDateTime = DateUtil.strTimeToLocalDateTime(value.toString());
                return localDateTime.toLocalTime();
            }
            if (type == java.time.LocalDateTime.class) {
                return DateUtil.strTimeToLocalDateTime(value.toString());
            }
        }
        return null;
    }

    /**
     * 增加一列
     *
     * @param common
     * @param columnInfo
     * @return
     */
    public static String addColumn(Common common, ColumnInfo columnInfo, String afterColumnName) {
        StringBuilder sql = new StringBuilder();
        JdbcType jdbcType = JdbcType.getType(columnInfo.getType());
        sql.append(getTableFieldName(common, columnInfo.getName()));
        sql.append(SqlConstant.SPACES);
        sql.append(jdbcType.name());
        if (columnInfo.getLength() != null && columnInfo.getLength() > 0) {
            sql.append(SqlConstant.BEGIN_BRACKET);
            //字段长度
            sql.append(columnInfo.getLength());
            if (jdbcType.isFloat()) {
                sql.append(SqlConstant.COMMA);
                sql.append(columnInfo.getScale() == null ? 0 : columnInfo.getScale());
            }
            sql.append(SqlConstant.END_BRACKET);
        }
        //是否自增
        if (columnInfo.getAutoIncr() != null && columnInfo.getAutoIncr()) {
            if (common.getSqlBeanMeta().getDbType() == DbType.MySQL || common.getSqlBeanMeta().getDbType() == DbType.MariaDB) {
                sql.append(SqlConstant.SPACES);
                sql.append(SqlConstant.AUTO_INCREMENT);
            }
        }
        //默认值
        if (StringUtil.isNotBlank(columnInfo.getDfltValue())) {
            sql.append(SqlConstant.SPACES);
            sql.append(SqlConstant.DEFAULT);
            sql.append(SqlConstant.SPACES);
            sql.append(SqlBeanUtil.getSqlValue(common, columnInfo.getDfltValue(), jdbcType));
        }
        //是否为null
        if ((columnInfo.getNotnull() != null && columnInfo.getNotnull()) || columnInfo.getPk()) {
            sql.append(SqlConstant.SPACES);
            sql.append(SqlConstant.NOT_NULL);
        } else if (common instanceof Alter) {
            Alter alter = (Alter) common;
            if (alter.getType() == AlterType.MODIFY && columnInfo.getNotnull() != null && !columnInfo.getNotnull()) {
                sql.append(SqlConstant.SPACES);
                sql.append(SqlConstant.NULL);
            }
        }
        //如果是Mysql或MariaDB
        if (common.getSqlBeanMeta().getDbType() == DbType.MySQL || common.getSqlBeanMeta().getDbType() == DbType.MariaDB) {
            //存在备注
            if (StringUtil.isNotBlank(columnInfo.getRemarks())) {
                sql.append(SqlConstant.SPACES);
                sql.append(SqlConstant.COMMENT);
                sql.append(SqlConstant.SPACES);
                sql.append(SqlConstant.SINGLE_QUOTATION_MARK);
                sql.append(columnInfo.getRemarks());
                sql.append(SqlConstant.SINGLE_QUOTATION_MARK);
            }
            //存在排序
            if (StringUtil.isNotBlank(afterColumnName)) {
                String escape = SqlBeanUtil.getEscape(common);
                sql.append(SqlConstant.SPACES);
                sql.append(SqlConstant.AFTER);
                sql.append(SqlConstant.SPACES);
                sql.append(escape);
                sql.append(afterColumnName);
                sql.append(escape);
            }
        }
        return sql.toString();
    }

    /**
     * 参数为空抛出异常
     *
     * @param object
     * @param message
     */
    public static void isNull(Object object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 检查
     */
    public static void check(Common common) {
        isNull(common.getSqlBeanMeta(), "请设置sqlBeanConfig");
        isNull(common.getSqlBeanMeta().getDbType(), "请设置sqlBeanConfig -> dbType");
    }

    /**
     * 获取列字段对象
     *
     * @param field
     * @return Column
     */
    public static Column getColumnByField(Field field, Class<?> clazz) {
        Table table = SqlBeanUtil.getTable(clazz);
        return new Column(table.getAlias(), SqlBeanUtil.getTableFieldName(field, SqlBeanUtil.getSqlTable(field.getDeclaringClass())), "");
    }

    /**
     * 获取列字段对象
     *
     * @param lambda
     * @return Column
     */
    public static Column getColumnByLambda(SerializedLambda lambda) {
        String getter = lambda.getImplMethodName();
        String fieldName = Introspector.decapitalize(getter.replace("get", ""));
        try {
            Class<?> tableClass = Class.forName(lambda.getImplClass().replace("/", "."));
            String methodType = lambda.getInstantiatedMethodType();
            SqlTable sqlTable = SqlBeanUtil.getSqlTable(Class.forName(methodType.substring(methodType.indexOf("(L") + 2, methodType.indexOf(";")).replace("/", ".")));
            String tableAlias = sqlTable != null ? (StringUtil.isNotBlank(sqlTable.alias()) ? sqlTable.alias() : sqlTable.value()) : tableClass.getSimpleName();
            if ("class".equals(fieldName)) {
                return new Column(tableAlias, "*", "", false);
            }
            Field field = tableClass.getDeclaredField(fieldName);
            String columnName = SqlBeanUtil.getTableFieldName(field, sqlTable);
            return new Column(tableAlias, columnName, "");
        } catch (ClassNotFoundException e) {
            throw new SqlBeanException("找不到类：" + e.getMessage());
        } catch (NoSuchFieldException e) {
            logger.warning("找不到字段: " + getter + ", " + e.getMessage());
            throw new SqlBeanException("找不到字段,请检查:" + getter + "方法名与所对应的字段名是否符合标准,如：id字段对应的get方法名应该为getId()");
        }
    }

    /**
     * lambda数组转Column数组
     *
     * @param filterColumns
     * @param <T>
     * @param <R>
     * @return
     */
    public static <T, R> Column[] funToColumn(ColumnFun<T, R>[] filterColumns) {
        Column[] columns = new Column[filterColumns.length];
        for (int i = 0; i < filterColumns.length; i++) {
            columns[i] = LambdaUtil.getColumn(filterColumns[i]);
        }
        return columns;
    }

    /**
     * 复制对象
     *
     * @return
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public static <T> T copy(T target) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(target);
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
            return (T) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            logger.warning("对象复制失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 获取泛型类型
     *
     * @param clazz
     * @return
     */
    public static List<Class<?>> getGenericTypeBySuperclass(Class<?> clazz) {
        Type[] typeArray = new Type[]{clazz.getGenericSuperclass()};
        return getGenericType(typeArray);
    }

    /**
     * 获取泛型类型
     *
     * @param clazz
     * @return
     */
    public static List<Class<?>> getGenericTypeByInterfaces(Class<?> clazz) {
        return getGenericType(clazz.getGenericInterfaces());
    }

    /**
     * 获取泛型类型
     *
     * @param typeArray
     * @return
     */
    public static List<Class<?>> getGenericType(Type[] typeArray) {
        List<Class<?>> classes = new ArrayList<>();
        for (Type type : typeArray) {
            if (type instanceof ParameterizedType) {
                Type[] types = ((ParameterizedType) type).getActualTypeArguments();
                for (Type t : types) {
                    Class<?> trueTypeClass = (Class<?>) t;
                    try {
                        classes.add(SqlBeanUtil.class.getClassLoader().loadClass(trueTypeClass.getName()));
                    } catch (ClassNotFoundException ignored) {
                    }
                }
            }
        }
        return classes;
    }

    /**
     * 获取实体类字段类型
     *
     * @param field
     * @return
     */
    public static Class<?> getEntityClassFieldType(Field field) {
        Class<?> clazz = field.getType();
        if (clazz.isEnum() && SqlEnum.class.isAssignableFrom(clazz)) {
            Type[] typeArray = clazz.getGenericInterfaces();
            clazz = SqlBeanUtil.getGenericType(typeArray).get(0);
            if (clazz == null) {
                throw new SqlBeanException(field.getDeclaringClass().getName() + "实体类中的枚举类字段：" + field.getType().getSimpleName() + "在实现SqlEnum接口时必须指定泛型类型");
            }
            if (!SqlBeanUtil.isBaseType(clazz)) {
                throw new SqlBeanException(field.getDeclaringClass().getName() + "实体类中的枚举类字段：" + field.getType().getSimpleName() + "在实现SqlEnum接口时指定的泛型类型不支持");
            }
        }
        return clazz;
    }

    /**
     * 匹配枚举
     *
     * @param field
     * @param value
     * @return
     */
    public static SqlEnum<?> matchEnum(Field field, Object value) {
        SqlEnum<?>[] sqlEnums = (SqlEnum<?>[]) field.getType().getEnumConstants();
        for (SqlEnum<?> item : sqlEnums) {
            if (item.getCode().equals(value)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 获得实体类对应的常量类
     *
     * @param clazz
     * @return
     * @throws ClassNotFoundException
     */
    public static Class<?> getConstantClass(Class<?> clazz) {
        String name = clazz.getName();
        String constantClassName = name.substring(0, name.indexOf(clazz.getSimpleName())) + "sql." + clazz.getSimpleName() + "$";
        try {
            return Class.forName(constantClassName);
        } catch (ClassNotFoundException e) {
            logger.warning("ClassNotFoundException [" + e.getMessage() + "]");
        }
        return null;
    }

    /**
     * 获取实体类常量类中的备注
     *
     * @param constantClass
     * @return
     */
    public static String getBeanRemarks(Class<?> constantClass) {
        if (constantClass == null) {
            return "";
        }
        try {
            return (String) constantClass.getField("_remarks").get(null);
        } catch (NoSuchFieldException e) {
            logger.warning("NoSuchFieldException [" + e.getMessage() + "]");
        } catch (IllegalAccessException e) {
            logger.warning("IllegalAccessException [" + e.getMessage() + "]");
        }
        return "";
    }

    /**
     * 获取实体类常量类中的字段备注
     *
     * @param constantClass
     * @param fieldName
     * @return
     */
    public static String getFiledRemarks(Class<?> constantClass, String fieldName) {
        if (constantClass == null || StringUtil.isEmpty(fieldName)) {
            return "";
        }
        try {
            return ((Column) constantClass.getField(fieldName + "$").get(null)).getRemarks();
        } catch (IllegalAccessException e) {
            logger.warning("IllegalAccessException [" + e.getMessage() + "]");
        } catch (NoSuchFieldException e) {
            logger.warning("NoSuchFieldException [" + e.getMessage() + "]");
        }
        return "";
    }

    /**
     * 获取字段的JdbcType
     *
     * @param sqlBeanMeta
     * @param field
     * @return
     */
    public static JdbcType getJdbcType(SqlBeanMeta sqlBeanMeta, Field field) {
        SqlColumn sqlColumn = field.getAnnotation(SqlColumn.class);
        if (sqlColumn != null && sqlColumn.type() != JdbcType.NOTHING) {
            return sqlColumn.type();
        }
        return sqlBeanMeta.getDbType().getSqlDialect().getJdbcType(field);
    }

    /**
     * 获取JSON字符串
     *
     * @param sqlJSON
     * @param value
     * @return
     */
    public static String getJSONValue(SqlJSON sqlJSON, Object value) {
        JSONConvert jsonConvert = null;
        if (sqlJSON != null && sqlJSON.convert() != JSONConvert.class && sqlJSON.convert() != JSONConvertImpl.class) {
            try {
                jsonConvert = sqlJSON.convert().getDeclaredConstructor().newInstance();
            } catch (InstantiationException e) {
                logger.warning("JSON转换器实例化失败: " + e.getMessage());
            } catch (IllegalAccessException | NoSuchMethodException e) {
                logger.warning("JSON转换器访问失败: " + e.getMessage());
            } catch (InvocationTargetException e) {
                logger.warning("JSON转换器调用失败: " + e.getMessage());
            }
        } else {
            jsonConvert = new JSONConvertImpl();
        }
        if (value == null) {
            return null;
        }
        return jsonConvert.toJSONString(value);
    }

    /**
     * 将JSON字符串转换为实体类
     *
     * @param convert
     * @param json
     * @param field
     * @return
     */
    public static Object convertJSON(JSONConvert convert, String json, Field field) {
        if (StringUtil.isEmpty(json)) {
            return null;
        }
        if (json.trim().startsWith("[") && json.trim().endsWith("]")) {
            if (field.getType().isArray()) {
                return convertArray(convert.parseArray(json, field.getType().getComponentType()).toArray(), field.getType().getComponentType());
            } else {
                Type genericType = field.getGenericType();
                if (genericType instanceof ParameterizedType) {
                    ParameterizedType pt = (ParameterizedType) genericType;
                    for (Type typeArg : pt.getActualTypeArguments()) {
                        return convert.parseArray(json, (Class) typeArg);
                    }
                }
            }
            return convert.parse(json);
        } else if (json.trim().startsWith("{") && json.trim().endsWith("}")) {
            return convert.parseObject(json, field.getType());
        }
        return null;
    }

    /**
     * 转换数组类型
     *
     * @param array
     * @param targetType
     * @return
     */
    public static Object convertArray(Object array, Class<?> targetType) {
        if (!array.getClass().isArray()) return array;
        int length = Array.getLength(array);
        Object result = Array.newInstance(targetType, length);
        for (int i = 0; i < length; i++) {
            Object element = Array.get(array, i);
            Array.set(result, i, element != null ?
                    String.valueOf(element) : null);
        }
        return result;
    }


    /**
     * 将条件转换为Wrapper
     *
     * @param dataList
     * @return
     */
    public static Wrapper conditionDataToWrapper(List<ConditionData> dataList) {
        Wrapper wrapper = new Wrapper();
        for (ConditionData data : dataList) {
            Object itemData = data.getItem();
            if (itemData instanceof ConditionInfo) {
                ConditionInfo conditionInfo = (ConditionInfo) itemData;
                conditionInfo.setSqlLogic(data.getSqlLogic());
                wrapper.and(new Cond(conditionInfo));
            } else {
                List<ConditionData> subDataList = (List<ConditionData>) itemData;
                if (!dataList.isEmpty()) {
                    wrapper.and(conditionDataToWrapper(subDataList));
                }
            }
        }
        return wrapper;
    }

}
