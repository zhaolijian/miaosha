package com.kenai.service.impl;

import com.kenai.dao.PromoDOMapper;
import com.kenai.dataobject.PromoDO;
import com.kenai.error.BusinessException;
import com.kenai.error.EmBusinessError;
import com.kenai.service.ItemService;
import com.kenai.service.PromoService;
import com.kenai.service.UserService;
import com.kenai.service.model.ItemModel;
import com.kenai.service.model.PromoModel;
import com.kenai.service.model.UserModel;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class PromoServiceImpl implements PromoService {
    @Resource
    private PromoDOMapper promoDOMapper;

    @Resource
    private ItemService itemService;

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private UserService userService;

    @Override
    public PromoModel getPromoByItemId(Integer itemId) {
        // 获取对应商品的秒杀活动信息
        PromoDO promoDO = promoDOMapper.selectByItemId(itemId);
        PromoModel promoModel = convertFromDataObject(promoDO);
        if(promoModel == null){
            return null;
        }
        // 判断当前时间是否秒杀活动即将开始或正在进行
        if(promoModel.getStartDate().isAfterNow()){
            promoModel.setStatus(1);
        }else if(promoModel.getEndDate().isBeforeNow()){
            promoModel.setStatus(3);
        }else{
            promoModel.setStatus(2);
        }
        return promoModel;
    }

    /**
     * 发布促销活动
     * @param promoId
     */
    @Override
    public void publishpromo(Integer promoId) {
        // 通过活动id获取活动
        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);
        if(promoDO.getItemId() == null || promoDO.getItemId() == 0){
            return;
        }
        ItemModel itemModel = itemService.getItemById(promoDO.getItemId());
        // 将库存同步到redis中
        redisTemplate.opsForValue().set("promo_item_stock_" + itemModel.getId(), itemModel.getStock());
        // 将销量同步到redis中
        redisTemplate.opsForValue().set("promo_item_sales_" + itemModel.getId(), itemModel.getSales());
        // 将秒杀大闸的限制数字设置到redis中,并设置大闸的限制数量为库存的5倍
        redisTemplate.opsForValue().set("promo_door_count_" + promoId, itemModel.getStock() * 5);
    }

    /**
     * 生成秒杀活动令牌以及校验活动是否开始信息、商品信息、用户信息
     * @param promoId
     * @return
     */
    @Override
    public String generateSecondKillToken(Integer promoId, Integer itemId, Integer userId){
        // 判断库存是否已售罄，若对应的售罄key存在，则直接返回下单失败,之前在下订单方法中，现在前置到获取令牌方法中
        if(redisTemplate.hasKey("promo_item_stock_invalid_" + itemId)){
            return null;
        }
        // 校验是否有商品秒杀活动
        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);
        PromoModel promoModel = convertFromDataObject(promoDO);
        if(promoModel == null){
            return null;
        }
        // 校验秒杀活动是否开始
        if(promoModel.getStartDate().isAfterNow()){
            promoModel.setStatus(1);
        }else if(promoModel.getEndDate().isBeforeNow()){
            promoModel.setStatus(3);
        }else{
            promoModel.setStatus(2);
        }
        // 1表示秒杀未开始，2表示进行中，3表示已结束。如果秒杀活动不正在进行中，则不生成秒杀令牌
        if(promoModel.getStatus() != 2){
            return null;
        }
        // 校验商品信息是否存在
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if(itemModel == null){
            return null;
        }
        // 校验用户信息是否存在
        UserModel userModel = userService.getUserByIdInCache(userId);
        if(userModel == null){
            return null;
        }
        // 获取秒杀大闸的count数量
        long result = redisTemplate.opsForValue().increment("promo_door_count_" + promoId, -1);
        if(result < 0){
            return null;
        }
        // 生成秒杀令牌并存入redis缓存中，设置一个5分钟的有效期
        String token = UUID.randomUUID().toString().replace("-","");
        redisTemplate.opsForValue().set("promo_token_" + promoId + "_userid_" + userId + "_itemid_" + itemId, token);
        redisTemplate.expire("promo_token_" + promoId + "_userid_" + userId + "_itemid_" + itemId, 5, TimeUnit.MINUTES);
        return token;
    }

    private PromoModel convertFromDataObject(PromoDO promoDO){
        if(promoDO == null){
            return null;
        }
        PromoModel promoModel = new PromoModel();
        BeanUtils.copyProperties(promoDO, promoModel);
        promoModel.setPromoItemPrice(BigDecimal.valueOf(promoDO.getPromoItemPrice()));
        promoModel.setStartDate(new DateTime(promoDO.getStartDate()));
        promoModel.setEndDate(new DateTime(promoDO.getEndDate()));
        return promoModel;
    }
}
