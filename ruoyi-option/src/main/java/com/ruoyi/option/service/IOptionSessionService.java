package com.ruoyi.option.service;

import java.util.Map;

/**
 * OSM 会话 服务层（全局单个会话）
 * 
 * @author yscandice
 */
public interface IOptionSessionService
{
    /**
     * 登录 OSM 并把 Cookie 存入 Redis
     * 
     * @param email 账号
     * @param password 密码
     * @return 会话有效期（秒）
     */
    public int login(String email, String password);

    /**
     * 退出（删除 Cookie）
     */
    public void logout();

    /**
     * 获取当前 Cookie
     * 
     * @return Cookie，未登录返回 null
     */
    public String getCookie();

    /**
     * 会话失效：删除 Cookie 并暂停同步
     * 
     * @param reason 原因
     */
    public void expire(String reason);

    /**
     * 会话状态
     * 
     * @return 状态
     */
    public Map<String, Object> getStatus();
}
