package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.util;

import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.info.JavaElementInfo;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.info.XmlElementInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * 元素定位器（基于偏移量定位，参考IDEA书签核心思想）
 * 支持 XML 标签和 Java 元素的精确定位
 */
public class TagLocator {

    private static final Logger LOG = Logger.getInstance(TagLocator.class);
    private static final String LOG_PREFIX = "[TagLocator] ";

    // ==========================================================================
    // 元素信息创建方法
    // ==========================================================================

    /**
     * 创建 XML 元素信息（封装文件路径、偏移量等核心定位信息）
     */
    @Nullable
    public static XmlElementInfo createXmlElementInfo(@NotNull XmlTag element, @NotNull String sqlId, @NotNull String databaseId, @NotNull String elementType) {
        PsiFile containingFile = element.getContainingFile();
        VirtualFile virtualFile = getVirtualFile(containingFile);
        if (virtualFile == null) {
            LOG.debug(LOG_PREFIX + "XmlTag对应的文件不存在或无效: " + element);
            return null;
        }

        // 获取文件绝对路径
        String filePath = containingFile.getVirtualFile().getPath();
        // 获取标签精确偏移量
        TextRange textRange = element.getTextRange();
        int startOffset = textRange.getStartOffset();
        int endOffset = textRange.getEndOffset();

        // 构建并返回XmlElementInfo
        return new XmlElementInfo(filePath, startOffset, endOffset, elementType, sqlId, databaseId);
    }

    /**
     * 创建 Java 元素信息（封装文件路径、偏移量等核心定位信息）
     */
    @Nullable
    public static JavaElementInfo createJavaElementInfo(@NotNull PsiElement element, @NotNull String sqlId, @NotNull String elementType) {
        PsiFile containingFile = element.getContainingFile();
        VirtualFile virtualFile = getVirtualFile(containingFile);
        if (virtualFile == null) {
            LOG.debug(LOG_PREFIX + "PsiElement对应的文件不存在或无效: " + element);
            return null;
        }

        // 获取文件绝对路径
        String filePath = containingFile.getVirtualFile().getPath();
        // 获取Java元素精确偏移量
        TextRange textRange = element.getTextRange();
        int startOffset = textRange.getStartOffset();
        int endOffset = textRange.getEndOffset();

        // 构建并返回JavaElementInfo
        return new JavaElementInfo(filePath, startOffset, endOffset, elementType, sqlId);
    }

    // ==========================================================================
    // XML标签定位核心方法
    // ==========================================================================

    /**
     * 仅通过「文件路径+精确偏移量」定位XmlTag
     * 参考IDEA书签实现，偏移量不受格式调整（换行、缩进）影响，定位稳定高效
     */
    @Nullable
    public static XmlTag findXmlTagByInfo(@NotNull XmlElementInfo info, @NotNull Project project) {
        // 1. 前置校验
        if (!validateXmlElementInfo(info)) {
            return null;
        }

        // 2. 定位目标文件（通过文件路径找到VirtualFile）
        VirtualFile targetFile = LocalFileSystem.getInstance().findFileByPath(info.getFilePath());
        if (targetFile == null) {
            LOG.debug(LOG_PREFIX + "未找到XML文件: " + info.getFilePath());
            return null;
        }

        // 3. 转换为PSI文件
        PsiFile psiFile = PsiManager.getInstance(project).findFile(targetFile);
        if (!(psiFile instanceof XmlFile xmlFile)) {
            LOG.debug(LOG_PREFIX + "文件不是XML类型: " + info.getFilePath());
            return null;
        }

        return findXmlTagByRange(xmlFile, info.getStartOffset(), info.getEndOffset(), info);
    }

    // ==========================================================================
    // Java元素定位核心方法
    // ==========================================================================

    /**
     * 仅通过「文件路径+精确偏移量」定位Java元素（支持class/method/field/methodCall）
     * 核心逻辑与findXmlTagByInfo对齐，确保一致性和稳定性
     */
    @Nullable
    public static PsiElement findJavaTagByInfo(@NotNull JavaElementInfo info, @NotNull Project project) {
        // 1. 前置校验：过滤无效参数
        if (!validateJavaElementInfo(info)) {
            return null;
        }

        // 2. 定位目标Java文件（通过文件路径找到VirtualFile）
        VirtualFile targetFile = LocalFileSystem.getInstance().findFileByPath(info.getFilePath());
        if (targetFile == null) {
            LOG.debug(LOG_PREFIX + "未找到Java文件: " + info.getFilePath());
            return null;
        }

        // 3. 转换为PSI文件（必须是JavaFile类型）
        PsiFile psiFile = PsiManager.getInstance(project).findFile(targetFile);
        if (!(psiFile instanceof PsiJavaFile javaFile)) {
            LOG.debug(LOG_PREFIX + "文件不是Java类型: " + info.getFilePath());
            return null;
        }
        return findJavaElementByRange(javaFile, info.getStartOffset(), info.getEndOffset(), info.getElementType(), info);
    }

    // ==========================================================================
    // 辅助方法
    // ==========================================================================

    @Nullable
    private static VirtualFile getVirtualFile(PsiFile psiFile) {
        return psiFile != null ? psiFile.getVirtualFile() : null;
    }

    /**
     * 校验XmlElementInfo合法性
     */
    private static boolean validateXmlElementInfo(@NotNull XmlElementInfo info) {
        if (isEmpty(info.getFilePath())) {
            LOG.debug(LOG_PREFIX + "XmlElementInfo文件路径为空");
            return false;
        }
        if (!isValidOffsetRange(info.getStartOffset(), info.getEndOffset())) {
            LOG.debug(LOG_PREFIX + "XmlElementInfo无效偏移量: start=" + info.getStartOffset() + ", end=" + info.getEndOffset());
            return false;
        }
        return true;
    }

    /**
     * 校验JavaElementInfo合法性
     */
    private static boolean validateJavaElementInfo(@NotNull JavaElementInfo info) {
        if (isEmpty(info.getFilePath())) {
            LOG.debug(LOG_PREFIX + "JavaElementInfo文件路径为空");
            return false;
        }
        if (!isValidOffsetRange(info.getStartOffset(), info.getEndOffset())) {
            LOG.debug(LOG_PREFIX + "JavaElementInfo无效偏移量: start=" + info.getStartOffset() + ", end=" + info.getEndOffset());
            return false;
        }
        return true;
    }

    private static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    private static boolean isValidOffsetRange(int start, int end) {
        return start >= 0 && end > start;
    }

    @Nullable
    private static XmlTag findXmlTagByRange(@NotNull XmlFile xmlFile, int startOffset, int endOffset, @NotNull XmlElementInfo info) {
        // 先尝试精确匹配
        PsiElement elementAtOffset = xmlFile.findElementAt(startOffset);
        if (elementAtOffset == null) {
            LOG.debug(LOG_PREFIX + "XML偏移量[" + startOffset + "]处无元素 (文件: " + info.getFilePath() + ")");
            return null;
        }

        XmlTag targetTag = PsiTreeUtil.getParentOfType(elementAtOffset, XmlTag.class);
        if (targetTag != null && isExactRangeMatch(targetTag.getTextRange(), startOffset, endOffset)) {
            return targetTag;
        }

        // 精确匹配失败，尝试范围匹配
        LOG.debug(LOG_PREFIX + "XML精确偏移量匹配失败，尝试包含范围匹配: 预期[" + startOffset + "," + endOffset + "], 实际[" + (targetTag != null ? targetTag.getTextRange().getStartOffset() : "null") + "," + (targetTag != null ? targetTag.getTextRange().getEndOffset() : "null") + "] (文件: " + info.getFilePath() + ")");

        return findInnermostXmlTagContainingRange(xmlFile, startOffset, endOffset);
    }

    /**
     * 查找包含偏移量范围的最内层XmlTag
     */
    @Nullable
    private static PsiElement findJavaElementByRange(@NotNull PsiJavaFile javaFile, int startOffset, int endOffset, @NotNull String elementType, @NotNull JavaElementInfo info) {
        PsiElement elementAtOffset = javaFile.findElementAt(startOffset);
        if (elementAtOffset == null) {
            LOG.debug(LOG_PREFIX + "Java偏移量[" + startOffset + "]处无PSI元素 (文件: " + info.getFilePath() + ")");
            return null;
        } else {
            return elementAtOffset;
        }
    }

    private static boolean isExactRangeMatch(TextRange range, int start, int end) {
        return range.getStartOffset() == start && range.getEndOffset() == end;
    }


    /**
     * 查找包含偏移量范围的最内层XmlTag
     */
    @Nullable
    private static XmlTag findInnermostXmlTagContainingRange(@NotNull XmlFile xmlFile, int startOffset, int endOffset) {
        XmlTag rootTag = xmlFile.getDocument() != null ? xmlFile.getDocument().getRootTag() : null;
        if (rootTag == null) {
            return null;
        }

        Collection<XmlTag> allTags = PsiTreeUtil.findChildrenOfType(rootTag, XmlTag.class);
        XmlTag bestMatch = null;
        int smallestRange = Integer.MAX_VALUE;

        for (XmlTag tag : allTags) {
            TextRange range = tag.getTextRange();
            if (range.contains(startOffset) && range.contains(endOffset)) {
                int rangeSize = range.getLength();
                if (rangeSize < smallestRange) {
                    smallestRange = rangeSize;
                    bestMatch = tag;
                }
            }
        }
        return bestMatch;
    }
}