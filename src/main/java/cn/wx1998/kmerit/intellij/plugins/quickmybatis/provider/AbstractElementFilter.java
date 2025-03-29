package cn.wx1998.kmerit.intellij.plugins.quickmybatis.provider;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

// 导入静态资源，用于加载图标
import static cn.wx1998.kmerit.intellij.plugins.quickmybatis.util.Icons.IMAGES_MAPPER_METHOD_SVG;

// 抽象类 AbstractElementFilter，用于提供基础的过滤逻辑
public abstract class AbstractElementFilter {

    // 获取结果集的抽象方法，子类需要实现该方法以返回具体的 DOM 元素集合
    protected abstract Collection<? extends DomElement> getResults(@NotNull PsiElement element);

    // 收集导航标记的方法，用于为目标元素生成导航标记
    public void collectNavigationMarkers(@NotNull PsiElement element, @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        // 调用 getResults 方法获取与当前元素相关的 DOM 元素集合
        final Collection<? extends DomElement> results = getResults(element);
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
            final List<XmlTag> xmlTags = results.stream().map(DomElement::getXmlTag).collect(Collectors.toList());
            // 创建导航标记构建器，设置图标、对齐方式、渲染器、目标对象以及工具提示信息
            NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder.create(IconLoader.getIcon(IMAGES_MAPPER_METHOD_SVG)).setAlignment(GutterIconRenderer.Alignment.CENTER).setCellRenderer(new GotoMapperXmlSchemaTypeRendererProvider.MyRenderer()).setTargets(xmlTags).setTooltipTitle(tooltipText);

            // 将生成的导航标记信息添加到结果集中
            if (targetMarkerInfo != null) {
                result.add(builder.createLineMarkerInfo(targetMarkerInfo));
            }
        }
    }

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


    // 新增的工具提示构建方法
    private String buildTooltipText(PsiElement element, PsiElement target) {
        return " 导航到 XML";
    }
}
