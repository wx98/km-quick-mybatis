package cn.wx1998.kmerit.intellij.plugins.quickmybatis.provider;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.MyBatisCache;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.MyBatisCacheFactory;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info.JavaElementInfo;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.parser.MyBatisXmlStructure;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.services.JavaService;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.util.DomUtils;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.util.TagLocator;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.codeInsight.navigation.impl.PsiTargetPresentationRenderer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
    MyBatisCache myBatisCache;

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element, @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        // 如果当前元素不是目标元素，则直接返回
        if (!isTheElement(element)) {
            return;
        }
        myBatisCache = MyBatisCacheFactory.getRecommendedParser(element.getProject());

        // 应用处理逻辑并生成导航标记
        Optional<? extends PsiElement[]> processResult = apply((XmlToken) element);
        if (processResult.isPresent()) {
            PsiElement[] arrays = processResult.get();
            NavigationGutterIconBuilder<PsiElement> navigationGutterIconBuilder = NavigationGutterIconBuilder.create(getIcon());
            if (arrays.length > 0) {
                navigationGutterIconBuilder.setTooltipTitle(getTooltip(arrays[0], element));
            }
            navigationGutterIconBuilder.setTargets(arrays);
            navigationGutterIconBuilder.setTargetRenderer(getRender());
            RelatedItemLineMarkerInfo<PsiElement> lineMarkerInfo = navigationGutterIconBuilder.createLineMarkerInfo(element);
            result.add(lineMarkerInfo);
        }
    }

    private @NotNull Supplier<? extends PsiTargetPresentationRenderer<PsiElement>> getRender() {
        return () -> new PsiTargetPresentationRenderer<>() {
            @Nls
            @NotNull
            @Override
            public String getElementText(@NotNull PsiElement element) {
                if (element instanceof PsiClass psiClass) {
                    // 处理Java类（接口）
                    return psiClass.getName() != null ? psiClass.getName() : "未知类";
                } else if (element instanceof PsiMethod psiMethod) {
                    // 处理Java方法（展示方法名+参数类型简写）
                    String methodName = psiMethod.getName() != null ? psiMethod.getName() : "未知方法";
                    // 参数类型简写（如：String→S，Integer→I，无参数→()）
                    String paramShorthand = Arrays.stream(psiMethod.getParameterList().getParameters()).map(param -> param.getName()).collect(Collectors.joining(","));
                    return methodName + "(" + paramShorthand + ")";
                } else if (element instanceof PsiField psiField) {
                    // 处理字段
                    return psiField.getName() != null ? psiField.getName() : "未知字段";
                } else if (element instanceof PsiIdentifier psiIdentifier) {
                    PsiMethodCallExpression validMethodCallFromSqlSessionIdentifier = PsiTreeUtil.getParentOfType(psiIdentifier, PsiMethodCallExpression.class, true);
                    if (validMethodCallFromSqlSessionIdentifier == null) {
                        return "未找到方法调用"; // 增加空值防护
                    }

                    PsiFile containingFile = element.getContainingFile();
                    Project project = containingFile != null ? containingFile.getProject() : null;
                    int lineNumber = 0;
                    if (containingFile != null && project != null) {
                        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
                        Document document = documentManager.getDocument(containingFile);
                        if (document != null) {
                            int startOffset = validMethodCallFromSqlSessionIdentifier.getTextRange().getStartOffset();
                            lineNumber = document.getLineNumber(startOffset) + 1;
                        }
                    }
                    String firstParamValue = JavaService.parseExpression(validMethodCallFromSqlSessionIdentifier);
                    String methodName = validMethodCallFromSqlSessionIdentifier.getMethodExpression().getReferenceName();
                    String originalText = validMethodCallFromSqlSessionIdentifier.getText();
                    String finalText = firstParamValue != null ? JavaService.replaceFirstParam(originalText, methodName, firstParamValue) : originalText;

                    return " line:" + lineNumber + " -> " + finalText;
                } else {
                    // 默认处理（避免强转异常）
                    return element.getText().length() > 20 ? element.getText().substring(0, 20) + "..." : element.getText();
                }
            }

            /**
             * 容器文本：只展示文件名（简洁无路径）
             */
            @Nls
            @NotNull
            @Override
            public String getContainerText(@NotNull PsiElement element) {
                return element.getContainingFile().getVirtualFile().getName();
            }
        };
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
        boolean flag1 = element instanceof XmlToken;
        boolean flag2 = false;
        if (element instanceof XmlToken xmlToken) {
            flag2 = isTargetType(xmlToken);
        }
        boolean flag3 = DomUtils.isMybatisFile(element.getContainingFile());
        return flag1 && flag2 && flag3;
    }

    /**
     * 应用查找给定 MyBatis XML 元素对应的 Java 方法或类的逻辑。
     * 使用直接XML解析获取namespace和statement ID，通过缓存的JavaElementInfo+偏移量定位目标元素。
     *
     * @param from 表示 MyBatis XML 元素的源 XmlToken
     * @return 如果找到，则包含 PsiElements（Java 方法或类）数组的 Optional；否则为空 Optional
     */
    /**
     * 应用查找给定 MyBatis XML 元素对应的 Java 方法或类的逻辑。
     * 使用直接XML解析获取namespace和statement ID，通过缓存的JavaElementInfo+偏移量定位目标元素。
     *
     * @param from 表示 MyBatis XML 元素的源 XmlToken
     * @return 如果找到，则包含 PsiElements（Java 方法或类）数组的 Optional；否则为空 Optional
     */
    public Optional<? extends PsiElement[]> apply(@NotNull XmlToken from) {
        LOG.debug("Applying XmlLineMarkerProvider for element: " + from.getText());

        // 1. 校验包含文件是否为XML文件
        PsiElement containingFile = from.getContainingFile();
        if (!(containingFile instanceof XmlFile xmlFile)) {
            LOG.debug("Containing file is not an XmlFile");
            return Optional.empty();
        }

        // 2. 向上查找当前元素对应的XmlTag（确保定位到statement/mapper标签）
        PsiElement parent = from.getParent();
        while (parent != null && !(parent instanceof XmlTag)) {
            parent = parent.getParent();
        }
        if (parent == null) {
            LOG.debug("Could not find parent XmlTag for element: " + from.getText());
            return Optional.empty();
        }
        XmlTag currentTag = (XmlTag) parent;
        String tagName = currentTag.getName().toLowerCase();
        Project project = from.getProject();

        // 3. 处理mapper根标签：查找对应的Java接口/类（基于namespace关联的JavaElementInfo）
        if (MAPPER_TAG.equalsIgnoreCase(tagName)) {
            String namespace = currentTag.getAttributeValue("namespace");
            if (StringUtil.isEmpty(namespace)) {
                LOG.debug("Mapper tag missing namespace attribute");
                return Optional.empty();
            }

            // 从缓存获取namespace对应的Java元素信息
            Set<JavaElementInfo> javaElementInfos = myBatisCache.getSqlIdToJavaElements().get(namespace);
            if (javaElementInfos == null || javaElementInfos.isEmpty()) {
                LOG.debug("No JavaElementInfo found for namespace: " + namespace);
                return Optional.empty();
            }

            // 遍历JavaElementInfo，通过TagLocator定位具体的PsiClass
            List<PsiElement> targetClasses = new ArrayList<>();
            for (JavaElementInfo info : javaElementInfos) {
                PsiElement javaElement = TagLocator.findJavaTagByInfo(info, project);
                targetClasses.add(javaElement);
            }

            if (targetClasses.isEmpty()) {
                LOG.debug("No Java class found for namespace: " + namespace);
                return Optional.empty();
            }
            // 转换为PsiClass数组返回（保持原有返回类型兼容）
            return Optional.of(targetClasses.toArray(new PsiElement[0]));
        }

        // 4. 处理statement标签（select/insert/update/delete）：查找对应的Java方法
        else if (isStatementTag(tagName)) {
            String id = currentTag.getAttributeValue("id");
            if (StringUtil.isEmpty(id)) {
                LOG.debug("Statement tag missing id attribute");
                return Optional.empty();
            }

            // 获取根mapper标签的namespace，拼接完整SQL ID
            XmlTag rootTag = xmlFile.getDocument() != null ? xmlFile.getDocument().getRootTag() : null;
            if (rootTag == null) {
                LOG.debug("Could not find root mapper tag in XML file");
                return Optional.empty();
            }
            String namespace = rootTag.getAttributeValue("namespace");
            if (StringUtil.isEmpty(namespace)) {
                LOG.debug("Root mapper tag missing namespace attribute");
                return Optional.empty();
            }
            String fullSqlId = namespace + "." + id;

            // 从缓存获取SQL ID对应的Java元素信息
            Set<JavaElementInfo> javaElementInfos = myBatisCache.getJavaElementsBySqlId(fullSqlId);
            if (javaElementInfos == null || javaElementInfos.isEmpty()) {
                LOG.debug("No JavaElementInfo found for sqlId: " + fullSqlId);
                return Optional.empty();
            }

            // 遍历JavaElementInfo，通过TagLocator定位具体的PsiMethod
            List<PsiElement> targetMethods = new ArrayList<>();
            for (JavaElementInfo info : javaElementInfos) {
                PsiElement javaElement = TagLocator.findJavaTagByInfo(info, project);
                targetMethods.add(javaElement);
            }

            if (targetMethods.isEmpty()) {
                LOG.debug("No Java method found for sqlId: " + fullSqlId);
                return Optional.empty();
            }
            // 转换为PsiMethod数组返回（保持原有返回类型兼容）
            return Optional.of(targetMethods.toArray(new PsiElement[0]));
        }

        // 5. 非目标标签类型，返回空
        LOG.debug("Element is not a target tag type (mapper/select/insert/update/delete): " + tagName);
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
    public @Nullable("null means disabled") String getName() {
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


}
