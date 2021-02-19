package com.kenai.controller;

import com.google.common.util.concurrent.RateLimiter;
import com.kenai.CommonReturnType;
import com.kenai.error.BusinessException;
import com.kenai.error.EmBusinessError;
import com.kenai.mq.MqProducer;
import com.kenai.service.ItemService;
import com.kenai.service.OrderService;
import com.kenai.service.PromoService;
import com.kenai.service.model.OrderModel;
import com.kenai.service.model.UserModel;
import com.kenai.util.CodeUtil;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

@RestController
@RequestMapping("/order")
@CrossOrigin(origins = {"*"}, allowCredentials = "true")
public class OrderController extends BaseController{
    private static final String CONTENT_TYPE_FORMED = "application/x-www-form-urlencoded";

    @Resource
    private OrderService orderService;

    @Resource
    private HttpServletRequest httpServletRequest;

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private MqProducer mqProducer;

    @Resource
    private ItemService itemService;

    @Resource
    private PromoService promoService;

    private ExecutorService executorService;

    private RateLimiter orderCreateRateLimiter;

    @PostConstruct
    public void init(){
        // newFixedThreadPool(): 创建一个定长线程池，可控制线程最大并发数，超出的线程会在队列中等待。
        // 开辟20个线程数的线程池,同一时间只能处理20个请求，其他的请求放在队列中等待，用来队列化泄洪
        executorService = Executors.newFixedThreadPool(20);
        // 参数为permitspersecond，即每秒钟允许通过的请求数，即TPS
        orderCreateRateLimiter = RateLimiter.create(100);
    }

    // 生成验证码
    @GetMapping(value = "/generateverifycode")
    // 在Servlet中，当服务器响应客户端的一个请求时，就要用到HttpServletResponse接口
    public void generateverifycode(HttpServletResponse response) throws BusinessException, IOException {
        // 根据token获取用户信息
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if (StringUtils.isEmpty(token)) {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户未登陆，不能生成验证码");
        }
        // 获取用户登陆信息
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if (userModel == null) {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户未登陆，不能生成验证码");
        }
        Map<String, Object> map = CodeUtil.generateCodeAndPic();
        redisTemplate.opsForValue().set("verify_code_" + userModel.getId(), map.get("code"));
        redisTemplate.expire("verify_code_" + userModel.getId(), 5, TimeUnit.MINUTES);
        ImageIO.write((RenderedImage)map.get("codePic"), "jpeg", response.getOutputStream());
    }

    /**
     * 生成秒杀令牌
     * @param itemId
     * @param promoId
     * @return
     * @throws BusinessException
     */
    @PostMapping(value = "/generatetoken", consumes = {CONTENT_TYPE_FORMED})
    public CommonReturnType generatetoken(@RequestParam("itemId") Integer itemId,
                                          @RequestParam("promoId") Integer promoId,
                                          @RequestParam("verifyCode") String verifyCode) throws BusinessException {
        // 根据token获取用户信息
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if(StringUtils.isEmpty(token)){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户未登陆，不能下单");
        }
        // 获取用户登陆信息
        UserModel userModel = (UserModel)redisTemplate.opsForValue().get(token);
        if(userModel == null){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户未登陆，不能下单");
        }
        // 验证验证码的有效性
        String redisVerifyCode = (String) redisTemplate.opsForValue().get("verify_code_" + userModel.getId());
        if(StringUtils.isEmpty(redisVerifyCode)){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "请求非法");
        }
        if(!redisVerifyCode.equalsIgnoreCase(verifyCode)){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "验证码错误");
        }
        // 获取秒杀访问令牌
        String promoToken = promoService.generateSecondKillToken(promoId, itemId, userModel.getId());
        if(promoToken == null){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "生成令牌失败");
        }
        return CommonReturnType.create(promoToken);
    }

    /**
     * 封装下单请求
     * @param itemId
     * @param promoId
     * @param amount
     * @return
     * @throws BusinessException
     */
    @PostMapping(value = "/createorder", consumes = {CONTENT_TYPE_FORMED})
    public CommonReturnType createOrder(@RequestParam("itemId") Integer itemId,
                                        @RequestParam(value = "promoId", required = false) Integer promoId,
                                        @RequestParam("amount") Integer amount,
                                        @RequestParam(value = "promoToken", required = false) String promoToken) throws BusinessException {
        // 使用session的方法
//        Boolean isLogin = (Boolean) httpServletRequest.getSession().getAttribute("IS_LOGIN");
//        if(isLogin == null || !isLogin){
//            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户未登陆，不能下单");
//        }
//        获取登陆用户信息
//        UserModel userModel = (UserModel) httpServletRequest.getSession().getAttribute("LOGIN_USER");
        // 没有令牌则不能下单
        if(!orderCreateRateLimiter.tryAcquire()){
            throw new BusinessException(EmBusinessError.RATELIMIT);
        }
        // 使用token的方法获取用户信息
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if(StringUtils.isEmpty(token)){
            throw new BusinessException(EmBusinessError.USER_NOT_EXIST, "用户未登陆，不能下单");
        }
        UserModel userModel = (UserModel)redisTemplate.opsForValue().get(token);
        if(userModel == null){
            throw new BusinessException(EmBusinessError.USER_NOT_EXIST, "用户未登陆，不能下单");
        }
        // 校验秒杀令牌是否正确
        if(promoId != null){
            String inRedisPromoToken = (String) redisTemplate.opsForValue().get("promo_token_" + promoId + "_userid_" + userModel.getId() + "_itemid_" + itemId);
            if(inRedisPromoToken == null){
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "秒杀令牌校验失败");
            }
            if(!org.apache.commons.lang3.StringUtils.equals(inRedisPromoToken, promoToken)){
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "秒杀令牌校验失败");
            }
        }
        // 同步调用线程池的submit方法
        // 当将一个Callable的对象传递给ExecutorService的submit方法，则该call方法自动在一个线程上执行，并且会返回执行结果Future对象。
        // 即每一个初始化库存流水操作、rocketmq事务型消息、下订单操作放在一个线程中执行，一共20个线程，则可以同时有20个这一系列操作，其他的放在队列中
        Future<Object> future = executorService.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                //  初始化库存流水（id、itemid、amount、status存入数据库流水表）
                String stockLogId = itemService.initStockLog(itemId, amount);
                // 完成对应的下单事务型消息机制
                boolean orderState = mqProducer.transactionAsyncReduceStockAndAddSales(userModel.getId(), itemId, promoId, amount, stockLogId);
                // 下单失败
                if(!orderState){
                    throw new BusinessException(EmBusinessError.UNKNOWN_ERROR, "下单失败");
                }
                return null;
            }
        });
        try {
            // 返回null
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        }
        return CommonReturnType.create(null);
    }
}
