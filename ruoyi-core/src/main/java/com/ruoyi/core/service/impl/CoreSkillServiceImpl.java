package com.ruoyi.core.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.core.domain.CoreSkill;
import com.ruoyi.core.mapper.CoreSkillMapper;
import com.ruoyi.core.service.ICoreSkillService;

/** Skill 文件服务 */
@Service
public class CoreSkillServiceImpl implements ICoreSkillService
{
    @Autowired
    private CoreSkillMapper skillMapper;

    @Override
    public List<CoreSkill> selectCoreSkillList(CoreSkill skill) { return skillMapper.selectCoreSkillList(skill); }

    @Override
    public List<CoreSkill> selectPublicCoreSkillList() { return skillMapper.selectPublicCoreSkillList(); }

    @Override
    public CoreSkill selectCoreSkillById(Long skillId) { return skillMapper.selectCoreSkillById(skillId); }

    @Override
    public int insertCoreSkill(CoreSkill skill) { return skillMapper.insertCoreSkill(skill); }

    @Override
    public int updateCoreSkillStatus(CoreSkill skill) { return skillMapper.updateCoreSkillStatus(skill); }

    @Override
    public int increaseDownloadCount(Long skillId) { return skillMapper.increaseDownloadCount(skillId); }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteCoreSkillByIds(Long[] skillIds)
    {
        int rows = 0;
        for (Long skillId : skillIds)
        {
            CoreSkill skill = skillMapper.selectCoreSkillById(skillId);
            if (skill == null)
            {
                continue;
            }
            rows += skillMapper.deleteCoreSkillById(skillId);
            deleteStoredFile(skill.getFilePath());
        }
        return rows;
    }

    private void deleteStoredFile(String filePath)
    {
        if (filePath == null || filePath.isBlank())
        {
            return;
        }
        try
        {
            Path root = Path.of(RuoYiConfig.getProfile()).toAbsolutePath().normalize();
            String relativePath = FileUtils.stripPrefix(filePath).replaceFirst("^[\\/]+", "");
            Path target = root.resolve(relativePath).normalize();
            if (!target.startsWith(root))
            {
                throw new ServiceException("Skill 文件路径非法");
            }
            Files.deleteIfExists(target);
        }
        catch (IOException e)
        {
            throw new ServiceException("删除 Skill 文件失败: " + e.getMessage());
        }
    }
}
