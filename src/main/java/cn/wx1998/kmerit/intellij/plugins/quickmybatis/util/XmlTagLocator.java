package cn.wx1998.kmerit.intellij.plugins.quickmybatis.util;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.JavaElementInfo;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.XmlElementInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
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
        int startOffset = element.getTextRange().getStartOffset();
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

        // 3. 获取根标签
        XmlDocument document = ((XmlFile) psiFile).getDocument();
        if (document == null) {
            LOG.debug("XML文件无文档节点: " + info.getFilePath());
            return null;
        }
        XmlTag rootTag = document.getRootTag();
        if (rootTag == null) {
            LOG.debug("XML文件无 root 标签: " + info.getFilePath());
            return null;
        }

        // 4. 使用XPath查找目标标签
        return findTagByXPath(rootTag, info.getXpath());
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
            if (child.getName().equals(tagName)) {
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

        return null;
    }
}