package com.ruoyi.core.service;

import java.util.List;
import com.ruoyi.core.domain.CoreSkill;

/** Skill 文件服务接口 */
public interface ICoreSkillService
{
    List<CoreSkill> selectCoreSkillList(CoreSkill skill);
    List<CoreSkill> selectPublicCoreSkillList();
    CoreSkill selectCoreSkillById(Long skillId);
    int insertCoreSkill(CoreSkill skill);
    int updateCoreSkillStatus(CoreSkill skill);
    int increaseDownloadCount(Long skillId);
    int deleteCoreSkillByIds(Long[] skillIds);
}
