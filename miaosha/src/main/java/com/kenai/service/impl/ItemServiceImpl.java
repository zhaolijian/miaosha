package com.kenai.service.impl;

import com.kenai.dao.ItemDOMapper;
import com.kenai.dao.ItemStockDOMapper;
import com.kenai.dao.StockLogDOMapper;
import com.kenai.dataobject.ItemDO;
import com.kenai.dataobject.ItemStockDO;
import com.kenai.dataobject.PromoDO;
import com.kenai.dataobject.StockLogDO;
import com.kenai.error.BusinessException;
import com.kenai.error.EmBusinessError;
import com.kenai.mq.MqProducer;
import com.kenai.service.ItemService;
import com.kenai.service.PromoService;
import com.kenai.service.model.ItemModel;
import com.kenai.service.model.PromoModel;
import com.kenai.validator.ValidationResult;
import com.kenai.validator.ValidatorImpl;
import com.mysql.fabric.FabricCommunicationException;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ItemServiceImpl implements ItemService {
    @Resource
    private ValidatorImpl validator;

    @Resource
    private ItemDOMapper itemDOMapper;

    @Resource
    private ItemStockDOMapper itemStockDOMapper;

    @Resource
    private PromoService promoService;

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private MqProducer mqProducer;

    @Resource
    private StockLogDOMapper stockLogDOMapper;

    private ItemDO convertItemDOFromItemModel(ItemModel itemModel){
        if(itemModel == null){
            return null;
        }
        ItemDO itemDO = new ItemDO();
        BeanUtils.copyProperties(itemModel, itemDO);
        // 因为数据库中是double类型，而model中是BigDecimal类型，需要转换下。java中double、float类型有精度问题
        itemDO.setPrice(itemModel.getPrice().doubleValue());
        return itemDO;
    }

    private ItemStockDO convertItemStockDOFromItemModel(ItemModel itemModel){
        if(itemModel == null){
            return null;
        }
        ItemStockDO itemStockDO = new ItemStockDO();
        itemStockDO.setItemId(itemModel.getId());
        itemStockDO.setStock(itemModel.getStock());
        return itemStockDO;
    }


    @Override
    @Transactional
    public ItemModel createItem(ItemModel itemModel) throws BusinessException {
        // 校验入参
        ValidationResult result = validator.validate(itemModel);
        if(result.isHasErrors()){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, result.getErrMsg());
        }

        // 转化itemmodel到dataobject
        ItemDO itemDO = convertItemDOFromItemModel(itemModel);

        // 写入数据库
        itemDOMapper.insertSelective(itemDO);
        // 为了拿到id字段值(xml中设置了自增主键，itemDO会获取到该值)，以用于itemstock表的插入
        itemModel.setId(itemDO.getId());

        ItemStockDO itemStockDO = convertItemStockDOFromItemModel(itemModel);
        itemStockDOMapper.insertSelective(itemStockDO);

        // 返回创建完成的对象
        return getItemById(itemModel.getId());
    }

    /**
     * 根据商品销量降序查询所有商品
     * @return
     */
    @Override
    public List<ItemModel> listItem() {
        List<ItemDO> itemDOList = itemDOMapper.listItem();
//        java8的写法：将itemDOList集合中的每一个元素转换为itemModel
        List<ItemModel> itemModelList = itemDOList.stream().map(itemDO -> {
            ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(itemDO.getId());
            ItemModel itemModel = convertModelFromDataObject(itemDO, itemStockDO);
            return itemModel;
        }).collect(Collectors.toList());
        return itemModelList;
    }

    /**
     * 获取商品信息
     * @param id
     * @return
     */
    @Override
    public ItemModel getItemById(Integer id) {
        ItemDO itemDO = itemDOMapper.selectByPrimaryKey(id);
        if(itemDO == null){
            return null;
        }
        // 获取库存数量
        ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(itemDO.getId());
        ItemModel itemModel = convertModelFromDataObject(itemDO, itemStockDO);
        // 获取活动商品信息
        PromoModel promoModel = promoService.getPromoByItemId(itemModel.getId());
        // 说明该商品有促销活动处于未结束状态（包括未开始和进行中）
        if(promoModel != null && promoModel.getStatus() != 3){
            itemModel.setPromoModel(promoModel);
        }
        return itemModel;
    }

    /**
     * item及promo model缓存模型
     */
    public ItemModel getItemByIdInCache(Integer itemId){
        ItemModel itemModel = (ItemModel)redisTemplate.opsForValue().get("item_validate_" + itemId);
        if(itemModel == null){
            itemModel = this.getItemById(itemId);
            if(itemModel == null){
                return null;
            }else{
                redisTemplate.opsForValue().set("item_validate_" + itemId, itemModel);
                redisTemplate.expire("item_validate_" + itemId, 10, TimeUnit.MINUTES);
            }
        }
        return itemModel;
    }

    /**
     * 减库存
     * @param itemId
     * @param amount
     * @return
     * @throws BusinessException
     */
    @Override
    @Transactional
    public boolean decreaseStock(Integer itemId, Integer amount) throws BusinessException {
//        int affectedRow = itemStockDOMapper.decreaseStock(itemId, amount);
        // 减缓存中的库存操作，返回剩余库存数量
        long result = redisTemplate.opsForValue().increment("promo_item_stock_" + itemId, amount * -1);
        if(result > 0){
//            // 发送消息
//            boolean mqResult = mqProducer.asyncReduceStock(itemId, amount);
//            // 发送消息失败，则将库存补回去，返回false
//            if(!mqResult){
//                redisTemplate.opsForValue().increment("promo_item_stock_" + itemId, amount);
//                return false;
//            }
            return true;
//            打上库存售罄标识
        }else if(result == 0){
            redisTemplate.opsForValue().set("promo_item_stock_invalid_" + itemId, "true");
//            返回更新库存成功
            return true;
        }else{
            // 更新库存失败，将库存补回去
            increaseStock(itemId, amount);
            return false;
        }
    }

    @Override
    public boolean increaseStock(Integer itemId, Integer amount) throws BusinessException {
        redisTemplate.opsForValue().increment("promo_item_stock_" + itemId, amount);
        return true;
    }

    /**
     * 异步更新库存
     * @param itemId
     * @param amount
     * @return
     */
    @Override
    public boolean asyncDecreaseStock(Integer itemId, Integer amount) {
        // 发送消息
        return mqProducer.asyncReduceStock(itemId, amount);
    }

    /**
     * 销量增加
     * @param itemId
     * @param amount
     */
    @Override
    @Transactional
    public void increaseSales(Integer itemId, Integer amount) {
        redisTemplate.opsForValue().increment("promo_item_sales_" + itemId, amount);
    }

    /**
     * 初始化库存流水
     * @param itemId
     * @param amount
     */
    @Override
    @Transactional
    public String initStockLog(Integer itemId, Integer amount) {
        StockLogDO stockLogDO = new StockLogDO();
        stockLogDO.setItemId(itemId);
        stockLogDO.setAmount(amount);
        stockLogDO.setStockLogId(UUID.randomUUID().toString().replace("-",""));
        stockLogDO.setStatus(1);
        stockLogDOMapper.insertSelective(stockLogDO);
        return stockLogDO.getStockLogId();
    }

    private ItemModel convertModelFromDataObject(ItemDO itemDO, ItemStockDO itemStockDO){
        if(itemDO == null){
            return null;
        }
        ItemModel itemModel = new ItemModel();
        BeanUtils.copyProperties(itemDO, itemModel);
        itemModel.setPrice(BigDecimal.valueOf(itemDO.getPrice()));
        itemModel.setStock(itemStockDO.getStock());
        return itemModel;
    }
}
