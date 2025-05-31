package cn.wx1998.kmerit.intellij.plugins.quickmybatis.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The type Java utils.
 *
 * @author yanglin
 */
public final class JavaUtils {

    private JavaUtils() {
        throw new UnsupportedOperationException();
    }

    public static Optional<PsiMethod[]> findMethods(@NotNull Project project, @Nullable String clazzName, @Nullable String methodName) {
        if (StringUtil.isEmpty(clazzName) && StringUtil.isEmpty(methodName)) {
            return Optional.empty();
        }
        Optional<PsiClass[]> classes = findClasses(project, clazzName);
        if (classes.isPresent()) {

            List<PsiMethod> collect = Arrays.stream(classes.get())
                    .map(psiClass -> psiClass.findMethodsByName(methodName, true))
                    .flatMap(Arrays::stream)
                    .toList();
            return collect.isEmpty() ? Optional.empty() : Optional.of(collect.toArray(new PsiMethod[0]));

        }
        return Optional.empty();
    }

    public static Optional<PsiClass[]> findClasses(@NotNull Project project, @NotNull String clazzName) {
        return Optional.of(JavaPsiFacade.getInstance(project).findClasses(clazzName, GlobalSearchScope.allScope(project)));
    }

}
