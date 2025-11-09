package cn.wx1998.kmerit.intellij.plugins.quickmybatis.services;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.XmlElementInfo;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.setting.MyBatisSetting;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.setting.MyPluginSettings;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.util.DomUtils;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.util.XmlTagLocator;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.classFilter.ClassFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
        // 关键：将所有 Psi 相关操作（遍历+判断+PsiFile创建）完全包裹在 ReadAction 中
        return ReadAction.compute(() -> {
            List<XmlFile> mybatisFiles = new ArrayList<>();
            // 获取项目的所有内容根目录（非 Psi 操作，但在 ReadAction 中执行不影响）
            for (VirtualFile contentRoot : ProjectRootManager.getInstance(project).getContentSourceRoots()) {
                if (contentRoot != null && contentRoot.isDirectory() && contentRoot.isValid()) {
                    findXmlFilesRecursively(contentRoot, mybatisFiles);
                }
            }
            LOG.debug("Total MyBatis XML files found: " + mybatisFiles.size());
            return mybatisFiles;
        });
    }


    /**
     * 递归查找 XML 文件
     */
    private void findXmlFilesRecursively(VirtualFile directory, List<XmlFile> result) {
        if (directory == null || !directory.isDirectory() || !directory.isValid()) return;


        for (VirtualFile file : directory.getChildren()) {
            if (file.isDirectory()) {
                findXmlFilesRecursively(file, result);
            } else if (file.getName().toLowerCase().endsWith(".xml")) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
                if (fileIndex.isExcluded(file)) {
                    LOG.debug("忽略被排除的文件: " + file.getPath());
                    continue;
                }
                if (!fileIndex.isInSourceContent(file)) {
                    LOG.debug("忽略非源码目录文件: " + file.getPath());
                    continue;
                }

                if (psiFile instanceof XmlFile) {
                    if (DomUtils.isMybatisFile(psiFile)) {
                        result.add((XmlFile) psiFile);
                        LOG.debug("Found MyBatis XML file: " + file.getPath());
                    }
                }
            }
        }
    }


    /**
     * 判断方法是否是 SqlSession 的方法
     *
     * @param method
     * @return
     */
    private boolean isSqlSessionMethod(PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }
        if (!method.getModifierList().hasExplicitModifier(PsiModifier.PUBLIC)) {
            return false;
        }
        final var classFilters = MyPluginSettings.getInstance().getClassFilters();
        if (classFilters != null) {
            final var qualifiedName = containingClass.getQualifiedName();
            for (ClassFilter classFilter : classFilters) {
                final var pattern = classFilter.getPattern();
                if (StringUtil.equals(pattern, qualifiedName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 基于类名计算 SQL ID（结合配置的命名规则）
     */
    @Nullable
    private String calculateClassSqlId(@NotNull PsiClass psiClass) {
        String className = psiClass.getQualifiedName();
        if (className == null) return null;

        // 从配置获取类级别的命名规则模板（默认："${className}"）
        String template = setting.getClassNamingRule();
        return template.replace("${className}", className);
    }





}
