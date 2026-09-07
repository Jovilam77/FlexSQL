import cn.vonce.sql.annotation.SqlColumn;
import cn.vonce.sql.annotation.SqlTable;
import java.math.BigDecimal;

/**
 * 订单详情
 *
 * @author Jovi《imjovi@qq.com》
 * @version 1.0《2025/2/11 14:37》
 */
@SqlTable(value = "t_pay_order_details", autoAlter = true)
public class PayOrderDetails {

    /**
     * 订单id
     */
    @SqlColumn(notNull = true)
    private Long orderId;

    /**
     * 商品id（订单明细的商品id，如果是商标、设计、专利等则为服务id，如果是商品则为商品id）
     */
    @SqlColumn(notNull = true)
    private Long goodsId;

    /**
     * 商标小类数量
     */
    @SqlColumn(notNull = true)
    private Integer quantity;

    /**
     * 商标产品基础服务价格
     */
    @SqlColumn(notNull = true)
    private BigDecimal basePrice;

    /**
     * 商标产品附加服务价格(商标小类超过10个)
     */
    @SqlColumn(notNull = true)
    private BigDecimal additionalPrice;

    /**
     * 总价
     */
    @SqlColumn(notNull = true)
    private BigDecimal totalPrice;

    /**
     * 备注
     */
    private String remark;

}
