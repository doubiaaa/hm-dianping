package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result senCode(String phone, HttpSession session) {
        // 检验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //如果不符合 返回错误信息
            return Result.fail("手机号格式错误");
        }
        //符合 生成验证码
        String code = RandomUtil.randomNumbers(6);


        //保存验证码到session
        //session.setAttribute("code", code);

        //保存到验证码redis中
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送验证码
        log.debug("发送验证码成功,验证码: " + code);
        //返回ok
        return Result.ok();
    }

    /**
     * @param loginForm
     * @return
     * @paramd 登录
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1 校验手机号
        String phone = loginForm.getPhone();
        // 检验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //如果不符合 返回错误信息
            return Result.fail("手机号格式错误");
        }
        //2 校验验证码
        Object cachecode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        //3 一致 根据手机号查询用户
        if (cachecode == null || !cachecode.toString().equals(code)) {
            //返回错误信息
            return Result.fail("验证码错误");
        }
        //4 判断用户是否存在
        User user = query().eq("phone", phone).one();
        if (user == null) {
            // 不存在 新建用户保存
            user = createUserWithPhone(phone);
        }
        // 保存信息到token中
        String token = UUID.randomUUID().toString(true);
        //将user对象转成hash存进redis
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true).setFieldValueEditor((fieldName,fieldvalue)->fieldvalue.toString()));
        String tokenKey = LOGIN_TOKENKEY+ token;
        //存储
        stringRedisTemplate.opsForHash().putAll(tokenKey , userMap);
        //设置有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_TOKENKEY_TIME, TimeUnit.MINUTES);
        //保存到session中
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        //创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //保存
        save(user);
        //返回
        return user;
    }

}
