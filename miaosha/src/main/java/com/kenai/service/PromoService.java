package com.kenai.service;

import com.kenai.error.BusinessException;
import com.kenai.service.model.PromoModel;

public interface PromoService {
    /**
     * 根据itemId获取即将进行的或正在进行的秒杀活动
     * @param itemId
     * @return
     */
    PromoModel getPromoByItemId(Integer itemId);

    /**
     * 促销活动发布
     */
    void publishpromo(Integer promoId);

    /**
     * 生成秒杀活动令牌以及校验活动是否开始信息、商品信息、用户信息
     * @return
     */
    String generateSecondKillToken(Integer promoId, Integer itemId, Integer userId) throws BusinessException;
}
