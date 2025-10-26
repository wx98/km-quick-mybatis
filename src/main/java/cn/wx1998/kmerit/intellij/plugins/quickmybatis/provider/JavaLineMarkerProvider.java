package cn.wx1998.kmerit.intellij.plugins.quickmybatis.provider;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.services.JavaService;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.codeInsight.navigation.impl.PsiTargetPresentationRenderer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.xml.XmlTagImpl;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.CommonProcessors;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

import static cn.wx1998.kmerit.intellij.plugins.quickmybatis.util.Icons.IMAGES_MAPPER_METHOD_SVG;

public class JavaLineMarkerProvider extends RelatedItemLineMarkerProvider {

    // 获取日志记录器实例
    private static final Logger LOG = Logger.getInstance(JavaLineMarkerProvider.class);

    static Map<Class<?>, Supplier<AbstractElementFilter>> filterMap = Map.of(PsiClass.class, PsiClassAbstractElementFilter::new, PsiMethod.class, PsiMethodAbstractElementFilter::new, PsiField.class, PsiFieldAbstractElementFilter::new, PsiMethodCallExpression.class, PsiMethodCallExpressionElementFilter::new);

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element, @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        final var filter = createFilter(element);
        filter.collectNavigationMarkers(element, result);
    }

    // 定义一个工厂方法来创建过滤器
    private AbstractElementFilter createFilter(Object element) {
        for (Map.Entry<Class<?>, Supplier<AbstractElementFilter>> entry : filterMap.entrySet()) {
            if (entry.getKey().isInstance(element))
                return entry.getValue().get();
        }
        return new EmptyAbstractElementFilter();
    }


    private static class PsiClassAbstractElementFilter extends AbstractElementFilter {
        @Override
        protected Collection<? extends XmlTag> getResults(@NotNull PsiElement element) {
            // 收集与PsiClass相关的Mapper节点
            CommonProcessors.CollectProcessor<XmlTag> processor = new CommonProcessors.CollectProcessor<>();
            JavaService.getInstance(element.getProject()).processClass((PsiClass) element);
            return processor.getResults();
        }
    }

    private static class PsiMethodAbstractElementFilter extends AbstractElementFilter {
        @Override
        protected Collection<? extends XmlTag> getResults(@NotNull PsiElement element) {
            // 收集与PsiMethod相关的ID DOM元素节点
            CommonProcessors.CollectProcessor<XmlTag> processor = new CommonProcessors.CollectProcessor<>();
            JavaService.getInstance(element.getProject()).processMethod(((PsiMethod) element));
            return processor.getResults();
        }
    }

    private static class PsiFieldAbstractElementFilter extends AbstractElementFilter {

        @Override
        protected Collection<? extends XmlTag> getResults(@NotNull PsiElement element) {
            CommonProcessors.CollectProcessor<XmlTag> processor = new CommonProcessors.CollectProcessor<>();
            JavaService.getInstance(element.getProject()).processField((PsiField) element);
            return processor.getResults();
        }
    }

    private static class PsiMethodCallExpressionElementFilter extends AbstractElementFilter {
        @Override
        protected Collection<? extends XmlTag> getResults(@NotNull PsiElement element) {
            CommonProcessors.CollectProcessor<XmlTag> processor = new CommonProcessors.CollectProcessor<>();
            JavaService.getInstance(element.getProject()).processMethodCall(((PsiMethodCallExpression) element));
            return processor.getResults();
        }
    }

    public static class EmptyAbstractElementFilter extends AbstractElementFilter {
        @Override
        protected Collection<? extends XmlTag> getResults(@NotNull PsiElement element) {
            return Collections.emptyList();
        }
    }

}


abstract class AbstractElementFilter {

    private static @Nullable PsiElement getPsiElement(@NotNull PsiElement element) {
        PsiElement targetMarkerInfo = null;
        if (element instanceof PsiNameIdentifierOwner) {
            targetMarkerInfo = Objects.requireNonNull(((PsiNameIdentifierOwner) element).getNameIdentifier());

        }
        if (element instanceof PsiMethodCallExpression) {
            PsiReferenceExpression methodExpression = ((PsiMethodCallExpression) element).getMethodExpression();
            targetMarkerInfo = methodExpression.getReferenceNameElement();
        }
        return targetMarkerInfo;
    }

    // 获取结果集的抽象方法，子类需要实现该方法以返回具体的 DOM 元素集合
    protected abstract Collection<? extends XmlTag> getResults(@NotNull PsiElement element);

    // 收集导航标记的方法，用于为目标元素生成导航标记
    public void collectNavigationMarkers(@NotNull PsiElement element, @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        // 调用 getResults 方法获取与当前元素相关的 DOM 元素集合
        final Collection<? extends XmlTag> results = getResults(element);
        // 如果结果集不为空，则继续处理
        if (!results.isEmpty()) {
            // 获取目标标记信息的标识符
            PsiElement targetMarkerInfo = null;
            if (element instanceof PsiNameIdentifierOwner) {
                targetMarkerInfo = Objects.requireNonNull(((PsiNameIdentifierOwner) element).getNameIdentifier());

            }
            if (element instanceof PsiMethodCallExpression) {
                PsiReferenceExpression methodExpression = ((PsiMethodCallExpression) element).getMethodExpression();
                targetMarkerInfo = methodExpression.getReferenceNameElement();
            }

            // 修改后的工具提示生成逻辑
            String tooltipText = buildTooltipText(element, targetMarkerInfo);

            // 将 DOM 元素转换为对应的 XML 标签列表
            final List<XmlTag> xmlTags = new ArrayList<>();
            for (XmlTag tag : results) {
                xmlTags.add(tag);
            }
            // 创建导航标记构建器，设置图标、对齐方式、目标对象以及工具提示信息
            NavigationGutterIconBuilder<PsiElement> builder =
                    NavigationGutterIconBuilder.create(IconLoader.getIcon(IMAGES_MAPPER_METHOD_SVG, this.getClass()))
                            .setAlignment(GutterIconRenderer.Alignment.CENTER)
                            .setTargets(xmlTags)
                            .setTargetRenderer(getRenderer())
                            .setTooltipTitle(tooltipText);

            // 将生成的导航标记信息添加到结果集中
            if (targetMarkerInfo != null) {
                result.add(builder.createLineMarkerInfo(targetMarkerInfo));
            }
        }

    }

    public Supplier<PsiTargetPresentationRenderer<PsiElement>> getRenderer() {
        return new Supplier<PsiTargetPresentationRenderer<PsiElement>>() {
            @Override
            public PsiTargetPresentationRenderer<PsiElement> get() {
                return new PsiTargetPresentationRenderer<PsiElement>() {
                    @Nls
                    @NotNull
                    @Override
                    public String getElementText(@NotNull PsiElement element) {
                        XmlTag xmlTag = (XmlTag) element;
                        XmlAttribute attr = xmlTag.getAttribute("id", XmlUtil.XML_SCHEMA_URI);
                        attr = attr == null ? xmlTag.getAttribute("id") : attr;
                        String elementText = attr == null || attr.getValue() == null ? xmlTag.getName() : attr.getValue();
                        XmlTag parentTag = xmlTag.getParentTag();
                        String namespace = "";
                        if (parentTag != null) {
                            namespace = parentTag.getAttribute("namespace").getValue() + ".";
                        }
                        return namespace + elementText;
                    }

                    @Nls
                    @Nullable
                    @Override
                    public String getContainerText(@NotNull PsiElement element) {
                        XmlTagImpl xmlTag = (XmlTagImpl) element;
                        final PsiFile file = element.getContainingFile();
                        String databaseId = getDatabaseId(xmlTag);
                        return file.getVirtualFile().getName() + databaseId;
                    }

                    @NotNull
                    private String getDatabaseId(XmlTagImpl element) {
                        // 获取 XML 标签的 "databaseId" 属性值，如果不存在则返回空字符串
                        final XmlAttribute databaseIdAttr = element.getAttribute("databaseId");
                        String databaseId = null;
                        if (databaseIdAttr != null) {
                            databaseId = "," + databaseIdAttr.getValue();
                        }
                        if (databaseId == null) {
                            databaseId = "";
                        }
                        return databaseId;
                    }
                };
            }
        };
    }

    // 新增的工具提示构建方法
    private String buildTooltipText(PsiElement element, PsiElement target) {
        return " 导航到 XML";
    }

}
