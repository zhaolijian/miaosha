package com.kenai.service.impl;

import com.kenai.dao.OrderDOMapper;
import com.kenai.dao.SequenceDOMapper;
import com.kenai.dao.StockLogDOMapper;
import com.kenai.dataobject.OrderDO;
import com.kenai.dataobject.SequenceDO;
import com.kenai.dataobject.StockLogDO;
import com.kenai.error.BusinessException;
import com.kenai.error.EmBusinessError;
import com.kenai.service.ItemService;
import com.kenai.service.OrderService;
import com.kenai.service.UserService;
import com.kenai.service.model.ItemModel;
import com.kenai.service.model.OrderModel;
import com.kenai.service.model.UserModel;
import org.apache.tomcat.jni.Time;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class OrderServiceImpl implements OrderService {
    @Resource
    private ItemService itemService;

    @Resource
    private UserService userService;

    @Resource
    private OrderDOMapper orderDOMapper;

    @Resource
    private SequenceDOMapper sequenceDOMapper;

    @Resource
    private StockLogDOMapper stockLogDOMapper;

    @Override
    @Transactional
    public OrderModel createOrder(Integer userId, Integer itemId, Integer promoId, Integer amount, String stockLogId) throws BusinessException {
        // 1.校验下单状态，下单的商品是否存在，用户是否合法、数量是正确、秒杀活动信息
////        ItemModel itemModel = itemService.getItemById(itemId);
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if(itemModel == null){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "商品信息不存在");
        }
//        用户信息在生成秒杀令牌时校验
////        UserModel userModel = userService.getUserById(userId);
//        UserModel userModel = userService.getUserByIdInCache(userId);
//        if(userModel == null){
//            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "用户未注册");
//        }
        if(amount <= 0 || amount > 99){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "数量信息不正确");
        }
        // 校验秒杀活动信息(在生成秒杀令牌时校验)
//        if(promoId != null){
//            // 校验该商品是否有活动
//            if(promoId.intValue() != itemModel.getPromoModel().getId()){
//                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "活动信息不正确");
//                // 校验活动是否正在进行中
//            }else if(itemModel.getPromoModel().getStatus() != 2){
//                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "活动未开始");
//            }
//        }
        // 2.落单减库存
        boolean result = itemService.decreaseStock(itemId, amount);
        if(!result){
            throw new BusinessException((EmBusinessError.STOCK_NOT_ENOUGH));
        }

        // 3.订单入库
        OrderModel orderModel = new OrderModel();
        orderModel.setItemId(itemId);
        orderModel.setUserId(userId);
        orderModel.setAmount(amount);
        if(promoId != null){
            orderModel.setItemPrice(itemModel.getPromoModel().getPromoItemPrice());
        }else{
            orderModel.setItemPrice(itemModel.getPrice());
        }
        orderModel.setPromoId(promoId);
        orderModel.setOrderPrice(orderModel.getItemPrice().multiply(BigDecimal.valueOf(amount)));
        // 生成交易流水号（订单号）
        orderModel.setId(generateOrderNo());

        OrderDO orderDO = convertFromOrderModel(orderModel);
        orderDOMapper.insertSelective(orderDO);

        // 4. 商品销量增加，先增加到缓存中，然后通过rocketmq事务消息机制发送消息
        itemService.increaseSales(itemId, amount);

        // 设置库存流水状态为成功
        StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
        if(stockLogDO == null){
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        }
        // status为2表示扣减库存成功
        stockLogDO.setStatus(2);
        stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);


//        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
//            @Override
//            // 在事务提交后执行异步更新库存操作，问题是如果事务提交后异步更新库存操作失败，则会出现产生订单，但是库存没有减少的情况
//            public void afterCommit() {
//                // 异步更新库存
//                boolean mqResult = itemService.asyncDecreaseStock(itemId, amount);
//                if(!mqResult){
//                    try {
//                        itemService.increaseStock(itemId, amount);
//                        throw new BusinessException(EmBusinessError.MQ_SEND_FAIL);
//                    } catch (BusinessException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//        });

        // 5. 返回前端
        return orderModel;
    }

    // 该注解的作用： 开启一个新的事务，不管外部事务执行有没有成功该事务都不会回滚。
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateOrderNo(){
        // 订单号有16位
        StringBuilder stringBuilder = new StringBuilder();
        // 1. 前8位为时间信息，年月日
        LocalDateTime now = LocalDateTime.now();
        // DateTimeFormatter.ISO_DATE: 年-月-日的形式
        String nowDate = now.format(DateTimeFormatter.ISO_DATE).replace("-", "");
        stringBuilder.append(nowDate);

        // 2. 中间6位为自增序列
        // 获取当前sequence
        SequenceDO sequenceDO = sequenceDOMapper.getSequenceByName("order_info");
        int sequence = sequenceDO.getCurrentValue();
        sequenceDO.setCurrentValue(sequence + sequenceDO.getStep());
        sequenceDOMapper.updateByPrimaryKeySelective(sequenceDO);
        String sequenceStr = String.valueOf(sequence);
        for(int i = 0; i < 6 - sequenceStr.length(); i++){
            stringBuilder.append("0");
        }
        stringBuilder.append(sequenceStr);

        // 3. 最后2位为分库分表位
        stringBuilder.append("00");
        return stringBuilder.toString();
    }

    private OrderDO convertFromOrderModel(OrderModel orderModel){
        if(orderModel == null){
            return null;
        }
        OrderDO orderDO = new OrderDO();
        BeanUtils.copyProperties(orderModel, orderDO);
        orderDO.setItemPrice(orderModel.getItemPrice().doubleValue());
        orderDO.setOrderPrice(orderModel.getOrderPrice().doubleValue());
        return orderDO;
    }
}
