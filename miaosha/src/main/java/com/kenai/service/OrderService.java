package com.kenai.service;

import com.kenai.error.BusinessException;
import com.kenai.service.model.OrderModel;

public interface OrderService {
    OrderModel createOrder(Integer userId, Integer itemId, Integer promoId, Integer amount, String stockLogId) throws BusinessException;
}
