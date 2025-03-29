package cn.wx1998.kmerit.intellij.plugins.quickmybatis.provider;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.dom.Mapper;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.services.JavaService;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.util.CommonProcessors;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

public class MapperLineMarkerProvider extends RelatedItemLineMarkerProvider {

    // 获取日志记录器实例
    private static final Logger LOG = Logger.getInstance(MapperLineMarkerProvider.class);

    static Map<Class<?>, Supplier<AbstractElementFilter>> filterMap = Map.of(
            PsiClass.class, PsiClassAbstractElementFilter::new,
            PsiMethod.class, PsiMethodAbstractElementFilter::new,
            PsiField.class, PsiFieldAbstractElementFilter::new,
            PsiMethodCallExpression.class, PsiMethodCallExpressionElementFilter::new
    );

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element, @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        createFilter(element).collectNavigationMarkers(element, result);
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
        protected Collection<? extends DomElement> getResults(@NotNull PsiElement element) {
            // 收集与PsiClass相关的Mapper节点
            CommonProcessors.CollectProcessor<DomElement> processor = new CommonProcessors.CollectProcessor<>();
            // JavaService.getInstance(element.getProject()).processClass((PsiClass) element, processor);
            LOG.warn("1:\t" + element.getText().replaceAll("\n", ""));
            return processor.getResults();
        }
    }

    private static class PsiMethodAbstractElementFilter extends AbstractElementFilter {
        @Override
        protected Collection<? extends DomElement> getResults(@NotNull PsiElement element) {
            // 收集与PsiMethod相关的ID DOM元素节点
            CommonProcessors.CollectProcessor<DomElement> processor = new CommonProcessors.CollectProcessor<>();
            // JavaService.getInstance(element.getProject()).processMethod(((PsiMethod) element), processor);
            LOG.warn("2:\t" + element.getText().replaceAll("\n", ""));
            return processor.getResults();
        }
    }

    private static class PsiFieldAbstractElementFilter extends AbstractElementFilter {

        @Override
        protected Collection<? extends DomElement> getResults(@NotNull PsiElement element) {
            CommonProcessors.CollectProcessor<Mapper> processor = new CommonProcessors.CollectProcessor<>();
            //JavaService.getInstance(element.getProject()).processField((PsiField) element, processor);
            LOG.warn("3:\t" + element.getText().replaceAll("\n", ""));
            return processor.getResults();
        }
    }

    private static class PsiMethodCallExpressionElementFilter extends AbstractElementFilter {
        @Override
        protected Collection<? extends DomElement> getResults(@NotNull PsiElement element) {
            CommonProcessors.CollectProcessor<DomElement> processor = new CommonProcessors.CollectProcessor<>();
            JavaService.getInstance(element.getProject()).processMethodCall(((PsiMethodCallExpression) element), processor);
            LOG.warn("4:\t" + element.getText().replaceAll("\n", ""));
            return processor.getResults();
        }
    }

    public static class EmptyAbstractElementFilter extends AbstractElementFilter {
        @Override
        protected Collection<? extends DomElement> getResults(@NotNull PsiElement element) {
            return Collections.emptyList();
        }
    }

}
