package com.kenai.service.model;

import lombok.Data;
import org.joda.time.DateTime;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class PromoModel implements Serializable{
    private Integer id;

    /**
     * 秒杀活动状态：1表示未开始，2表示进行中，3表示已结束
     */
    private Integer status;

    /**
     * 秒杀活动名称
     */
    private String promoName;

    /**
     * 秒杀活动开始时间
     */
    private DateTime startDate;

    /**
     * 秒杀活动结束时间
     */
    private DateTime endDate;

    /**
     * 秒杀活动商品
     */
    private Integer itemId;

    /**
     * 秒杀活动商品价格
     */
    private BigDecimal promoItemPrice;
}
