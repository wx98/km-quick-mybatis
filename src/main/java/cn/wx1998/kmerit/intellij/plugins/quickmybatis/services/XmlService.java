package cn.wx1998.kmerit.intellij.plugins.quickmybatis.services;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.setting.MyBatisSetting;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.util.ProjectFileUtils;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class XmlService implements Serializable {

    // 获取日志记录器实例
    private static final Logger LOG = Logger.getInstance(XmlService.class);

    @Serial
    private static final long serialVersionUID = 1L;
    private final Project project;
    private final MyBatisSetting setting; // 配置类，管理命名规则

    /**
     * Instantiates a new Java service.
     *
     * @param project the project
     */
    public XmlService(Project project) {
        this.project = project;
        this.setting = MyBatisSetting.getInstance(project);
    }

    /**
     * Gets instance.
     *
     * @param project the project
     * @return the instance
     */
    public static XmlService getInstance(@NotNull Project project) {
        XmlService service = project.getService(XmlService.class);
        return service;
    }


    public Project getProject() {
        return project;
    }

    /**
     * 获取项目中所有的MyBatis XML文件
     */
    public List<XmlFile> getMyBatisXmlFiles() {
        LOG.debug("Finding all MyBatis XML files in project");
        return ReadAction.compute(() -> {
            List<PsiFile> filesByTypeInSourceRoots = ProjectFileUtils.getVirtualFileListByTypeInSourceRoots(project, "xml");
            List<XmlFile> xmlFileList = new ArrayList<>();
            for (PsiFile file : filesByTypeInSourceRoots) {
                if (file instanceof XmlFile xmlFile) {
                    xmlFileList.add(xmlFile);
                    LOG.debug("Found MyBatis XML file: " + file.getVirtualFile().getPath());
                }
            }
            LOG.debug("Total MyBatis XML files found: " + xmlFileList.size());
            return xmlFileList;
        });
    }


}
