package com.ruoyi.option.mapper;

import java.util.List;
import com.ruoyi.option.domain.OptionAccessPolicy;

/**
 * 会员菜单数据权限策略 数据层（option_access_policy）
 * 
 * @author yscandice
 */
public interface OptionAccessPolicyMapper
{
    /**
     * 查询会员菜单数据权限策略
     * 
     * @param policyId 主键
     * @return 会员菜单数据权限策略
     */
    public OptionAccessPolicy selectOptionAccessPolicyById(Long policyId);

    /**
     * 查询会员菜单数据权限策略列表
     * 
     * @param optionAccessPolicy 查询条件
     * @return 会员菜单数据权限策略集合
     */
    public List<OptionAccessPolicy> selectOptionAccessPolicyList(OptionAccessPolicy optionAccessPolicy);

    /**
     * 按菜单编码查询策略
     * 
     * @param resourceCode 菜单编码
     * @return 菜单权限策略
     */
    public OptionAccessPolicy selectOptionAccessPolicyByCode(String resourceCode);

    /**
     * 新增会员菜单数据权限策略
     * 
     * @param optionAccessPolicy 会员菜单数据权限策略
     * @return 结果
     */
    public int insertOptionAccessPolicy(OptionAccessPolicy optionAccessPolicy);

    /**
     * 修改会员菜单数据权限策略
     * 
     * @param optionAccessPolicy 会员菜单数据权限策略
     * @return 结果
     */
    public int updateOptionAccessPolicy(OptionAccessPolicy optionAccessPolicy);

    /**
     * 删除会员菜单数据权限策略
     * 
     * @param policyId 主键
     * @return 结果
     */
    public int deleteOptionAccessPolicyById(Long policyId);

    /**
     * 批量删除会员菜单数据权限策略
     * 
     * @param policyIds 需要删除的主键集合
     * @return 结果
     */
    public int deleteOptionAccessPolicyByIds(Long[] policyIds);
}
