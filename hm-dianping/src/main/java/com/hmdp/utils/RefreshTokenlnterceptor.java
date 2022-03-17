package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenlnterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenlnterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取请求头token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true;
        }
        //抽取tokenKey
        String key = RedisConstants.LOGIN_TOKENKEY + token;
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(key);
        //判断用户是否存在
        if (map.isEmpty()) {
            return true;
        }
        //查询到的hash数据转换成userDto对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(map, new UserDTO(), false);
        //存在 保存用户到threadLocal
        UserHolder.saveUser(userDTO);
        //设置有效期
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_TOKENKEY_TIME, TimeUnit.MINUTES);
        //放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
            //移除用户
        UserHolder.removeUser();
    }
}
