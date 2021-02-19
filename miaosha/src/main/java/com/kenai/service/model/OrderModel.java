package com.kenai.service.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 用户下单的交易模型
 */
@Data
public class OrderModel implements Serializable{
    /**
     * 下单号
     */
    private String id;

    /**
     * 若非空，表示以秒杀商品方式下单
     */
    private Integer promoId;

    /**
     * 购买商品的单价,若promoId非空，则表示秒杀商品价格
     */
    private BigDecimal itemPrice;

    /**
     * 购买用户id
     */
    private Integer userId;

    /**
     * 购买的商品id
     */
    private Integer itemId;

    /**
     * 购买数量
     */
    private Integer amount;

    /**
     * 购买金额,若promoId非空，则表示秒杀商品价格
     */
    private BigDecimal orderPrice;
}
