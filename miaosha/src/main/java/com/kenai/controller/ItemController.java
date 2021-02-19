package com.kenai.controller;

import com.kenai.CommonReturnType;
import com.kenai.controller.viewobject.ItemVO;
import com.kenai.error.BusinessException;
import com.kenai.service.CacheService;
import com.kenai.service.ItemService;
import com.kenai.service.PromoService;
import com.kenai.service.model.ItemModel;
import com.kenai.service.model.PromoModel;
import org.joda.time.format.DateTimeFormat;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/item")
@CrossOrigin(origins = {"*"}, allowCredentials = "true")
public class ItemController extends BaseController{
    private static final String CONTENT_TYPE_FORMED = "application/x-www-form-urlencoded";

    @Resource
    private ItemService itemService;

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private CacheService cacheService;

    @Resource
    private PromoService promoService;
    
    /**
     * 创建商品
     * @param title
     * @param price
     * @param stock
     * @param description
     * @param imgUrl
     * @return
     * @throws BusinessException
     */
    @PostMapping(value = "/create")
    public CommonReturnType createItem(@RequestParam("title") String title,
                                       @RequestParam("price") BigDecimal price,
                                       @RequestParam("stock") Integer stock,
                                       @RequestParam("description") String description,
                                       @RequestParam("imgUrl") String imgUrl) throws BusinessException {
        ItemModel itemModel = new ItemModel();
        itemModel.setTitle(title);
        itemModel.setPrice(price);
        itemModel.setStock(stock);
        itemModel.setDescription(description);
        itemModel.setImgUrl(imgUrl);
        ItemModel itemModel1ForReturn = itemService.createItem(itemModel);
        ItemVO itemVO = convertVOFromModel(itemModel1ForReturn);
        return CommonReturnType.create(itemVO);
    }

    /**
     * 发布促销活动：将库存同步到redis中
     * @param id
     * @return
     */
    @GetMapping(value = "/publishpromo")
    public CommonReturnType publishPromo(@RequestParam("id") Integer id){
        promoService.publishpromo(id);
        return CommonReturnType.create(null);
    }


    /**
     * 商品详情页浏览
     * @param id
     * @return
     */
    @GetMapping(value = "/get")
    public CommonReturnType getItem(@RequestParam("id") Integer id){
        ItemModel itemModel = null;
        // 先取本地缓存
        itemModel = (ItemModel)cacheService.getFromCommonCache("item_" + id);
        if(itemModel == null){
            // 若本地缓存不存在，从redis中取
            itemModel = (ItemModel)redisTemplate.opsForValue().get("item_" + id);
            if(itemModel == null) {
                // 若redis中也不存在，则从数据库中取
                itemModel = itemService.getItemById(id);
                redisTemplate.opsForValue().set("item_" + id, itemModel);
                redisTemplate.expire("item_" + id, 10, TimeUnit.MINUTES);
            }
            // 填充本地缓存
            cacheService.setCommonCache("item_" + id, itemModel);
        }
        ItemVO itemVO = convertVOFromModel(itemModel);
        return CommonReturnType.create(itemVO);
    }

    /**
     * 商品列表页面按销量降序展示
     * @return
     */
    @GetMapping("/list")
    public CommonReturnType listItem() {
        List<ItemModel> itemModelList = itemService.listItem();
        List<ItemVO> itemVOList = itemModelList.stream().map(itemModel -> {
            return convertVOFromModel(itemModel);
        }).collect(Collectors.toList());
        return CommonReturnType.create(itemVOList);
    }

    private ItemVO convertVOFromModel(ItemModel itemModel){
        if(itemModel == null){
            return null;
        }
        ItemVO itemVO = new ItemVO();
        BeanUtils.copyProperties(itemModel, itemVO);
        // 说明有正在进行或者还未结束的秒杀活动
        if(itemModel.getPromoModel() != null){
            PromoModel promoModel = itemModel.getPromoModel();
            itemVO.setPromoId(promoModel.getId());
            itemVO.setPromoPrice(promoModel.getPromoItemPrice());
            itemVO.setPromoStatus(promoModel.getStatus());
            itemVO.setStartDate(promoModel.getStartDate().toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")));
        }else{
            // 没有秒杀活动，状态为0
            itemVO.setPromoStatus(0);
        }
        return itemVO;
    }
}
