package cn.wx1998.kmerit.intellij.plugins.quickmybatis.provider;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.parser.MyBatisXmlStructure;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.util.DomUtils;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.util.JavaUtils;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

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
public class XmlLineMarkerProvider extends RelatedItemLineMarkerProvider {

    private static final Logger LOG = Logger.getInstance(XmlLineMarkerProvider.class);

    /**
     * The constant MAPPER_TAG 表示 MyBatis Mapper 根标签名称。
     */
    private static final String MAPPER_TAG = MyBatisXmlStructure.MAPPER_TAG;

    /**
     * The constant TARGET_TYPES 包含此提供程序支持的 MyBatis 语句类型集合。
     * 这些包括 select、insert、update 和 delete 语句。
     */
    private static final Set<String> TARGET_TYPES = Set.of(MyBatisXmlStructure.SELECT_TAG, MyBatisXmlStructure.INSERT_TAG, MyBatisXmlStructure.UPDATE_TAG, MyBatisXmlStructure.DELETE_TAG);

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element, @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        // 如果当前元素不是目标元素，则直接返回
        if (!isTheElement(element)) {
            return;
        }
        // 应用处理逻辑并生成导航标记
        Optional<? extends PsiElement[]> processResult = apply((XmlToken) element);
        if (processResult.isPresent()) {
            PsiElement[] arrays = processResult.get();
            NavigationGutterIconBuilder<PsiElement> navigationGutterIconBuilder = NavigationGutterIconBuilder.create(getIcon());
            if (arrays.length > 0) {
                navigationGutterIconBuilder.setTooltipTitle(getTooltip(arrays[0], element));
            }
            navigationGutterIconBuilder.setTargets(arrays);
            RelatedItemLineMarkerInfo<PsiElement> lineMarkerInfo = navigationGutterIconBuilder.createLineMarkerInfo(element);
            result.add(lineMarkerInfo);
        }
    }

    /**
     * 确定给定的 PsiElement 是否为目标 MyBatis XML 元素。
     * 目标元素必须是 XmlToken，属于支持的类型（例如 select、insert 等），
     * 并且位于有效的 MyBatis XML 文件中。
     *
     * @param element 要检查的 PsiElement
     * @return 如果元素是目标 MyBatis XML 元素，则为 true；否则为 false
     */
    public boolean isTheElement(@NotNull PsiElement element) {
        LOG.debug("Checking if element is target type: " + element.getClass().getSimpleName());
        return element instanceof XmlToken
                && isTargetType((XmlToken) element)
                && DomUtils.isMybatisFile(element.getContainingFile());
    }

    /**
     * 应用查找给定 MyBatis XML 元素对应的 Java 方法或类的逻辑。
     * 使用直接XML解析而不是DOM来获取namespace和statement ID。
     *
     * @param from 表示 MyBatis XML 元素的源 XmlToken
     * @return 如果找到，则包含 PsiElements（Java 方法或类）数组的 Optional；否则为空 Optional
     */
    public Optional<? extends PsiElement[]> apply(@NotNull XmlToken from) {
        LOG.debug("Applying StatementLineMarkerProvider for element: " + from.getText());

        // 获取包含文件
        PsiElement containingFile = from.getContainingFile();
        if (!(containingFile instanceof XmlFile)) {
            LOG.debug("Containing file is not an XmlFile");
            return Optional.empty();
        }

        // 从元素向上查找XmlTag
        PsiElement parent = from.getParent();
        while (parent != null && !(parent instanceof XmlTag)) {
            parent = parent.getParent();
        }

        if (parent == null) {
            LOG.debug("Could not find parent XmlTag");
            return Optional.empty();
        }

        XmlTag currentTag = (XmlTag) parent;
        String tagName = currentTag.getName();

        // 获取项目
        final Project project = from.getProject();

        // 处理mapper根标签 - 查找对应的Java类
        if (MAPPER_TAG.equalsIgnoreCase(tagName)) {
            String namespace = currentTag.getAttributeValue("namespace");
            if (StringUtil.isEmpty(namespace)) {
                LOG.debug("Mapper tag missing namespace attribute");
                return Optional.empty();
            }

            LOG.debug("Finding Java classes for namespace: " + namespace);
            Optional<PsiClass[]> classes = JavaUtils.findClasses(project, namespace);
            int mapperIndex = namespace.lastIndexOf("Mapper");
            if (mapperIndex == -1) { // 避免 substring 越界，若没有 "Mapper" 后缀则直接返回 classes
                return classes;
            }
            String substring = namespace.substring(0, mapperIndex);
            Optional<PsiClass[]> classes1 = JavaUtils.findClasses(project, substring);
            // 合并两个 Optional 中的 PsiClass 数组
            return Optional.ofNullable(mergePsiClasses(classes, classes1));
        }

        // 处理statement标签 - 查找对应的Java方法
        else if (isStatementTag(tagName)) {
            // 获取statement的id属性
            String id = currentTag.getAttributeValue("id");
            if (StringUtil.isEmpty(id)) {
                LOG.debug("Statement tag missing id attribute");
                return Optional.empty();
            }

            // 查找根mapper标签获取namespace
            XmlTag rootTag = ((XmlFile) containingFile).getDocument().getRootTag();
            if (rootTag == null) {
                LOG.debug("Could not find root tag in XML file");
                return Optional.empty();
            }

            String namespace = rootTag.getAttributeValue("namespace");
            if (StringUtil.isEmpty(namespace)) {
                LOG.debug("Root tag missing namespace attribute");
                return Optional.empty();
            }

            LOG.debug("Finding Java methods for namespace: " + namespace + ", id: " + id);
            Optional<PsiMethod[]> methods = JavaUtils.findMethods(project, namespace, id);

            // 如果直接查找失败，尝试去掉namespace末尾的Mapper后缀再查找
            if (!methods.isPresent() || methods.get().length == 0) {
                int mapperIndex = namespace.lastIndexOf("Mapper");
                if (mapperIndex != -1 && mapperIndex == namespace.length() - 6) {
                    String shortNamespace = namespace.substring(0, mapperIndex);
                    LOG.debug("Trying to find methods with short namespace: " + shortNamespace);
                    return JavaUtils.findMethods(project, shortNamespace, id);
                }
            }

            return methods;
        }

        LOG.debug("Element is not a target tag type: " + tagName);
        return Optional.empty();
    }

    /**
     * 检查标签名称是否为MyBatis语句标签
     */
    private boolean isStatementTag(String tagName) {
        return TARGET_TYPES.contains(tagName.toLowerCase());
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
        LOG.debug("Checking target type for token: " + token.getText());
        Boolean targetType = null;
        String tokenText = token.getText();

        // 检查是否为mapper根标签
        if (MAPPER_TAG.equalsIgnoreCase(tokenText)) {
            // 检查当前元素是否为开始标签
            PsiElement nextSibling = token.getNextSibling();
            if (nextSibling instanceof PsiWhiteSpace) {
                // 如果下一个兄弟节点是空白符，则认为是目标类型
                targetType = true;
            }
        }

        // 检查是否为statement标签
        if (targetType == null) {
            if (TARGET_TYPES.contains(tokenText.toLowerCase())) {
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

        LOG.debug("Target type check result: " + targetType + " for token: " + tokenText);
        return targetType; // 返回最终的目标类型判断结果
    }

    /**
     * 返回此行标记提供程序的名称。
     * 此名称由 IDE 内部用于标识提供程序。
     *
     * @return 提供程序的名称，如果禁用则为 null
     */
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
        return IconLoader.getIcon(IMAGES_STATEMENT_SVG, this.getClass());
    }

    /**
     * Generate line marker tooltip when hovering over marker elements.
     * The tooltip provides information about the corresponding Java method or class.
     * If no specific method or class is found, fallback to showing text from the containing file.
     *
     * @param element Source PsiElement (e.g., MyBatis XML element)
     * @param target  Target PsiElement (e.g., Java method or class)
     * @return Tooltip text
     */
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

    /**
     * 合并两个 Optional<PsiClass[]> 中的数组，忽略空值，可选去重
     */
    private PsiClass[] mergePsiClasses(Optional<PsiClass[]> opt1, Optional<PsiClass[]> opt2) {
        List<PsiClass> mergedList = new ArrayList<>();

        // 添加第一个 Optional 中的元素（若存在）
        opt1.ifPresent(psiClasses -> mergedList.addAll(Arrays.asList(psiClasses)));

        // 添加第二个 Optional 中的元素（若存在）
        opt2.ifPresent(psiClasses -> mergedList.addAll(Arrays.asList(psiClasses)));

        // 若合并后为空，返回 null（最终会被 Optional 包装为 empty）
        if (mergedList.isEmpty()) {
            return null;
        }

        // 可选：去重（如果需要避免重复的 PsiClass）
        // 注意：PsiClass 是 PsiElement 的子类，可通过 equals 判断是否为同一元素
        List<PsiClass> distinctList = new ArrayList<>();
        for (PsiClass psiClass : mergedList) {
            if (!distinctList.contains(psiClass)) {
                distinctList.add(psiClass);
            }
        }

        // 转换为数组并返回
        return distinctList.toArray(new PsiClass[0]);
    }

}
