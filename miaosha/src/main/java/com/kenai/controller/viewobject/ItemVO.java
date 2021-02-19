package com.kenai.controller.viewobject;

import lombok.Data;
import org.joda.time.DateTime;

import java.math.BigDecimal;

/**
 * 用来给前端用户展示
 */
@Data
public class ItemVO {
    private Integer id;

    // 商品名称
    private String title;

    // 商品价格
    private BigDecimal price;

    // 商品库存
    private Integer stock;

    // 商品描述
    private String description;

    // 商品销量
    private Integer sales;

    // 商品图片信息
    private String imgUrl;

    // 记录商品是否在秒杀活动中，0表示没有秒杀活动，1表示秒杀活动待开始，2表示秒杀活动进行中
    private Integer promoStatus;

    // 秒杀活动价格
    private BigDecimal promoPrice;

    // 秒杀活动ID
    private Integer promoId;

    // 秒杀活动开始时间
    private String startDate;
}
