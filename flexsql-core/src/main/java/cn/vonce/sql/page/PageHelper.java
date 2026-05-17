package cn.vonce.sql.page;


import cn.vonce.sql.bean.Order;
import cn.vonce.sql.bean.Paging;
import cn.vonce.sql.bean.Select;
import cn.vonce.sql.constant.SqlConstant;
import cn.vonce.sql.enumerate.SqlSort;
import cn.vonce.sql.uitls.ReflectUtil;
import cn.vonce.sql.uitls.SqlBeanUtil;
import cn.vonce.sql.uitls.StringUtil;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.logging.Logger;

/**
 * 分页助手，使用该分页助手必须实现PagingService 接口
 *
 * @param <T>
 * @author jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2017年3月14日上午11:59:50
 */
public class PageHelper<T> implements Serializable {

    private static final Logger logger = Logger.getLogger(PageHelper.class.getName());

    public static final String SELECT = "select";
    public static final String COUNT = "count";

    public PageHelper() {
    }

    /**
     * 实例化分页助手 - 手动设置参数
     *
     * @param pagenum  当前页
     * @param pagesize 每页数量
     */
    public PageHelper(Integer pagenum, Integer pagesize) {
        this(pagenum, pagesize, null, null);
    }

    /**
     * 实例化分页助手 - 手动设置参数
     *
     * @param pagenum   当前页
     * @param pagesize  每页数量
     * @param timestamp 时间戳
     */
    public PageHelper(Integer pagenum, Integer pagesize, String timestamp) {
        this(pagenum, pagesize, null, timestamp);
    }

    /**
     * 实例化分页助手 - 手动设置参数
     *
     * @param pagenum   当前页
     * @param pagesize  每页数量
     * @param timestamp 时间戳
     */
    public PageHelper(Integer pagenum, Integer pagesize, boolean startByZero, String timestamp) {
        this(pagenum, pagesize, startByZero, null, timestamp);
    }

    /**
     * 实例化分页助手 - 手动设置参数
     *
     * @param pagenum   当前页
     * @param pagesize  每页数量
     * @param orders    排序
     * @param timestamp 时间戳
     */
    public PageHelper(Integer pagenum, Integer pagesize, Order[] orders, String timestamp) {
        init(pagenum, pagesize, false, orders, timestamp);
    }

    /**
     * 实例化分页助手 - 手动设置参数
     *
     * @param pagenum   当前页
     * @param pagesize  每页数量
     * @param orders    排序
     * @param timestamp 时间戳
     */
    public PageHelper(Integer pagenum, Integer pagesize, boolean startByZero, Order[] orders, String timestamp) {
        init(pagenum, pagesize, startByZero, orders, timestamp);
    }

    /**
     * 初始化
     *
     * @param pagenum
     * @param pagesize
     * @param startByZero
     * @param orders
     * @param timestamp
     */
    public void init(Integer pagenum, Integer pagesize, boolean startByZero, Order[] orders, String timestamp) {
        if (this.pagenum == null) {
            this.pagenum = pagenum == null ? (startByZero ? 1 : 0) : pagenum;
        }
        if (this.pagesize == null) {
            this.pagesize = pagesize == null ? 10 : pagesize;
        }
        if (this.orders == null) {
            this.orders = orders;
        }
        if (this.timestamp == null) {
            this.setTimestamp(timestamp);
        }
        this.startByZero = startByZero;
    }

    private Integer pagenum;
    private Integer pagesize;
    private boolean startByZero;
    private Integer totalRecords;
    private Integer totalPage;
    private Order[] orders;
    private String timestamp;
    private List<T> dataList;
    private PagingMethod pagingMethod;

    /**
     * 获取当前页
     *
     * @return
     */
    public Integer getPagenum() {
        return pagenum;
    }

    /**
     * 获取每页数量
     *
     * @return
     */
    public Integer getPagesize() {
        return pagesize;
    }

    /**
     * 获取当前页是否从0开始
     *
     * @return
     */
    public boolean getStartByZero() {
        return startByZero;
    }

    /**
     * 获取总数量
     *
     * @return
     */
    public Integer getTotalRecords() {
        return totalRecords;
    }

    /**
     * 获取总页数
     *
     * @return
     */
    public Integer getTotalPage() {
        return totalPage;
    }

    public Order[] getOrders() {
        return orders;
    }

    /**
     * 获取分页时间戳
     *
     * @return
     */
    public String getTimestamp() {
        return timestamp;
    }

    /**
     * 设置分页时间戳(格式必须为："yyyy-MM-dd HH:mm:ss"， 不正确则设置为空)
     *
     * @param timestamp
     */
    private void setTimestamp(String timestamp) {
        boolean convertSuccess = true;
        try {
            if (StringUtil.isNotEmpty(timestamp)) {
                // 指定日期格式为四位年/两位月份/两位日期，注意yyyy-MM-dd区分大小写；
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                // 设置lenient为false.
                // 否则SimpleDateFormat会比较宽松地验证日期，比如2007/02/29会被接受，并转换成2007/03/01
                format.setLenient(false);
                format.parse(timestamp);
            } else {
                convertSuccess = false;
            }
        } catch (ParseException e) {
            // e.printStackTrace();
            // 如果throw java.text.ParseException或者NullPointerException，就说明格式不对
            convertSuccess = false;
        }
        if (convertSuccess) {
            this.timestamp = timestamp;
        }
    }

    /**
     * 获取分页列表数据
     *
     * @return
     */
    public List<T> getDataList() {
        return dataList;
    }

    /**
     * 设置分页列表数据
     *
     * @param dataList
     */
    public void setDataList(List<T> dataList) {
        this.dataList = dataList;
    }

    /**
     * 获取pagingMethod分页查询的方法名称<br>
     * 如果pagingMethod为空 默认 count 方法名为"count",select方法名为"select"
     *
     * @return
     */
    public PagingMethod getPagingMethod() {
        if (pagingMethod == null) {
            pagingMethod = new PagingMethod(COUNT, SELECT);
        }
        return pagingMethod;
    }

    /**
     * 设置pagingMethod，count(统计),select(查询)方法的名称
     *
     * @param pagingMethod
     */
    public void setPagingMethod(PagingMethod pagingMethod) {
        this.pagingMethod = pagingMethod;
    }

    /**
     * 总数量、总页数处理
     *
     * @param count
     */
    public void dispose(int count) {
        // 保存总数
        this.totalRecords = count;
        // 计算最多有几页
        this.totalPage = totalRecords % pagesize == 0 ? totalRecords / pagesize : totalRecords / pagesize + 1;
    }

    /**
     * 获得分页数据
     *
     * @param select      查询条件拓展
     * @param pageService 分页接口
     * @return
     */
    public PageHelper<T> paging(Select select, PagingService pageService) {
        return paging(null, select, pageService);
    }

    /**
     * 获得分页数据
     *
     * @param returnType
     * @param select      SqlBean
     * @param pageService 分页接口
     * @return
     */
    public PageHelper<T> paging(Class<T> returnType, Select select, PagingService pageService) {
        try {
            Class<?> clazz = pageService.getClass();
            // 衍生一个对象用于select查询
            Select countSelect = SqlBeanUtil.copy(select);
            //标识为克隆对象
            ReflectUtil.instance().invoke(Select.class, countSelect, "count", true);
            //设置分页
            select.page(pagenum, pagesize, startByZero);
            select.orderBy(orders);
            //参数class类型
            Class[] paramClasses = {Select.class};
            //查询统计参数
            Object[] countParams = {countSelect};
            //查询列表参数
            Object[] listParams = {select};
            if (returnType != null) {
                paramClasses = new Class[]{Class.class, Select.class};
                countParams = new Object[]{returnType, countSelect};
                listParams = new Object[]{returnType, select};
            }
            // 计算共有几页
            this.dispose((int) ReflectUtil.instance().invoke(clazz, pageService, this.getPagingMethod().getCount(), paramClasses, countParams));
            // 分页数据列表
            this.setDataList((List<T>) ReflectUtil.instance().invoke(clazz, pageService, this.getPagingMethod().getSelect(), paramClasses, listParams));
        } catch (Exception e) {
            logger.warning("Failed to execute paging query: " + e.getMessage());
        }
        return this;
    }

    /**
     * 获取排序对象
     *
     * @param sortdatafields
     * @param sortorders
     * @return
     */
    public Order[] getOrder(String[] sortdatafields, String[] sortorders) {
        Order[] orders = null;
        if (sortdatafields != null && sortdatafields.length > 0) {
            orders = new Order[sortdatafields.length];
            for (int i = 0; i < sortdatafields.length; i++) {
                Order order = null;
                String field = sortdatafields[i];
                String sort = null;
                try {
                    sort = sortorders[i];
                } catch (Exception e) {
                }
                if (StringUtil.isNotEmpty(field) && field.indexOf(SqlConstant.POINT) > -1) {
                    String[] tableNameAndField = field.split("\\" + SqlConstant.POINT);
                    order = new Order(tableNameAndField[0], tableNameAndField[1], SqlSort.get(sort));
                } else {
                    order = new Order(field, SqlSort.get(sort));
                }
                orders[i] = order;
            }
        }
        return orders;
    }

    /**
     * 返回结果集
     *
     * @return
     */
    public ResultData<T> getResultData() {
        ResultData<T> resultData = new ResultData();
        resultData.setData(this.getDataList());
        resultData.setPagenum(this.getPagenum());
        resultData.setPagesize(this.getPagesize());
        resultData.setTotalRecords(this.getTotalRecords());
        resultData.setTotalPage(this.getTotalPage());
        resultData.setTimestamp(this.getTimestamp());
        return resultData;
    }

    /**
     * 返回分页参数对象
     *
     * @return
     */
    public Paging getPaging() {
        return new Paging(this.pagenum, this.pagesize, getStartByZero(), this.orders);
    }

    /**
     * 实现分类接口的方法
     *
     * @author jovi
     * @version 1.0
     */
    public static class PagingMethod {
        private String count;
        private String select;

        public PagingMethod() {
            super();
        }

        public PagingMethod(String count, String select) {
            super();
            this.count = count;
            this.select = select;
        }

        public String getCount() {
            return count;
        }

        public void setCount(String count) {
            this.count = count;
        }

        public String getSelect() {
            return select;
        }

        public void setSelect(String select) {
            this.select = select;
        }

    }
}
