package com.kenai.service.impl;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.kenai.service.CacheService;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

@Service
public class CacheServiceImpl implements CacheService {
    private Cache<String, Object> commonCache = null;

    // 在构造器后执行，在init()方法前面执行，只被服务器执行一次
    @PostConstruct
    public void init(){
        commonCache = CacheBuilder.newBuilder()
                // 设置缓存容器的初始容量为10
                .initialCapacity(10)
                // 缓存中最多可以存储100个key，超出则按照LRU算法移除最近最少使用的缓存
                .maximumSize(100)
                // 设置写缓存后多长时间过期
                .expireAfterWrite(60, TimeUnit.SECONDS).build();
    }

    @Override
    public void setCommonCache(String key, Object value) {
        commonCache.put(key, value);
    }

    @Override
    public Object getFromCommonCache(String key) {
        return commonCache.getIfPresent(key);
    }
}
