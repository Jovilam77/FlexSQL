package cn.vonce.sql.java.service;

import cn.vonce.sql.bean.ColumnInfo;
import cn.vonce.sql.bean.CommonCondition;
import cn.vonce.sql.bean.Condition;
import cn.vonce.sql.config.SqlBeanMeta;
import cn.vonce.sql.define.ConditionHandle;
import cn.vonce.sql.enumerate.DbType;
import cn.vonce.sql.enumerate.JdbcType;
import cn.vonce.sql.helper.Wrapper;
import cn.vonce.sql.uitls.SqlBeanUtil;
import cn.vonce.sql.uitls.StringUtil;

import java.util.Collection;
import java.util.List;

/**
 * SqlBeanServiceImpl 抽象类 获取配置通用实现
 *
 * @param
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 */
public abstract class BaseSqlBeanServiceImpl<T> {


    public abstract SqlBeanMeta getSqlBeanMeta();

    /**
     * 处理字段信息
     *
     * @param columnInfoList
     */
    public void handleColumnInfo(List<ColumnInfo> columnInfoList) {
        DbType dbType = getSqlBeanMeta().getDbType();
        if (dbType == DbType.Derby) {
            for (ColumnInfo info : columnInfoList) {
                String[] values = info.getType().split(" ");
                //设置字段类型
                info.setType(StringUtil.getWord(values[0]));
                //如果存在空格分割说明字段名后面存在NOT NULL，即不能为空
                if (values.length > 1) {
                    info.setNotnull(true);
                } else {
                    info.setNotnull(false);
                }
                //设置字段长度范围
                String range[] = StringUtil.getBracketContent(values[0]).split(",");
                info.setLength("".equals(range[0]) ? 0 : Long.parseLong(range[0]));
                info.setScale(range.length == 1 ? 0 : Integer.parseInt(range[1]));
                info.setDfltValue(processDefaultValue(info.getDfltValue()));
            }
        } else if (dbType == DbType.H2 || dbType == DbType.Postgresql) {
            Boolean toUpperCase = getSqlBeanMeta().getSqlBeanConfig().getToUpperCase();
            for (ColumnInfo info : columnInfoList) {
                if ("CHARACTER VARYING".equalsIgnoreCase(info.getType())) {
                    info.setType(JdbcType.VARCHAR.getName(toUpperCase));
                } else if ("CHARACTER LARGE OBJECT".equalsIgnoreCase(info.getType())) {
                    info.setType(JdbcType.LONGTEXT.getName(toUpperCase));
                } else if ("BINARY VARYING".equalsIgnoreCase(info.getType())) {
                    info.setType(JdbcType.BLOB.getName(toUpperCase));
                } else if ("BINARY LARGE OBJECT".equalsIgnoreCase(info.getType())) {
                    info.setType(JdbcType.LONGBLOB.getName(toUpperCase));
                } else if ("TIMESTAMP WITHOUT TIME ZONE".equalsIgnoreCase(info.getType())) {
                    info.setType(JdbcType.TIMESTAMP.getName(toUpperCase));
                } else if ("TIME WITHOUT TIME ZONE".equalsIgnoreCase(info.getType())) {
                    info.setType(JdbcType.TIME.getName(toUpperCase));
                }
                String deftValue = info.getDfltValue();
                if (deftValue != null && StringUtil.isNotBlank(deftValue) && deftValue.indexOf("'::") > -1) {
                    info.setDfltValue(deftValue.substring(1, deftValue.indexOf("'::")));
                }
            }
        } else if (dbType == DbType.Oracle || dbType == DbType.DB2) {
            for (ColumnInfo info : columnInfoList) {
                info.setDfltValue(processDefaultValue(info.getDfltValue()));
            }
        } else if (dbType == DbType.SQLite) {
            for (ColumnInfo info : columnInfoList) {
                if (info.getType().indexOf("(") > -1 && info.getType().indexOf(")") > -1) {
                    String range = info.getType().substring(info.getType().indexOf("(") + 1, info.getType().indexOf(")"));
                    if (range.indexOf(",") > -1) {
                        String ranges[] = range.split(",");
                        info.setLength(Long.parseLong(ranges[0]));
                        info.setScale(Integer.parseInt(ranges[1]));
                    } else {
                        info.setLength(Long.parseLong(range));
                    }
                    info.setType(info.getType().substring(0, info.getType().indexOf("(")));
                }
                info.setDfltValue(processDefaultValue(info.getDfltValue()));
            }
        }
    }

    private String processDefaultValue(String dfltValue) {
        if (dfltValue != null) {
            dfltValue = dfltValue.trim();
            if (dfltValue.charAt(0) == '\'' && dfltValue.charAt(dfltValue.length() - 1) == '\'') {
                return dfltValue.substring(1, dfltValue.length() - 1);
            } else {
                return dfltValue;
            }
        }
        return null;
    }

    /**
     * 设置自增id（统一使用 getGeneratedKeys 回填，取代两段式 last_insert_id）
     *
     * @param clazz    实体类（用于取 @SqlId 字段定义）
     * @param beanList 待回填的 bean 集合（与 keys 顺序一致）
     * @param keys     与 bean 顺序一致的数据库生成键值（来自 getGeneratedKeys）
     * @param <T>
     */
    public <T> void setAutoIncrId(Class<?> clazz, Collection<T> beanList, List<Long> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        SqlBeanUtil.setAutoIncrId(clazz, keys, beanList.toArray());
    }

    protected void conditionHandle(CommonCondition<?> condition, ConditionHandle<T> cond) {
        Condition<T> result = new Condition<>();
        cond.handle(result);
        condition.where().getDataList().addAll(result.getDataList());
    }

    protected Wrapper conditionHandle(ConditionHandle<T> cond) {
        Condition<T> result = new Condition<>();
        cond.handle(result);
        return SqlBeanUtil.conditionDataToWrapper(result.getDataList());
    }

}
