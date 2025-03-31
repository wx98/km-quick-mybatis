package cn.wx1998.kmerit.intellij.plugins.quickmybatis.provider;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.Optional;

/**
 * 简单行标记提供者类。
 * 该类是一个抽象类，用于提供简单的行标记功能，主要用于代码导航和提示。
 * 它通过判断元素类型、应用处理逻辑以及生成导航标记来实现功能。
 * 
 * @param <F> 继承自 PsiElement 的泛型类型，表示处理的元素类型
 * @param <T> 处理结果的泛型类型，表示导航目标的类型
 * @author yanglin
 */
public abstract class SimpleLineMarkerProvider<F extends PsiElement, T> extends RelatedItemLineMarkerProvider {

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element, @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        // 如果当前元素不是目标元素，则直接返回
        if (!isTheElement(element)) {
            return;
        }

        // 应用处理逻辑并生成导航标记
        Optional<? extends T[]> processResult = apply((F) element);
        if (processResult.isPresent()) {
            T[] arrays = processResult.get();
            NavigationGutterIconBuilder navigationGutterIconBuilder = NavigationGutterIconBuilder.create(getIcon());
            if (arrays.length > 0) {
                navigationGutterIconBuilder.setTooltipTitle(getTooltip(arrays[0], element));
            }
            navigationGutterIconBuilder.setTargets(arrays);
            RelatedItemLineMarkerInfo<PsiElement> lineMarkerInfo = navigationGutterIconBuilder.createLineMarkerInfo(element);
            result.add(lineMarkerInfo);
        }
    }

    /**
     * 判断是否为目标元素。
     * 该方法用于判断传入的 PsiElement 是否为目标元素。
     * 
     * @param element 当前处理的 PsiElement 元素
     * @return 如果是目标元素则返回 true，否则返回 false
     */
    public abstract boolean isTheElement(@NotNull PsiElement element);

    /**
     * 应用处理逻辑。
     * 该方法用于对传入的 PsiElement 应用处理逻辑，并返回处理结果。
     * 
     * @param from 当前处理的 PsiElement 元素
     * @return 处理结果的 Optional 对象
     */
    public abstract Optional<? extends T[]> apply(@NotNull F from);

    /**
     * 获取图标。
     * 该方法用于获取导航标记的图标。
     * 
     * @return 导航标记的图标对象
     */
    @Override
    @NotNull
    public abstract Icon getIcon();

    /**
     * 获取工具提示。
     * 该方法用于获取导航标记的工具提示信息。
     * 
     * @param array  处理结果数组中的一个元素
     * @param target 当前处理的 PsiElement 元素
     * @return 工具提示信息字符串
     */
    @NotNull
    public abstract String getTooltip(T array, @NotNull PsiElement target);
}
