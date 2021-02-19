package com.kenai.service;


import com.kenai.error.BusinessException;
import com.kenai.service.model.UserModel;

import java.io.Serializable;

public interface UserService{
    UserModel getUserById(Integer id);

    /**
     * 通过缓存获取用户对象
     * @param id
     * @return
     */
    UserModel getUserByIdInCache(Integer id);

    void register(UserModel userModel) throws BusinessException;

    /**
     *
     * @param telephone 用户注册手机
     * @param encrptPassword  用户加密后的密码
     * @throws BusinessException
     */
    UserModel validateLogin(String telephone, String encrptPassword) throws BusinessException;
}
