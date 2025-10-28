package cn.vonce.sql.enumerate;

/**
 * 填充类型
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2022/6/24 21:04
 */
public enum FillWith {

    /**
     * 新增时如果数据为null则填充默认值
     */
    INSERT,
    /**
     * 更新时如果数据为nul填则充l默认值
     */
    UPDATE,
    /**
     * 新增和更新同时，如果数据为nul填则充默认值
     */
    TOGETHER,
    /**
     * 更新时每次都填充默认值
     */
    UPDATE_EVERYTIME

}
