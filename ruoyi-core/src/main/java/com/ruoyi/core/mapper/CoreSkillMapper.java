package com.ruoyi.core.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import com.ruoyi.core.domain.CoreSkill;

/** Skill 文件数据层 */
@Mapper
public interface CoreSkillMapper
{
    List<CoreSkill> selectCoreSkillList(CoreSkill skill);
    List<CoreSkill> selectPublicCoreSkillList();
    CoreSkill selectCoreSkillById(Long skillId);
    int insertCoreSkill(CoreSkill skill);
    int updateCoreSkillStatus(CoreSkill skill);
    int increaseDownloadCount(Long skillId);
    int deleteCoreSkillById(Long skillId);
}
