package com.kenai.mq;

import com.alibaba.fastjson.JSON;
import com.kenai.dao.StockLogDOMapper;
import com.kenai.dataobject.StockLogDO;
import com.kenai.error.BusinessException;
import com.kenai.service.OrderService;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class MqProducer {
    private DefaultMQProducer producer;

    private TransactionMQProducer transactionMQProducer;

    @Resource
    private OrderService orderService;

    @Resource
    private StockLogDOMapper stockLogDOMapper;

    @Value("${mq.nameserver.addr}")
    private String nameAddr;

    @Value("${mq.topicname}")
    private String topicName;

    @PostConstruct
    public void init() throws MQClientException {
        // 做mq producer的初始化
//        producer = new DefaultMQProducer("product_group");
//        // producer连接nameserver
//        producer.setNamesrvAddr(nameAddr);
//        producer.start();
        transactionMQProducer = new TransactionMQProducer("transaction_producer_group");
        transactionMQProducer.setNamesrvAddr(nameAddr);
        transactionMQProducer.start();
        transactionMQProducer.setTransactionListener(new TransactionListener() {
            @Override
//          在发送消息后调用，用于执行本地事务，如果本地事务执行成功，rocketmq再提交消息
            public LocalTransactionState executeLocalTransaction(Message message, Object args) {
                Integer userId = (Integer) ((Map)args).get("userId");
                Integer promoId = (Integer) ((Map)args).get("promoId");
                Integer itemId = (Integer) ((Map)args).get("itemId");
                Integer amount = (Integer) ((Map)args).get("amount");
                String stockLogId = (String) ((Map)args).get("stockLogId");
                // 真正要做的事：创建订单
                try {
                    orderService.createOrder(userId, itemId, promoId, amount, stockLogId);
                } catch (BusinessException e) {
                    e.printStackTrace();
                    // 抛出任何异常都要回滚
                    // 设置对应的stocklog为回滚状态
                    StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
                    stockLogDO.setStatus(3);
                    stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }
                return LocalTransactionState.COMMIT_MESSAGE;
            }

            // 如果消息发送状态是UNKNOW，则调用该方法进行回查，最多回查15次，间隔1分钟
            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt msg) {
                // 根据是否扣减库存成功，来判断是否返回ROLLBACK_MESSAGE还是COMMIT_MESSAGE，或者是继续UNKNOWN
                String jsonString = new String(msg.getBody());
                Map<String, Object> map = JSON.parseObject(jsonString, Map.class);
                String stockLogId = (String) map.get("stockLogId");
                StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
                if(stockLogDO == null){
                    return LocalTransactionState.UNKNOW;
                }
                // 2表示下单扣减库存成功
                if(stockLogDO.getStatus() == 2){
                    return LocalTransactionState.COMMIT_MESSAGE;
                    // 1表示初始状态,说明卡在orderService.createOrder（）那了
                }else if(stockLogDO.getStatus() == 1){
                    return LocalTransactionState.UNKNOW;
                }
                return LocalTransactionState.ROLLBACK_MESSAGE;
            }
        });
    }

    // 事务型同步库存扣减消息
    public Boolean transactionAsyncReduceStockAndAddSales(Integer userId, Integer itemId, Integer promoId, Integer amount, String stockLogId) {
//        bodyMap为rocketmq要传输的消息
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("itemId", itemId);
        bodyMap.put("amount", amount);
        bodyMap.put("stockLogId", stockLogId);
//        argsMap为其他的一些参数
        Map<String, Object> argsMap = new HashMap<>();
        argsMap.put("userId", userId);
        argsMap.put("promoId", promoId);
        argsMap.put("itemId", itemId);
        argsMap.put("amount", amount);
        argsMap.put("stockLogId", stockLogId);
        Message message = new Message(topicName, "increase",
                JSON.toJSON(bodyMap).toString().getBytes(StandardCharsets.UTF_8));
        TransactionSendResult transactionSendResult = null;
        try {
            transactionSendResult = transactionMQProducer.sendMessageInTransaction(message, argsMap);
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        }
        if(transactionSendResult.getLocalTransactionState() == LocalTransactionState.ROLLBACK_MESSAGE){
            return false;
        }else if(transactionSendResult.getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE){
            return true;
        }
        return false;
    }

    // 同步库存扣减消息
    public Boolean asyncReduceStock(Integer itemId, Integer amount){
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("itemId", itemId);
        bodyMap.put("amount", amount);
        Message message = new Message(topicName, "increase",
                JSON.toJSON(bodyMap).toString().getBytes(StandardCharsets.UTF_8));
        try {
            producer.send(message);
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        } catch (RemotingException e) {
            e.printStackTrace();
            return false;
        } catch (MQBrokerException e) {
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}