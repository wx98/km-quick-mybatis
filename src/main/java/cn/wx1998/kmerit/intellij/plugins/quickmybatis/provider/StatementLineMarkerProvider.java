package cn.wx1998.kmerit.intellij.plugins.quickmybatis.provider;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.dom.*;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.util.DomUtils;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.util.JavaUtils;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Optional;

import static cn.wx1998.kmerit.intellij.plugins.quickmybatis.util.Icons.IMAGES_STATEMENT_SVG;

/**
 * xml 跳转到 java
 * StatementLineMarkerProvider 类用于在 MyBatis XML 文件中提供行标记功能。
 * 它帮助开发者在 MyBatis 映射器 XML 元素和对应的 Java 方法或类之间进行导航。
 * <p>
 * 主要功能包括：
 * 1. 识别 MyBatis XML 文件中的目标元素（如 select、insert、update、delete）。
 * 2. 提供 MyBatis XML 元素与 Java 方法或类之间的导航链接。
 * 3. 在悬停时显示相关信息的工具提示。
 */
public class StatementLineMarkerProvider extends SimpleLineMarkerProvider<XmlToken, PsiElement> {

    /**
     * The constant MAPPER_CLASS 表示 Mapper 类的小写名称。
     * 它用于在 MyBatis XML 文件中识别根映射器标签。
     */
    private static final String MAPPER_CLASS = Mapper.class.getSimpleName().toLowerCase();
    /**
     * The constant TARGET_TYPES 包含此提供程序支持的 MyBatis 语句类型集合。
     * 这些包括 select、insert、update 和 delete 语句。
     */
    private static final ImmutableSet<String> TARGET_TYPES = ImmutableSet.of(
            Select.class.getSimpleName().toLowerCase(),
            Insert.class.getSimpleName().toLowerCase(),
            Update.class.getSimpleName().toLowerCase(),
            Delete.class.getSimpleName().toLowerCase()
    );

    /**
     * 确定给定的 PsiElement 是否为目标 MyBatis XML 元素。
     * 目标元素必须是 XmlToken，属于支持的类型（例如 select、insert 等），
     * 并且位于有效的 MyBatis XML 文件中。
     *
     * @param element 要检查的 PsiElement
     * @return 如果元素是目标 MyBatis XML 元素，则为 true；否则为 false
     */
    @Override
    public boolean isTheElement(@NotNull PsiElement element) {
        return element instanceof XmlToken
                && isTargetType((XmlToken) element)
                && DomUtils.isMybatisFile(element.getContainingFile());
    }

    /**
     * 应用查找给定 MyBatis XML 元素对应的 Java 方法或类的逻辑。
     * 如果元素是 IdDomElement（例如 select、insert），则搜索匹配的 Java 方法。
     * 否则，尝试根据 XML 标签的 namespace 属性查找对应的 Java 类。
     *
     * @param from 表示 MyBatis XML 元素的源 XmlToken
     * @return 如果找到，则包含 PsiElements（Java 方法或类）数组的 Optional；否则为空 Optional
     */
    @Override
    public Optional<? extends PsiElement[]> apply(@NotNull XmlToken from) {
        // 获取与XML元素关联的DOM元素
        DomElement domElement = DomUtil.getDomElement(from);
        if (null == domElement) {
            return Optional.empty();
        }

        // 处理ID DOM元素（MyBatis方法级映射）
        else if (domElement instanceof IdDomElement) {
            final Project project = from.getProject();
            final Mapper mapper = DomUtil.getParentOfType(domElement, Mapper.class, true);
            if (mapper != null) {
                String namespace = mapper.getNamespace().getStringValue();
                String id = ((IdDomElement) domElement).getId().getRawText();

                Optional<PsiMethod[]> classes = JavaUtils.findMethods(project, namespace, id);
                if (classes.isPresent() && classes.get().length != 0) {
                    return classes;
                } else {
                    int mapperIndex = namespace.lastIndexOf("Mapper");
                    if (mapperIndex != -1 && mapperIndex == namespace.length() - 6) {
                        namespace = namespace.substring(0, mapperIndex);
                    }
                    return JavaUtils.findMethods(project, namespace, id);
                }
            } else {
                return Optional.empty();
            }
        } else {
            // 处理非ID DOM元素（通常对应Java类查找）
            XmlTag xmlTag = domElement.getXmlTag();
            if (xmlTag == null) {
                return Optional.empty();
            }
            // 从XML标签中提取namespace进行类查找
            String namespace = xmlTag.getAttributeValue("namespace");
            if (StringUtil.isEmpty(namespace)) {
                return Optional.empty();
            }

            final Optional<PsiClass[]> classes = JavaUtils.findClasses(from.getProject(), namespace);
            if (classes.isPresent() && classes.get().length != 0) {
                return Optional.empty();
            } else {
                JavaUtils.findClasses(from.getProject(), namespace);
                return Optional.empty();
            }
        }
    }

    /**
     * 检查给定的 XmlToken 是否表示目标 MyBatis XML 元素。
     * 目标元素可以是：
     * 1. 根映射器标签。
     * 2. 支持的 MyBatis 语句标签（例如 select、insert、update、delete）。
     *
     * @param token 要检查的 XmlToken
     * @return 如果 token 表示目标 MyBatis XML 元素，则为 true；否则为 false
     */
    private boolean isTargetType(@NotNull XmlToken token) {
        Boolean targetType = null;
        if (MAPPER_CLASS.equals(token.getText())) {
            // 检查当前元素是否为开始标签
            PsiElement nextSibling = token.getNextSibling();
            if (nextSibling instanceof PsiWhiteSpace) {
                // 如果下一个兄弟节点是空白符，则认为是目标类型
                targetType = true;
            }
        }
        if (targetType == null) {
            if (TARGET_TYPES.contains(token.getText())) {
                PsiElement parent = token.getParent();
                // 检查当前节点是否为标签
                if (parent instanceof XmlTag) {
                    // 检查当前元素是否为开始标签
                    PsiElement nextSibling = token.getNextSibling();
                    if (nextSibling instanceof PsiWhiteSpace) {
                        // 如果下一个兄弟节点是空白符，则认为是目标类型
                        targetType = true;
                    }
                }
            }
        }
        if (targetType == null) {
            targetType = false; // 如果未匹配到任何条件，则不是目标类型
        }
        return targetType; // 返回最终的目标类型判断结果
    }

    /**
     * 返回此行标记提供程序的名称。
     * 此名称由 IDE 内部用于标识提供程序。
     *
     * @return 提供程序的名称，如果禁用则为 null
     */
    @Override
    public @Nullable("null means disabled")
    String getName() {
        return "Statement line marker";
    }

    /**
     * 返回用于表示此行标记提供程序的图标。
     * 图标显示在标记的 MyBatis XML 元素旁边的编辑器边距中。
     *
     * @return 行标记的图标
     */
    @NotNull
    @Override
    public Icon getIcon() {
        return IconLoader.getIcon(IMAGES_STATEMENT_SVG);
    }

    /**
     * 生成悬停在标记元素上时的行标记工具提示。
     * 工具提示提供有关对应 Java 方法或类的信息。
     * 如果没有找到特定的方法或类，则回退到显示包含文件的文本。
     *
     * @param element 源 PsiElement（例如 MyBatis XML 元素）
     * @param target  目标 PsiElement（例如 Java 方法或类）
     * @return 工具提示文本
     */
    @Override
    @NotNull
    public String getTooltip(PsiElement element, @NotNull PsiElement target) {
        String text = null;
        if (element instanceof PsiMethod psiMethod) {
            PsiClass containingClass = psiMethod.getContainingClass();
            if (containingClass != null) {
                text = containingClass.getQualifiedName() + "#" + psiMethod.getName();
            }
        }
        if (text == null && element instanceof PsiClass psiClass) {
            text = psiClass.getQualifiedName();
        }
        if (text == null) {
            text = target.getContainingFile().getText();
        }
        return "找到源码 -> " + text;
    }

}
