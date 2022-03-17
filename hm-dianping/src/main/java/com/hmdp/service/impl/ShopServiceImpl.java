package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import io.netty.util.internal.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sun.plugin2.message.ShowDocumentMessage;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryByid(Long id) {


        //  Shop shop = queryWithPassThrough(id);


        //缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(CACHE_TOKENKEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //逻辑删除解决缓存击穿
        //Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);
        //互斥锁
        //Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    //public Shop queryWithMutex(Long id) {
    //    //从redis查询缓存
    //    String key = CACHE_TOKENKEY + id;
    //
    //    String shopJson = stringRedisTemplate.opsForValue().get(key);
    //    //判断是否存在
    //    if (StrUtil.isNotBlank(shopJson)) {
    //        //存在直接返回
    //        return JSONUtil.toBean(shopJson, Shop.class);
    //    }
    //    if (shopJson != null) {
    //        return null;
    //    }
    //    //实现缓存重建
    //    Shop shop = null;
    //
    //    try {
    //        //声明一个锁名
    //        String lockKey = "lock:shop" + id;
    //        //获取互斥锁
    //        boolean isLock = tryLock(lockKey);
    //        //判断是否获取成功
    //        if (!isLock) {
    //            //失败 则休眠并重试
    //            Thread.sleep(50);
    //            return queryWithMutex(id);
    //        }
    //        //不存在在根据id查询数据库
    //        shop = getById(id);
    //        //不存在 返回错误
    //        if (shop == null) {
    //            stringRedisTemplate.opsForValue().set(key, "", LOGIN_NULL, TimeUnit.MINUTES);
    //            return null;
    //        }
    //        //存在 写入redis
    //        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
    //    } catch (InterruptedException e) {
    //        throw new RuntimeException(e);
    //    } finally {
    //        //释放互斥锁
    //        unlock(key);
    //    }
    //    //返回
    //    return shop;
    //}

    public Shop queryWithPassThrough(Long id) {
        //从redis查询缓存
        String key = CACHE_TOKENKEY + id;

        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //存在直接返回
            return JSONUtil.toBean(shopJson, Shop.class);

        }
        if (shopJson != null) {
            return null;
        }
        //不存在在根据id查询数据库
        Shop shop = getById(id);
        //不存在 返回错误
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", LOGIN_NULL, TimeUnit.MINUTES);
            return null;
        }
        //存在 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回
        return shop;
    }


    ////互斥锁
    //private boolean tryLock(String key) {
    //    Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
    //    return BooleanUtil.isTrue(flag);
    //}
    //
    ////删除锁
    //private void unlock(String key)  {
    //    stringRedisTemplate.delete(key);
    //}


    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_TOKENKEY + shop.getId());
        return Result.ok();
    }

}
