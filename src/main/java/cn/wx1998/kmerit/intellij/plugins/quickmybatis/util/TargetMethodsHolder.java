package cn.wx1998.kmerit.intellij.plugins.quickmybatis.util;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.setting.MyPluginSettings;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.ui.classFilter.ClassFilter;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TargetMethodsHolder {
    private final Project project;
    private final Set<PsiMethod> targetMethods;

    public TargetMethodsHolder(Project project) {
        this.project = project;
        // 修正 #1: 使用标准 JDK 创建线程安全的 Set
        this.targetMethods = Collections.newSetFromMap(new ConcurrentHashMap<>());
        reloadTargetMethods();
    }

    /**
     * 根据配置重新加载目标方法
     */
    public void reloadTargetMethods() {

        ReadAction.run(() -> {

            targetMethods.clear();

            MyPluginSettings settings = MyPluginSettings.getInstance();
            // 修正 #2: 使用你自己的配置获取方式
            // 假设 getClassNamesToMonitor() 返回一个 List<String>，包含 "org.apache.ibatis.session.SqlSession" 等
            ClassFilter[] classFilters = settings.getClassFilters();
            List<String> classNamesToMonitor = new ArrayList<>();
            for (ClassFilter classFilter : classFilters) {
                String pattern = classFilter.getPattern();
                classNamesToMonitor.add(pattern);
            }


            if (classNamesToMonitor == null || classNamesToMonitor.isEmpty()) {
                // 如果没有配置，可以添加一些默认值
                classNamesToMonitor = Arrays.asList("org.apache.ibatis.session.SqlSession");
            }

            for (String className : classNamesToMonitor) {
                if (className == null || className.isEmpty()) {
                    continue;
                }

                // 查找所有匹配该类名的 PsiClass
                String shortClassName = className.substring(className.lastIndexOf('.') + 1);
                PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(shortClassName, GlobalSearchScope.allScope(project));

                for (PsiClass psiClass : classes) {
                    if (className.equals(psiClass.getQualifiedName())) {
                        // 找到匹配的类，获取其所有公共方法
                        PsiMethod[] methods = psiClass.getMethods();
                        for (PsiMethod method : methods) {
                            if (method.hasModifierProperty(PsiModifier.PUBLIC)) {
                                // 过滤：只收集第一个参数是 String 类型的方法
                                PsiParameterList parameterList = method.getParameterList();
                                PsiParameter[] parameters = parameterList.getParameters();
                                if (parameters.length > 0) {
                                    PsiType firstParamType = parameters[0].getType();
                                    if ("java.lang.String".equals(firstParamType.getCanonicalText())) {
                                        targetMethods.add(method);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });

    }

    /**
     * 判断一个方法调用表达式是否是对目标方法的调用
     */
    public boolean isTargetMethodCall(@NotNull PsiMethodCallExpression callExpr) {
        PsiMethod resolvedMethod = callExpr.resolveMethod();
        return resolvedMethod != null && targetMethods.contains(resolvedMethod);
    }

    /**
     * 获取所有目标方法
     */
    public Set<PsiMethod> getTargetMethods() {
        return Collections.unmodifiableSet(targetMethods);
    }
}