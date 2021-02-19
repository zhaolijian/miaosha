package com.kenai.controller;

import com.alibaba.druid.util.StringUtils;
import com.kenai.CommonReturnType;
import com.kenai.controller.viewobject.UserVO;
import com.kenai.error.BusinessException;
import com.kenai.error.EmBusinessError;
import com.kenai.service.UserService;
import com.kenai.service.impl.UserServiceImpl;
import com.kenai.service.model.UserModel;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.security.MD5Encoder;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import sun.misc.BASE64Encoder;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/user")
@Slf4j
@CrossOrigin(origins = {"*"}, allowCredentials = "true")
public class UserController extends BaseController{
    private static final String CONTENT_TYPE_FORMED = "application/x-www-form-urlencoded";

    @Resource
    private UserService userService;

    @Resource
    private HttpServletRequest httpServletRequest;

    @Resource
    private RedisTemplate redisTemplate;


    // 用户登陆接口
    @PostMapping(value = "/login", consumes = {CONTENT_TYPE_FORMED})
    public CommonReturnType login(@RequestParam("telephone") String telephone, @RequestParam("password") String password) throws BusinessException, NoSuchAlgorithmException {
        // 入参校验
        if(StringUtils.isEmpty(telephone) || StringUtils.isEmpty(password)){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR);
        }
        // 用户登陆服务，用来校验用户登陆是否合法
        UserModel userModel = userService.validateLogin(telephone, EncodeByMd5(password));
        // 使用session的方法： 将登陆凭证加入到用户登陆成功的session内
//        this.httpServletRequest.getSession().setAttribute("IS_LOGIN", true);
//        this.httpServletRequest.getSession().setAttribute("LOGIN_USER", userModel);
//        return CommonReturnType.create(null);

        // 使用token的方法： 将原来的方法修改成若用户登陆验证成功，将对应的登陆信息和登陆凭证一起存入redis中
        // 使用UUID生成登陆凭证token
        String uuidToken = UUID.randomUUID().toString().replace("-", "");
        // 建立token和用户登陆态之间的联系
        redisTemplate.opsForValue().set(uuidToken, userModel);
        // uuidToken的过期时间为1h
        redisTemplate.expire(uuidToken, 1, TimeUnit.HOURS);
        return CommonReturnType.create(uuidToken);
    }

    // 用户注册接口
    @PostMapping(value = "/register", consumes = {CONTENT_TYPE_FORMED})
    public CommonReturnType register(@RequestParam(name = "telephone") String telephone,
                                     @RequestParam(name = "otpCode") String otpCode,
                                     @RequestParam(name = "name") String name,
                                     @RequestParam(name = "gender") Integer gender,
                                     @RequestParam(name = "age") Integer age,
                                     @RequestParam(name = "password") String password) throws BusinessException, UnsupportedEncodingException, NoSuchAlgorithmException {
        // 验证手机号和对应的otpCode相符合
        String inSessionOtpCode = (String) this.httpServletRequest.getSession().getAttribute(telephone);
        // 判断用户输入的验证码和服务器端session中存储的该用户的验证码是否一致
        if(!StringUtils.equals(otpCode, inSessionOtpCode)){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "短信验证码不符合");
        }
        // 用户的注册流程
        UserModel userModel = new UserModel();
        userModel.setName(name);
        userModel.setGender(new Byte(String.valueOf(gender.intValue())));
        userModel.setAge(age);
        userModel.setTelephone(telephone);
        userModel.setRegisterMode("byphone");
        userModel.setEncrptPassword(this.EncodeByMd5(password));
        userService.register(userModel);
        return CommonReturnType.create(null);
    }


    public String EncodeByMd5(String str) throws NoSuchAlgorithmException {
        //确定计算方法
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        BASE64Encoder base64Encoder = new BASE64Encoder();
        //加密字符串
        return base64Encoder.encode(md5.digest(str.getBytes(StandardCharsets.UTF_8)));
    }


    // 用户获取otp短信接口
    @PostMapping(value = "/getotp",consumes = {CONTENT_TYPE_FORMED})
    public CommonReturnType getOtp(@RequestParam("telephone") String telephone){
        //需要按照一定的规则生成OTP验证码
        Random random = new Random();
        int randomInt = random.nextInt(99999);
        randomInt += 10000;
        String otpCode = String.valueOf(randomInt);

        //将OTP验证码同对应用户的手机号关联,使用httpSession的方式绑定手机号和OTPCODE(键值对)
        httpServletRequest.getSession().setAttribute(telephone, otpCode);

        //将OTP验证码通过短信通道发送给用户，省略
        System.out.println("telephone= " + telephone + "& otpCode= " + otpCode);
        return CommonReturnType.create(null);
    }

    @GetMapping("/get/{id}")
    public CommonReturnType getUser(@PathVariable("id") Integer id) throws BusinessException {
        // 调用service服务获取对应id的用户对象并返回给前端
        UserModel userModel =  userService.getUserById(id);

        // 若获取的对应用户信息不存在
        if(userModel == null){
            throw new BusinessException(EmBusinessError.USER_NOT_EXIST);
        }

//        将核心领域模型用户对象转换为可供UI使用的viewobject
        UserVO userVO = convertFromModel(userModel);
        // 返回通用对象
        return CommonReturnType.create(userVO);
    }

    private UserVO convertFromModel(UserModel userModel){
        if(userModel == null){
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(userModel, userVO);
        return userVO;
    }
}
