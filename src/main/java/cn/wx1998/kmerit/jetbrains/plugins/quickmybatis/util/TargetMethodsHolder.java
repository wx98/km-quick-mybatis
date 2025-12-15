package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.util;

import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.setting.MyPluginSettings;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.ui.classFilter.ClassFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TargetMethodsHolder {
    private final Project project;

    public TargetMethodsHolder(Project project) {
        this.project = project;
    }

    /**
     * 根据配置重新加载目标方法
     */
    public Set<PsiMethod> reloadTargetMethods() {
        Set<PsiMethod> targetMethods = Collections.newSetFromMap(new ConcurrentHashMap<>());
        return ReadAction.compute(() -> {
            targetMethods.clear();
            MyPluginSettings settings = MyPluginSettings.getInstance();
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
            return targetMethods;
        });

    }
}