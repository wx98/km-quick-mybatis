package cn.wx1998.kmerit.intellij.plugins.quickmybatis.util;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.JavaElementInfo;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.XmlElementInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class XmlTagLocator {

    private static final Logger LOG = Logger.getInstance(XmlTagLocator.class);

    /**
     * 创建 XML 元素信息（封装文件路径、行号等）
     */
    @Nullable
    public static XmlElementInfo createXmlElementInfo(@NotNull XmlTag element, @NotNull String sqlId, @NotNull String elementType) {
        PsiFile containingFile = element.getContainingFile();
        if (containingFile == null || containingFile.getVirtualFile() == null) return null;

        // 获取文件路径
        String filePath = containingFile.getVirtualFile().getPath();

        // 获取行号（文档行号从 0 开始，显示时通常 +1）
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(element.getProject());
        int startOffset = element.getTextRange().getStartOffset();
        int lineNumber = Objects.requireNonNull(documentManager.getDocument(containingFile)).getLineNumber(startOffset) + 1;

        String xpath = generateXPathForTag(element);
        return new XmlElementInfo(filePath, lineNumber, elementType, sqlId, xpath);
    }

    /**
     * 创建 Java 元素信息（封装文件路径、行号等）
     */
    @Nullable
    public static JavaElementInfo createJavaElementInfo(@NotNull PsiElement element, @NotNull String sqlId, @NotNull String elementType) {
        PsiFile containingFile = element.getContainingFile();
        if (containingFile == null || containingFile.getVirtualFile() == null) return null;

        // 获取文件路径
        String filePath = containingFile.getVirtualFile().getPath();

        // 获取行号（文档行号从 0 开始，显示时通常 +1）
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(element.getProject());
        TextRange textRange = element.getTextRange();
        int startOffset = textRange.getStartOffset();
        int lineNumber = Objects.requireNonNull(documentManager.getDocument(containingFile)).getLineNumber(startOffset) + 1;

        String xpath = generateXPathForPsiElement(element);

        return new JavaElementInfo(filePath, lineNumber, elementType, sqlId, xpath);
    }

    /**
     * 为PsiElement生成定位路径（Java元素使用简单路径格式）
     */
    private static String generateXPathForPsiElement(@NotNull PsiElement element) {
        List<String> pathSegments = new ArrayList<>();

        // 从元素向上遍历至文件根节点
        PsiElement current = element;
        while (current != null && !(current instanceof PsiFile)) {
            String segment = getPsiElementSegment(current);
            pathSegments.add(0, segment);
            current = current.getParent();
        }

        return String.join("/", pathSegments);
    }

    /**
     * 获取PsiElement的路径片段
     */
    private static String getPsiElementSegment(PsiElement element) {
        if (element instanceof PsiClass) {
            return "class:" + ((PsiClass) element).getName();
        } else if (element instanceof PsiMethod method) {
            String params = Arrays.stream(method.getParameterList().getParameters()).map(param -> param.getType().getPresentableText()).collect(Collectors.joining(","));
            return "method:" + method.getName() + "(" + params + ")";
        } else if (element instanceof PsiField) {
            return "field:" + ((PsiField) element).getName();
        } else if (element instanceof PsiIdentifier) {
            return "identifier:" + element.getText();
        }
        return element.getClass().getSimpleName() + ":" + element.getText().replaceAll("\\s+", " ");
    }

    /**
     * 为XML标签生成XPath表达式
     */
    @NotNull
    public static String generateXPathForTag(@NotNull XmlTag tag) {
        List<String> xpathSegments = new ArrayList<>();

        // 从当前标签向上遍历至根标签
        XmlTag currentTag = tag;
        while (currentTag != null) {
            StringBuilder segment = new StringBuilder(currentTag.getName());

            // 添加id属性条件（如果存在）
            String idAttr = currentTag.getAttributeValue("id");
            if (idAttr != null) {
                segment.append("[@id='").append(escapeXpathString(idAttr)).append("']");
            } else {
                // 计算同级同名称标签的位置索引
                XmlTag parent = currentTag.getParentTag();
                if (parent != null) {
                    XmlTag[] siblings = parent.getSubTags();
                    int index = 0;
                    int position = 1; // XPath索引从1开始
                    for (XmlTag sibling : siblings) {
                        if (sibling.getName().equals(currentTag.getName())) {
                            if (sibling == currentTag) {
                                position = index + 1;
                                break;
                            }
                            index++;
                        }
                    }
                    segment.append("[").append(position).append("]");
                }
            }

            xpathSegments.add(0, segment.toString());
            currentTag = currentTag.getParentTag();
        }

        // 前缀添加根节点标识
        return "/" + String.join("/", xpathSegments);
    }

    /**
     * 转义XPath字符串中的特殊字符
     */
    private static String escapeXpathString(String value) {
        return value.replace("'", "''");
    }

    /**
     * 根据XmlElementInfo定位XmlTag
     */
    @Nullable
    public static XmlTag findXmlTagByInfo(@NotNull XmlElementInfo info, @NotNull Project project) {
        // 1. 定位XML文件
        VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(info.getFilePath());
        if (vFile == null) {
            LOG.debug("找不到XML文件: " + info.getFilePath());
            return null;
        }

        // 2. 转换为PsiFile
        PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);
        if (!(psiFile instanceof XmlFile)) {
            LOG.debug("不是XML文件: " + info.getFilePath());
            return null;
        }
        XmlFile xmlFile = (XmlFile) psiFile;

        // 3. 获取根标签
        XmlDocument document = xmlFile.getDocument();
        if (document == null) {
            LOG.debug("XML文件无文档节点: " + info.getFilePath());
            // 尝试使用另一种方式获取根标签
            XmlTag[] tags = xmlFile.getRootTag().getSubTags();
            if (tags.length > 0) {
                XmlTag rootTag = tags[0];
                if (info.getTagName().equalsIgnoreCase("mapper") && info.getXpath().equals("/mapper")) {
                    // 如果是根节点mapper标签的查找请求，直接返回根标签
                    LOG.debug("直接返回根标签mapper: " + info.getFilePath());
                    return rootTag;
                }
            }
            return null;
        }

        XmlTag rootTag = document.getRootTag();
        if (rootTag == null) {
            LOG.debug("XML文件无 root 标签: " + info.getFilePath());
            return null;
        }

        // 4. 特殊处理根节点mapper标签的情况
        if (info.getTagName().equalsIgnoreCase("mapper") && info.getXpath().equals("/mapper")) {
            LOG.debug("匹配到根节点mapper标签: " + info.getFilePath());
            return rootTag;
        }

        // 5. 首先尝试使用XPath查找目标标签
        XmlTag tagByXPath = findTagByXPath(rootTag, info.getXpath());
        if (tagByXPath != null) {
            return tagByXPath;
        }

        // 6. 如果XPath查找失败，尝试使用标签名和SQL ID查找
        LOG.debug("XPath查找失败，尝试使用标签名和SQL ID查找: " + info.getTagName() + "@" + info.getSqlId());
        XmlTag tagByIdAndName = findTagByIdAndName(rootTag, info.getTagName(), info.getSqlId());
        if (tagByIdAndName != null) {
            return tagByIdAndName;
        }

        // 7. 针对行号为0的情况，尝试直接返回根标签
        if (info.getLineNumber() == 0 && info.getTagName().equalsIgnoreCase("mapper")) {
            LOG.debug("行号为0且标签名为mapper，尝试返回根标签: " + info.getFilePath());
            return rootTag;
        }

        // 8. 如果上述方法都失败，尝试使用行号作为最后手段（但跳过行号为0的情况）
        if (info.getLineNumber() > 0) {
            LOG.debug("标签名和SQL ID查找失败，尝试使用行号查找: " + info.getLineNumber());
            return findTagByLineNumber(xmlFile, info.getLineNumber());
        }

        // 9. 最后的后备方案：尝试查找文件中的第一个匹配标签名的标签
        LOG.debug("所有方法都失败，尝试查找第一个匹配标签名的标签: " + info.getTagName());
        return findFirstMatchingTagName(xmlFile, info.getTagName());
    }

    /**
     * 根据XPath表达式从根标签开始查找目标标签
     */
    @Nullable
    private static XmlTag findTagByXPath(@NotNull XmlTag rootTag, @NotNull String xpath) {
        // 解析XPath segments（去除开头的/）
        String[] segments = xpath.startsWith("/") ? xpath.substring(1).split("/") : xpath.split("/");

        XmlTag current = rootTag;
        for (String segment : segments) {
            current = findChildTag(current, segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /**
     * 从当前标签的子标签中查找匹配segment的标签
     */
    @Nullable
    private static XmlTag findChildTag(@NotNull XmlTag parent, @NotNull String segment) {
        // 解析标签名和条件（如: select[@id='findUser'] 或 resultMap[2]）
        String tagName = segment;
        String condition = null;
        int bracketIndex = segment.indexOf('[');
        if (bracketIndex > 0 && segment.endsWith("]")) {
            tagName = segment.substring(0, bracketIndex);
            condition = segment.substring(bracketIndex + 1, segment.length() - 1);
        }

        // 收集所有同名子标签
        List<XmlTag> candidates = new ArrayList<>();
        for (XmlTag child : parent.getSubTags()) {
            // 标签名比较忽略大小写，增加匹配成功率
            if (child.getName().equalsIgnoreCase(tagName)) {
                candidates.add(child);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // 处理条件
        if (condition == null) {
            return candidates.get(0); // 默认取第一个
        } else if (condition.startsWith("@id='")) {
            // id属性匹配
            String targetId = condition.substring(5, condition.length() - 1);
            for (XmlTag candidate : candidates) {
                if (targetId.equals(candidate.getAttributeValue("id"))) {
                    return candidate;
                }
            }
            // 如果精确匹配失败，尝试宽松匹配（忽略大小写）
            for (XmlTag candidate : candidates) {
                String idValue = candidate.getAttributeValue("id");
                if (idValue != null && targetId.equalsIgnoreCase(idValue)) {
                    return candidate;
                }
            }
        } else {
            // 索引匹配（数字）
            try {
                int index = Integer.parseInt(condition) - 1; // 转换为0基索引
                if (index >= 0 && index < candidates.size()) {
                    return candidates.get(index);
                }
            } catch (NumberFormatException e) {
                LOG.warn("无效的XPath索引格式: " + condition);
            }
        }

        // 如果所有条件匹配都失败，返回第一个匹配标签名的结果作为后备
        return candidates.get(0);
    }

    /**
     * 根据标签名和SQL ID查找标签
     */
    @Nullable
    private static XmlTag findTagByIdAndName(@NotNull XmlTag rootTag, @NotNull String tagName, @NotNull String sqlId) {
        // 递归搜索整个XML树
        return findTagByIdAndNameRecursive(rootTag, tagName, sqlId);
    }

    /**
     * 递归搜索标签
     */
    @Nullable
    private static XmlTag findTagByIdAndNameRecursive(@NotNull XmlTag currentTag, @NotNull String targetTagName, @NotNull String targetSqlId) {
        // 检查当前标签
        if (currentTag.getName().equalsIgnoreCase(targetTagName)) {
            String idAttr = currentTag.getAttributeValue("id");
            if (idAttr != null && (targetSqlId.equals(idAttr) || targetSqlId.endsWith("." + idAttr))) {
                return currentTag;
            }
        }

        // 递归检查子标签
        for (XmlTag child : currentTag.getSubTags()) {
            XmlTag result = findTagByIdAndNameRecursive(child, targetTagName, targetSqlId);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    /**
     * 根据行号查找标签
     */
    @Nullable
    private static XmlTag findTagByLineNumber(@NotNull XmlFile xmlFile, int lineNumber) {
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(xmlFile.getProject());
        var document = documentManager.getDocument(xmlFile);
        if (document == null) {
            return null;
        }

        try {
            // 将行号转换为偏移量（注意：文档行号从0开始，而我们的行号从1开始）
            int adjustedLineNumber = Math.max(0, lineNumber - 1);
            if (adjustedLineNumber >= document.getLineCount()) {
                return null;
            }

            int offset = document.getLineStartOffset(adjustedLineNumber);

            // 查找该位置的元素
            PsiElement element = xmlFile.findElementAt(offset);
            if (element == null) {
                return null;
            }

            // 向上查找XmlTag
            while (element != null && !(element instanceof XmlTag)) {
                element = element.getParent();
            }

            return element != null ? (XmlTag) element : null;
        } catch (Exception e) {
            LOG.warn("根据行号查找标签失败: " + lineNumber, e);
            return null;
        }
    }

    /**
     * 查找文件中第一个匹配指定标签名的标签
     */
    @Nullable
    private static XmlTag findFirstMatchingTagName(@NotNull XmlFile xmlFile, @NotNull String tagName) {
        // 先检查根标签
        XmlDocument document = xmlFile.getDocument();
        if (document != null) {
            XmlTag rootTag = document.getRootTag();
            if (rootTag != null && rootTag.getName().equalsIgnoreCase(tagName)) {
                return rootTag;
            }
        }

        // 遍历所有根标签
        for (XmlTag tag : xmlFile.getRootTag().getSubTags()) {
            if (tag.getName().equalsIgnoreCase(tagName)) {
                return tag;
            }
        }

        return null;
    }
}