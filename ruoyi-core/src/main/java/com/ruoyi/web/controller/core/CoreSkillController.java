package com.ruoyi.web.controller.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileUploadUtils;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.core.domain.CoreSkill;
import com.ruoyi.core.service.ICoreSkillService;

/** 开源 Skill 管理 */
@RestController
@RequestMapping("/core/skill")
public class CoreSkillController extends BaseController
{
    private static final String[] ALLOWED_EXTENSION = { "zip" };

    @Autowired
    private ICoreSkillService skillService;

    @PreAuthorize("@ss.hasPermi('tool:opensource:list')")
    @GetMapping("/list")
    public TableDataInfo list(CoreSkill skill)
    {
        startPage();
        List<CoreSkill> list = skillService.selectCoreSkillList(skill);
        return getDataTable(list);
    }

    @Anonymous
    @GetMapping("/public/list")
    public AjaxResult publicList()
    {
        return success(skillService.selectPublicCoreSkillList());
    }

    @Log(title = "开源工具", businessType = BusinessType.INSERT)
    @PreAuthorize("@ss.hasPermi('tool:opensource:upload')")
    @PostMapping("/upload")
    public AjaxResult upload(@RequestParam("file") MultipartFile file,
            @RequestParam("skillName") String skillName,
            @RequestParam(value = "version", defaultValue = "1.0.0") String version,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "author", required = false) String author,
            @RequestParam(value = "repoUrl", required = false) String repoUrl) throws Exception
    {
        if (file == null || file.isEmpty())
        {
            return error("请选择 Skill ZIP 文件");
        }
        if (StringUtils.isEmpty(skillName) || skillName.length() > 100)
        {
            return error("Skill 名称不能为空且不能超过100个字符");
        }
        if (!"zip".equals(FilenameUtils.getExtension(file.getOriginalFilename()).toLowerCase(Locale.ROOT)))
        {
            return error("仅支持上传 ZIP 格式的 Skill 包");
        }
        if (!containsSkillManifest(file))
        {
            return error("ZIP 包内必须包含 SKILL.md 文件");
        }

        String filePath = FileUploadUtils.upload(RuoYiConfig.getUploadPath() + "/skill", file, ALLOWED_EXTENSION, true);
        CoreSkill skill = new CoreSkill();
        skill.setSkillName(skillName.trim());
        skill.setVersion(StringUtils.isEmpty(version) ? "1.0.0" : version.trim());
        skill.setDescription(description);
        skill.setAuthor(author);
        skill.setRepoUrl(repoUrl);
        skill.setFileName(FilenameUtils.getName(file.getOriginalFilename()));
        skill.setFilePath(filePath);
        skill.setFileSize(file.getSize());
        skill.setDownloadCount(0L);
        skill.setStatus("0");
        skill.setCreateBy(getUsername());
        skillService.insertCoreSkill(skill);
        return success(skill);
    }

    @PreAuthorize("@ss.hasPermi('tool:opensource:edit')")
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody CoreSkill skill)
    {
        skill.setUpdateBy(getUsername());
        return toAjax(skillService.updateCoreSkillStatus(skill));
    }

    @PreAuthorize("@ss.hasPermi('tool:opensource:download')")
    @GetMapping("/download/{skillId}")
    public void download(@PathVariable Long skillId, HttpServletResponse response) throws IOException
    {
        downloadSkill(skillId, false, response);
    }

    @Anonymous
    @GetMapping("/public/download/{skillId}")
    public void publicDownload(@PathVariable Long skillId, HttpServletResponse response) throws IOException
    {
        downloadSkill(skillId, true, response);
    }

    @Log(title = "开源工具", businessType = BusinessType.DELETE)
    @PreAuthorize("@ss.hasPermi('tool:opensource:remove')")
    @DeleteMapping("/{skillIds}")
    public AjaxResult remove(@PathVariable Long[] skillIds)
    {
        return toAjax(skillService.deleteCoreSkillByIds(skillIds));
    }

    private boolean containsSkillManifest(MultipartFile file) throws IOException
    {
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream()))
        {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null)
            {
                String name = entry.getName().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (!entry.isDirectory() && ("skill.md".equals(name) || name.endsWith("/skill.md")))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private void downloadSkill(Long skillId, boolean publicOnly, HttpServletResponse response) throws IOException
    {
        CoreSkill skill = skillService.selectCoreSkillById(skillId);
        if (skill == null || (publicOnly && !"0".equals(skill.getStatus())))
        {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Path root = Path.of(RuoYiConfig.getProfile()).toAbsolutePath().normalize();
        String relativePath = FileUtils.stripPrefix(skill.getFilePath()).replaceFirst("^[\\/]+", "");
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target))
        {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        FileUtils.setAttachmentResponseHeader(response, skill.getFileName());
        try (InputStream input = Files.newInputStream(target))
        {
            input.transferTo(response.getOutputStream());
        }
        skillService.increaseDownloadCount(skillId);
    }
}
